package com.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.expensetracker.ai.SmartQueryEngine
import com.expensetracker.ai.SavingsGoal
import com.expensetracker.ai.SavingsGoalManager
import com.expensetracker.ai.SpendingPrediction
import com.expensetracker.ai.SpendingPredictor
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

@Composable
fun AiInsightsScreen(
    spendingPredictor: SpendingPredictor,
    savingsGoalManager: SavingsGoalManager,
    smartQueryEngine: SmartQueryEngine
) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val scope = rememberCoroutineScope()

    var prediction by remember { mutableStateOf<SpendingPrediction?>(null) }
    var goals by remember { mutableStateOf(savingsGoalManager.getGoals()) }
    var chatQuery by remember { mutableStateOf("") }
    var chatResponse by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showAddGoal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        prediction = spendingPredictor.predict()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "AI Insights",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Spending Prediction Card
        prediction?.let { pred ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.TrendingUp, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Month-End Prediction",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Projected: ${currencyFormat.format(pred.projectedMonthEnd)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (pred.comparedToLastMonth > 10) Color(0xFFFF5252)
                                else Color(0xFF4CAF50)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Daily avg: ${currencyFormat.format(pred.dailyAverage)} • ${pred.daysRemaining} days left",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val comparison = if (pred.comparedToLastMonth > 0) {
                            "${pred.comparedToLastMonth}% more than last month"
                        } else {
                            "${-pred.comparedToLastMonth}% less than last month"
                        }
                        Text(
                            text = comparison,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pred.comparedToLastMonth > 10) Color(0xFFFF5252)
                                else Color(0xFF4CAF50)
                        )

                        if (pred.categoryProjections.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Category Projections",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            pred.categoryProjections.forEach { cat ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${cat.category.displayName} ${cat.trend}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = currencyFormat.format(cat.projected),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Savings Goals
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Savings Goals",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { showAddGoal = !showAddGoal }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Goal")
                }
            }
        }

        if (showAddGoal) {
            item {
                AddGoalCard(
                    onAdd = { name, amount, date ->
                        savingsGoalManager.addGoal(name, amount, date)
                        goals = savingsGoalManager.getGoals()
                        showAddGoal = false
                    }
                )
            }
        }

        if (goals.isEmpty()) {
            item {
                Text(
                    text = "No savings goals yet. Tap + to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(goals) { goal ->
            GoalCard(
                goal = goal,
                isOnTrack = savingsGoalManager.isOnTrack(goal),
                progress = savingsGoalManager.getProgressPercent(goal),
                onDelete = {
                    savingsGoalManager.deleteGoal(goal.id)
                    goals = savingsGoalManager.getGoals()
                },
                onAddMoney = { amount ->
                    savingsGoalManager.addToGoal(goal.id, amount)
                    goals = savingsGoalManager.getGoals()
                },
                currencyFormat = currencyFormat
            )
        }

        // AI Chat
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ask about your expenses",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "e.g., \"How much did I spend on food?\", \"What's my biggest expense?\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            OutlinedTextField(
                value = chatQuery,
                onValueChange = { chatQuery = it },
                label = { Text("Ask anything...") },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (chatQuery.isNotBlank()) {
                                isLoading = true
                                scope.launch {
                                    chatResponse = smartQueryEngine.query(chatQuery)
                                    isLoading = false
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (chatQuery.isNotBlank()) {
                        isLoading = true
                        scope.launch {
                            chatResponse = smartQueryEngine.query(chatQuery)
                            isLoading = false
                        }
                    }
                }),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        if (isLoading) {
            item {
                Text(
                    text = "Thinking...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (chatResponse.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = chatResponse,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun AddGoalCard(onAdd: (String, Double, LocalDate) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("6") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Goal name (e.g., Emergency Fund)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() } },
                label = { Text("Target amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = months,
                onValueChange = { months = it.filter { c -> c.isDigit() } },
                label = { Text("Months to achieve") },
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = {
                    val targetAmount = amount.toDoubleOrNull() ?: return@TextButton
                    val targetMonths = months.toIntOrNull() ?: 6
                    val targetDate = LocalDate.now().plusMonths(targetMonths.toLong())
                    onAdd(name, targetAmount, targetDate)
                },
                enabled = name.isNotBlank() && amount.isNotBlank()
            ) {
                Text("Create Goal")
            }
        }
    }
}

@Composable
private fun GoalCard(
    goal: SavingsGoal,
    isOnTrack: Boolean,
    progress: Float,
    onDelete: () -> Unit,
    onAddMoney: (Double) -> Unit,
    currencyFormat: NumberFormat
) {
    var addAmount by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${currencyFormat.format(goal.savedSoFar)} of ${currencyFormat.format(goal.targetAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isOnTrack) Color(0xFF4CAF50) else Color(0xFFFFAB40)
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isOnTrack) "On track" else "Behind schedule",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOnTrack) Color(0xFF4CAF50) else Color(0xFFFFAB40)
                )
                Text(
                    text = "Save ${currencyFormat.format(goal.monthlySavingNeeded)}/month",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = addAmount,
                    onValueChange = { addAmount = it.filter { c -> c.isDigit() } },
                    label = { Text("₹") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                TextButton(onClick = {
                    addAmount.toDoubleOrNull()?.let { amt ->
                        onAddMoney(amt)
                        addAmount = ""
                    }
                }) {
                    Text("Add")
                }
            }
        }
    }
}
