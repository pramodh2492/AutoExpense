package com.expensetracker.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.expensetracker.data.local.TransactionDao
import com.expensetracker.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

@HiltWorker
class DailyReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: TransactionDao,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = LocalDateTime.now()
        val todayStart = now.toLocalDate().atStartOfDay()

        val todaySpent = dao.getTotalSpent(todayStart, now).first() ?: 0.0

        notificationHelper.showDailyReminder(todaySpent)

        return Result.success()
    }
}
