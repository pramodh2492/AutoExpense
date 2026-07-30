package com.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.expensetracker.data.model.PaymentSource
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.model.TransactionCategory
import com.expensetracker.data.model.TransactionType
import com.expensetracker.ui.components.GlassCard
import com.expensetracker.ui.components.GlassSectionHeader
import com.expensetracker.ui.components.GradientPillButton
import com.expensetracker.ui.theme.AppTheme
import com.expensetracker.viewmodel.ExpenseViewModel
import java.time.LocalDateTime

@Composable
fun AddTransactionScreen(
    viewModel: ExpenseViewModel,
    onDone: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(TransactionType.DEBIT) }
    var selectedCategory by remember { mutableStateOf(TransactionCategory.OTHER) }
    var selectedSource by remember { mutableStateOf(PaymentSource.UPI) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    val glass = AppTheme.glass
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = glass.accentGradient.first().copy(alpha = 0.30f),
        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        containerColor = Color.Transparent
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (selectedType == TransactionType.DEBIT) "Add Expense" else "Add Income",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Amount + type + merchant details
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Debit / Credit toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == TransactionType.DEBIT,
                        onClick = { selectedType = TransactionType.DEBIT },
                        label = { Text("Expense (Debit)") },
                        colors = chipColors
                    )
                    FilterChip(
                        selected = selectedType == TransactionType.CREDIT,
                        onClick = { selectedType = TransactionType.CREDIT },
                        label = { Text("Income (Credit)") },
                        colors = chipColors
                    )
                }

                // Prominent amount field
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text(if (selectedType == TransactionType.DEBIT) "Paid to (shop/person/app)" else "Received from (person/company)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Payment source
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassSectionHeader(title = "Payment method")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(PaymentSource.UPI, PaymentSource.CREDIT_CARD, PaymentSource.DEBIT_CARD).forEach { source ->
                        FilterChip(
                            selected = selectedSource == source,
                            onClick = { selectedSource = source },
                            label = {
                                Text(
                                    when (source) {
                                        PaymentSource.UPI -> "UPI"
                                        PaymentSource.CREDIT_CARD -> "Credit Card"
                                        PaymentSource.DEBIT_CARD -> "Debit Card"
                                        else -> source.name
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = chipColors
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(PaymentSource.NET_BANKING, PaymentSource.WALLET, PaymentSource.ECS_NACH).forEach { source ->
                        FilterChip(
                            selected = selectedSource == source,
                            onClick = { selectedSource = source },
                            label = {
                                Text(
                                    when (source) {
                                        PaymentSource.NET_BANKING -> "Net Banking"
                                        PaymentSource.WALLET -> "Wallet"
                                        PaymentSource.ECS_NACH -> "ECS/NACH"
                                        else -> source.name
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = chipColors
                        )
                    }
                }
            }
        }

        // Category
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassSectionHeader(title = "Category")
                Column {
                    OutlinedButton(
                        onClick = { showCategoryPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedCategory.displayName)
                    }
                    DropdownMenu(
                        expanded = showCategoryPicker,
                        onDismissRequest = { showCategoryPicker = false }
                    ) {
                        TransactionCategory.entries.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.displayName) },
                                onClick = {
                                    selectedCategory = category
                                    showCategoryPicker = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Note
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        GradientPillButton(
            text = if (selectedType == TransactionType.DEBIT) "Add Expense" else "Add Income",
            modifier = Modifier.fillMaxWidth(),
            enabled = amount.isNotBlank() && merchant.isNotBlank(),
            onClick = {
                val amountVal = amount.toDoubleOrNull() ?: return@GradientPillButton
                if (merchant.isBlank()) return@GradientPillButton

                val now = LocalDateTime.now()
                val transaction = Transaction(
                    amount = amountVal,
                    merchant = merchant.trim(),
                    category = selectedCategory,
                    type = selectedType,
                    source = selectedSource,
                    accountInfo = "",
                    rawSms = if (note.isBlank()) "Manual entry" else "Manual: $note",
                    smsHash = "manual_${now.hashCode()}_${amountVal.hashCode()}",
                    timestamp = now
                )
                viewModel.addManualTransaction(transaction)
                onDone()
            }
        )
    }
}
