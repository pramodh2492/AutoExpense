package com.expensetracker.ui.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(viewModel: ExpenseViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var renameText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ViewFilter.DEBITS) }
    var searchQuery by remember { mutableStateOf("") }
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    val filteredByType = when (selectedFilter) {
        ViewFilter.DEBITS -> transactions.filter { it.type == TransactionType.DEBIT }
        ViewFilter.CREDITS -> transactions.filter { it.type == TransactionType.CREDIT }
        ViewFilter.ALL -> transactions
    }

    // Apply search filter
    val filtered = if (searchQuery.isBlank()) {
        filteredByType
    } else {
        val query = searchQuery.trim().lowercase()
        val queryAsNumber = query.toDoubleOrNull()
        filteredByType.filter { txn ->
            txn.merchant.lowercase().contains(query) ||
                txn.category.name.lowercase().contains(query) ||
                (queryAsNumber != null && txn.amount == queryAsNumber)
        }
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

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            placeholder = { Text("Search by merchant, category, or amount") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            singleLine = true
        )

        // Summary
        Text(
            text = "${filtered.size} transactions • ${currencyFormat.format(totalAmount)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (filtered.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No transactions found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            "No results for '$searchQuery'"
                        } else {
                            when (selectedFilter) {
                                ViewFilter.DEBITS -> "No debits this period"
                                ViewFilter.CREDITS -> "No credits this period"
                                ViewFilter.ALL -> "No transactions this period"
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                grouped.forEach { (date, dayTransactions) ->
                    item {
                        DateHeader(date = date, total = dayTransactions.sumOf { it.amount })
                    }
                    items(dayTransactions, key = { it.id }) { transaction ->
                        val dismissState = rememberDismissState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == DismissValue.DismissedToStart) {
                                    viewModel.deleteTransaction(transaction)
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismiss(
                            state = dismissState,
                            directions = setOf(DismissDirection.EndToStart),
                            background = {
                                val color by animateColorAsState(
                                    targetValue = when (dismissState.targetValue) {
                                        DismissValue.DismissedToStart -> Color(0xFFFF1744)
                                        else -> Color.Transparent
                                    },
                                    label = "swipe_bg_color"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.White
                                    )
                                }
                            },
                            dismissContent = {
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
                                    },
                                    onDelete = {
                                        viewModel.deleteTransaction(transaction)
                                    }
                                )
                            }
                        )
                    }
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
