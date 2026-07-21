package com.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.model.TransactionCategory
import com.expensetracker.data.model.TransactionType
import com.expensetracker.ui.components.TransactionItem
import com.expensetracker.viewmodel.ExpenseViewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ViewFilter(val label: String) {
    DEBITS("Debits"),
    CREDITS("Credits"),
    ALL("All")
}

@Composable
fun TransactionListScreen(viewModel: ExpenseViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var renameText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ViewFilter.DEBITS) }
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    val filtered = when (selectedFilter) {
        ViewFilter.DEBITS -> transactions.filter { it.type == TransactionType.DEBIT }
        ViewFilter.CREDITS -> transactions.filter { it.type == TransactionType.CREDIT }
        ViewFilter.ALL -> transactions
    }

    val grouped = filtered.groupBy { it.timestamp.toLocalDate() }
        .toSortedMap(compareByDescending { it })

    val totalAmount = filtered.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Transactions",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ViewFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.label) }
                )
            }
        }

        // Summary
        Text(
            text = "${filtered.size} transactions • ${currencyFormat.format(totalAmount)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            grouped.forEach { (date, dayTransactions) ->
                item {
                    DateHeader(date = date, total = dayTransactions.sumOf { it.amount })
                }
                items(dayTransactions) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        onCategoryChange = { newCategory ->
                            viewModel.updateCategory(transaction, newCategory)
                        },
                        onToggleSelfTransfer = {
                            viewModel.toggleSelfTransfer(transaction)
                        },
                        onRename = {
                            editingTransaction = transaction
                            renameText = transaction.merchant
                        }
                    )
                }
            }
        }
    }

    if (editingTransaction != null) {
        AlertDialog(
            onDismissRequest = { editingTransaction = null },
            title = { Text("Rename Merchant") },
            text = {
                Column {
                    Text(
                        text = "What is this place?",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("Merchant name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editingTransaction?.let { txn ->
                        viewModel.renameMerchant(txn, renameText.trim())
                    }
                    editingTransaction = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTransaction = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DateHeader(date: LocalDate, total: Double) {
    val today = LocalDate.now()
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val label = when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("dd MMM, EEEE"))
        else -> date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = currencyFormat.format(total),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
