package com.expensetracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.expensetracker.data.model.MerchantCategoryMapping
import com.expensetracker.data.model.PaymentSource
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.model.TransactionCategory
import com.expensetracker.data.model.TransactionType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Database(
    entities = [Transaction::class, MerchantCategoryMapping::class],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun merchantCategoryDao(): MerchantCategoryDao
}

class Converters {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? = value?.format(formatter)

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? =
        value?.let { LocalDateTime.parse(it, formatter) }

    @TypeConverter
    fun fromCategory(value: TransactionCategory): String = value.name

    @TypeConverter
    fun toCategory(value: String): TransactionCategory = TransactionCategory.valueOf(value)

    @TypeConverter
    fun fromType(value: TransactionType): String = value.name

    @TypeConverter
    fun toType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromSource(value: PaymentSource): String = value.name

    @TypeConverter
    fun toSource(value: String): PaymentSource = PaymentSource.valueOf(value)
}
