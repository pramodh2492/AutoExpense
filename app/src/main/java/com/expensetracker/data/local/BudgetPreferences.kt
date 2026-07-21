package com.expensetracker.data.local

import android.content.Context
import android.content.SharedPreferences
import com.expensetracker.data.model.TransactionCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)

    var totalMonthlyBudget: Double
        get() = prefs.getFloat("total_monthly_budget", 0f).toDouble()
        set(value) = prefs.edit().putFloat("total_monthly_budget", value.toFloat()).apply()

    fun getCategoryBudget(category: TransactionCategory): Double {
        return prefs.getFloat("budget_${category.name}", 0f).toDouble()
    }

    fun setCategoryBudget(category: TransactionCategory, amount: Double) {
        prefs.edit().putFloat("budget_${category.name}", amount.toFloat()).apply()
    }

    fun getAllCategoryBudgets(): Map<TransactionCategory, Double> {
        return TransactionCategory.entries
            .associateWith { getCategoryBudget(it) }
            .filter { it.value > 0 }
    }

    fun hasAlertedAt80(category: String, month: String): Boolean {
        return prefs.getBoolean("alert_80_${category}_$month", false)
    }

    fun markAlertedAt80(category: String, month: String) {
        prefs.edit().putBoolean("alert_80_${category}_$month", true).apply()
    }

    fun hasAlertedAt100(category: String, month: String): Boolean {
        return prefs.getBoolean("alert_100_${category}_$month", false)
    }

    fun markAlertedAt100(category: String, month: String) {
        prefs.edit().putBoolean("alert_100_${category}_$month", true).apply()
    }
}
