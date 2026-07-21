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
import java.time.temporal.TemporalAdjusters

@HiltWorker
class WeeklySummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: TransactionDao,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = LocalDateTime.now()
        val weekStart = now.minusDays(7)

        val totalSpent = dao.getTotalSpent(weekStart, now).first() ?: 0.0
        if (totalSpent == 0.0) return Result.success()

        val categories = dao.getCategoryBreakdown(weekStart, now).first()
        val top = categories.maxByOrNull { it.total }

        notificationHelper.showWeeklySummary(
            totalSpent = totalSpent,
            topCategory = top?.category?.displayName ?: "Other",
            topAmount = top?.total ?: 0.0
        )

        return Result.success()
    }
}
