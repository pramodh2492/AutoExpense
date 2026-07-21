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
    val synced: Boolean = false
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
