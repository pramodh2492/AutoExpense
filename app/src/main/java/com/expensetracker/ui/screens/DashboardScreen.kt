package com.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.expensetracker.data.local.Insight
import com.expensetracker.data.local.InsightType
import com.expensetracker.tax.TaxCalculator
import com.expensetracker.tax.TaxInsights
import com.expensetracker.ui.components.CategoryPieChart
import com.expensetracker.ui.components.TransactionItem
import com.expensetracker.ui.navigation.Screen
import com.expensetracker.viewmodel.ExpenseViewModel
import com.expensetracker.viewmodel.TimePeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: ExpenseViewModel,
    navController: NavHostController,
    taxCalculator: TaxCalculator? = null
) {
    val stats by viewModel.stats.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val context = LocalContext.current

    // Tax insights state
    var taxInsights by remember { mutableStateOf<TaxInsights?>(null) }
    LaunchedEffect(Unit) {
        if (taxCalculator != null) {
            taxInsights = withContext(Dispatchers.IO) {
                try { taxCalculator.calculateTaxInsights() } catch (_: Exception) { null }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Expense Tracker",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(onClick = { ShareReportHelper.shareReport(context, stats) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Report",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { viewModel.scanExistingSms() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (isLoading) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Loading indicator
        if (isLoading) {
            item {
                Text(
                    text = "⟳ Scanning messages...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        item {
            var showDatePicker by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TimePeriod.entries.filter { it != TimePeriod.CUSTOM }.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { viewModel.setPeriod(period) },
                        label = { Text(period.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
                FilterChip(
                    selected = selectedPeriod == TimePeriod.CUSTOM,
                    onClick = { showDatePicker = true },
                    label = { Text("Custom", style = MaterialTheme.typography.labelSmall) }
                )
            }

            if (showDatePicker) {
                val dateRangePickerState = rememberDateRangePickerState()

                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            val startMillis = dateRangePickerState.selectedStartDateMillis
                            val endMillis = dateRangePickerState.selectedEndDateMillis
                            if (startMillis != null && endMillis != null) {
                                val start = java.time.Instant.ofEpochMilli(startMillis)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate().atStartOfDay()
                                val end = java.time.Instant.ofEpochMilli(endMillis)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate().atTime(23, 59, 59)
                                viewModel.setCustomDateRange(start, end)
                                showDatePicker = false
                            }
                        }) { Text("Apply") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    DateRangePicker(
                        state = dateRangePickerState,
                        modifier = Modifier.height(400.dp)
                    )
                }
            }
        }

        // Empty state when no transactions and not loading
        if (transactions.isEmpty() && !isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No transactions yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your expenses will appear here automatically when you receive bank SMS",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.scanExistingSms() }) {
                            Text("Scan SMS Now")
                        }
                    }
                }
            }
        }


        // Only show stats and transactions when we have data
        if (transactions.isNotEmpty()) {
            // Gradient stat cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GradientStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Spent",
                        amount = currencyFormat.format(stats.totalSpent),
                        gradientColors = listOf(Color(0xFFFF5252), Color(0xFFFF1744))
                    )
                    GradientStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Income",
                        amount = currencyFormat.format(stats.totalIncome),
                        gradientColors = listOf(Color(0xFF4CAF50), Color(0xFF00C853))
                    )
                }
            }

            // Savings card
            if (stats.totalSavings > 0) {
                item {
                    GradientStatCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = "Savings & Investments",
                        amount = currencyFormat.format(stats.totalSavings),
                        gradientColors = listOf(Color(0xFF26A69A), Color(0xFF00897B))
                    )
                }
            }

            // Balance card
            item {
                val balance = stats.totalIncome - stats.totalSpent - stats.totalSavings
                GradientStatCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Balance",
                    amount = currencyFormat.format(balance),
                    gradientColors = if (balance >= 0)
                        listOf(Color(0xFF7C4DFF), Color(0xFF536DFE))
                    else
                        listOf(Color(0xFFFF6F00), Color(0xFFFF3D00))
                )
            }

            if (stats.categoryBreakdown.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Where your money goes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            CategoryPieChart(
                                data = stats.categoryBreakdown,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                        }
                    }
                }
            }

            // Smart Insights
            if (insights.isNotEmpty()) {
                item {
                    Text(
                        text = "Insights",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(insights) { insight ->
                    InsightCard(insight = insight)
                }
            }

            item {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            items(transactions.take(10)) { transaction ->
                TransactionItem(transaction = transaction)
            }
        }
    }
}

@Composable
fun GradientStatCard(
    modifier: Modifier = Modifier,
    title: String,
    amount: String,
    gradientColors: List<Color>
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(gradientColors))
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun InsightCard(insight: Insight) {
    val (icon, color) = when (insight.type) {
        InsightType.OVERSPEND -> Icons.Default.TrendingUp to Color(0xFFFF5252)
        InsightType.SAVING -> Icons.Default.TrendingDown to Color(0xFF4CAF50)
        InsightType.TREND -> Icons.Default.Timeline to Color(0xFF2196F3)
        InsightType.TIP -> Icons.Default.Lightbulb to Color(0xFFFFAB40)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = insight.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
