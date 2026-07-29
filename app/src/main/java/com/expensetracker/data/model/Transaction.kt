package com.expensetracker.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["smsHash"], unique = true)]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val merchant: String,
    val category: TransactionCategory,
    val type: TransactionType,
    val source: PaymentSource,
    val accountInfo: String,
    val rawSms: String,
    val smsHash: String = "",
    val timestamp: LocalDateTime,
    val isSelfTransfer: Boolean = false,
    val synced: Boolean = false,
    // Split-with-friends. `amount` always stays the TRUE total paid (needed for dedup
    // matching and richer-SMS merging); the split is stored alongside it, never by
    // mutating amount.
    // splitJson: serialized List<SplitParticipant> (your own share is implicit =
    //   amount - sum(friend shares) and is never "owed"). "" = not split.
    // reimbursedAmount: denormalized sum of friend shares already marked paid, so SQL
    //   can compute effective spend (amount - reimbursedAmount) without parsing JSON.
    val splitJson: String = "",
    val reimbursedAmount: Double = 0.0
) {
    /** What this transaction actually cost you right now: total minus money paid back. */
    val effectiveAmount: Double
        get() = (amount - reimbursedAmount).coerceAtLeast(0.0)

    val isSplit: Boolean
        get() = splitJson.isNotBlank()
}

/**
 * One friend on a split. `share` is what they owe you; `paid` flips when they settle up.
 * Your own share is not stored here — it's the remainder of the total.
 */
data class SplitParticipant(
    val name: String,
    val share: Double,
    val paid: Boolean = false
)

enum class TransactionCategory {
    FOOD_DINING,
    GROCERIES,
    TRANSPORT,
    SHOPPING,
    BILLS_UTILITIES,
    ENTERTAINMENT,
    HEALTH,
    EDUCATION,
    SALARY,
    SAVINGS,
    TRANSFER,
    ATM_WITHDRAWAL,
    EMI,
    SUBSCRIPTION,
    FUEL,
    TRAVEL,
    OTHER;

    val displayName: String
        get() = name.replace("_", " & ").lowercase()
            .replaceFirstChar { it.uppercase() }
}

enum class TransactionType {
    DEBIT,
    CREDIT
}

enum class PaymentSource {
    UPI,
    CREDIT_CARD,
    DEBIT_CARD,
    NET_BANKING,
    ECS_NACH,
    WALLET,
    UNKNOWN
}
