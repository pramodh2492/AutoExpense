package com.expensetracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.expensetracker.worker.BudgetAlertWorker
import com.expensetracker.worker.DailyReminderWorker
import com.expensetracker.worker.WeeklySummaryWorker

import dagger.hilt.android.HiltAndroidApp
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class ExpenseTrackerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        initializeAppCheck()
        scheduleBackgroundWork()
    }

    private fun initializeAppCheck() {
        AppCheckInitializer.init()
    }

    private fun scheduleBackgroundWork() {
        val workManager = WorkManager.getInstance(this)

        // Daily 9 PM reminder
        val now = LocalDateTime.now()
        val ninePM = now.toLocalDate().atTime(LocalTime.of(21, 0))
        val delayToNinePM = if (now.isBefore(ninePM)) {
            Duration.between(now, ninePM)
        } else {
            Duration.between(now, ninePM.plusDays(1))
        }

        val dailyReminder = PeriodicWorkRequestBuilder<DailyReminderWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(delayToNinePM.toMinutes(), TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "daily_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            dailyReminder
        )

        // Budget alerts — check every 6 hours
        val budgetAlert = PeriodicWorkRequestBuilder<BudgetAlertWorker>(
            6, TimeUnit.HOURS
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "budget_alert",
            ExistingPeriodicWorkPolicy.KEEP,
            budgetAlert
        )

        // Weekly summary — every 7 days
        val weeklySummary = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(
            7, TimeUnit.DAYS
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "weekly_summary",
            ExistingPeriodicWorkPolicy.KEEP,
            weeklySummary
        )
    }
}
