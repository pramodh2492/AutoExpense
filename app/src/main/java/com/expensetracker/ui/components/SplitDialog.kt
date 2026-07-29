package com.expensetracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.expensetracker.data.model.SplitParticipant
import com.expensetracker.data.model.Transaction
import java.text.NumberFormat
import java.util.Locale

/**
 * Editable split row held in dialog state. `share` is a String so the field can be
 * cleared/typed freely; it's parsed to Double only on save.
 */
private data class SplitRow(
    var name: String,
    var share: String,
    var paid: Boolean
)

/**
 * Split-with-friends dialog. You start with your own share plus one friend; the total
 * is divided equally by default and every share is editable. Each friend has a "paid"
 * checkbox — ticking it reduces your recorded spend (handled by the repository). Saving
 * an empty friend list clears the split. The transaction's `amount` is never changed.
 */
@Composable
fun SplitDialog(
    transaction: Transaction,
    existing: List<SplitParticipant>,
    onDismiss: () -> Unit,
    onSave: (List<SplitParticipant>) -> Unit,
    onClear: () -> Unit
) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    // Seed the friend rows. If already split, load them; otherwise start with one friend
    // and an equal 2-way split (you + friend) as a sensible default.
    val rows = remember {
        mutableStateListOf<SplitRow>().apply {
            if (existing.isNotEmpty()) {
                existing.forEach { add(SplitRow(it.name, formatShare(it.share), it.paid)) }
            } else {
                val half = transaction.amount / 2.0
                add(SplitRow("", formatShare(half), false))
            }
        }
    }

    fun friendTotal(): Double = rows.sumOf { it.share.toDoubleOrNull() ?: 0.0 }
    val yourShare = (transaction.amount - friendTotal())

    /** Re-divide the total equally across you + all current friends. */
    fun splitEqually() {
        val people = rows.size + 1 // + you
        if (people <= 0) return
        val each = transaction.amount / people
        rows.indices.forEach { i -> rows[i] = rows[i].copy(share = formatShare(each)) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing.isEmpty()) "Split with friends" else "Edit split") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Total ${currencyFormat.format(transaction.amount)} • ${transaction.merchant}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tick a friend once they've paid you back — your spend drops by their share.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = row.name,
                            onValueChange = { rows[index] = rows[index].copy(name = it) },
                            label = { Text("Friend") },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f)
                        )
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(
                            value = row.share,
                            onValueChange = { new ->
                                // Allow only digits and a single decimal point.
                                if (new.isEmpty() || new.matches(Regex("""\d*\.?\d*"""))) {
                                    rows[index] = rows[index].copy(share = new)
                                }
                            },
                            label = { Text("Owes") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(
                            checked = row.paid,
                            onCheckedChange = { rows[index] = rows[index].copy(paid = it) }
                        )
                        IconButton(onClick = { rows.removeAt(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove friend")
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { rows.add(SplitRow("", "0", false)) }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add friend")
                    }
                    OutlinedButton(onClick = { splitEqually() }) {
                        Text("Split equally")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Your share: ${currencyFormat.format(yourShare.coerceAtLeast(0.0))}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (yourShare < 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                )
                if (yourShare < 0) {
                    Text(
                        text = "Friends' shares exceed the total.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = yourShare >= 0,
                onClick = {
                    // Keep only named friends with a positive share.
                    val participants = rows.mapNotNull { r ->
                        val name = r.name.trim()
                        val share = r.share.toDoubleOrNull() ?: 0.0
                        if (name.isNotEmpty() && share > 0) {
                            SplitParticipant(name = name, share = share, paid = r.paid)
                        } else null
                    }
                    if (participants.isEmpty()) onClear() else onSave(participants)
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text("Remove split", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** Trim trailing ".0" so shares display cleanly in the editable field. */
private fun formatShare(value: Double): String {
    return if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.US, "%.2f", value)
}
