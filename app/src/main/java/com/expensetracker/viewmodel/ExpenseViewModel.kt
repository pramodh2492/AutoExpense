package com.expensetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.analytics.AnalyticsHelper
import com.expensetracker.data.local.Insight
import com.expensetracker.data.local.InsightsEngine
import com.expensetracker.data.local.UserPreferences
import com.expensetracker.data.model.ExpenseStats
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.model.TransactionCategory
import com.expensetracker.data.model.TransactionType
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.sms.SmsScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val smsScanner: SmsScanner,
    private val smsParser: com.expensetracker.sms.SmsParser,
    private val insightsEngine: InsightsEngine,
    private val userPreferences: UserPreferences,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(TimePeriod.THIS_MONTH)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod.asStateFlow()

    val transactions: StateFlow<List<Transaction>> = repository.getAllTransactions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val transactionCount: StateFlow<Int> = repository.getTransactionCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val _stats = MutableStateFlow(ExpenseStats())
    val stats: StateFlow<ExpenseStats> = _stats.asStateFlow()

    private val _monthlyStats = MutableStateFlow(ExpenseStats())
    val monthlyStats: StateFlow<ExpenseStats> = _monthlyStats.asStateFlow()

    private val _dailySpend = MutableStateFlow<Map<String, Double>>(emptyMap())
    val dailySpend: StateFlow<Map<String, Double>> = _dailySpend.asStateFlow()

    private val _insights = MutableStateFlow<List<Insight>>(emptyList())
    val insights: StateFlow<List<Insight>> = _insights.asStateFlow()

    private val _monthlyDailySpend = MutableStateFlow<Map<String, Double>>(emptyMap())
    val monthlyDailySpend: StateFlow<Map<String, Double>> = _monthlyDailySpend.asStateFlow()

    private var monthlyStatsJob: Job? = null

    // NOTE: init must run AFTER every MutableStateFlow above is declared.
    // viewModelScope uses Dispatchers.Main.immediate, so launching from init on
    // the main thread executes the coroutine body synchronously up to the first
    // suspension. If a repository flow emits without suspending, `_field.value = it`
    // fires during construction — any backing field declared below init would still
    // be null and crash with NPE (was the ExpenseViewModel.kt:77 crash).
    init {
        loadStats()
        loadMonthlyStats()
    }

    private fun loadMonthlyStats() {
        monthlyStatsJob?.cancel()
        monthlyStatsJob = viewModelScope.launch {
            val now = LocalDateTime.now()
            val monthStart = now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()
            launch { repository.getExpenseStats(monthStart, now).collect { _monthlyStats.value = it } }
            launch { repository.getDailySpend(monthStart, now).collect { _monthlyDailySpend.value = it } }
        }
    }

    fun setPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
        loadStats()
    }

    private var statsJob: Job? = null

    private fun loadStats() {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            val (start, end) = getDateRange(_selectedPeriod.value)
            launch { repository.getExpenseStats(start, end).collect { _stats.value = it } }
            launch { repository.getDailySpend(start, end).collect { _dailySpend.value = it } }
            launch { repository.getAllTransactions().collect { txns ->
                _insights.value = insightsEngine.generateInsights(txns)
            } }
        }
    }

    fun scanExistingSms() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            try {
                _isLoading.value = true
                // One-time data repairs, bumped when parser rules change.
                // v2: fix wrong credit/debit type. v3: purge rows older builds wrongly
                // captured (ads, loyalty/points SMS) that current rules now reject.
                // v4: full inbox re-scan so old terse rows get upgraded to the richer
                //     duplicate SMS (insertAll now replaces a stored row when a more
                //     informative SMS for the same transaction is re-parsed).
                // v5: full re-scan so new merchant-extraction patterns (ICICI
                //     "<date>; Name credited", Axis card "IST Name Avl", UPI P2M last
                //     segment) back-fill merchants onto rows earlier builds saved as "Unknown".
                val CURRENT_REPAIR_VERSION = 5
                val needsFullRescan = userPreferences.lastRepairVersion < CURRENT_REPAIR_VERSION
                if (needsFullRescan) {
                    repository.repairTransactionTypes(smsParser)
                    repository.purgeNonTransactional(smsParser)
                    userPreferences.lastRepairVersion = CURRENT_REPAIR_VERSION
                }
                // Normally scan only new SMS since last scan. On a repair-triggered full
                // rescan, re-read the ENTIRE inbox (not just the default 90-day window) so
                // the richer-duplicate upgrade reaches ALL transactions saved by earlier
                // builds, however old. ~10 years back effectively means "everything".
                val scanResult = if (needsFullRescan) {
                    smsScanner.scanExistingSms(sinceTimestamp = 0L, daysBack = 3650)
                } else {
                    smsScanner.scanExistingSms(sinceTimestamp = userPreferences.lastSmsTimestamp)
                }
                if (scanResult.transactions.isNotEmpty()) {
                    repository.insertAll(scanResult.transactions)
                    autoDetectSalaryAccount(scanResult.transactions)
                    analyticsHelper.logSmsScanned(scanResult.transactions.size)
                }
                // Fill in merchant names for bank debits that came out as "Unknown", using
                // names harvested from loyalty SMS (e.g. CUB "Rs 1174 debited" + Nuts n Spices
                // "bill value Rs 1174" -> merchant "Nuts n Spices", category Groceries).
                repository.applyMerchantHints(scanResult.merchantHints)
                // Re-run categorization over old rows so keyword-DB improvements
                // (e.g. "nut n spice" -> Groceries) reach transactions saved earlier.
                repository.recategorizeUncategorized()
                // Save the latest SMS timestamp for next scan
                if (scanResult.maxTimestamp > userPreferences.lastSmsTimestamp) {
                    userPreferences.lastSmsTimestamp = scanResult.maxTimestamp
                }
            } catch (e: Exception) {
                // Silently handle - permission not granted or other issue
            } finally {
                // Ensure loading shows for at least 1 second so user sees feedback
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 1000) {
                    kotlinx.coroutines.delay(1000 - elapsed)
                }
                _isLoading.value = false
                // Cancel and restart all collectors with fresh time ranges
                loadStats()
                loadMonthlyStats()
            }
        }
    }

    private fun autoDetectSalaryAccount(transactions: List<Transaction>) {
        if (userPreferences.salaryAccountLast4.isNotBlank()) return
        if (!userPreferences.isOnboarded) {
            // Find largest recurring credit — likely salary
            val credits = transactions.filter { it.type == TransactionType.CREDIT && it.amount >= 20000 }
            val salaryCandidate = credits
                .groupBy { it.accountInfo }
                .maxByOrNull { (_, txns) -> txns.size }

            if (salaryCandidate != null) {
                val accountLast4 = salaryCandidate.key
                    .replace(Regex("[^0-9]"), "")
                    .takeLast(4)
                if (accountLast4.length == 4) {
                    userPreferences.salaryAccountLast4 = accountLast4
                    userPreferences.addAccount(accountLast4)
                }
            }
            userPreferences.isOnboarded = true
        }
    }

    fun updateCategory(transaction: Transaction, newCategory: TransactionCategory) {
        viewModelScope.launch {
            analyticsHelper.logCategoryChanged(
                oldCategory = transaction.category.name,
                newCategory = newCategory.name
            )
            repository.updateCategoryAndLearn(transaction, newCategory)
        }
    }

    fun updateSingleTransaction(transaction: Transaction, newCategory: TransactionCategory) {
        viewModelScope.launch {
            repository.update(transaction.copy(category = newCategory))
        }
    }

    fun toggleSelfTransfer(transaction: Transaction) {
        viewModelScope.launch {
            repository.update(transaction.copy(isSelfTransfer = !transaction.isSelfTransfer))
        }
    }

    /** Current split participants for a transaction (empty if not split). */
    fun splitParticipants(transaction: Transaction): List<com.expensetracker.data.model.SplitParticipant> =
        repository.parseSplit(transaction)

    /** Save or clear a split. Empty list removes the split; amount is never modified. */
    fun saveSplit(
        transaction: Transaction,
        participants: List<com.expensetracker.data.model.SplitParticipant>
    ) {
        viewModelScope.launch {
            repository.saveSplit(transaction, participants)
        }
    }

    fun renameMerchant(transaction: Transaction, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renameMerchant(transaction, newName)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.delete(transaction)
        }
    }

    fun addManualTransaction(transaction: Transaction) {
        viewModelScope.launch {
            analyticsHelper.logTransactionAdded(
                type = transaction.type.name,
                category = transaction.category.name,
                amount = transaction.amount,
                source = "manual"
            )
            repository.insert(transaction)
        }
    }

    private val _syncStatus = MutableStateFlow("")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    fun backupToCloud() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _syncStatus.value = "Backing up..."
                repository.backupToCloud()
                _syncStatus.value = "Backup complete!"
            } catch (e: Exception) {
                _syncStatus.value = "Backup failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun restoreFromCloud() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _syncStatus.value = "Restoring..."
                val count = repository.restoreFromCloud()
                _syncStatus.value = "Restored $count transactions!"
            } catch (e: Exception) {
                _syncStatus.value = "Restore failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _customStartDate = MutableStateFlow<LocalDateTime?>(null)
    private val _customEndDate = MutableStateFlow<LocalDateTime?>(null)

    fun setCustomDateRange(start: LocalDateTime, end: LocalDateTime) {
        _customStartDate.value = start
        _customEndDate.value = end
        _selectedPeriod.value = TimePeriod.CUSTOM
        loadStats()
    }

    private fun getDateRange(period: TimePeriod): Pair<LocalDateTime, LocalDateTime> {
        val now = LocalDateTime.now()
        val start = when (period) {
            TimePeriod.TODAY -> now.toLocalDate().atStartOfDay()
            TimePeriod.THIS_WEEK -> now.minusDays(now.dayOfWeek.value.toLong() - 1).toLocalDate().atStartOfDay()
            TimePeriod.THIS_MONTH -> now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()
            TimePeriod.LAST_MONTH -> now.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()
            TimePeriod.LAST_3_MONTHS -> now.minusMonths(3).toLocalDate().atStartOfDay()
            TimePeriod.THIS_YEAR -> now.with(TemporalAdjusters.firstDayOfYear()).toLocalDate().atStartOfDay()
            TimePeriod.CUSTOM -> _customStartDate.value ?: now.minusMonths(1).toLocalDate().atStartOfDay()
        }
        val end = when (period) {
            TimePeriod.LAST_MONTH -> now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()
            TimePeriod.CUSTOM -> _customEndDate.value ?: now
            else -> now
        }
        return start to end
    }
}

enum class TimePeriod(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    LAST_3_MONTHS("3 Months"),
    THIS_YEAR("This Year"),
    CUSTOM("Custom")
}
