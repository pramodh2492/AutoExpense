package com.expensetracker.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDismissState
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
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
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.model.TransactionCategory
import com.expensetracker.data.model.TransactionType
import com.expensetracker.data.local.UserPreferences
import com.expensetracker.ui.components.GlassCard
import com.expensetracker.ui.components.LocalSpotlight
import com.expensetracker.ui.components.SpotlightTargets
import com.expensetracker.ui.components.GroupPickerDialog
import com.expensetracker.ui.components.SplitActionSheet
import com.expensetracker.ui.components.TransactionItem
import com.expensetracker.ui.components.spotlightTarget
import com.expensetracker.ui.theme.AppTheme
import com.expensetracker.viewmodel.ExpenseViewModel
import com.expensetracker.viewmodel.GroupViewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ViewFilter(val label: String) {
    DEBITS("Debits"),
    CREDITS("Credits"),
    ALL("All"),
    TO_CATEGORIZE("To categorize")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    viewModel: ExpenseViewModel,
    groupViewModel: GroupViewModel? = null,
    userPreferences: UserPreferences? = null,
    onNavigateToGroups: (() -> Unit)? = null,
    onSignInRequired: (() -> Unit)? = null
) {
    val transactions by viewModel.transactions.collectAsState()
    val glass = AppTheme.glass
    val spotlight = LocalSpotlight.current
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var renameText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ViewFilter.DEBITS) }
    var searchQuery by remember { mutableStateOf("") }
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var lastDeletedTransaction by remember { mutableStateOf<Transaction?>(null) }

    // Category change confirmation
    var pendingCategoryChange by remember { mutableStateOf<Pair<Transaction, TransactionCategory>?>(null) }

    // Split-with-friends dialog target
    var splittingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showSplitActionSheet by remember { mutableStateOf<Transaction?>(null) }
    var showGroupPicker by remember { mutableStateOf<Transaction?>(null) }
    val myGroups by (groupViewModel?.myGroups?.collectAsState() ?: remember { androidx.compose.runtime.mutableStateOf(emptyList()) })

    val filteredByType = when (selectedFilter) {
        ViewFilter.DEBITS -> transactions.filter { it.type == TransactionType.DEBIT }
        ViewFilter.CREDITS -> transactions.filter { it.type == TransactionType.CREDIT }
        ViewFilter.ALL -> transactions
        // Untrained merchants land in OTHER — surface them so they're easy to categorize.
        ViewFilter.TO_CATEGORIZE -> transactions.filter { it.category == TransactionCategory.OTHER }
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

    // The topmost visible row — the tour spotlights it to demo tap-to-recategorize/split.
    val firstTxnId = grouped.entries.firstOrNull()?.value?.firstOrNull()?.id

    // Use effective (post-split) amounts so the header total matches the figures shown on
    // each row — a split debit contributes only your share.
    val totalAmount = filtered.sumOf { it.effectiveAmount }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 80.dp)
            )
        }
    ) { scaffoldPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
            .padding(16.dp)
    ) {
        // Title with the running count + total folded into the same row, so the
        // summary isn't a separate stacked line eating vertical space.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "Transactions",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${filtered.size} • ${currencyFormat.format(totalAmount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val toCategorizeCount = transactions.count { it.category == TransactionCategory.OTHER }
            ViewFilter.entries.forEach { filter ->
                val label = if (filter == ViewFilter.TO_CATEGORIZE && toCategorizeCount > 0) {
                    "${filter.label} ($toCategorizeCount)"
                } else {
                    filter.label
                }
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(label) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        selectedContainerColor = glass.accentGradient.first().copy(alpha = 0.22f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = MaterialTheme.colorScheme.outline,
                        selectedBorderColor = glass.accentGradient.first()
                    )
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
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = glass.glassSolid.copy(alpha = 0.25f),
                unfocusedContainerColor = glass.glassSolid.copy(alpha = 0.18f),
                focusedBorderColor = glass.accentGradient.first(),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        if (filtered.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassCard(contentPadding = PaddingValues(32.dp)) {
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
                                    ViewFilter.TO_CATEGORIZE -> "Everything's categorized 🎉"
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                grouped.forEach { (date, dayTransactions) ->
                    item {
                        DateHeader(date = date, total = dayTransactions.sumOf { it.effectiveAmount })
                    }
                    items(dayTransactions, key = { it.id }) { transaction ->
                        val dismissState = rememberDismissState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == DismissValue.DismissedToStart) {
                                    lastDeletedTransaction = transaction
                                    viewModel.deleteTransaction(transaction)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Transaction deleted",
                                            actionLabel = "UNDO",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            lastDeletedTransaction?.let { viewModel.addManualTransaction(it) }
                                        }
                                        lastDeletedTransaction = null
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismiss(
                            modifier = if (spotlight != null && transaction.id == firstTxnId)
                                Modifier.spotlightTarget(SpotlightTargets.FIRST_TRANSACTION, spotlight)
                            else Modifier,
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
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(color)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    // Only render while the row is actually being swiped — the
                                    // card on top is now translucent glass, so a permanently
                                    // drawn icon would bleed through and overlap the amount.
                                    if (dismissState.dismissDirection == DismissDirection.EndToStart) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.White
                                        )
                                    }
                                }
                            },
                            dismissContent = {
                                TransactionItem(
                                    transaction = transaction,
                                    onCategoryChange = { newCategory ->
                                        pendingCategoryChange = transaction to newCategory
                                    },
                                    onToggleSelfTransfer = {
                                        viewModel.toggleSelfTransfer(transaction)
                                    },
                                    onRename = {
                                        editingTransaction = transaction
                                        renameText = transaction.merchant
                                    },
                                    // Splitting only applies to money you paid out (debits).
                                    onSplit = if (com.expensetracker.config.FeatureFlags.SPLIT_ENABLED &&
                                        transaction.type == TransactionType.DEBIT) {
                                        { showSplitActionSheet = transaction }
                                    } else null,
                                    onDelete = {
                                        lastDeletedTransaction = transaction
                                        viewModel.deleteTransaction(transaction)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Transaction deleted",
                                                actionLabel = "UNDO",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                lastDeletedTransaction?.let { viewModel.addManualTransaction(it) }
                                            }
                                            lastDeletedTransaction = null
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
    }

    // Category change confirmation dialog
    if (pendingCategoryChange != null) {
        val (txn, newCategory) = pendingCategoryChange!!

        // Salary category — always "just this one" (same merchant can send salary + cashback)
        if (newCategory == TransactionCategory.SALARY) {
            viewModel.updateSingleTransaction(txn, newCategory)
            pendingCategoryChange = null
        } else {
            AlertDialog(
                onDismissRequest = { pendingCategoryChange = null },
                title = { Text("Apply to all?") },
                text = {
                    Text("Apply \"${newCategory.displayName}\" to all ${if (txn.type == TransactionType.DEBIT) "debits" else "credits"} from \"${txn.merchant}\" (past & future)? Or just this one?")
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateCategory(txn, newCategory)
                        pendingCategoryChange = null
                    }) { Text("All & Future") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.updateSingleTransaction(txn, newCategory)
                        pendingCategoryChange = null
                    }) { Text("Just this one") }
                }
            )
        }
    }

    // Action sheet — choose split mode
    showSplitActionSheet?.let { txn ->
        SplitActionSheet(
            hasGroups = myGroups.isNotEmpty(),
            onSplitWithFriends = {
                showSplitActionSheet = null
                splittingTransaction = txn
            },
            onAddToGroup = {
                showGroupPicker = txn
                showSplitActionSheet = null
            },
            onCreateGroup = {
                showSplitActionSheet = null
                if (groupViewModel?.isSignedIn == true) {
                    groupViewModel.setPendingExpense(txn.merchant, txn.amount)
                    onNavigateToGroups?.invoke()
                } else onSignInRequired?.invoke()
            },
            onDismiss = { showSplitActionSheet = null }
        )
    }

    // Group picker — shown when user taps "Add to existing group"
    showGroupPicker?.let { txn ->
        GroupPickerDialog(
            groups = myGroups.filter { !it.isClosed },
            onGroupSelected = { group ->
                showGroupPicker = null
                val members = group.members.filter { it.uid != groupViewModel?.currentUid }
                groupViewModel?.addExpense(
                    code = group.code,
                    description = txn.merchant,
                    amount = txn.amount,
                    members = members,
                    onDone = {}
                )
            },
            onDismiss = { showGroupPicker = null }
        )
    }

    // Split-with-friends bottom sheet
    splittingTransaction?.let { txn ->
        val existingSplit = remember(txn.id, txn.splitJson) { viewModel.splitParticipants(txn) }
        // Collect group expenses at this merchant across all groups — visible to friends
        val merchantGroupExpenses = remember(txn.merchant, myGroups) {
            myGroups.flatMap { group ->
                group.expenses.filter { expense ->
                    expense.description.equals(txn.merchant, ignoreCase = true)
                }
            }
        }
        com.expensetracker.ui.components.SplitDialog(
            transaction = txn,
            existing = existingSplit,
            upiId = userPreferences?.upiId ?: "",
            groupExpenses = merchantGroupExpenses,
            onDismiss = { splittingTransaction = null },
            onSave = { participants ->
                viewModel.saveSplit(txn, participants)
                splittingTransaction = null
            },
            onClear = {
                viewModel.saveSplit(txn, emptyList())
                splittingTransaction = null
            }
        )
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
    val glass = AppTheme.glass
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .width(4.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(glass.accentGradient))
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = currencyFormat.format(total),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
