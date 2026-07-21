package com.expensetracker.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.expensetracker.data.local.BudgetPreferences
import com.expensetracker.data.local.TransactionDao
import com.expensetracker.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@HiltWorker
class BudgetAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: TransactionDao,
    private val budgetPreferences: BudgetPreferences,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = LocalDateTime.now()
        val monthStart = now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()
        val currentMonth = now.format(DateTimeFormatter.ofPattern("yyyy-MM"))

        // Check overall monthly budget
        val totalBudget = budgetPreferences.totalMonthlyBudget
        if (totalBudget > 0) {
            val totalSpent = dao.getTotalSpent(monthStart, now).first() ?: 0.0
            val percent = (totalSpent / totalBudget * 100).toInt()

            if (percent >= 100 && !budgetPreferences.hasAlertedAt100("TOTAL", currentMonth)) {
                notificationHelper.showBudgetAlert(
                    category = "Total Budget",
                    spent = totalSpent,
                    usual = totalBudget
                )
                budgetPreferences.markAlertedAt100("TOTAL", currentMonth)
            } else if (percent >= 80 && !budgetPreferences.hasAlertedAt80("TOTAL", currentMonth)) {
                notificationHelper.showBudgetWarning(
                    category = "Total Budget",
                    spent = totalSpent,
                    budget = totalBudget,
                    percent = percent
                )
                budgetPreferences.markAlertedAt80("TOTAL", currentMonth)
            }
        }

        // Check per-category budgets
        val categoryBudgets = budgetPreferences.getAllCategoryBudgets()
        if (categoryBudgets.isEmpty()) return Result.success()

        val categories = dao.getCategoryBreakdown(monthStart, now).first()

        for (catTotal in categories) {
            val budget = categoryBudgets[catTotal.category] ?: continue
            if (budget <= 0) continue

            val percent = (catTotal.total / budget * 100).toInt()
            val categoryKey = catTotal.category.name

            if (percent >= 100 && !budgetPreferences.hasAlertedAt100(categoryKey, currentMonth)) {
                notificationHelper.showBudgetAlert(
                    category = catTotal.category.displayName,
                    spent = catTotal.total,
                    usual = budget
                )
                budgetPreferences.markAlertedAt100(categoryKey, currentMonth)
            } else if (percent >= 80 && !budgetPreferences.hasAlertedAt80(categoryKey, currentMonth)) {
                notificationHelper.showBudgetWarning(
                    category = catTotal.category.displayName,
                    spent = catTotal.total,
                    budget = budget,
                    percent = percent
                )
                budgetPreferences.markAlertedAt80(categoryKey, currentMonth)
            }
        }

        return Result.success()
    }
}
