package com.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.expensetracker.data.local.BudgetPreferences
import com.expensetracker.data.model.TransactionCategory
import com.expensetracker.ui.components.CategoryStyle
import com.expensetracker.ui.components.GlassCard
import com.expensetracker.ui.components.GlassSectionHeader
import com.expensetracker.ui.components.getCategoryStyle
import com.expensetracker.viewmodel.ExpenseViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun BudgetScreen(
    viewModel: ExpenseViewModel,
    budgetPreferences: BudgetPreferences
) {
    val stats by viewModel.monthlyStats.collectAsState()
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    var totalBudget by remember { mutableStateOf(budgetPreferences.totalMonthlyBudget.let { if (it > 0) it.toInt().toString() else "" }) }

    val spendingCategories = listOf(
        TransactionCategory.FOOD_DINING,
        TransactionCategory.GROCERIES,
        TransactionCategory.TRANSPORT,
        TransactionCategory.SHOPPING,
        TransactionCategory.BILLS_UTILITIES,
        TransactionCategory.ENTERTAINMENT,
        TransactionCategory.HEALTH,
        TransactionCategory.EDUCATION,
        TransactionCategory.FUEL,
        TransactionCategory.TRAVEL,
        TransactionCategory.SUBSCRIPTION,
        TransactionCategory.EMI,
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Budget",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Set monthly spending limits",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Total monthly budget
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    GlassSectionHeader(title = "Total Monthly Budget")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = totalBudget,
                        onValueChange = {
                            totalBudget = it.filter { c -> c.isDigit() }
                            totalBudget.toDoubleOrNull()?.let { amt ->
                                budgetPreferences.totalMonthlyBudget = amt
                            }
                        },
                        label = { Text("Monthly budget (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (budgetPreferences.totalMonthlyBudget > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val spent = stats.totalSpent
                        val budget = budgetPreferences.totalMonthlyBudget
                        val remaining = budget - spent
                        val percent = (spent / budget).toFloat().coerceIn(0f, 1.5f)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${currencyFormat.format(spent)} of ${currencyFormat.format(budget)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${(percent * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    percent >= 1f -> Color(0xFFFF5252)
                                    percent >= 0.8f -> Color(0xFFFFAB40)
                                    else -> Color(0xFF4CAF50)
                                }
                            )
                        }
                        LinearProgressIndicator(
                            progress = percent.coerceAtMost(1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = when {
                                percent >= 1f -> Color(0xFFFF5252)
                                percent >= 0.8f -> Color(0xFFFFAB40)
                                else -> Color(0xFF4CAF50)
                            },
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (remaining >= 0) "₹${String.format("%,.0f", remaining)} remaining"
                                else "₹${String.format("%,.0f", -remaining)} over budget!",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (remaining >= 0) Color(0xFF4CAF50) else Color(0xFFFF5252)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            GlassSectionHeader(title = "Category Limits")
        }

        items(spendingCategories) { category ->
            CategoryBudgetItem(
                category = category,
                currentSpent = stats.categoryBreakdown[category] ?: 0.0,
                budgetPreferences = budgetPreferences,
                currencyFormat = currencyFormat
            )
        }
    }
}

@Composable
private fun CategoryBudgetItem(
    category: TransactionCategory,
    currentSpent: Double,
    budgetPreferences: BudgetPreferences,
    currencyFormat: NumberFormat
) {
    val style = getCategoryStyle(category)
    var budgetText by remember {
        mutableStateOf(budgetPreferences.getCategoryBudget(category).let {
            if (it > 0) it.toInt().toString() else ""
        })
    }
    val budget = budgetPreferences.getCategoryBudget(category)

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(style.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = style.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = style.color
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (budget > 0) {
                        Text(
                            text = "${currencyFormat.format(currentSpent)} of ${currencyFormat.format(budget)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = {
                        budgetText = it.filter { c -> c.isDigit() }
                        val amount = budgetText.toDoubleOrNull() ?: 0.0
                        budgetPreferences.setCategoryBudget(category, amount)
                    },
                    label = { Text("₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(0.8f)
                )
            }

            if (budget > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                val percent = (currentSpent / budget).toFloat()
                val remaining = budget - currentSpent
                LinearProgressIndicator(
                    progress = percent.coerceAtMost(1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = when {
                        percent >= 1f -> Color(0xFFFF5252)
                        percent >= 0.8f -> Color(0xFFFFAB40)
                        else -> style.color
                    },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (remaining >= 0) "₹${String.format("%,.0f", remaining)} left"
                            else "₹${String.format("%,.0f", -remaining)} over!",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (remaining >= 0) Color(0xFF4CAF50) else Color(0xFFFF5252)
                    )
                    Text(
                        text = "${(percent * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
