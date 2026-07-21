package com.expensetracker.ai

import com.expensetracker.data.local.TransactionDao
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.model.TransactionCategory
import com.expensetracker.data.model.TransactionType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineQueryEngine @Inject constructor(
    private val dao: TransactionDao
) {

    suspend fun query(question: String): String {
        val q = question.lowercase().trim()
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val monthStart = today.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay()
        val lastMonthStart = today.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay()
        val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1).atStartOfDay()
        val todayStart = today.atStartOfDay()

        val allTxns = dao.getAllTransactions().first()
        val thisMonthTxns = allTxns.filter { it.timestamp.isAfter(monthStart) }
        val debits = thisMonthTxns.filter { it.type == TransactionType.DEBIT && !it.isSelfTransfer }

        return when {
            // Total spend queries
            q.containsAny("how much", "total", "spent") && q.containsAny("today") ->
                answerTotalSpend(allTxns, todayStart, now, "today")

            q.containsAny("how much", "total", "spent") && q.containsAny("week", "this week") ->
                answerTotalSpend(allTxns, weekStart, now, "this week")

            q.containsAny("how much", "total", "spent") && q.containsAny("last month", "previous month") ->
                answerTotalSpend(allTxns, lastMonthStart, monthStart, "last month")

            q.containsAny("how much", "total", "spent") && q.containsAny("month", "this month") ->
                answerTotalSpend(allTxns, monthStart, now, "this month")

            // Category queries: "how much on food", "food spending"
            q.containsAny("food", "restaurant", "swiggy", "zomato", "dining") ->
                answerCategory(debits, TransactionCategory.FOOD_DINING)

            q.containsAny("grocery", "groceries", "supermarket", "vegetables") ->
                answerCategory(debits, TransactionCategory.GROCERIES)

            q.containsAny("transport", "uber", "ola", "cab", "auto", "rapido") ->
                answerCategory(debits, TransactionCategory.TRANSPORT)

            q.containsAny("shopping", "amazon", "flipkart", "myntra") ->
                answerCategory(debits, TransactionCategory.SHOPPING)

            q.containsAny("bill", "utility", "electricity", "recharge", "wifi", "broadband") ->
                answerCategory(debits, TransactionCategory.BILLS_UTILITIES)

            q.containsAny("entertainment", "netflix", "movie", "spotify") ->
                answerCategory(debits, TransactionCategory.ENTERTAINMENT)

            q.containsAny("health", "medical", "pharmacy", "hospital", "doctor") ->
                answerCategory(debits, TransactionCategory.HEALTH)

            q.containsAny("fuel", "petrol", "diesel") ->
                answerCategory(debits, TransactionCategory.FUEL)

            q.containsAny("travel", "flight", "hotel", "trip") ->
                answerCategory(debits, TransactionCategory.TRAVEL)

            q.containsAny("emi", "loan") ->
                answerCategory(debits, TransactionCategory.EMI)

            q.containsAny("subscription", "subscribe") ->
                answerCategory(debits, TransactionCategory.SUBSCRIPTION)

            q.containsAny("saving", "invest", "mutual fund", "sip") ->
                answerCategory(debits, TransactionCategory.SAVINGS)

            // Biggest / highest
            q.containsAny("biggest", "highest", "largest", "maximum", "max", "most expensive") ->
                answerBiggest(debits)

            // Smallest
            q.containsAny("smallest", "lowest", "minimum", "cheapest") ->
                answerSmallest(debits)

            // Merchant queries: "how much at swiggy"
            q.containsAny("how much") && q.containsAny("at", "on", "to", "for") ->
                answerMerchant(debits, q)

            // Top merchants
            q.containsAny("top", "most") && q.containsAny("merchant", "shop", "place", "where") ->
                answerTopMerchants(debits)

            // Count
            q.containsAny("how many", "count", "number of") && q.containsAny("transaction", "payment", "expense") ->
                "You have ${debits.size} expenses this month totalling ₹${format(debits.sumOf { it.amount })}."

            // Daily average
            q.containsAny("daily", "average", "per day") ->
                answerDailyAvg(debits)

            // Compare months
            q.containsAny("compare", "vs", "versus", "more than last", "less than last") ->
                answerComparison(allTxns, monthStart, lastMonthStart, now)

            // Weekend
            q.containsAny("weekend") ->
                answerWeekend(debits)

            // Recurring
            q.containsAny("recurring", "fixed", "repeat", "subscription", "emi") && q.containsAny("cost", "total", "how much") ->
                answerRecurring(debits)

            // Generic advice
            q.containsAny("tip", "advice", "save", "reduce", "cut") ->
                answerSavingsTip(debits)

            // Fallback
            else -> "I can answer questions like:\n• \"How much did I spend this month?\"\n• \"Biggest expense?\"\n• \"How much on food?\"\n• \"Top merchants?\"\n• \"Daily average?\"\n• \"Compare with last month?\"\n• \"Tips to save money?\""
        }
    }

    private fun answerTotalSpend(txns: List<Transaction>, start: LocalDateTime, end: LocalDateTime, period: String): String {
        val debits = txns.filter {
            it.timestamp.isAfter(start) && it.timestamp.isBefore(end) &&
                    it.type == TransactionType.DEBIT && !it.isSelfTransfer &&
                    it.category != TransactionCategory.SAVINGS
        }
        val total = debits.sumOf { it.amount }
        val count = debits.size
        return "You spent ₹${format(total)} $period across $count transactions."
    }

    private fun answerCategory(debits: List<Transaction>, category: TransactionCategory): String {
        val filtered = debits.filter { it.category == category }
        if (filtered.isEmpty()) return "No ${category.displayName} expenses this month."
        val total = filtered.sumOf { it.amount }
        val count = filtered.size
        val topMerchant = filtered.groupBy { it.merchant }
            .maxByOrNull { (_, txns) -> txns.sumOf { it.amount } }
        val topInfo = topMerchant?.let { " Top: ${it.key} (₹${format(it.value.sumOf { t -> t.amount })})" } ?: ""
        return "${category.displayName}: ₹${format(total)} across $count transactions.$topInfo"
    }

    private fun answerBiggest(debits: List<Transaction>): String {
        val biggest = debits.maxByOrNull { it.amount } ?: return "No expenses found."
        return "Biggest expense: ₹${format(biggest.amount)} at ${biggest.merchant} on ${biggest.timestamp.format(DateTimeFormatter.ofPattern("dd MMM"))} (${biggest.category.displayName})"
    }

    private fun answerSmallest(debits: List<Transaction>): String {
        val smallest = debits.minByOrNull { it.amount } ?: return "No expenses found."
        return "Smallest expense: ₹${format(smallest.amount)} at ${smallest.merchant} on ${smallest.timestamp.format(DateTimeFormatter.ofPattern("dd MMM"))}"
    }

    private fun answerMerchant(debits: List<Transaction>, query: String): String {
        val merchants = debits.map { it.merchant.lowercase() }.distinct()
        val matchedMerchant = merchants.firstOrNull { query.contains(it) }
            ?: merchants.firstOrNull { m -> query.split(" ").any { m.contains(it) && it.length > 3 } }
            ?: return "Couldn't find that merchant. Try asking about a specific category instead."

        val txns = debits.filter { it.merchant.lowercase() == matchedMerchant }
        val total = txns.sumOf { it.amount }
        return "You spent ₹${format(total)} at ${txns.first().merchant} this month (${txns.size} times)."
    }

    private fun answerTopMerchants(debits: List<Transaction>): String {
        val top5 = debits.filter { it.merchant != "Unknown" }
            .groupBy { it.merchant }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
            .take(5)

        if (top5.isEmpty()) return "No merchant data available."
        return "Top merchants this month:\n" + top5.mapIndexed { i, (name, amount) ->
            "${i + 1}. $name — ₹${format(amount)}"
        }.joinToString("\n")
    }

    private fun answerDailyAvg(debits: List<Transaction>): String {
        val days = debits.map { it.timestamp.toLocalDate() }.distinct().size
        if (days == 0) return "No spending data yet."
        val avg = debits.sumOf { it.amount } / days
        return "Your daily average spend is ₹${format(avg)} (based on $days days this month)."
    }

    private fun answerComparison(txns: List<Transaction>, monthStart: LocalDateTime, lastMonthStart: LocalDateTime, now: LocalDateTime): String {
        val thisMonth = txns.filter { it.timestamp.isAfter(monthStart) && it.type == TransactionType.DEBIT && !it.isSelfTransfer }.sumOf { it.amount }
        val lastMonth = txns.filter { it.timestamp.isAfter(lastMonthStart) && it.timestamp.isBefore(monthStart) && it.type == TransactionType.DEBIT && !it.isSelfTransfer }.sumOf { it.amount }

        if (lastMonth == 0.0) return "No data from last month to compare."
        val diff = thisMonth - lastMonth
        val percent = ((diff / lastMonth) * 100).toInt()
        return if (diff > 0) {
            "You're spending ₹${format(diff)} more than last month ($percent% increase). This month: ₹${format(thisMonth)}, Last month: ₹${format(lastMonth)}."
        } else {
            "You're spending ₹${format(-diff)} less than last month (${-percent}% decrease). This month: ₹${format(thisMonth)}, Last month: ₹${format(lastMonth)}."
        }
    }

    private fun answerWeekend(debits: List<Transaction>): String {
        val weekend = debits.filter { it.timestamp.dayOfWeek.value >= 6 }
        val weekday = debits.filter { it.timestamp.dayOfWeek.value < 6 }
        val weekendTotal = weekend.sumOf { it.amount }
        val weekdayTotal = weekday.sumOf { it.amount }
        return "Weekend spending: ₹${format(weekendTotal)} (${weekend.size} txns)\nWeekday spending: ₹${format(weekdayTotal)} (${weekday.size} txns)"
    }

    private fun answerRecurring(debits: List<Transaction>): String {
        val recurring = debits.filter { it.category == TransactionCategory.SUBSCRIPTION || it.category == TransactionCategory.EMI }
        if (recurring.isEmpty()) return "No recurring/fixed costs detected this month."
        val total = recurring.sumOf { it.amount }
        val breakdown = recurring.groupBy { it.merchant }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
            .take(5)
            .joinToString("\n") { "• ${it.key}: ₹${format(it.value)}" }
        return "Fixed costs this month: ₹${format(total)}\n$breakdown"
    }

    private fun answerSavingsTip(debits: List<Transaction>): String {
        val categoryTotals = debits.groupBy { it.category }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }

        val top = categoryTotals.firstOrNull() ?: return "Not enough data for tips yet."
        val topMerchant = debits.filter { it.category == top.key }
            .groupBy { it.merchant }
            .maxByOrNull { (_, txns) -> txns.sumOf { it.amount } }

        val tips = mutableListOf<String>()
        tips.add("Your biggest spending category is ${top.key.displayName} at ₹${format(top.value)}.")

        if (top.key == TransactionCategory.FOOD_DINING && topMerchant != null) {
            tips.add("You spent ₹${format(topMerchant.value.sumOf { it.amount })} at ${topMerchant.key}. Try cooking at home 2-3 times a week to save ~30%.")
        }
        if (top.key == TransactionCategory.SHOPPING) {
            tips.add("Try the 24-hour rule: wait a day before buying anything over ₹1,000.")
        }

        val subscriptions = debits.filter { it.category == TransactionCategory.SUBSCRIPTION }.sumOf { it.amount }
        if (subscriptions > 500) {
            tips.add("You have ₹${format(subscriptions)} in subscriptions. Review if you use all of them.")
        }

        return tips.joinToString("\n")
    }

    private fun format(amount: Double): String = String.format("%,.0f", amount)

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }
}
