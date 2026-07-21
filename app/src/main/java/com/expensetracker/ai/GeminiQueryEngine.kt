package com.expensetracker.ai

import com.expensetracker.data.local.TransactionDao
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.model.TransactionType
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiQueryEngine @Inject constructor(
    private val dao: TransactionDao
) {
    private var apiKey: String = ""

    fun setApiKey(key: String) {
        apiKey = key
    }

    suspend fun query(userQuestion: String): String {
        if (apiKey.isBlank()) {
            return "Please set your Gemini API key in Settings to use AI queries."
        }

        val context = buildFinancialContext()
        val prompt = buildPrompt(userQuestion, context)

        return try {
            callGemini(prompt)
        } catch (e: Exception) {
            "Sorry, couldn't process that: ${e.message}"
        }
    }

    private suspend fun buildFinancialContext(): String {
        val now = LocalDateTime.now()
        val monthStart = now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()
        val lastMonthStart = now.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay()

        val thisMonthTxns = dao.getTransactionsBetween(monthStart, now).first()
        val lastMonthTxns = dao.getTransactionsBetween(lastMonthStart, monthStart).first()

        val thisMonthSpent = thisMonthTxns.filter { it.type == TransactionType.DEBIT && !it.isSelfTransfer }.sumOf { it.amount }
        val lastMonthSpent = lastMonthTxns.filter { it.type == TransactionType.DEBIT && !it.isSelfTransfer }.sumOf { it.amount }

        val categoryBreakdown = thisMonthTxns
            .filter { it.type == TransactionType.DEBIT && !it.isSelfTransfer }
            .groupBy { it.category }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
            .joinToString("\n") { "  ${it.key.displayName}: ₹${String.format("%,.0f", it.value)}" }

        val topMerchants = thisMonthTxns
            .filter { it.type == TransactionType.DEBIT && it.merchant != "Unknown" }
            .groupBy { it.merchant }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
            .take(10)
            .joinToString("\n") { "  ${it.key}: ₹${String.format("%,.0f", it.value)} (${thisMonthTxns.count { t -> t.merchant == it.key }} times)" }

        val recentTxns = thisMonthTxns
            .filter { it.type == TransactionType.DEBIT }
            .take(20)
            .joinToString("\n") { "  ${it.timestamp.toLocalDate()} | ${it.merchant} | ₹${String.format("%,.0f", it.amount)} | ${it.category.displayName}" }

        return """
User's Financial Data (Indian Rupees ₹):

THIS MONTH (${now.month} ${now.year}):
- Total spent: ₹${String.format("%,.0f", thisMonthSpent)}
- Day ${now.dayOfMonth} of ${now.toLocalDate().lengthOfMonth()}

LAST MONTH:
- Total spent: ₹${String.format("%,.0f", lastMonthSpent)}

CATEGORY BREAKDOWN (this month):
$categoryBreakdown

TOP MERCHANTS (this month):
$topMerchants

RECENT TRANSACTIONS:
$recentTxns
        """.trimIndent()
    }

    private fun buildPrompt(question: String, context: String): String {
        return """
You are a helpful personal finance assistant for an Indian user. Answer their question based on their actual spending data below.

Rules:
- Always use ₹ (Indian Rupees)
- Be concise (2-3 sentences max)
- Give specific numbers from the data
- If asked for advice, be practical and specific to their spending patterns
- Don't make up data — only reference what's provided

$context

User's question: $question
        """.trimIndent()
    }

    private fun callGemini(prompt: String): String {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 15000
        connection.readTimeout = 15000

        val body = JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
        }

        OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }

        val response = connection.inputStream.bufferedReader().readText()
        val json = JSONObject(response)

        return json
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }
}
