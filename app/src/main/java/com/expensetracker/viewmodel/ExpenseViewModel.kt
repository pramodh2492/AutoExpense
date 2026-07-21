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

    init {
        loadStats()
        loadMonthlyStats()
    }

    private val _monthlyDailySpend = MutableStateFlow<Map<String, Double>>(emptyMap())
    val monthlyDailySpend: StateFlow<Map<String, Double>> = _monthlyDailySpend.asStateFlow()

    private fun loadMonthlyStats() {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val monthStart = now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()
            repository.getExpenseStats(monthStart, now).collect { stats ->
                _monthlyStats.value = stats
            }
        }
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val monthStart = now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()
            repository.getDailySpend(monthStart, now).collect { daily ->
                _monthlyDailySpend.value = daily
            }
        }
    }

    fun setPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            val (start, end) = getDateRange(_selectedPeriod.value)
            repository.getExpenseStats(start, end).collect { stats ->
                _stats.value = stats
            }
        }
        viewModelScope.launch {
            val (start, end) = getDateRange(_selectedPeriod.value)
            repository.getDailySpend(start, end).collect { daily ->
                _dailySpend.value = daily
            }
        }
        viewModelScope.launch {
            repository.getAllTransactions().collect { txns ->
                _insights.value = insightsEngine.generateInsights(txns)
            }
        }
    }

    fun scanExistingSms() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                // Repair old transactions with wrong credit/debit type
                repository.repairTransactionTypes(smsParser)
                // Scan for new SMS
                val transactions = smsScanner.scanExistingSms()
                if (transactions.isNotEmpty()) {
                    repository.insertAll(transactions)
                    autoDetectSalaryAccount(transactions)
                    analyticsHelper.logSmsScanned(transactions.size)
                }
            } catch (e: Exception) {
                // Silently handle - permission not granted or other issue
            } finally {
                _isLoading.value = false
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

    fun toggleSelfTransfer(transaction: Transaction) {
        viewModelScope.launch {
            repository.update(transaction.copy(isSelfTransfer = !transaction.isSelfTransfer))
        }
    }

    fun renameMerchant(transaction: Transaction, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.update(transaction.copy(merchant = newName))
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

    private fun getDateRange(period: TimePeriod): Pair<LocalDateTime, LocalDateTime> {
        val now = LocalDateTime.now()
        val start = when (period) {
            TimePeriod.TODAY -> now.toLocalDate().atStartOfDay()
            TimePeriod.THIS_WEEK -> now.minusDays(now.dayOfWeek.value.toLong() - 1).toLocalDate().atStartOfDay()
            TimePeriod.THIS_MONTH -> now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()
            TimePeriod.LAST_MONTH -> now.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()
            TimePeriod.LAST_3_MONTHS -> now.minusMonths(3).toLocalDate().atStartOfDay()
            TimePeriod.THIS_YEAR -> now.with(TemporalAdjusters.firstDayOfYear()).toLocalDate().atStartOfDay()
        }
        val end = when (period) {
            TimePeriod.LAST_MONTH -> now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()
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
    THIS_YEAR("This Year")
}
