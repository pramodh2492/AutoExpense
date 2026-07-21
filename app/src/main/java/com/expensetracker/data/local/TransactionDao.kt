package com.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.model.TransactionCategory
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<Transaction>)

    @Update
    suspend fun update(transaction: Transaction)

    @Query("UPDATE transactions SET category = :category WHERE LOWER(merchant) = LOWER(:merchant)")
    suspend fun updateCategoryForMerchant(merchant: String, category: TransactionCategory)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE rawSms NOT LIKE 'Manual%' ORDER BY timestamp DESC")
    suspend fun getAllSmsTransactions(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun getTransactionsBetween(start: LocalDateTime, end: LocalDateTime): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE category = :category ORDER BY timestamp DESC")
    fun getByCategory(category: TransactionCategory): Flow<List<Transaction>>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'DEBIT' AND isSelfTransfer = 0 AND category != 'SAVINGS' AND timestamp BETWEEN :start AND :end")
    fun getTotalSpent(start: LocalDateTime, end: LocalDateTime): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'CREDIT' AND timestamp BETWEEN :start AND :end")
    fun getTotalIncome(start: LocalDateTime, end: LocalDateTime): Flow<Double?>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM transactions
        WHERE type = 'DEBIT' AND isSelfTransfer = 0 AND timestamp BETWEEN :start AND :end
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getCategoryBreakdown(start: LocalDateTime, end: LocalDateTime): Flow<List<CategoryTotal>>

    @Query("""
        SELECT merchant, SUM(amount) as totalSpent, COUNT(*) as transactionCount
        FROM transactions
        WHERE type = 'DEBIT' AND isSelfTransfer = 0 AND timestamp BETWEEN :start AND :end
        GROUP BY merchant
        ORDER BY totalSpent DESC
        LIMIT :limit
    """)
    fun getTopMerchants(start: LocalDateTime, end: LocalDateTime, limit: Int = 10): Flow<List<MerchantTotal>>

    @Query("SELECT * FROM transactions WHERE synced = 0")
    suspend fun getUnsyncedTransactions(): List<Transaction>

    @Query("UPDATE transactions SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM transactions")
    fun getTransactionCount(): Flow<Int>

    /**
     * Check if a potential duplicate exists: same amount + same account within time window.
     */
    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE amount = :amount
        AND accountInfo = :accountInfo
        AND timestamp BETWEEN :windowStart AND :windowEnd
    """)
    suspend fun countDuplicatesSameAccount(
        amount: Double,
        accountInfo: String,
        windowStart: LocalDateTime,
        windowEnd: LocalDateTime
    ): Int

    /**
     * Tighter check: same amount within a narrow time window (for cases where account might differ).
     */
    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE amount = :amount
        AND timestamp BETWEEN :windowStart AND :windowEnd
    """)
    suspend fun countDuplicatesNarrow(
        amount: Double,
        windowStart: LocalDateTime,
        windowEnd: LocalDateTime
    ): Int
}

data class CategoryTotal(
    val category: TransactionCategory,
    val total: Double
)

data class MerchantTotal(
    val merchant: String,
    val totalSpent: Double,
    val transactionCount: Int
)
