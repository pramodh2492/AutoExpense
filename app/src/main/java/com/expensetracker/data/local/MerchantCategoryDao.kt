package com.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.expensetracker.data.model.MerchantCategoryMapping
import com.expensetracker.data.model.TransactionCategory

@Dao
interface MerchantCategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(mapping: MerchantCategoryMapping)

    @Query("SELECT category FROM merchant_category_mappings WHERE merchant = :merchant LIMIT 1")
    suspend fun getCategoryForMerchant(merchant: String): TransactionCategory?

    @Query("SELECT * FROM merchant_category_mappings")
    suspend fun getAll(): List<MerchantCategoryMapping>
}
