package com.expensetracker.tax

import android.content.Context
import com.expensetracker.data.local.TransactionDao
import com.expensetracker.data.local.UserPreferences
import com.expensetracker.data.model.TransactionCategory
import com.expensetracker.data.model.TransactionType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.Month
import javax.inject.Inject
import javax.inject.Singleton

data class TaxInsights(
    val annualIncome: Double,
    val financialYear: String,
    val section80C: TaxSection,
    val section80D: TaxSection,
    val section80CCD: TaxSection,
    val section24B: TaxSection,
    val section80E: TaxSection,
    val manualDeductions: List<ManualDeduction>,
    val oldRegimeTax: Double,
    val newRegimeTax: Double,
    val recommendedRegime: String,
    val savingsPotential: Double,
    val tips: List<String>
)

data class TaxSection(
    val name: String,
    val limit: Double,
    val utilized: Double,
    val items: List<TaxItem>,
    val remaining: Double
)

data class TaxItem(
    val description: String,
    val amount: Double,
    val isAutoDetected: Boolean
)

data class ManualDeduction(
    val id: String,
    val section: String,
    val description: String,
    val amount: Double
)

@Singleton
class TaxCalculator @Inject constructor(
    private val transactionDao: TransactionDao,
    private val userPreferences: UserPreferences,
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val taxPrefs = context.getSharedPreferences("tax_prefs", Context.MODE_PRIVATE)

    suspend fun calculateTaxInsights(): TaxInsights {
        val now = LocalDateTime.now()
        val financialYear = getFinancialYear(now)
        val (fyStart, fyEnd) = getFYDates(now)

        val transactions = transactionDao.getTransactionsInRange(fyStart, fyEnd)

        // Calculate income: use transactions marked as SALARY category, or user-configured salary.
        // Detected salary only covers the FY so far, so annualize it by months elapsed —
        // otherwise (e.g. 3 months in) the income looks tiny and both regimes wrongly show ₹0.
        val salaryTransactions = transactions.filter { it.category == TransactionCategory.SALARY }
        val annualIncome = if (salaryTransactions.isNotEmpty()) {
            val detectedSoFar = salaryTransactions.sumOf { it.amount }
            val monthsElapsed = monthsElapsedInFY(fyStart, now)
            (detectedSoFar / monthsElapsed) * 12
        } else {
            userPreferences.monthlySalary * 12
        }

        // Auto-detect 80C items
        val keywords80C = listOf("lic", "elss", "ppf", "life insurance", "mutual fund sip")
        val items80C = transactions.filter { txn ->
            txn.type == TransactionType.DEBIT &&
                keywords80C.any { keyword ->
                    txn.merchant.lowercase().contains(keyword) ||
                        txn.rawSms.lowercase().contains(keyword)
                }
        }.map { TaxItem(it.merchant.ifEmpty { "LIC/ELSS/PPF" }, it.amount, true) }

        // Auto-detect 80D items
        val keywords80D = listOf("health insurance", "star health", "policy premium", "medical insurance")
        val items80D = transactions.filter { txn ->
            txn.type == TransactionType.DEBIT &&
                keywords80D.any { keyword ->
                    txn.merchant.lowercase().contains(keyword) ||
                        txn.rawSms.lowercase().contains(keyword)
                }
        }.map { TaxItem(it.merchant.ifEmpty { "Health Insurance" }, it.amount, true) }

        // Auto-detect 80CCD (NPS)
        val keywords80CCD = listOf("nps", "national pension")
        val items80CCD = transactions.filter { txn ->
            txn.type == TransactionType.DEBIT &&
                keywords80CCD.any { keyword ->
                    txn.merchant.lowercase().contains(keyword) ||
                        txn.rawSms.lowercase().contains(keyword)
                }
        }.map { TaxItem(it.merchant.ifEmpty { "NPS" }, it.amount, true) }

        // Auto-detect 24B (Home loan interest)
        val keywords24B = listOf("home loan", "housing", "hdfc home", "lic housing")
        val items24B = transactions.filter { txn ->
            txn.type == TransactionType.DEBIT &&
                keywords24B.any { keyword ->
                    txn.merchant.lowercase().contains(keyword) ||
                        txn.rawSms.lowercase().contains(keyword)
                }
        }.map { TaxItem(it.merchant.ifEmpty { "Home Loan EMI" }, it.amount, true) }

        // Auto-detect 80E (Education loan)
        val keywords80E = listOf("education loan", "student loan", "edu loan")
        val items80E = transactions.filter { txn ->
            txn.type == TransactionType.DEBIT &&
                keywords80E.any { keyword ->
                    txn.merchant.lowercase().contains(keyword) ||
                        txn.rawSms.lowercase().contains(keyword)
                }
        }.map { TaxItem(it.merchant.ifEmpty { "Education Loan" }, it.amount, true) }

        // Get manual deductions
        val manualDeductions = getManualDeductions()

        // Build sections with manual deductions included
        val manual80C = manualDeductions.filter { it.section == "80C" }
            .map { TaxItem(it.description, it.amount, false) }
        val manual80D = manualDeductions.filter { it.section == "80D" }
            .map { TaxItem(it.description, it.amount, false) }
        val manual80CCD = manualDeductions.filter { it.section == "80CCD" }
            .map { TaxItem(it.description, it.amount, false) }
        val manual24B = manualDeductions.filter { it.section == "24B" }
            .map { TaxItem(it.description, it.amount, false) }
        val manual80E = manualDeductions.filter { it.section == "80E" }
            .map { TaxItem(it.description, it.amount, false) }

        val allItems80C = items80C + manual80C
        val allItems80D = items80D + manual80D
        val allItems80CCD = items80CCD + manual80CCD
        val allItems24B = items24B + manual24B
        val allItems80E = items80E + manual80E

        val limit80C = 150000.0
        val limit80D = 25000.0 // 50000 if senior - default to 25000
        val limit80CCD = 50000.0
        val limit24B = 200000.0

        val utilized80C = allItems80C.sumOf { it.amount }.coerceAtMost(limit80C)
        val utilized80D = allItems80D.sumOf { it.amount }.coerceAtMost(limit80D)
        val utilized80CCD = allItems80CCD.sumOf { it.amount }.coerceAtMost(limit80CCD)
        val utilized24B = allItems24B.sumOf { it.amount }.coerceAtMost(limit24B)
        val utilized80E = allItems80E.sumOf { it.amount } // no limit

        val section80C = TaxSection("Section 80C", limit80C, utilized80C, allItems80C, limit80C - utilized80C)
        val section80D = TaxSection("Section 80D", limit80D, utilized80D, allItems80D, limit80D - utilized80D)
        val section80CCD = TaxSection("Section 80CCD(1B)", limit80CCD, utilized80CCD, allItems80CCD, limit80CCD - utilized80CCD)
        val section24B = TaxSection("Section 24(b)", limit24B, utilized24B, allItems24B, limit24B - utilized24B)
        val section80E = TaxSection("Section 80E", Double.MAX_VALUE, utilized80E, allItems80E, Double.MAX_VALUE)

        // Calculate taxes
        val totalDeductions = utilized80C + utilized80D + utilized80CCD + utilized24B + utilized80E
        val oldRegimeTax = calculateOldRegimeTax(annualIncome, totalDeductions)
        val newRegimeTax = calculateNewRegimeTax(annualIncome)

        val recommendedRegime = if (newRegimeTax <= oldRegimeTax) "NEW" else "OLD"

        // Calculate savings potential
        val maxPossibleDeductions = limit80C + limit80D + limit80CCD + limit24B
        val unusedDeductions = maxPossibleDeductions - (utilized80C + utilized80D + utilized80CCD + utilized24B)
        val savingsPotential = if (recommendedRegime == "OLD") {
            unusedDeductions * 0.3 // at highest slab
        } else {
            val potentialOldTax = calculateOldRegimeTax(annualIncome, maxPossibleDeductions)
            if (potentialOldTax < newRegimeTax) newRegimeTax - potentialOldTax else 0.0
        }

        // Generate tips
        val tips = generateTips(annualIncome, section80C, section80D, section80CCD, section24B, recommendedRegime)

        return TaxInsights(
            annualIncome = annualIncome,
            financialYear = financialYear,
            section80C = section80C,
            section80D = section80D,
            section80CCD = section80CCD,
            section24B = section24B,
            section80E = section80E,
            manualDeductions = manualDeductions,
            oldRegimeTax = oldRegimeTax,
            newRegimeTax = newRegimeTax,
            recommendedRegime = recommendedRegime,
            savingsPotential = savingsPotential,
            tips = tips
        )
    }

    private fun calculateNewRegimeTax(income: Double): Double {
        val standardDeduction = 75000.0
        val taxableIncome = (income - standardDeduction).coerceAtLeast(0.0)

        // Rebate: No tax if income <= 12L after standard deduction
        if (taxableIncome <= 1200000.0) return 0.0

        var tax = 0.0
        val slabs = listOf(
            400000.0 to 0.0,
            400000.0 to 0.05,
            400000.0 to 0.10,
            400000.0 to 0.15,
            400000.0 to 0.20,
            400000.0 to 0.25,
            Double.MAX_VALUE to 0.30
        )

        var remaining = taxableIncome
        for ((slabAmount, rate) in slabs) {
            val taxable = remaining.coerceAtMost(slabAmount)
            tax += taxable * rate
            remaining -= taxable
            if (remaining <= 0) break
        }

        // Add 4% cess
        tax *= 1.04
        return tax
    }

    private fun calculateOldRegimeTax(income: Double, deductions: Double): Double {
        val standardDeduction = 50000.0
        val taxableIncome = (income - standardDeduction - deductions).coerceAtLeast(0.0)

        // Rebate: No tax if taxable income <= 5L
        if (taxableIncome <= 500000.0) return 0.0

        var tax = 0.0
        val slabs = listOf(
            250000.0 to 0.0,
            250000.0 to 0.05,
            500000.0 to 0.20,
            Double.MAX_VALUE to 0.30
        )

        var remaining = taxableIncome
        for ((slabAmount, rate) in slabs) {
            val taxable = remaining.coerceAtMost(slabAmount)
            tax += taxable * rate
            remaining -= taxable
            if (remaining <= 0) break
        }

        // Add 4% cess
        tax *= 1.04
        return tax
    }

    private fun generateTips(
        income: Double,
        section80C: TaxSection,
        section80D: TaxSection,
        section80CCD: TaxSection,
        section24B: TaxSection,
        recommendedRegime: String
    ): List<String> {
        val tips = mutableListOf<String>()

        if (section80C.remaining > 0 && recommendedRegime == "OLD") {
            val saving = section80C.remaining * 0.3
            tips.add("Invest ₹${formatAmount(section80C.remaining)} more in ELSS/PPF to save ₹${formatAmount(saving)} in tax under Old Regime")
        }

        if (section80D.remaining > 0) {
            val saving = section80D.remaining * 0.3
            tips.add("Get health insurance cover to claim ₹${formatAmount(section80D.remaining)} deduction and save ₹${formatAmount(saving)} in tax")
        }

        if (section80CCD.utilized == 0.0) {
            val saving = 50000.0 * 0.3
            tips.add("NPS contribution of ₹50,000 can save ₹${formatAmount(saving)} additionally under Section 80CCD(1B)")
        }

        if (income > 1000000 && recommendedRegime == "NEW") {
            val totalDeductions = section80C.utilized + section80D.utilized + section80CCD.utilized + section24B.utilized
            if (totalDeductions < 375000) {
                tips.add("Consider maximizing deductions (80C + 80D + NPS) to make Old Regime beneficial")
            }
        }

        if (tips.isEmpty()) {
            tips.add("You're making good use of available tax deductions!")
        }

        return tips
    }

    // ---- Manual Tax Calculator (3-tab wizard) ----

    /** Persisted input for the manual tax calculator. Returns null if never saved. */
    fun getSavedCalculatorInput(): TaxCalculatorInput? {
        val json = taxPrefs.getString("calculator_input", null) ?: return null
        return try {
            gson.fromJson(json, TaxCalculatorInput::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveCalculatorInput(input: TaxCalculatorInput) {
        taxPrefs.edit().putString("calculator_input", gson.toJson(input)).apply()
    }

    /**
     * Build an input for the calculator, prefilled from what we can detect via SMS.
     * The user is always expected to review/edit these values before calculating.
     * If a saved input exists, that takes precedence (user's own edits win).
     */
    suspend fun buildPrefilledInput(): TaxCalculatorInput {
        getSavedCalculatorInput()?.let { return it }

        val insights = try {
            calculateTaxInsights()
        } catch (e: Exception) {
            null
        }

        return if (insights != null) {
            TaxCalculatorInput(
                salaryIncome = insights.annualIncome,
                deduction80C = insights.section80C.utilized,
                deduction80D = insights.section80D.utilized,
                deduction80CCD1B = insights.section80CCD.utilized,
                homeLoanInterestSelfOccupied = insights.section24B.utilized,
                deductionOther = insights.section80E.utilized
            )
        } else {
            TaxCalculatorInput(salaryIncome = userPreferences.monthlySalary * 12)
        }
    }

    fun getManualDeductions(): List<ManualDeduction> {
        val json = taxPrefs.getString("manual_deductions", null) ?: return emptyList()
        val type = object : TypeToken<List<ManualDeduction>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addManualDeduction(deduction: ManualDeduction) {
        val current = getManualDeductions().toMutableList()
        current.add(deduction)
        saveManualDeductions(current)
    }

    fun removeManualDeduction(id: String) {
        val current = getManualDeductions().toMutableList()
        current.removeAll { it.id == id }
        saveManualDeductions(current)
    }

    private fun saveManualDeductions(deductions: List<ManualDeduction>) {
        val json = gson.toJson(deductions)
        taxPrefs.edit().putString("manual_deductions", json).apply()
    }

    private fun getFinancialYear(now: LocalDateTime): String {
        return if (now.monthValue >= 4) {
            "${now.year}-${(now.year + 1) % 100}"
        } else {
            "${now.year - 1}-${now.year % 100}"
        }
    }

    // Number of months of the current financial year that have elapsed (inclusive of the
    // current month), clamped to 1..12 so we never divide by zero or over-annualize.
    private fun monthsElapsedInFY(fyStart: LocalDateTime, now: LocalDateTime): Int {
        val months = ((now.year - fyStart.year) * 12 + (now.monthValue - fyStart.monthValue)) + 1
        return months.coerceIn(1, 12)
    }

    private fun getFYDates(now: LocalDateTime): Pair<LocalDateTime, LocalDateTime> {
        val start: LocalDateTime
        val end: LocalDateTime
        if (now.monthValue >= 4) {
            start = LocalDateTime.of(now.year, Month.APRIL, 1, 0, 0)
            end = LocalDateTime.of(now.year + 1, Month.MARCH, 31, 23, 59, 59)
        } else {
            start = LocalDateTime.of(now.year - 1, Month.APRIL, 1, 0, 0)
            end = LocalDateTime.of(now.year, Month.MARCH, 31, 23, 59, 59)
        }
        return start to end
    }

    private fun formatAmount(amount: Double): String {
        return String.format("%,.0f", amount)
    }
}
