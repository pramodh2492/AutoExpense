package com.expensetracker.ai

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class SavingsGoal(
    val id: String,
    val name: String,
    val targetAmount: Double,
    val savedSoFar: Double = 0.0,
    val targetDate: String,
    val createdDate: String = LocalDate.now().toString(),
    val monthlySavingNeeded: Double = 0.0
)

@Singleton
class SavingsGoalManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("savings_goals", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getGoals(): List<SavingsGoal> {
        val json = prefs.getString("goals", "[]") ?: "[]"
        val type = object : TypeToken<List<SavingsGoal>>() {}.type
        return gson.fromJson(json, type)
    }

    fun addGoal(name: String, targetAmount: Double, targetDate: LocalDate) {
        val goals = getGoals().toMutableList()
        val today = LocalDate.now()
        val monthsRemaining = java.time.Period.between(today, targetDate).toTotalMonths()
        val monthlySaving = if (monthsRemaining > 0) targetAmount / monthsRemaining else targetAmount

        goals.add(
            SavingsGoal(
                id = System.currentTimeMillis().toString(),
                name = name,
                targetAmount = targetAmount,
                targetDate = targetDate.toString(),
                monthlySavingNeeded = monthlySaving
            )
        )
        saveGoals(goals)
    }

    fun updateProgress(goalId: String, newAmount: Double) {
        val goals = getGoals().toMutableList()
        val index = goals.indexOfFirst { it.id == goalId }
        if (index >= 0) {
            goals[index] = goals[index].copy(savedSoFar = newAmount)
            saveGoals(goals)
        }
    }

    fun addToGoal(goalId: String, amount: Double) {
        val goals = getGoals().toMutableList()
        val index = goals.indexOfFirst { it.id == goalId }
        if (index >= 0) {
            goals[index] = goals[index].copy(savedSoFar = goals[index].savedSoFar + amount)
            saveGoals(goals)
        }
    }

    fun deleteGoal(goalId: String) {
        val goals = getGoals().filterNot { it.id == goalId }
        saveGoals(goals)
    }

    fun getProgressPercent(goal: SavingsGoal): Float {
        return (goal.savedSoFar / goal.targetAmount).toFloat().coerceIn(0f, 1f)
    }

    fun isOnTrack(goal: SavingsGoal): Boolean {
        val today = LocalDate.now()
        val created = LocalDate.parse(goal.createdDate)
        val target = LocalDate.parse(goal.targetDate)
        val totalMonths = java.time.Period.between(created, target).toTotalMonths().toFloat()
        val monthsPassed = java.time.Period.between(created, today).toTotalMonths().toFloat()
        if (totalMonths <= 0) return goal.savedSoFar >= goal.targetAmount
        val expectedProgress = monthsPassed / totalMonths
        val actualProgress = goal.savedSoFar / goal.targetAmount
        return actualProgress >= expectedProgress * 0.8
    }

    private fun saveGoals(goals: List<SavingsGoal>) {
        prefs.edit().putString("goals", gson.toJson(goals)).apply()
    }
}

private fun java.time.Period.toTotalMonths(): Long {
    return this.years * 12L + this.months
}
