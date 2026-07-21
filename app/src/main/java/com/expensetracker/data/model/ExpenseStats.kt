package com.expensetracker.data.model

data class ExpenseStats(
    val totalSpent: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalSavings: Double = 0.0,
    val categoryBreakdown: Map<TransactionCategory, Double> = emptyMap(),
    val merchantBreakdown: Map<String, Double> = emptyMap(),
    val dailySpend: Map<String, Double> = emptyMap(),
    val topMerchants: List<MerchantStat> = emptyList(),
    val recurringPayments: List<RecurringPayment> = emptyList()
)

data class MerchantStat(
    val name: String,
    val totalSpent: Double,
    val transactionCount: Int
)

data class RecurringPayment(
    val merchant: String,
    val amount: Double,
    val category: TransactionCategory,
    val frequency: String
)
