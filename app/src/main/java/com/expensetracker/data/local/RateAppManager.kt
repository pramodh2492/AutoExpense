package com.expensetracker.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the rate-me prompt logic.
 *
 * Conditions to show:
 * - 7+ days since first launch OR 20+ app opens
 * - User has not tapped "Never"
 * - If user tapped "Later", wait 3 more days before showing again
 */
@Singleton
class RateAppManager @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "rate_app_prefs"
        private const val KEY_FIRST_LAUNCH_TIME = "first_launch_time"
        private const val KEY_APP_OPEN_COUNT = "app_open_count"
        private const val KEY_NEVER_ASK = "never_ask"
        private const val KEY_LATER_TIMESTAMP = "later_timestamp"
        private const val KEY_HAS_RATED = "has_rated"

        private const val DAYS_BEFORE_PROMPT = 7L
        private const val OPENS_BEFORE_PROMPT = 20
        private const val DAYS_AFTER_LATER = 3L

        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Record first launch time if not set
        if (prefs.getLong(KEY_FIRST_LAUNCH_TIME, 0L) == 0L) {
            prefs.edit().putLong(KEY_FIRST_LAUNCH_TIME, System.currentTimeMillis()).apply()
        }
    }

    /**
     * Call this every time the app opens.
     */
    fun recordAppOpen() {
        val count = prefs.getInt(KEY_APP_OPEN_COUNT, 0) + 1
        prefs.edit().putInt(KEY_APP_OPEN_COUNT, count).apply()
    }

    /**
     * Returns true if the rate dialog should be shown.
     */
    fun shouldShowRateDialog(): Boolean {
        // Already rated or permanently dismissed
        if (prefs.getBoolean(KEY_HAS_RATED, false)) return false
        if (prefs.getBoolean(KEY_NEVER_ASK, false)) return false

        // Check "Later" cooldown
        val laterTimestamp = prefs.getLong(KEY_LATER_TIMESTAMP, 0L)
        if (laterTimestamp > 0L) {
            val daysSinceLater = (System.currentTimeMillis() - laterTimestamp) / MILLIS_PER_DAY
            return daysSinceLater >= DAYS_AFTER_LATER
        }

        // Check usage thresholds
        val firstLaunch = prefs.getLong(KEY_FIRST_LAUNCH_TIME, System.currentTimeMillis())
        val daysSinceFirstLaunch = (System.currentTimeMillis() - firstLaunch) / MILLIS_PER_DAY
        val appOpenCount = prefs.getInt(KEY_APP_OPEN_COUNT, 0)

        return daysSinceFirstLaunch >= DAYS_BEFORE_PROMPT || appOpenCount >= OPENS_BEFORE_PROMPT
    }

    /**
     * User tapped "Rate Now" — mark as rated.
     */
    fun onUserRated() {
        prefs.edit().putBoolean(KEY_HAS_RATED, true).apply()
    }

    /**
     * User tapped "Later" — will ask again after 3 days.
     */
    fun onUserLater() {
        prefs.edit().putLong(KEY_LATER_TIMESTAMP, System.currentTimeMillis()).apply()
    }

    /**
     * User tapped "Never" — never ask again.
     */
    fun onUserNever() {
        prefs.edit().putBoolean(KEY_NEVER_ASK, true).apply()
    }
}
