package com.expensetracker.data.repository

import com.expensetracker.data.local.KnownMerchants
import com.expensetracker.data.local.MerchantCategoryDao
import com.expensetracker.data.local.MerchantKey
import com.expensetracker.data.local.TransactionDao
import com.expensetracker.data.model.ExpenseStats
import com.expensetracker.data.model.MerchantCategoryMapping
import com.expensetracker.data.model.MerchantStat
import com.expensetracker.data.model.PaymentSource
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.model.TransactionCategory
import com.expensetracker.data.model.TransactionType
import com.expensetracker.data.remote.FirestoreService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao,
    private val merchantCategoryDao: MerchantCategoryDao,
    private val firestoreService: FirestoreService
) {

    suspend fun insert(transaction: Transaction) {
        // Check type-specific mapping first (e.g., "amazon|CREDIT" → Salary).
        // Keys are normalized (MerchantKey) so name variants of the same shop match.
        val typeKey = MerchantKey.typeKey(transaction.merchant, transaction.type.name)
        val learnedByType = merchantCategoryDao.getCategoryForMerchant(typeKey)
        val learnedPlain = merchantCategoryDao.getCategoryForMerchant(MerchantKey.normalize(transaction.merchant))
        val learned = learnedByType ?: learnedPlain
        val final = if (learned != null) transaction.copy(category = learned) else transaction

        // Smart dedup: if the same transaction is already stored, keep whichever SMS
        // carries more information (e.g. "spent at SWIGGY via UPI" beats "Rs.200 debited").
        val existingDup = findDuplicateInDb(final)
        if (existingDup != null) {
            if (informationScore(final) > informationScore(existingDup)) {
                // Replace in place — preserve the stored row's id and any manual edits
                // (a user-corrected category/merchant should never be lost to a re-scan).
                dao.update(mergeRicher(existingDup, final))
            }
            return
        }
        dao.insert(final)
    }

    /**
     * How much useful information a transaction carries. Higher = keep this one when two
     * SMS describe the same debit/credit. Concrete signals beat generic fallbacks.
     */
    private fun informationScore(t: Transaction): Int {
        var score = 0
        if (t.merchant.isNotBlank() && !t.merchant.equals("Unknown", ignoreCase = true)) score += 4
        if (t.category != TransactionCategory.OTHER) score += 3
        if (t.source != PaymentSource.UNKNOWN) score += 2
        if (t.accountInfo.isNotBlank()) score += 2
        // Tiebreaker: a longer body usually means more detail (payee, ref, etc.)
        score += (t.rawSms.length / 40).coerceAtMost(3)
        return score
    }

    /**
     * Merge the richer SMS into the existing stored row, keeping the stored row's id so
     * Room updates instead of inserting. The richer body/merchant/source/category win,
     * but a stored non-OTHER category (likely a user correction) is preserved.
     */
    private fun mergeRicher(existing: Transaction, richer: Transaction): Transaction {
        val keepCategory = if (existing.category != TransactionCategory.OTHER) existing.category
        else richer.category
        return richer.copy(
            id = existing.id,
            category = keepCategory,
            isSelfTransfer = existing.isSelfTransfer || richer.isSelfTransfer,
            // A split is a user action, never present on a freshly parsed SMS — always
            // carry the stored split forward so a background re-scan can't erase it.
            splitJson = existing.splitJson,
            reimbursedAmount = existing.reimbursedAmount,
            synced = false
        )
    }

    suspend fun insertAll(transactions: List<Transaction>) {
        val mappings = merchantCategoryDao.getAll().associate { it.merchant to it.category }

        // Step 1: Dedup within the batch itself — keeping the richer of same-scan duplicates.
        val batchDeduped = deduplicateBatch(transactions)

        // Step 2: Apply learned category mappings.
        val withCategories = batchDeduped.map { txn ->
            val typeKey = MerchantKey.typeKey(txn.merchant, txn.type.name)
            val learned = mappings[typeKey] ?: mappings[MerchantKey.normalize(txn.merchant)]
            if (learned != null) txn.copy(category = learned) else txn
        }

        // Step 3: Reconcile against existing DB records. A brand-new transaction is inserted;
        // one that duplicates a stored row replaces it only if it carries more information.
        val toInsert = mutableListOf<Transaction>()
        for (txn in withCategories) {
            val existingDup = findDuplicateInDb(txn)
            if (existingDup == null) {
                toInsert.add(txn)
            } else if (informationScore(txn) > informationScore(existingDup)) {
                dao.update(mergeRicher(existingDup, txn))
            }
        }
        dao.insertAll(toInsert)
    }

    /**
     * Smart dedup within a batch:
     * - Same amount + same account + same day = duplicate (multiple SMS for same debit)
     * - Same amount + within 2 min = duplicate (same SMS processed twice)
     * - Otherwise NOT a duplicate (you can buy ₹200 coffee twice in a day)
     */
    private fun deduplicateBatch(transactions: List<Transaction>): List<Transaction> {
        val result = mutableListOf<Transaction>()
        val sorted = transactions.sortedBy { it.timestamp }

        for (txn in sorted) {
            val dupIndex = result.indexOfFirst { existing ->
                if (existing.amount != txn.amount || existing.type != txn.type) return@indexOfFirst false

                val minutesApart = kotlin.math.abs(
                    java.time.Duration.between(existing.timestamp, txn.timestamp).toMinutes()
                )

                // Within 2 minutes: same amount/type — but only a duplicate if the two SMS
                // don't prove they're DIFFERENT payments (different ref or different merchant).
                if (minutesApart <= 2) return@indexOfFirst sameTransactionEvent(existing, txn)

                // Same account + same day: still require they aren't clearly separate payments.
                val sameAccount = existing.accountInfo.isNotBlank() &&
                        existing.accountInfo == txn.accountInfo
                val sameDay = existing.timestamp.toLocalDate() == txn.timestamp.toLocalDate()

                sameAccount && sameDay && sameTransactionEvent(existing, txn)
            }
            if (dupIndex < 0) {
                result.add(txn)
            } else if (informationScore(txn) > informationScore(result[dupIndex])) {
                // Two SMS in the same scan describe one transaction — keep the richer one.
                result[dupIndex] = mergeRicher(result[dupIndex], txn)
            }
        }
        return result
    }

    private val refRegex = Regex(
        """(?:UPI|IMPS|NEFT|RTGS)?\s*Ref(?:erence)?\s*(?:no\.?|number|:|-)?\s*(\d{6,})""",
        RegexOption.IGNORE_CASE
    )

    private fun extractRef(body: String): String =
        refRegex.find(body)?.groupValues?.get(1).orEmpty()

    private fun isRealMerchant(m: String): Boolean =
        m.isNotBlank() && !m.equals("Unknown", ignoreCase = true) && !m.equals("Salary", ignoreCase = true)

    /**
     * Decide whether two same-amount, same-type transactions are really the SAME event
     * (one payment that generated two SMS — e.g. bank + UPI app) versus two SEPARATE
     * payments that happen to be the same amount close in time.
     *
     * They are DIFFERENT payments if either:
     *  - both SMS carry a reference number and the numbers differ, or
     *  - both name a real (known) merchant and the merchants differ.
     * Otherwise we treat them as the same event and dedup.
     */
    private fun sameTransactionEvent(a: Transaction, b: Transaction): Boolean {
        val refA = extractRef(a.rawSms)
        val refB = extractRef(b.rawSms)
        if (refA.isNotBlank() && refB.isNotBlank() && refA != refB) return false

        if (isRealMerchant(a.merchant) && isRealMerchant(b.merchant) &&
            !MerchantKey.normalize(a.merchant).equals(MerchantKey.normalize(b.merchant), ignoreCase = true)
        ) return false

        return true
    }

    /**
     * Check against DB:
     * - Same amount + within 2 min = duplicate
     * - Same amount + same account + same day = duplicate
     * Both now require the two SMS to look like the SAME payment (see sameTransactionEvent) —
     * two separate ₹200 payments close in time are kept as two transactions.
     */
    private suspend fun isDuplicateInDb(transaction: Transaction): Boolean =
        findDuplicateInDb(transaction) != null

    /**
     * Like isDuplicateInDb but returns the matching stored row (or null) so the caller can
     * compare information and keep the richer message. Narrow window wins over same-day.
     * Candidates that are clearly a SEPARATE payment (different ref / different merchant)
     * are filtered out, so genuine same-amount purchases within 2 min are NOT merged.
     */
    private suspend fun findDuplicateInDb(transaction: Transaction): Transaction? {
        val narrowStart = transaction.timestamp.minusMinutes(2)
        val narrowEnd = transaction.timestamp.plusMinutes(2)
        dao.findDuplicatesNarrow(
            amount = transaction.amount,
            type = transaction.type.name,
            windowStart = narrowStart,
            windowEnd = narrowEnd
        ).firstOrNull { sameTransactionEvent(it, transaction) }?.let { return it }

        if (transaction.accountInfo.isNotBlank()) {
            val dayStart = transaction.timestamp.toLocalDate().atStartOfDay()
            val dayEnd = dayStart.plusDays(1)
            dao.findDuplicatesSameAccount(
                amount = transaction.amount,
                type = transaction.type.name,
                accountInfo = transaction.accountInfo,
                windowStart = dayStart,
                windowEnd = dayEnd
            ).firstOrNull { sameTransactionEvent(it, transaction) }?.let { return it }
        }
        return null
    }

    suspend fun update(transaction: Transaction) {
        dao.update(transaction)
    }

    /**
     * Rename a transaction's merchant (used to fix rows that parsed as "Unknown").
     * If the row is still uncategorized, re-run categorization against the new name —
     * so renaming "Unknown" -> "Swiggy" both names it AND files it under Food. A learned
     * mapping for the new name wins over the keyword DB; a category the user already set
     * (non-OTHER) is never overridden.
     */
    suspend fun renameMerchant(transaction: Transaction, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        var updated = transaction.copy(merchant = trimmed)
        if (transaction.category == TransactionCategory.OTHER) {
            val learned = merchantCategoryDao.getCategoryForMerchant(
                MerchantKey.typeKey(trimmed, transaction.type.name)
            ) ?: merchantCategoryDao.getCategoryForMerchant(MerchantKey.normalize(trimmed))
            val resolved = learned ?: KnownMerchants.categorize(trimmed, trimmed)
            if (resolved != null && resolved != TransactionCategory.OTHER) {
                updated = updated.copy(category = resolved)
            }
        }
        dao.update(updated.copy(synced = false))
    }

    private val gson = com.google.gson.Gson()
    private val splitListType =
        object : com.google.gson.reflect.TypeToken<List<com.expensetracker.data.model.SplitParticipant>>() {}.type

    /** Deserialize a transaction's stored split, or empty list if it isn't split. */
    fun parseSplit(transaction: Transaction): List<com.expensetracker.data.model.SplitParticipant> {
        if (transaction.splitJson.isBlank()) return emptyList()
        return try {
            gson.fromJson(transaction.splitJson, splitListType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Save (or clear) a split on a transaction. `amount` is never touched — only the
     * split ledger and the denormalized reimbursed total, so effective spend
     * (amount - reimbursedAmount) reflects your own share plus any unpaid friend shares.
     * An empty list clears the split entirely.
     */
    suspend fun saveSplit(
        transaction: Transaction,
        participants: List<com.expensetracker.data.model.SplitParticipant>
    ) {
        // Only friend shares that are still unpaid stay "out of pocket"; drop any share
        // that exceeds the total defensively so effectiveAmount can't go negative.
        val friendTotal = participants.sumOf { it.share }.coerceIn(0.0, transaction.amount)
        val reimbursed = participants.filter { it.paid }.sumOf { it.share }.coerceIn(0.0, friendTotal)
        val json = if (participants.isEmpty()) "" else gson.toJson(participants)
        dao.update(
            transaction.copy(
                splitJson = json,
                reimbursedAmount = reimbursed,
                synced = false
            )
        )
    }

    suspend fun delete(transaction: Transaction) {
        dao.deleteById(transaction.id)
    }

    /**
     * Re-run the known-merchants categorizer over already-stored transactions.
     * Categories are only computed once at insert time, so improvements to the keyword
     * DB (e.g. "nut n spice" -> Groceries) never reach old rows without this pass.
     *
     * Only touches rows still sitting in OTHER, so it never overrides a category the
     * user set manually or one that was already detected correctly.
     * Returns the number of transactions re-categorized.
     */
    suspend fun recategorizeUncategorized(): Int {
        val all = dao.getAllTransactions().first()
        var updated = 0
        for (txn in all) {
            if (txn.category != TransactionCategory.OTHER) continue
            // Prefer a learned mapping for this merchant; fall back to the keyword DB.
            val learned = merchantCategoryDao.getCategoryForMerchant(
                MerchantKey.typeKey(txn.merchant, txn.type.name)
            ) ?: merchantCategoryDao.getCategoryForMerchant(MerchantKey.normalize(txn.merchant))
            val newCategory = learned
                ?: com.expensetracker.data.local.KnownMerchants.categorize(txn.merchant, txn.rawSms)
            if (newCategory != null && newCategory != TransactionCategory.OTHER) {
                dao.update(txn.copy(category = newCategory))
                updated++
            }
        }
        return updated
    }

    /**
     * Enrich stored bank debits using merchant names harvested from loyalty/points SMS.
     * A hint ("Nuts n Spices", Rs 1174, ~7:08 PM) is matched to a stored DEBIT of the same
     * amount within a time window whose merchant is still "Unknown", and fills in the real
     * merchant name + category. Never overrides a merchant/category the user already set.
     * Returns the number of transactions enriched.
     */
    suspend fun applyMerchantHints(hints: List<com.expensetracker.sms.SmsParser.MerchantHint>): Int {
        if (hints.isEmpty()) return 0
        var enriched = 0
        for (hint in hints) {
            // Loyalty SMS often arrives minutes after the debit — use a generous ±60 min window.
            val start = hint.timestamp.minusMinutes(60)
            val end = hint.timestamp.plusMinutes(60)
            val candidates = dao.findDuplicatesNarrow(
                amount = hint.amount,
                type = TransactionType.DEBIT.name,
                windowStart = start,
                windowEnd = end
            )
            // Only fill in rows that lack a real merchant name (don't clobber good data).
            val target = candidates.firstOrNull {
                it.merchant.isBlank() || it.merchant.equals("Unknown", ignoreCase = true)
            } ?: continue

            val category = if (target.category == TransactionCategory.OTHER) {
                KnownMerchants.categorize(hint.merchant, hint.merchant) ?: target.category
            } else target.category

            dao.update(target.copy(merchant = hint.merchant, category = category, synced = false))
            enriched++
        }
        return enriched
    }

    /**
     * Delete already-stored SMS rows that older builds wrongly captured but the CURRENT
     * rules reject — real-estate ads ("Price starts Rs.X ... EMI onwards"), loyalty/points
     * SMS ("bill value ... points"), etc. Categorization improvements never remove these;
     * only this pass does. Manual entries are left untouched (getAllSmsTransactions excludes them).
     * Returns the number of rows purged.
     */
    suspend fun purgeNonTransactional(parser: com.expensetracker.sms.SmsParser): Int {
        val smsTransactions = dao.getAllSmsTransactions()
        var purged = 0
        for (txn in smsTransactions) {
            if (!parser.isStillTransactional(txn.rawSms)) {
                dao.deleteById(txn.id)
                purged++
            }
        }
        return purged
    }

    suspend fun repairTransactionTypes(parser: com.expensetracker.sms.SmsParser) {
        val smsTransactions = dao.getAllSmsTransactions()
        for (txn in smsTransactions) {
            val reparsed = parser.reparseType(txn.rawSms)
            if (reparsed != txn.type) {
                dao.update(txn.copy(type = reparsed))
            }
        }
    }

    suspend fun updateCategoryAndLearn(transaction: Transaction, newCategory: TransactionCategory) {
        // Retroactively recategorize every stored transaction whose merchant normalizes to the
        // same key AND has the same type. Matching on the normalized key (not the exact string)
        // means correcting "SWIGGY*ORDER1" also fixes "Swiggy Ltd", "swiggy", etc.
        // Same-type only, so an Amazon salary (CREDIT) correction never touches Amazon shopping (DEBIT).
        val normalized = MerchantKey.normalize(transaction.merchant)
        val affected = dao.getAllTransactions().first().filter {
            it.type == transaction.type && MerchantKey.normalize(it.merchant) == normalized
        }
        for (txn in affected) {
            if (txn.category != newCategory) dao.update(txn.copy(category = newCategory))
        }

        // Save ONLY type-specific mapping: "normalizedMerchant|TYPE" → category.
        // Do NOT save a plain merchant mapping — it would override the other type.
        val mappingKey = MerchantKey.typeKey(transaction.merchant, transaction.type.name)
        merchantCategoryDao.save(
            MerchantCategoryMapping(
                merchant = mappingKey,
                category = newCategory
            )
        )

        // Sync mapping to cloud
        try {
            firestoreService.syncMerchantMappings(
                listOf(mappingKey to newCategory.name)
            )
        } catch (_: Exception) {}
    }

    fun getAllTransactions(): Flow<List<Transaction>> = dao.getAllTransactions()

    fun getTransactionsBetween(start: LocalDateTime, end: LocalDateTime): Flow<List<Transaction>> =
        dao.getTransactionsBetween(start, end)

    fun getByCategory(category: TransactionCategory): Flow<List<Transaction>> =
        dao.getByCategory(category)

    fun getExpenseStats(start: LocalDateTime, end: LocalDateTime): Flow<ExpenseStats> {
        return combine(
            dao.getTotalSpent(start, end),
            dao.getTotalIncome(start, end),
            dao.getCategoryBreakdown(start, end),
            dao.getTopMerchants(start, end)
        ) { spent, income, categories, merchants ->
            val categoryMap = categories.associate { it.category to it.total }
            val savings = categoryMap[TransactionCategory.SAVINGS] ?: 0.0
            ExpenseStats(
                totalSpent = spent ?: 0.0,
                totalIncome = income ?: 0.0,
                totalSavings = savings,
                categoryBreakdown = categoryMap,
                topMerchants = merchants.map {
                    MerchantStat(it.merchant, it.totalSpent, it.transactionCount)
                }
            )
        }
    }

    fun getDailySpend(start: LocalDateTime, end: LocalDateTime): Flow<Map<String, Double>> {
        return dao.getTransactionsBetween(start, end).map { transactions ->
            transactions
                .filter { it.type == TransactionType.DEBIT }
                .groupBy { it.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE) }
                // effectiveAmount subtracts money paid back, so split debits count only your share.
                .mapValues { (_, txns) -> txns.sumOf { it.effectiveAmount } }
        }
    }

    fun getTransactionCount(): Flow<Int> = dao.getTransactionCount()

    suspend fun backupToCloud() {
        firestoreService.ensureAuthenticated()
        val allTransactions = dao.getAllTransactions().first()
        if (allTransactions.isNotEmpty()) {
            firestoreService.syncAllTransactions(allTransactions)
        }
        val mappings = merchantCategoryDao.getAll().map { it.merchant to it.category.name }
        if (mappings.isNotEmpty()) {
            firestoreService.syncMerchantMappings(mappings)
        }
    }

    suspend fun restoreFromCloud(): Int {
        firestoreService.ensureAuthenticated()
        val cloudTransactions = firestoreService.fetchAllTransactions()
        var restored = 0
        for (txn in cloudTransactions) {
            val exists = try {
                isDuplicateInDb(txn)
            } catch (_: Exception) { false }
            if (!exists) {
                dao.insert(txn)
                restored++
            }
        }
        // Restore merchant mappings
        val mappings = firestoreService.fetchMerchantMappings()
        for ((merchant, category) in mappings) {
            try {
                merchantCategoryDao.save(
                    MerchantCategoryMapping(merchant, TransactionCategory.valueOf(category))
                )
            } catch (_: Exception) {}
        }
        return restored
    }
}
