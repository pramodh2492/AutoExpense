package com.expensetracker.security

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)

    var isAppLockEnabled: Boolean
        get() = prefs.getBoolean("app_lock_enabled", false)
        set(value) = prefs.edit().putBoolean("app_lock_enabled", value).apply()

    var appPin: String
        get() = prefs.getString("app_pin", "") ?: ""
        set(value) = prefs.edit().putString("app_pin", value).apply()

    var useBiometric: Boolean
        get() = prefs.getBoolean("use_biometric", true)
        set(value) = prefs.edit().putBoolean("use_biometric", value).apply()

    fun verifyPin(input: String): Boolean {
        return input == appPin
    }

    fun setLock(pin: String, biometric: Boolean) {
        appPin = pin
        useBiometric = biometric
        isAppLockEnabled = true
    }

    fun removeLock() {
        isAppLockEnabled = false
        appPin = ""
    }
}
