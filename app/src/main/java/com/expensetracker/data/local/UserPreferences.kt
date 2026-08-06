package com.expensetracker.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("expense_tracker_prefs", Context.MODE_PRIVATE)

    var salaryAccountLast4: String
        get() = prefs.getString("salary_account", "") ?: ""
        set(value) = prefs.edit().putString("salary_account", value).apply()

    var userAccountNumbers: Set<String>
        get() = prefs.getStringSet("user_accounts", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("user_accounts", value).apply()

    var minSalaryAmount: Double
        get() = prefs.getFloat("min_salary_amount", 30000f).toDouble()
        set(value) = prefs.edit().putFloat("min_salary_amount", value.toFloat()).apply()

    var isOnboarded: Boolean
        get() = prefs.getBoolean("is_onboarded", false)
        set(value) = prefs.edit().putBoolean("is_onboarded", value).apply()

    var monthlySalary: Double
        get() = prefs.getFloat("monthly_salary", 0f).toDouble()
        set(value) = prefs.edit().putFloat("monthly_salary", value.toFloat()).apply()

    var hasSalaryConfigured: Boolean
        get() = prefs.getBoolean("has_salary_configured", false)
        set(value) = prefs.edit().putBoolean("has_salary_configured", value).apply()

    var hasSeenSignInPrompt: Boolean
        get() = prefs.getBoolean("has_seen_sign_in_prompt", false)
        set(value) = prefs.edit().putBoolean("has_seen_sign_in_prompt", value).apply()

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean("has_completed_onboarding", false)
        set(value) = prefs.edit().putBoolean("has_completed_onboarding", value).apply()

    // One-time tip on the transaction list explaining that tapping a row lets you
    // change category, rename the merchant, and split with friends.
    var hasSeenTransactionTips: Boolean
        get() = prefs.getBoolean("has_seen_transaction_tips", false)
        set(value) = prefs.edit().putBoolean("has_seen_transaction_tips", value).apply()

    // One-time interactive feature-discovery tour (spotlight coach-marks) shown on the
    // dashboard after the user's first transactions load.
    var hasSeenFeatureTour: Boolean
        get() = prefs.getBoolean("has_seen_feature_tour", false)
        set(value) = prefs.edit().putBoolean("has_seen_feature_tour", value).apply()

    fun addAccount(last4: String) {
        val current = userAccountNumbers.toMutableSet()
        current.add(last4)
        userAccountNumbers = current
    }

    fun removeAccount(last4: String) {
        val current = userAccountNumbers.toMutableSet()
        current.remove(last4)
        userAccountNumbers = current
    }

    var geminiApiKey: String
        get() = prefs.getString("gemini_api_key", "") ?: ""
        set(value) = prefs.edit().putString("gemini_api_key", value).apply()

    var lastSmsTimestamp: Long
        get() = prefs.getLong("last_sms_timestamp", 0L)
        set(value) = prefs.edit().putLong("last_sms_timestamp", value).apply()

    var lastRepairVersion: Int
        get() = prefs.getInt("last_repair_version", 0)
        set(value) = prefs.edit().putInt("last_repair_version", value).apply()

    var upiId: String
        get() = prefs.getString("upi_id", "") ?: ""
        set(value) = prefs.edit().putString("upi_id", value).apply()
}
