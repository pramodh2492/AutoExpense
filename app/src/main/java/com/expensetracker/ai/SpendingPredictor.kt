package com.expensetracker.ai

import com.expensetracker.data.local.TransactionDao
import com.expensetracker.data.model.TransactionCategory
import com.expensetracker.data.model.TransactionType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

data class SpendingPrediction(
    val projectedMonthEnd: Double,
    val dailyAverage: Double,
    val daysRemaining: Int,
    val projectedRemaining: Double,
    val comparedToLastMonth: Int,
    val categoryProjections: List<CategoryProjection>
)

data class CategoryProjection(
    val category: TransactionCategory,
    val currentSpent: Double,
    val projected: Double,
    val lastMonth: Double,
    val trend: String
)

@Singleton
class SpendingPredictor @Inject constructor(
    private val dao: TransactionDao
) {

    suspend fun predict(): SpendingPrediction {
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val monthStart = today.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay()
        val daysInMonth = today.lengthOfMonth()
        val daysPassed = today.dayOfMonth
        val daysRemaining = daysInMonth - daysPassed

        // Current month spend
        val currentSpent = dao.getTotalSpent(monthStart, now).first() ?: 0.0

        // Daily average this month
        val dailyAvg = if (daysPassed > 0) currentSpent / daysPassed else 0.0

        // Projected month-end
        val projected = dailyAvg * daysInMonth
        val projectedRemaining = dailyAvg * daysRemaining

        // Last month total for comparison
        val lastMonthStart = today.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay()
        val lastMonthEnd = monthStart
        val lastMonthSpent = dao.getTotalSpent(lastMonthStart, lastMonthEnd).first() ?: 0.0

        val comparedToLastMonth = if (lastMonthSpent > 0) {
            ((projected - lastMonthSpent) / lastMonthSpent * 100).toInt()
        } else 0

        // Category-wise projections
        val currentCategories = dao.getCategoryBreakdown(monthStart, now).first()
        val lastMonthCategories = dao.getCategoryBreakdown(lastMonthStart, lastMonthEnd).first()
        val lastMonthMap = lastMonthCategories.associate { it.category to it.total }

        val categoryProjections = currentCategories.map { cat ->
            val catDailyAvg = cat.total / daysPassed
            val catProjected = catDailyAvg * daysInMonth
            val lastMonthTotal = lastMonthMap[cat.category] ?: 0.0
            val trend = when {
                lastMonthTotal == 0.0 -> "new"
                catProjected > lastMonthTotal * 1.2 -> "↑ up"
                catProjected < lastMonthTotal * 0.8 -> "↓ down"
                else -> "→ same"
            }
            CategoryProjection(
                category = cat.category,
                currentSpent = cat.total,
                projected = catProjected,
                lastMonth = lastMonthTotal,
                trend = trend
            )
        }.sortedByDescending { it.projected }

        return SpendingPrediction(
            projectedMonthEnd = projected,
            dailyAverage = dailyAvg,
            daysRemaining = daysRemaining,
            projectedRemaining = projectedRemaining,
            comparedToLastMonth = comparedToLastMonth,
            categoryProjections = categoryProjections.take(5)
        )
    }
}
