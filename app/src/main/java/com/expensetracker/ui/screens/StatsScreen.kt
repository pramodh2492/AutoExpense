package com.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expensetracker.ui.components.CategoryPieChart
import com.expensetracker.ui.components.GlassCard
import com.expensetracker.ui.components.GlassSectionHeader
import com.expensetracker.viewmodel.ExpenseViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun StatsScreen(viewModel: ExpenseViewModel) {
    val stats by viewModel.monthlyStats.collectAsState()
    val dailySpend by viewModel.monthlyDailySpend.collectAsState()
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Analytics",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    GlassSectionHeader(title = "Spending by Category")
                    Spacer(modifier = Modifier.height(16.dp))
                    if (stats.categoryBreakdown.isNotEmpty()) {
                        CategoryPieChart(
                            data = stats.categoryBreakdown,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        stats.categoryBreakdown.entries
                            .sortedByDescending { it.value }
                            .forEach { (category, amount) ->
                                val percentage = if (stats.totalSpent > 0) {
                                    (amount / stats.totalSpent).toFloat()
                                } else 0f

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = category.displayName,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        LinearProgressIndicator(
                                            progress = percentage,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                        )
                                    }
                                    Text(
                                        text = currencyFormat.format(amount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                }
                            }
                    } else {
                        Text(
                            text = "No data yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    GlassSectionHeader(title = "Top Merchants")
                    Spacer(modifier = Modifier.height(12.dp))

                    if (stats.topMerchants.isNotEmpty()) {
                        stats.topMerchants.forEachIndexed { index, merchant ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row {
                                    Text(
                                        text = "${index + 1}.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Column {
                                        Text(
                                            text = merchant.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${merchant.transactionCount} transactions",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = currencyFormat.format(merchant.totalSpent),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "No data yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (dailySpend.isNotEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        GlassSectionHeader(title = "Daily Spending")
                        Spacer(modifier = Modifier.height(12.dp))
                        val maxDaily = dailySpend.values.maxOrNull() ?: 1.0
                        dailySpend.entries.sortedByDescending { it.key }.take(30).forEach { (date, amount) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = date.takeLast(5),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(0.2f)
                                )
                                LinearProgressIndicator(
                                    progress = (amount / maxDaily).toFloat(),
                                    modifier = Modifier
                                        .weight(0.5f)
                                        .height(8.dp),
                                )
                                Text(
                                    text = currencyFormat.format(amount),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .weight(0.3f)
                                        .padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
