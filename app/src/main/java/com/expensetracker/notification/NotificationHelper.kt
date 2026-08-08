package com.expensetracker.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.expensetracker.MainActivity
import com.expensetracker.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_TRANSACTION = "transaction_alerts"
        const val CHANNEL_WEEKLY = "weekly_summary"
        const val CHANNEL_BUDGET = "budget_alerts"
        const val CHANNEL_UNKNOWN = "unknown_merchant"
        const val CHANNEL_GROUP = "group_expenses"
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        val channels = listOf(
            NotificationChannel(
                CHANNEL_TRANSACTION, "Transaction Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "New transaction notifications" },

            NotificationChannel(
                CHANNEL_WEEKLY, "Weekly Summary",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Weekly spending summaries" },

            NotificationChannel(
                CHANNEL_BUDGET, "Budget Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Overspending warnings" },

            NotificationChannel(
                CHANNEL_UNKNOWN, "Categorize",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Help categorize unknown transactions" },

            NotificationChannel(
                CHANNEL_GROUP, "Group Expenses",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "New expenses added to your groups" },
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        channels.forEach { manager.createNotificationChannel(it) }
    }

    fun showTransactionNotification(merchant: String, amount: Double, category: String) {
        if (!hasPermission()) return

        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TRANSACTION)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("₹${String.format("%,.0f", amount)} spent")
            .setContentText("$merchant • $category")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(
            System.currentTimeMillis().toInt(), notification
        )
    }

    fun showWeeklySummary(totalSpent: Double, topCategory: String, topAmount: Double) {
        if (!hasPermission()) return

        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_WEEKLY)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Weekly Spend: ₹${String.format("%,.0f", totalSpent)}")
            .setContentText("Top: $topCategory (₹${String.format("%,.0f", topAmount)})")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("You spent ₹${String.format("%,.0f", totalSpent)} this week.\nBiggest category: $topCategory at ₹${String.format("%,.0f", topAmount)}.\nTap to see full breakdown."))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(1001, notification)
    }

    fun showBudgetAlert(category: String, spent: Double, usual: Double) {
        if (!hasPermission()) return

        val percent = ((spent / usual) * 100).toInt()
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ $category spending high")
            .setContentText("₹${String.format("%,.0f", spent)} spent ($percent% of usual ₹${String.format("%,.0f", usual)})")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(
            category.hashCode(), notification
        )
    }

    fun showUnknownMerchantPrompt(transactionId: Long, rawSms: String, amount: Double) {
        if (!hasPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "transactions")
        }
        val pending = PendingIntent.getActivity(
            context, transactionId.toInt(), intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_UNKNOWN)
            .setSmallIcon(android.R.drawable.ic_menu_help)
            .setContentTitle("₹${String.format("%,.0f", amount)} — What was this?")
            .setContentText("Tap to categorize this expense")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("We couldn't identify this merchant. Tap to name and categorize it.\n\n${rawSms.take(100)}"))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(
            transactionId.toInt(), notification
        )
    }

    fun showMonthlyReport(totalSpent: Double, totalSavings: Double, topCategories: String) {
        if (!hasPermission()) return

        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_WEEKLY)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Monthly Report Ready")
            .setContentText("Spent ₹${String.format("%,.0f", totalSpent)} | Saved ₹${String.format("%,.0f", totalSavings)}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Monthly Summary:\n• Spent: ₹${String.format("%,.0f", totalSpent)}\n• Saved: ₹${String.format("%,.0f", totalSavings)}\n• Top: $topCategories\n\nTap to see full analytics."))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(2001, notification)
    }

    fun showDailyReminder(todaySpent: Double) {
        if (!hasPermission()) return

        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val message = if (todaySpent > 0) {
            "You spent ₹${String.format("%,.0f", todaySpent)} today. Tap to review."
        } else {
            "No spending tracked today. Did you pay cash somewhere?"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_WEEKLY)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Daily Spending Review")
            .setContentText(message)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(3001, notification)
    }

    fun showBudgetWarning(category: String, spent: Double, budget: Double, percent: Int) {
        if (!hasPermission()) return

        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ $category: $percent% used")
            .setContentText("₹${String.format("%,.0f", spent)} of ₹${String.format("%,.0f", budget)} budget spent")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(
            "warn_$category".hashCode(), notification
        )
    }

    fun showGroupExpenseNotification(groupName: String, paidByName: String, description: String, amount: Double) {
        if (!hasPermission()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_GROUP)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("$paidByName added to $groupName")
            .setContentText("$description • ₹${String.format("%,.0f", amount)}")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(
            "group_${System.currentTimeMillis()}".hashCode(), notification
        )
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }
}
