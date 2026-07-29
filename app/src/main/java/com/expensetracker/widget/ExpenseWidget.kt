package com.expensetracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.room.Room
import com.expensetracker.data.local.AppDatabase
import com.expensetracker.data.local.MIGRATION_8_9
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

class ExpenseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = Room.databaseBuilder(
            context, AppDatabase::class.java, "expense_tracker_db"
        ).addMigrations(MIGRATION_8_9).fallbackToDestructiveMigration().build()

        val dao = db.transactionDao()
        val now = LocalDateTime.now()
        val todayStart = now.toLocalDate().atStartOfDay()
        val monthStart = now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()

        val todaySpent = dao.getTotalSpent(todayStart, now).first() ?: 0.0
        val monthSpent = dao.getTotalSpent(monthStart, now).first() ?: 0.0
        val txnCount = dao.getTransactionCount().first()

        db.close()

        provideContent {
            WidgetContent(todaySpent, monthSpent, txnCount)
        }
    }
}

@Composable
private fun WidgetContent(todaySpent: Double, monthSpent: Double, totalTxns: Int) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF14141F))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "Expense Tracker",
            style = TextStyle(
                color = ColorProvider(Color(0xFFB8B8CC)),
                fontSize = 12.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = "Today: ₹${String.format("%,.0f", todaySpent)}",
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "This Month: ₹${String.format("%,.0f", monthSpent)}",
            style = TextStyle(
                color = ColorProvider(Color(0xFFFF5252)),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = "$totalTxns transactions tracked",
            style = TextStyle(
                color = ColorProvider(Color(0xFF7C4DFF)),
                fontSize = 11.sp
            )
        )
    }
}

class ExpenseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExpenseWidget()
}
