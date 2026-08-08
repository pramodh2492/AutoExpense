package com.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expensetracker.data.model.PaymentSource
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.model.TransactionCategory
import com.expensetracker.data.model.TransactionType
import com.expensetracker.ui.theme.AppTheme
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TransactionItem(
    transaction: Transaction,
    onCategoryChange: ((TransactionCategory) -> Unit)? = null,
    onToggleSelfTransfer: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onSplit: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val categoryStyle = getCategoryStyle(transaction.category)

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(12.dp),
        onClick = { showMenu = true }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                // weight(1f) lets the merchant block yield space to the amount so a long
                // name ellipsizes rather than crowding the figure on the right.
                modifier = Modifier.weight(1f)
            ) {
                // Category colored icon box
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (transaction.isSelfTransfer) Color.Gray.copy(alpha = 0.2f)
                            else categoryStyle.color.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (transaction.isSelfTransfer) Icons.Default.SwapHoriz
                            else categoryStyle.icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (transaction.isSelfTransfer) Color.Gray
                            else categoryStyle.color
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = transaction.merchant,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                            textDecoration = if (transaction.isSelfTransfer) TextDecoration.LineThrough else TextDecoration.None
                        )
                        if (transaction.isSelfTransfer) {
                            Text(
                                text = " (Self)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                    Text(
                        text = "${transaction.category.displayName} • ${transaction.timestamp.format(
                            DateTimeFormatter.ofPattern("hh:mm a")
                        )}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Split icon — visible only on debits when the split callback is wired in.
            // Filled+colored when already split, outlined when not.
            if (onSplit != null && transaction.type == TransactionType.DEBIT) {
                val glass = AppTheme.glass
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (transaction.isSplit)
                                Brush.linearGradient(glass.accentGradient)
                            else
                                Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.08f),
                                        Color.White.copy(alpha = 0.08f)
                                    )
                                )
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSplit() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = if (transaction.isSplit) "Edit split" else "Split with friends",
                        tint = if (transaction.isSplit) Color.White
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Give the amount column a little breathing room from the merchant block so a
            // wide figure can never collide with the name on its left.
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                // When split, the bold figure is YOUR share (effective amount); the full
                // total is shown struck-through above it so nothing looks lost.
                val shownAmount = if (transaction.isSplit) transaction.effectiveAmount else transaction.amount
                if (transaction.isSplit) {
                    Text(
                        text = currencyFormat.format(transaction.amount),
                        style = MaterialTheme.typography.labelSmall,
                        textDecoration = TextDecoration.LineThrough,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${if (transaction.type == TransactionType.DEBIT) "-" else "+"}${currencyFormat.format(shownAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    color = when {
                        transaction.isSelfTransfer -> Color.Gray
                        transaction.type == TransactionType.DEBIT -> MaterialTheme.colorScheme.error
                        else -> Color(0xFF4CAF50)
                    }
                )
                Text(
                    text = if (transaction.isSplit) "Split" else getSourceLabel(transaction.source),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    color = if (transaction.isSplit) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            if (onToggleSelfTransfer != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (transaction.isSelfTransfer) "Unmark self-transfer"
                            else "Mark as self-transfer"
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                    onClick = {
                        onToggleSelfTransfer()
                        showMenu = false
                    }
                )
            }
            if (onRename != null) {
                DropdownMenuItem(
                    text = { Text("Rename merchant") },
                    onClick = {
                        onRename()
                        showMenu = false
                    }
                )
            }
            if (onSplit != null) {
                DropdownMenuItem(
                    text = { Text(if (transaction.isSplit) "Edit split" else "Split with friends") },
                    leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null) },
                    onClick = {
                        onSplit()
                        showMenu = false
                    }
                )
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        onDelete()
                        showMenu = false
                    }
                )
            }
            if (onCategoryChange != null) {
                DropdownMenuItem(
                    text = { Text("Change category") },
                    onClick = { }
                )
                TransactionCategory.entries.forEach { category ->
                    val style = getCategoryStyle(category)
                    DropdownMenuItem(
                        text = { Text(category.displayName) },
                        leadingIcon = {
                            Icon(
                                style.icon,
                                contentDescription = null,
                                tint = style.color,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            onCategoryChange(category)
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}

data class CategoryStyle(val icon: ImageVector, val color: Color)

fun getCategoryStyle(category: TransactionCategory): CategoryStyle {
    return when (category) {
        TransactionCategory.FOOD_DINING -> CategoryStyle(Icons.Default.Fastfood, Color(0xFFFF5252))
        TransactionCategory.GROCERIES -> CategoryStyle(Icons.Default.LocalGroceryStore, Color(0xFF4CAF50))
        TransactionCategory.TRANSPORT -> CategoryStyle(Icons.Default.DirectionsCar, Color(0xFF2196F3))
        TransactionCategory.SHOPPING -> CategoryStyle(Icons.Default.ShoppingBag, Color(0xFFFF9800))
        TransactionCategory.BILLS_UTILITIES -> CategoryStyle(Icons.Default.Receipt, Color(0xFF9C27B0))
        TransactionCategory.ENTERTAINMENT -> CategoryStyle(Icons.Default.Movie, Color(0xFFE91E63))
        TransactionCategory.HEALTH -> CategoryStyle(Icons.Default.FitnessCenter, Color(0xFF00BCD4))
        TransactionCategory.EDUCATION -> CategoryStyle(Icons.Default.School, Color(0xFFFFC107))
        TransactionCategory.SALARY -> CategoryStyle(Icons.Default.AccountBalance, Color(0xFF2E7D32))
        TransactionCategory.SAVINGS -> CategoryStyle(Icons.Default.AccountBalanceWallet, Color(0xFF26A69A))
        TransactionCategory.TRANSFER -> CategoryStyle(Icons.Default.SwapHoriz, Color(0xFF8BC34A))
        TransactionCategory.ATM_WITHDRAWAL -> CategoryStyle(Icons.Default.AccountBalance, Color(0xFF607D8B))
        TransactionCategory.EMI -> CategoryStyle(Icons.Default.CreditCard, Color(0xFFFF5722))
        TransactionCategory.SUBSCRIPTION -> CategoryStyle(Icons.Default.Repeat, Color(0xFF673AB7))
        TransactionCategory.FUEL -> CategoryStyle(Icons.Default.LocalGasStation, Color(0xFF009688))
        TransactionCategory.TRAVEL -> CategoryStyle(Icons.Default.Flight, Color(0xFF3F51B5))
        TransactionCategory.OTHER -> CategoryStyle(Icons.Default.Payment, Color(0xFF78909C))
    }
}

private fun getSourceLabel(source: PaymentSource): String {
    return when (source) {
        PaymentSource.UPI -> "UPI"
        PaymentSource.CREDIT_CARD -> "Credit Card"
        PaymentSource.DEBIT_CARD -> "Debit Card"
        PaymentSource.NET_BANKING -> "Net Banking"
        PaymentSource.ECS_NACH -> "ECS/NACH"
        PaymentSource.WALLET -> "Wallet"
        PaymentSource.UNKNOWN -> ""
    }
}
