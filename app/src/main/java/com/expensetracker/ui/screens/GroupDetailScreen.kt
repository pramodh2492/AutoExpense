package com.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

                // Expenses
                val expenses = group?.expenses ?: emptyList()
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
