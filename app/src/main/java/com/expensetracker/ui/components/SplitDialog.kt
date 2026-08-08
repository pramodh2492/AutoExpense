package com.expensetracker.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.data.model.GroupExpense
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
                        style = MaterialTheme.typography.bodyMedium
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
 * Bottom sheet split UI.
 *
 * Top half — people cards: avatar, name, their share of THIS transaction,
 * history of group expenses at the same merchant they're part of, and net
 * amount to receive from them.
 *
 * Bottom half — editable rows to adjust amounts, add friends, split equally.
 *
 * [groupExpenses] — expenses from the group filtered to this merchant so we
 * can show per-person history. Pass empty list if no group context.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitDialog(
    transaction: Transaction,
    existing: List<SplitParticipant>,
    upiId: String = "",
    groupExpenses: List<GroupExpense> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (List<SplitParticipant>) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val glass = AppTheme.glass
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

    var pendingContactIndex by remember { mutableStateOf(-1) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val idx = pendingContactIndex
        if (idx < 0 || idx >= rows.size) return@rememberLauncherForActivityResult
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
    val yourShare = transaction.amount - friendTotal()

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = glass.glassSolid,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // ── Header ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = transaction.merchant,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Total ${currencyFormat.format(transaction.amount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                // Your share pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (yourShare < 0) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                            else Brush.horizontalGradient(glass.accentGradient)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Your share",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = currencyFormat.format(yourShare.coerceAtLeast(0.0)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            if (yourShare < 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Friends' shares exceed total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── People cards ──────────────────────────────────────────
            if (rows.any { it.name.isNotBlank() }) {
                GlassSectionHeader(title = "People")
                Spacer(Modifier.height(12.dp))

                rows.forEachIndexed { index, row ->
                    if (row.name.isBlank()) return@forEachIndexed
                    val share = row.share.toDoubleOrNull() ?: 0.0

                    // Group expense history for this person at this merchant
                    val history = groupExpenses.filter { expense ->
                        expense.description.equals(transaction.merchant, ignoreCase = true) &&
                            expense.splitAmong.any { it.displayName.equals(row.name, ignoreCase = true) }
                    }
                    val totalOwed = history.sumOf { expense ->
                        expense.splitAmong
                            .filter { it.displayName.equals(row.name, ignoreCase = true) && !it.settled }
                            .sumOf { it.share }
                    } + if (!row.paid) share else 0.0

                    PersonCard(
                        name = row.name,
                        currentShare = share,
                        paid = row.paid,
                        history = history,
                        totalToReceive = totalOwed,
                        currencyFormat = currencyFormat,
                        onNotify = { sendMessage(row) }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(8.dp))
                Divider(color = Color.White.copy(alpha = 0.1f))
                Spacer(Modifier.height(16.dp))
            }

            // ── Edit rows ─────────────────────────────────────────────
            GlassSectionHeader(title = if (existing.isEmpty()) "Split with friends" else "Edit split")
            Spacer(Modifier.height(12.dp))

            rows.forEachIndexed { index, row ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    cornerRadius = 16.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Avatar
                            PersonAvatar(name = row.name, size = 36)
                            OutlinedTextField(
                                value = row.name,
                                onValueChange = { rows[index] = rows[index].copy(name = it) },
                                label = { Text("Friend name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                pendingContactIndex = index
                                contactPickerLauncher.launch(null)
                            }) {
                                Icon(
                                    Icons.Default.Contacts,
                                    contentDescription = "Pick contact",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = row.share,
                                onValueChange = { new ->
                                    if (new.isEmpty() || new.matches(Regex("""\d*\.?\d*"""))) {
                                        rows[index] = rows[index].copy(share = new)
                                    }
                                },
                                label = { Text("Amount they owe") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            // Paid toggle
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (row.paid) Brush.horizontalGradient(glass.accentGradient)
                                        else Brush.horizontalGradient(
                                            listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.05f))
                                        )
                                    )
                                    .border(
                                        1.dp,
                                        if (row.paid) Color.Transparent else Color.White.copy(alpha = 0.2f),
                                        RoundedCornerShape(50)
                                    )
                                    .clickable { rows[index] = rows[index].copy(paid = !row.paid) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (row.paid) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        if (row.paid) "Paid" else "Mark paid",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                            IconButton(onClick = { rows.removeAt(index) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (row.phone.isNotEmpty()) {
                            Text(
                                row.phone,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.padding(start = 44.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassCard(
                    modifier = Modifier.weight(1f),
                    cornerRadius = 50.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    onClick = { rows.add(SplitRow("", "", "0", false)) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add friend", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }
                GlassCard(
                    modifier = Modifier.weight(1f),
                    cornerRadius = 50.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    onClick = { splitEqually() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Split equally", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Action buttons ────────────────────────────────────────
            GradientPillButton(
                text = "Save & Notify",
                modifier = Modifier.fillMaxWidth(),
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
                    rows.filter { it.name.isNotBlank() && (it.share.toDoubleOrNull() ?: 0.0) > 0 }
                        .forEach { sendMessage(it) }
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (existing.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text("Remove split", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

// ── Person card ───────────────────────────────────────────────────────────────

@Composable
private fun PersonCard(
    name: String,
    currentShare: Double,
    paid: Boolean,
    history: List<GroupExpense>,
    totalToReceive: Double,
    currencyFormat: NumberFormat,
    onNotify: () -> Unit
) {
    val glass = AppTheme.glass
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column {
            // Top row: avatar + name + net amount to receive
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PersonAvatar(name = name, size = 44)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "This split: ${currencyFormat.format(currentShare)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (totalToReceive > 0) "You receive" else "Settled",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                    Text(
                        text = if (totalToReceive > 0) currencyFormat.format(totalToReceive) else "—",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (totalToReceive > 0) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.4f)
                    )
                }
            }

            // History rows — group expenses at this merchant involving this person
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Divider(color = Color.White.copy(alpha = 0.08f))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Previous at this merchant",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                history.take(3).forEach { expense ->
                    val personSplit = expense.splitAmong
                        .firstOrNull { it.displayName.equals(name, ignoreCase = true) }
                    val shareAmt = personSplit?.share ?: 0.0
                    val isSettled = personSplit?.settled == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = expense.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = currencyFormat.format(shareAmt),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isSettled) Color.White.copy(alpha = 0.35f) else Color(0xFFFFB74D)
                            )
                            if (isSettled) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Settled",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
                if (history.size > 3) {
                    Text(
                        text = "+${history.size - 3} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        textAlign = TextAlign.End
                    )
                }
            }

            // Notify button
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                GlassCard(
                    cornerRadius = 50.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    onClick = onNotify
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Notify",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (paid) "Remind" else "Notify",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

// ── Avatar ────────────────────────────────────────────────────────────────────

@Composable
fun PersonAvatar(name: String, size: Int = 40) {
    val glass = AppTheme.glass
    val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(glass.accentGradient)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.42).sp,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatShare(value: Double): String {
    return if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.US, "%.2f", value)
}
