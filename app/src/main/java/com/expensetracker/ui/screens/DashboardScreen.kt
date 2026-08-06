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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Receipt
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
import androidx.compose.foundation.layout.PaddingValues
import com.expensetracker.ui.components.CategoryPieChart
import com.expensetracker.ui.components.GlassCard
import com.expensetracker.ui.components.GlassSectionHeader
import com.expensetracker.ui.components.LocalSpotlight
import com.expensetracker.ui.components.SpotlightStep
import com.expensetracker.ui.components.SpotlightTargets
import com.expensetracker.ui.components.TransactionItem
import com.expensetracker.ui.components.spotlightTarget
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

    // One-time interactive feature-discovery tour. Runs once the first transactions have
    // loaded, so the elements it spotlights (balance card, nav, FAB) actually exist.
    val spotlight = LocalSpotlight.current
    LaunchedEffect(spotlight, transactions.isNotEmpty()) {
        if (spotlight != null && transactions.isNotEmpty() && !viewModel.hasSeenFeatureTour()) {
            spotlight.start(featureTourSteps(Screen.Dashboard.route, Screen.Transactions.route))
        }
    }

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

                    IconButton(
                        onClick = { viewModel.scanExistingSms() },
                        modifier = if (spotlight != null)
                            Modifier.spotlightTarget(SpotlightTargets.REFRESH, spotlight)
                        else Modifier
                    ) {
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
                    .then(
                        if (spotlight != null)
                            Modifier.spotlightTarget(SpotlightTargets.PERIOD_CHIPS, spotlight)
                        else Modifier
                    )
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
            // Hero balance card + compact stat tiles — one clear focal point,
            // supporting numbers grouped beneath it instead of stacked full-width blocks.
            item {
                val balance = stats.totalIncome - stats.totalSpent - stats.totalSavings
                GradientStatCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (spotlight != null)
                                Modifier.spotlightTarget(SpotlightTargets.BALANCE_CARD, spotlight)
                            else Modifier
                        ),
                    title = "Balance",
                    amount = currencyFormat.format(balance),
                    gradientColors = if (balance >= 0)
                        listOf(Color(0xFF7C4DFF), Color(0xFF536DFE))
                    else
                        listOf(Color(0xFFFF6F00), Color(0xFFFF3D00)),
                    hero = true
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CompactStatTile(
                        modifier = Modifier.weight(1f),
                        title = "Spent",
                        amount = currencyFormat.format(stats.totalSpent),
                        accent = Color(0xFFFF5252),
                        icon = Icons.Default.TrendingDown
                    )
                    CompactStatTile(
                        modifier = Modifier.weight(1f),
                        title = "Income",
                        amount = currencyFormat.format(stats.totalIncome),
                        accent = Color(0xFF4CAF50),
                        icon = Icons.Default.TrendingUp
                    )
                    if (stats.totalSavings > 0) {
                        CompactStatTile(
                            modifier = Modifier.weight(1f),
                            title = "Saved",
                            amount = currencyFormat.format(stats.totalSavings),
                            accent = Color(0xFF26A69A),
                            icon = Icons.Default.AccountBalanceWallet
                        )
                    }
                }
            }

            if (stats.categoryBreakdown.isNotEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(
                                text = "Where your money goes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
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
                    GlassSectionHeader(title = "Insights")
                }
                items(insights) { insight ->
                    InsightCard(insight = insight)
                }
            }

            item {
                GlassSectionHeader(title = "Recent Transactions")
            }

            items(transactions.take(10)) { transaction ->
                TransactionItem(transaction = transaction)
            }
        }
    }
}

/**
 * The one-time feature-discovery tour shown on first run. It opens with a quick orientation
 * on the dashboard, then navigates to the Transactions page — the heart of the app — and
 * spotlights a real transaction row to teach the two core actions: changing a category and
 * splitting an expense with friends.
 *
 * @param transactionsRoute the nav route for the Transactions list, so steps can jump there.
 */
private fun featureTourSteps(dashboardRoute: String, transactionsRoute: String): List<SpotlightStep> {
    val txnAccent = Color(0xFF26A69A)
    return listOf(
        SpotlightStep(
            targetKey = SpotlightTargets.BALANCE_CARD,
            title = "Your money at a glance",
            description = "This card shows your balance for the selected period — income minus what you've spent and saved.",
            icon = Icons.Default.AccountBalanceWallet,
            route = dashboardRoute
        ),
        SpotlightStep(
            targetKey = SpotlightTargets.PERIOD_CHIPS,
            title = "Switch the time period",
            description = "Tap a chip to see Today, this week, this month, or a custom range. You start on Today.",
            icon = Icons.Default.Timeline,
            route = dashboardRoute
        ),
        // From here the tour lives on the Transactions page.
        SpotlightStep(
            targetKey = SpotlightTargets.FIRST_TRANSACTION,
            title = "This is where it all happens",
            description = "Every transaction lands here, auto-sorted by date. Tap any one to open its options — let's try it.",
            icon = Icons.Default.Receipt,
            accent = txnAccent,
            route = transactionsRoute
        ),
        SpotlightStep(
            targetKey = SpotlightTargets.FIRST_TRANSACTION,
            title = "Fix the category",
            description = "Wrong category? Tap the transaction and pick the right one. Choose to apply it to just this one, or to every transaction from that merchant.",
            icon = Icons.Default.Category,
            accent = txnAccent,
            route = transactionsRoute
        ),
        SpotlightStep(
            targetKey = SpotlightTargets.FIRST_TRANSACTION,
            title = "Split with friends",
            description = "Shared the bill? Tap a transaction and choose Split — add the friends you split with and only your share counts toward your spending.",
            icon = Icons.Default.Groups,
            primaryLabel = "Got it",
            accent = txnAccent,
            route = transactionsRoute
        )
    )
}

@Composable
fun GradientStatCard(
    modifier: Modifier = Modifier,
    title: String,
    amount: String,
    gradientColors: List<Color>,
    hero: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(gradientColors))
    ) {
        // Glossy diagonal sheen for a richer, glassy hero.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)
                    )
                )
        )
        Column(modifier = Modifier.padding(if (hero) 24.dp else 20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(if (hero) 8.dp else 4.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = if (hero) 36.sp else 22.sp
                ),
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/** Small, low-noise glass tile for supporting numbers under the hero balance card. */
@Composable
fun CompactStatTile(
    modifier: Modifier = Modifier,
    title: String,
    amount: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    GlassCard(modifier = modifier, cornerRadius = 18.dp, contentPadding = PaddingValues(14.dp)) {
        Column {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
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

    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, contentPadding = PaddingValues(14.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
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
