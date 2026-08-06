package com.expensetracker.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.expensetracker.data.model.SplitParticipant
import com.expensetracker.data.model.Transaction
import com.expensetracker.ui.theme.AppTheme
import java.text.NumberFormat
import java.util.Locale

private data class SplitRow(
    var name: String,
    var phone: String = "",
    var share: String,
    var paid: Boolean
)

@Composable
fun SplitActionSheet(
    onSplitWithFriends: () -> Unit,
    onAddToGroup: () -> Unit,
    onCreateGroup: () -> Unit,
    onDismiss: () -> Unit,
    hasGroups: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split this expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientPillButton(
                    text = "Split with friends",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onDismiss(); onSplitWithFriends() }
                )
                if (hasGroups) {
                    GradientPillButton(
                        text = "Add to existing group",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onDismiss(); onAddToGroup() }
                    )
                }
                GradientPillButton(
                    text = "Create new group",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onDismiss(); onCreateGroup() }
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun GroupPickerDialog(
    groups: List<com.expensetracker.data.model.ExpenseGroup>,
    onGroupSelected: (com.expensetracker.data.model.ExpenseGroup) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (groups.isEmpty()) {
                    Text(
                        "No active groups found.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                } else {
                    groups.forEach { group ->
                        GradientPillButton(
                            text = group.name,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onGroupSelected(group) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Split-with-friends dialog. Now includes a contact picker per friend row and
 * fires a pre-filled SMS/WhatsApp intent on save so the user can notify each person.
 */
@Composable
fun SplitDialog(
    transaction: Transaction,
    existing: List<SplitParticipant>,
    upiId: String = "",
    onDismiss: () -> Unit,
    onSave: (List<SplitParticipant>) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val glass = AppTheme.glass
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    val rows = remember {
        mutableStateListOf<SplitRow>().apply {
            if (existing.isNotEmpty()) {
                existing.forEach { add(SplitRow(it.name, "", formatShare(it.share), it.paid)) }
            } else {
                val half = transaction.amount / 2.0
                add(SplitRow("", "", formatShare(half), false))
            }
        }
    }

    // Track which row is waiting for a contact pick
    var pendingContactIndex = remember { -1 }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val idx = pendingContactIndex
        if (idx < 0 || idx >= rows.size) return@rememberLauncherForActivityResult
        // Read name + phone from the contact URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val contactId = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                )
                val name = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
                ) ?: ""
                // Fetch phone number
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null
                )?.use { phoneCursor ->
                    if (phoneCursor.moveToFirst()) {
                        val phone = phoneCursor.getString(
                            phoneCursor.getColumnIndexOrThrow(
                                ContactsContract.CommonDataKinds.Phone.NUMBER
                            )
                        ) ?: ""
                        rows[idx] = rows[idx].copy(name = name, phone = phone)
                    } else {
                        rows[idx] = rows[idx].copy(name = name)
                    }
                }
            }
        }
        pendingContactIndex = -1
    }

    fun friendTotal(): Double = rows.sumOf { it.share.toDoubleOrNull() ?: 0.0 }
    val yourShare = (transaction.amount - friendTotal())

    fun splitEqually() {
        val people = rows.size + 1
        val each = transaction.amount / people
        rows.indices.forEach { i -> rows[i] = rows[i].copy(share = formatShare(each)) }
    }

    fun sendMessage(row: SplitRow) {
        val amount = row.share.toDoubleOrNull() ?: return
        val upiLink = if (upiId.isNotBlank())
            " Pay here: upi://pay?pa=$upiId&am=${String.format("%.2f", amount)}&tn=${transaction.merchant}"
        else ""
        val message = "Hey ${row.name}, your share for ${transaction.merchant} is " +
            "₹${String.format("%.2f", amount)}.$upiLink"
        val phone = row.phone.filter { it.isDigit() }
        val intent = if (phone.isNotEmpty()) {
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                putExtra("sms_body", message)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
        }
        context.startActivity(Intent.createChooser(intent, "Notify ${row.name}"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glass.glassSolid,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(glass.accentGradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(if (existing.isEmpty()) "Split with friends" else "Edit split")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
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
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        cornerRadius = 16.dp,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = row.name,
                                    onValueChange = { rows[index] = rows[index].copy(name = it) },
                                    label = { Text("Friend") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.3f),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            pendingContactIndex = index
                                            contactPickerLauncher.launch(null)
                                        }) {
                                            Icon(Icons.Default.Contacts,
                                                contentDescription = "Pick contact",
                                                modifier = Modifier.size(18.dp))
                                        }
                                    }
                                )
                                Spacer(Modifier.width(6.dp))
                                OutlinedTextField(
                                    value = row.share,
                                    onValueChange = { new ->
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
                                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                                }
                            }
                            if (row.phone.isNotEmpty()) {
                                Text(
                                    row.phone,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { rows.add(SplitRow("", "", "0", false)) }) {
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
                        "Friends' shares exceed the total.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            GradientPillButton(
                text = "Save",
                enabled = yourShare >= 0,
                onClick = {
                    val participants = rows.mapNotNull { r ->
                        val name = r.name.trim()
                        val share = r.share.toDoubleOrNull() ?: 0.0
                        if (name.isNotEmpty() && share > 0)
                            SplitParticipant(name = name, share = share, paid = r.paid)
                        else null
                    }
                    if (participants.isEmpty()) onClear() else onSave(participants)
                    // Fire message intents for all friends with a name
                    rows.filter { it.name.isNotBlank() && (it.share.toDoubleOrNull() ?: 0.0) > 0 }
                        .forEach { sendMessage(it) }
                }
            )
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

private fun formatShare(value: Double): String {
    return if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.US, "%.2f", value)
}
