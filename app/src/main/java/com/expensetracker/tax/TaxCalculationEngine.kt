package com.expensetracker.tax

/**
 * Manual tax calculator engine. Takes explicit income + deduction inputs (from the
 * 3-tab Tax Calculator UI) and computes tax under both regimes for a given FY + age group.
 *
 * This is an ESTIMATOR — it intentionally simplifies several edge cases (marginal relief
 * on rebate, surcharge for very high incomes, HRA computation). A disclaimer is shown in UI.
 */

enum class AgeGroup(val label: String) {
    BELOW_60("0-60"),
    SENIOR_60_80("60-80"),
    SUPER_SENIOR_80_PLUS("80+")
}

enum class FinancialYear(val label: String) {
    FY_2025_26("FY 2025-2026 (Return to be filed between 1st April 2026 - 31st Dec 2026)"),
    FY_2026_27("FY 2026-2027 (Return to be filed between 1st April 2027 - 31st Dec 2027)")
}

data class TaxCalculatorInput(
    val financialYear: FinancialYear = FinancialYear.FY_2026_27,
    val ageGroup: AgeGroup = AgeGroup.BELOW_60,

    // Income details
    val salaryIncome: Double = 0.0,
    val exemptAllowances: Double = 0.0,
    val interestIncome: Double = 0.0,
    val rentalIncome: Double = 0.0,
    val digitalAssetIncome: Double = 0.0,       // VDA/crypto — flat 30%
    val homeLoanInterestSelfOccupied: Double = 0.0,
    val homeLoanInterestLetOut: Double = 0.0,
    val otherIncome: Double = 0.0,

    // Deductions
    val deduction80C: Double = 0.0,
    val deduction80D: Double = 0.0,
    val deduction80EEA: Double = 0.0,
    val deduction80CCD2Employer: Double = 0.0,  // allowed in BOTH regimes
    val deduction80TTA: Double = 0.0,
    val deduction80G: Double = 0.0,
    val deduction80CCD1B: Double = 0.0,         // employee NPS (additional ₹50k)
    val deductionOther: Double = 0.0
)

data class RegimeResult(
    val grossTotalIncome: Double,
    val totalDeductions: Double,
    val taxableIncome: Double,
    val incomeTax: Double,       // slab tax before cess, after rebate
    val digitalAssetTax: Double, // flat 30% on VDA
    val cess: Double,
    val totalTax: Double
)

data class TaxCalculationResult(
    val oldRegime: RegimeResult,
    val newRegime: RegimeResult,
    val recommendedRegime: String,   // "OLD" or "NEW"
    val savings: Double              // abs difference between the two
)

private data class Slab(val upTo: Double, val rate: Double)

object TaxCalculationEngine {

    private const val CESS_RATE = 0.04
    private const val STD_DEDUCTION_OLD = 50000.0
    private const val STD_DEDUCTION_NEW = 75000.0
    private const val VDA_RATE = 0.30
    private const val HOUSE_PROPERTY_LOSS_CAP = 200000.0

    // Section caps
    private const val CAP_80C = 150000.0
    private const val CAP_80EEA = 150000.0
    private const val CAP_80TTA = 10000.0
    private const val CAP_80CCD1B = 50000.0

    fun calculate(input: TaxCalculatorInput): TaxCalculationResult {
        val old = calculateOldRegime(input)
        val new = calculateNewRegime(input)
        val recommended = if (new.totalTax <= old.totalTax) "NEW" else "OLD"
        return TaxCalculationResult(
            oldRegime = old,
            newRegime = new,
            recommendedRegime = recommended,
            savings = kotlin.math.abs(old.totalTax - new.totalTax)
        )
    }

    private fun calculateOldRegime(input: TaxCalculatorInput): RegimeResult {
        // Salary net of exempt allowances (HRA/LTA etc. allowed in old regime)
        val netSalary = (input.salaryIncome - input.exemptAllowances).coerceAtLeast(0.0)

        // House property: let-out (30% standard deduction on rent) minus its interest,
        // minus self-occupied interest (capped ₹2L). Net loss set-off capped at ₹2L.
        val letOut = input.rentalIncome * 0.70 - input.homeLoanInterestLetOut
        val selfOccupied = -minOf(input.homeLoanInterestSelfOccupied, HOUSE_PROPERTY_LOSS_CAP)
        val housePropertyIncome = (letOut + selfOccupied).coerceAtLeast(-HOUSE_PROPERTY_LOSS_CAP)

        val gross = netSalary + input.interestIncome + input.otherIncome + housePropertyIncome

        val deductions = minOf(input.deduction80C, CAP_80C) +
            input.deduction80D +
            minOf(input.deduction80EEA, CAP_80EEA) +
            input.deduction80CCD2Employer +
            minOf(input.deduction80TTA, CAP_80TTA) +
            input.deduction80G +
            minOf(input.deduction80CCD1B, CAP_80CCD1B) +
            input.deductionOther

        val taxable = (gross - STD_DEDUCTION_OLD - deductions).coerceAtLeast(0.0)

        // Rebate 87A (old): nil tax if taxable income <= ₹5L
        val slabTax = if (taxable <= 500000.0) 0.0 else slabTax(taxable, oldSlabs(input.ageGroup))

        return buildResult(gross, deductions, taxable, slabTax, input.digitalAssetIncome)
    }

    private fun calculateNewRegime(input: TaxCalculatorInput): RegimeResult {
        // New regime: exempt allowances and self-occupied home-loan interest NOT allowed.
        val netSalary = input.salaryIncome.coerceAtLeast(0.0)

        // Let-out property allowed, but a resulting loss cannot be set off against other heads.
        val letOut = (input.rentalIncome * 0.70 - input.homeLoanInterestLetOut).coerceAtLeast(0.0)

        val gross = netSalary + input.interestIncome + input.otherIncome + letOut

        // Only employer NPS 80CCD(2) is allowed in the new regime.
        val deductions = input.deduction80CCD2Employer

        val taxable = (gross - STD_DEDUCTION_NEW - deductions).coerceAtLeast(0.0)

        // Rebate 87A (new, FY25-26 onwards): nil tax if taxable income <= ₹12L
        val slabTax = if (taxable <= 1200000.0) 0.0 else slabTax(taxable, newSlabs())

        return buildResult(gross, deductions, taxable, slabTax, input.digitalAssetIncome)
    }

    private fun buildResult(
        gross: Double,
        deductions: Double,
        taxable: Double,
        slabTax: Double,
        digitalAssetIncome: Double
    ): RegimeResult {
        val vdaTax = digitalAssetIncome * VDA_RATE
        val cess = (slabTax + vdaTax) * CESS_RATE
        return RegimeResult(
            grossTotalIncome = gross,
            totalDeductions = deductions,
            taxableIncome = taxable,
            incomeTax = slabTax,
            digitalAssetTax = vdaTax,
            cess = cess,
            totalTax = slabTax + vdaTax + cess
        )
    }

    private fun slabTax(income: Double, slabs: List<Slab>): Double {
        var tax = 0.0
        var lower = 0.0
        for (slab in slabs) {
            if (income <= lower) break
            val taxableInSlab = minOf(income, slab.upTo) - lower
            if (taxableInSlab > 0) tax += taxableInSlab * slab.rate
            lower = slab.upTo
        }
        return tax
    }

    // New regime slabs (FY 2025-26 & 2026-27) — no age-based variation.
    private fun newSlabs(): List<Slab> = listOf(
        Slab(400000.0, 0.0),
        Slab(800000.0, 0.05),
        Slab(1200000.0, 0.10),
        Slab(1600000.0, 0.15),
        Slab(2000000.0, 0.20),
        Slab(2400000.0, 0.25),
        Slab(Double.MAX_VALUE, 0.30)
    )

    // Old regime slabs vary by age group (basic exemption limit).
    private fun oldSlabs(age: AgeGroup): List<Slab> = when (age) {
        AgeGroup.BELOW_60 -> listOf(
            Slab(250000.0, 0.0),
            Slab(500000.0, 0.05),
            Slab(1000000.0, 0.20),
            Slab(Double.MAX_VALUE, 0.30)
        )
        AgeGroup.SENIOR_60_80 -> listOf(
            Slab(300000.0, 0.0),
            Slab(500000.0, 0.05),
            Slab(1000000.0, 0.20),
            Slab(Double.MAX_VALUE, 0.30)
        )
        AgeGroup.SUPER_SENIOR_80_PLUS -> listOf(
            Slab(500000.0, 0.0),
            Slab(1000000.0, 0.20),
            Slab(Double.MAX_VALUE, 0.30)
        )
    }
}
