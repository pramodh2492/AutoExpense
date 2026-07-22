package com.expensetracker.data.repository

import com.expensetracker.data.local.MerchantCategoryDao
import com.expensetracker.data.local.TransactionDao
import com.expensetracker.data.model.ExpenseStats
import com.expensetracker.data.model.MerchantCategoryMapping
import com.expensetracker.data.model.MerchantStat
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
        // Smart dedup: skip if same amount within 5-minute window already exists
        if (isDuplicateInDb(transaction)) return

        val learned = merchantCategoryDao.getCategoryForMerchant(transaction.merchant.lowercase())
        val final = if (learned != null) transaction.copy(category = learned) else transaction
        dao.insert(final)
    }

    suspend fun insertAll(transactions: List<Transaction>) {
        val mappings = merchantCategoryDao.getAll().associate { it.merchant to it.category }

        // Step 1: Dedup within the batch itself (same amount within 5 min = keep first only)
        val batchDeduped = deduplicateBatch(transactions)

        // Step 2: Dedup against existing DB records
        val nonDuplicates = batchDeduped.filter { !isDuplicateInDb(it) }

        val updated = nonDuplicates.map { txn ->
            val learned = mappings[txn.merchant.lowercase()]
            if (learned != null) txn.copy(category = learned) else txn
        }
        dao.insertAll(updated)
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
            val isDupInResult = result.any { existing ->
                if (existing.amount != txn.amount || existing.type != txn.type) return@any false

                val minutesApart = kotlin.math.abs(
                    java.time.Duration.between(existing.timestamp, txn.timestamp).toMinutes()
                )

                // Within 2 minutes = always duplicate
                if (minutesApart <= 2) return@any true

                // Same account + same day = duplicate
                val sameAccount = existing.accountInfo.isNotBlank() &&
                        existing.accountInfo == txn.accountInfo
                val sameDay = existing.timestamp.toLocalDate() == txn.timestamp.toLocalDate()

                sameAccount && sameDay
            }
            if (!isDupInResult) {
                result.add(txn)
            }
        }
        return result
    }

    /**
     * Check against DB:
     * - Same amount + within 2 min = duplicate
     * - Same amount + same account + same day = duplicate
     */
    private suspend fun isDuplicateInDb(transaction: Transaction): Boolean {
        // Narrow: same amount + same type within 2 minutes
        val narrowStart = transaction.timestamp.minusMinutes(2)
        val narrowEnd = transaction.timestamp.plusMinutes(2)
        val narrowCount = dao.countDuplicatesNarrow(
            amount = transaction.amount,
            type = transaction.type.name,
            windowStart = narrowStart,
            windowEnd = narrowEnd
        )
        if (narrowCount > 0) return true

        // Same account + same type + same day
        if (transaction.accountInfo.isNotBlank()) {
            val dayStart = transaction.timestamp.toLocalDate().atStartOfDay()
            val dayEnd = dayStart.plusDays(1)
            val accountCount = dao.countDuplicatesSameAccount(
                amount = transaction.amount,
                type = transaction.type.name,
                accountInfo = transaction.accountInfo,
                windowStart = dayStart,
                windowEnd = dayEnd
            )
            if (accountCount > 0) return true
        }

        return false
    }

    suspend fun update(transaction: Transaction) {
        dao.update(transaction)
    }

    suspend fun delete(transaction: Transaction) {
        dao.deleteById(transaction.id)
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
        // Update ALL transactions from this merchant (past + current)
        dao.updateCategoryForMerchant(transaction.merchant, newCategory)
        // Save the mapping so future transactions auto-categorize
        merchantCategoryDao.save(
            MerchantCategoryMapping(
                merchant = transaction.merchant.lowercase(),
                category = newCategory
            )
        )
        // Sync mapping to cloud
        try {
            firestoreService.syncMerchantMappings(
                listOf(transaction.merchant.lowercase() to newCategory.name)
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
                .mapValues { (_, txns) -> txns.sumOf { it.amount } }
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
