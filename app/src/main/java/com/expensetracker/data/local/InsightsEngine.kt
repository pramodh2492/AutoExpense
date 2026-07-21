package com.expensetracker.data.local

import com.expensetracker.data.model.Transaction
import com.expensetracker.data.model.TransactionCategory
import com.expensetracker.data.model.TransactionType
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

data class Insight(
    val title: String,
    val description: String,
    val type: InsightType
)

enum class InsightType {
    OVERSPEND,
    SAVING,
    TREND,
    TIP
}

@Singleton
class InsightsEngine @Inject constructor(
    private val dao: TransactionDao
) {
    suspend fun generateInsights(transactions: List<Transaction>): List<Insight> {
        val insights = mutableListOf<Insight>()
        val now = LocalDateTime.now()
        val monthStart = now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()
        val lastMonthStart = now.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()

        val thisMonthTxns = transactions.filter {
            it.timestamp.isAfter(monthStart) && it.type == TransactionType.DEBIT && !it.isSelfTransfer
        }
        val lastMonthTxns = transactions.filter {
            it.timestamp.isAfter(lastMonthStart) && it.timestamp.isBefore(monthStart)
                    && it.type == TransactionType.DEBIT && !it.isSelfTransfer
        }

        // 1. Category comparison vs last month
        val thisMonthByCategory = thisMonthTxns.groupBy { it.category }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }
        val lastMonthByCategory = lastMonthTxns.groupBy { it.category }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }

        for ((category, amount) in thisMonthByCategory) {
            val lastMonth = lastMonthByCategory[category] ?: continue
            if (lastMonth < 500) continue
            val change = ((amount - lastMonth) / lastMonth * 100).toInt()
            if (change > 30) {
                insights.add(Insight(
                    title = "${category.displayName} is up ${change}%",
                    description = "₹${String.format("%,.0f", amount)} this month vs ₹${String.format("%,.0f", lastMonth)} last month",
                    type = InsightType.OVERSPEND
                ))
            } else if (change < -20) {
                insights.add(Insight(
                    title = "${category.displayName} spending down",
                    description = "You saved ₹${String.format("%,.0f", lastMonth - amount)} compared to last month",
                    type = InsightType.SAVING
                ))
            }
        }

        // 2. Top merchant this month
        val topMerchant = thisMonthTxns
            .filter { it.merchant != "Unknown" }
            .groupBy { it.merchant }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }
            .maxByOrNull { it.value }

        if (topMerchant != null && topMerchant.value > 1000) {
            val count = thisMonthTxns.count { it.merchant == topMerchant.key }
            insights.add(Insight(
                title = "Top spender: ${topMerchant.key}",
                description = "₹${String.format("%,.0f", topMerchant.value)} across $count transactions",
                type = InsightType.TREND
            ))
        }

        // 3. Daily average
        val days = thisMonthTxns.map { it.timestamp.toLocalDate() }.distinct().size
        if (days > 0) {
            val dailyAvg = thisMonthTxns.sumOf { it.amount } / days
            insights.add(Insight(
                title = "Daily average: ₹${String.format("%,.0f", dailyAvg)}",
                description = "Based on $days days of spending this month",
                type = InsightType.TREND
            ))
        }

        // 4. Weekend vs weekday
        val weekendSpend = thisMonthTxns
            .filter { it.timestamp.dayOfWeek.value >= 6 }
            .sumOf { it.amount }
        val weekdaySpend = thisMonthTxns
            .filter { it.timestamp.dayOfWeek.value < 6 }
            .sumOf { it.amount }
        val weekendDays = thisMonthTxns.map { it.timestamp.toLocalDate() }
            .distinct().count { it.dayOfWeek.value >= 6 }.coerceAtLeast(1)
        val weekdayDays = thisMonthTxns.map { it.timestamp.toLocalDate() }
            .distinct().count { it.dayOfWeek.value < 6 }.coerceAtLeast(1)

        val weekendAvg = weekendSpend / weekendDays
        val weekdayAvg = weekdaySpend / weekdayDays
        if (weekendAvg > weekdayAvg * 1.5 && weekendSpend > 1000) {
            insights.add(Insight(
                title = "Weekend spender!",
                description = "You spend ₹${String.format("%,.0f", weekendAvg)}/day on weekends vs ₹${String.format("%,.0f", weekdayAvg)} on weekdays",
                type = InsightType.TIP
            ))
        }

        // 5. Recurring patterns
        val recurring = thisMonthTxns
            .filter { it.category == TransactionCategory.SUBSCRIPTION || it.category == TransactionCategory.EMI }
            .sumOf { it.amount }
        if (recurring > 0) {
            insights.add(Insight(
                title = "Fixed costs: ₹${String.format("%,.0f", recurring)}",
                description = "EMIs + Subscriptions this month",
                type = InsightType.TREND
            ))
        }

        return insights.take(5)
    }
}
