package com.expensetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merchant_category_mappings")
data class MerchantCategoryMapping(
    @PrimaryKey val merchant: String,
    val category: TransactionCategory
)
