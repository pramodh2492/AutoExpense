package com.expensetracker.ai

import android.content.Context
import com.expensetracker.data.local.TransactionDao
import com.expensetracker.data.model.TransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smart query engine that uses Gemini Nano (on-device AI) when available,
 * and falls back to offline pattern matching on unsupported devices.
 */
@Singleton
class SmartQueryEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TransactionDao,
    private val offlineQueryEngine: OfflineQueryEngine
) {
    private var geminiNanoAvailable: Boolean? = null
    private var generativeModel: Any? = null

    suspend fun query(question: String): String {
        // Try Gemini Nano first (on supported devices)
        if (isGeminiNanoAvailable()) {
            val result = queryWithGeminiNano(question)
            if (result != null) return result
        }

        // Fallback to offline pattern matching
        return offlineQueryEngine.query(question)
    }

    fun isGeminiNanoAvailable(): Boolean {
        if (geminiNanoAvailable != null) return geminiNanoAvailable!!

        geminiNanoAvailable = try {
            val clazz = Class.forName("com.google.ai.edge.aicore.GenerativeModel")
            clazz != null
        } catch (e: ClassNotFoundException) {
            false
        }

        return geminiNanoAvailable!!
    }

    private suspend fun queryWithGeminiNano(question: String): String? {
        return try {
            val context = buildFinancialContext()
            val prompt = """
You are a personal finance assistant. Answer based on this data:
$context

Question: $question
Answer concisely in 1-2 sentences with specific numbers.
            """.trimIndent()

            // Use reflection to avoid compile-time dependency issues on unsupported devices
            val modelClass = Class.forName("com.google.ai.edge.aicore.GenerativeModel")
            val configClass = Class.forName("com.google.ai.edge.aicore.GenerationConfig")

            val configBuilder = configClass.getDeclaredMethod("getDefaultInstance").invoke(null)
            val model = generativeModel ?: modelClass
                .getDeclaredConstructor(configClass)
                .newInstance(configBuilder)
                .also { generativeModel = it }

            val generateMethod = modelClass.getDeclaredMethod("generateContent", String::class.java)
            val response = generateMethod.invoke(model, prompt)
            val getText = response?.javaClass?.getDeclaredMethod("getText")
            getText?.invoke(response) as? String
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun buildFinancialContext(): String {
        val now = LocalDateTime.now()
        val monthStart = now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()

        val txns = dao.getTransactionsBetween(monthStart, now).first()
        val debits = txns.filter { it.type == TransactionType.DEBIT && !it.isSelfTransfer }
        val total = debits.sumOf { it.amount }

        val categories = debits.groupBy { it.category }
            .mapValues { (_, t) -> t.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
            .take(5)
            .joinToString(", ") { "${it.key.displayName}: ₹${String.format("%,.0f", it.value)}" }

        val topMerchants = debits.filter { it.merchant != "Unknown" }
            .groupBy { it.merchant }
            .mapValues { (_, t) -> t.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
            .take(5)
            .joinToString(", ") { "${it.key}: ₹${String.format("%,.0f", it.value)}" }

        return "Month: ${now.month}. Total spent: ₹${String.format("%,.0f", total)}. Categories: $categories. Top merchants: $topMerchants."
    }
}
