package com.expensetracker.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import background
import androidx.compose.ui.draw.clip
import CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.expensetracker.data.model.ExpenseGroup
import com.expensetracker.data.model.GroupExpense
import com.expensetracker.data.model.Settlement
import com.expensetracker.ui.components.GlassBackground
import com.expensetracker.ui.components.GlassCard
import com.expensetracker.ui.components.GlassSectionHeader
import com.expensetracker.ui.components.GradientPillButton
import com.expensetracker.viewmodel.GroupViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    code: String,
    viewModel: GroupViewModel,
    navController: NavController
) {
    val group by viewModel.activeGroup.collectAsState()
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    var showAddExpense by remember { mutableStateOf(false) }
    var showCloseConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(code) { viewModel.observeGroup(code) }

    GlassBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        group?.name ?: code,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (group?.isClosed == false && group?.createdByUid == viewModel.currentUid) {
                        TextButton(onClick = { showCloseConfirm = true }) {
                            Text("Close group", color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Group code + member count
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Share this code to invite",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Text(
                                    code,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${group?.members?.size ?: 0} members",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                if (group?.isClosed == true) {
                                    Text(
                                        "Closed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                // Settlement summary
                val settlements = group?.settlementSummary(viewModel.currentUid ?: "") ?: emptyList()
                if (settlements.isNotEmpty()) {
                    item { GlassSectionHeader(title = "Settlement") }
                    items(settlements) { s -> SettlementRow(s, currencyFormat) }
                }

                // Add expense button
                if (group?.isClosed == false) {
                    item {
                        GradientPillButton(
                            text = "Add expense",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showAddExpense = true }
                        )
                    }
                }

                // Charts
                val expenses = group?.expenses ?: emptyList()
                val members = group?.members ?: emptyList()
                if (expenses.isNotEmpty()) {
                    item {
                        GlassSectionHeader(title = "Insights")
                        Spacer(Modifier.height(12.dp))
                        GroupSpendingChart(expenses = expenses)
                        Spacer(Modifier.height(12.dp))
                        GroupPersonChart(
                            expenses = expenses,
                            members = members,
                            currentUid = viewModel.currentUid ?: "",
                            currencyFormat = currencyFormat
                        )
                    }
                }

                // Expenses
                if (expenses.isNotEmpty()) {
                    item { GlassSectionHeader(title = "Expenses") }
                    items(expenses) { expense ->
                        ExpenseRow(
                            expense = expense,
                            currencyFormat = currencyFormat,
                            currentUid = viewModel.currentUid ?: "",
                            onSettle = { uid ->
                                viewModel.settleUp(code, expense.id, uid)
                            }
                        )
                    }
                }

                if (expenses.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "No expenses yet. Add the first one!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                // Total spent summary at the bottom
                if (expenses.isNotEmpty()) {
                    item {
                        val totalSpent = expenses.sumOf { it.amount }
                        val myShare = expenses.sumOf { expense ->
                            val uid = viewModel.currentUid ?: ""
                            if (expense.paidByUid == uid) {
                                // I paid — my share is total minus what others owe me
                                expense.amount - expense.splitAmong.sumOf { it.share }
                            } else {
                                // Someone else paid — my share is what I owe
                                expense.splitAmong.find { it.uid == uid }?.share ?: 0.0
                            }
                        }
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Total spent",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        currencyFormat.format(totalSpent),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "Your share",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        currencyFormat.format(myShare),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddExpense) {
        AddGroupExpenseDialog(
            members = group?.members?.filter { it.uid != viewModel.currentUid } ?: emptyList(),
            currentName = viewModel.currentName,
            onDismiss = { showAddExpense = false },
            onAdd = { description, amount ->
                showAddExpense = false
                val allMembers = group?.members?.filter { it.uid != viewModel.currentUid } ?: emptyList()
                viewModel.addExpense(code, description, amount, allMembers) {}
            }
        )
    }

    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text("Close group?") },
            text = { Text("No new expenses can be added, but everyone can still view the history.") },
            confirmButton = {
                GradientPillButton(text = "Close group", onClick = {
                    showCloseConfirm = false
                    viewModel.closeGroup(code) { navController.popBackStack() }
                })
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettlementRow(settlement: Settlement, fmt: NumberFormat) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                settlement.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                if (settlement.amount > 0)
                    "owes you ${fmt.format(settlement.amount)}"
                else
                    "you owe ${fmt.format(-settlement.amount)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (settlement.amount > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: GroupExpense,
    currencyFormat: NumberFormat,
    currentUid: String,
    onSettle: (String) -> Unit
) {
    val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        expense.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        "Paid by ${expense.paidByName} · ${fmt.format(Date(expense.timestamp))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                Text(
                    currencyFormat.format(expense.amount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            if (expense.splitAmong.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                expense.splitAmong.forEach { split ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${split.displayName}: ${currencyFormat.format(split.share)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (split.settled) Color.White.copy(alpha = 0.4f) else Color.White
                        )
                        if (!split.settled && expense.paidByUid == currentUid) {
                            IconButton(
                                onClick = { onSettle(split.uid) },
                                modifier = Modifier.height(24.dp).width(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Mark settled",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                        } else if (split.settled) {
                            Text(
                                "Settled",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }
        }
    }
}

private val chartColors = listOf(
    Color(0xFF9D7BFF), Color(0xFF00E5FF), Color(0xFFFFAB40),
    Color(0xFF4CAF50), Color(0xFFFF5252), Color(0xFF2196F3),
    Color(0xFFE91E63), Color(0xFF00BCD4), Color(0xFFFFC107)
)

// Chart 1 — Pie: spending by merchant/description
@Composable
private fun GroupSpendingChart(expenses: List<GroupExpense>) {
    val glass = com.expensetracker.ui.theme.AppTheme.glass
    val byMerchant = expenses
        .groupBy { it.description.trim() }
        .mapValues { (_, v) -> v.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }
    val total = byMerchant.sumOf { it.value }
    if (total == 0.0) return

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                "Where the group spent",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pie
                Canvas(modifier = Modifier.size(120.dp)) {
                    var startAngle = -90f
                    byMerchant.forEachIndexed { i, (_, amount) ->
                        val sweep = (amount / total * 360f).toFloat()
                        drawArc(
                            color = chartColors[i % chartColors.size],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = true,
                            topLeft = Offset(10f, 10f),
                            size = Size(size.width - 20f, size.height - 20f)
                        )
                        startAngle += sweep
                    }
                }
                // Legend
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    byMerchant.take(5).forEachIndexed { i, (desc, amount) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(chartColors[i % chartColors.size])
                            )
                            Text(
                                desc,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${((amount / total) * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                    if (byMerchant.size > 5) {
                        Text(
                            "+${byMerchant.size - 5} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }
    }
}

// Chart 2 — Horizontal bars: how much each person paid vs owes
@Composable
private fun GroupPersonChart(
    expenses: List<GroupExpense>,
    members: List<com.expensetracker.data.model.GroupMember>,
    currentUid: String,
    currencyFormat: NumberFormat
) {
    // Build per-person totals: paid = expenses where paidByUid == uid, owes = unsettled splits
    data class PersonStats(val name: String, val paid: Double, val owes: Double)

    val allNames = (members.map { it.uid to it.displayName } +
        expenses.map { it.paidByUid to it.paidByName }).toMap()

    val paidMap = mutableMapOf<String, Double>()
    val owesMap = mutableMapOf<String, Double>()
    expenses.forEach { expense ->
        paidMap[expense.paidByUid] = (paidMap[expense.paidByUid] ?: 0.0) + expense.amount
        expense.splitAmong.forEach { split ->
            if (!split.settled) {
                owesMap[split.uid] = (owesMap[split.uid] ?: 0.0) + split.share
            }
        }
    }

    val stats = allNames.map { (uid, name) ->
        PersonStats(name, paidMap[uid] ?: 0.0, owesMap[uid] ?: 0.0)
    }.filter { it.paid > 0 || it.owes > 0 }

    if (stats.isEmpty()) return

    val maxVal = stats.maxOf { maxOf(it.paid, it.owes) }.takeIf { it > 0 } ?: return

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Person-wise split",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.7f)
            )
            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF4CAF50)))
                    Text("Paid", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFFF5252)))
                    Text("Owes", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                }
            }
            stats.forEach { person ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        person.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    // Paid bar
                    if (person.paid > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Canvas(modifier = Modifier.weight(1f).height(10.dp)) {
                                val barW = (person.paid / maxVal * size.width).toFloat()
                                drawRoundRect(
                                    color = Color(0xFF4CAF50).copy(alpha = 0.25f),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(5f)
                                )
                                drawRoundRect(
                                    color = Color(0xFF4CAF50),
                                    size = Size(barW, size.height),
                                    cornerRadius = CornerRadius(5f)
                                )
                            }
                            Text(
                                currencyFormat.format(person.paid),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.width(72.dp)
                            )
                        }
                    }
                    // Owes bar
                    if (person.owes > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Canvas(modifier = Modifier.weight(1f).height(10.dp)) {
                                val barW = (person.owes / maxVal * size.width).toFloat()
                                drawRoundRect(
                                    color = Color(0xFFFF5252).copy(alpha = 0.25f),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(5f)
                                )
                                drawRoundRect(
                                    color = Color(0xFFFF5252),
                                    size = Size(barW, size.height),
                                    cornerRadius = CornerRadius(5f)
                                )
                            }
                            Text(
                                currencyFormat.format(person.owes),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFF5252),
                                modifier = Modifier.width(72.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddGroupExpenseDialog(
    members: List<com.expensetracker.data.model.GroupMember>,
    currentName: String,
    onDismiss: () -> Unit,
    onAdd: (description: String, amount: Double) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.matches(Regex("""\d*\.?\d*"""))) amountText = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (members.isNotEmpty()) {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    val each = if (amount > 0) amount / (members.size + 1) else 0.0
                    Text(
                        "Split equally: ₹${String.format("%.2f", each)} each " +
                            "(you + ${members.size} member${if (members.size > 1) "s" else ""})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        },
        confirmButton = {
            val amount = amountText.toDoubleOrNull() ?: 0.0
            GradientPillButton(
                text = "Add",
                enabled = description.isNotBlank() && amount > 0,
                onClick = { onAdd(description.trim(), amount) }
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
