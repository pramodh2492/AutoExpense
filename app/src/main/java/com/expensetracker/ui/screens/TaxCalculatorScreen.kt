package com.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.expensetracker.tax.AgeGroup
import com.expensetracker.tax.FinancialYear
import com.expensetracker.tax.TaxCalculationEngine
import com.expensetracker.tax.TaxCalculationResult
import com.expensetracker.tax.TaxCalculator
import com.expensetracker.tax.TaxCalculatorInput
import java.text.NumberFormat
import java.util.Locale

private val TABS = listOf("Basic details", "Income details", "Deduction")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxCalculatorScreen(
    taxCalculator: TaxCalculator
) {
    val currency = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    var input by remember { mutableStateOf(TaxCalculatorInput()) }
    var selectedTab by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<TaxCalculationResult?>(null) }
    var prefilled by remember { mutableStateOf(false) }

    // Prefill once from SMS data / saved input. Gate the form until this completes so
    // the field editors initialize from the populated values, not the empty defaults.
    LaunchedEffect(Unit) {
        input = taxCalculator.buildPrefilledInput()
        prefilled = true
    }

    if (!prefilled) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading…", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            TABS.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> BasicDetailsTab(
                    input = input,
                    onChange = { input = it },
                    onContinue = { selectedTab = 1 }
                )
                1 -> IncomeDetailsTab(
                    input = input,
                    onChange = { input = it },
                    currency = currency,
                    onBack = { selectedTab = 0 },
                    onContinue = { selectedTab = 2 }
                )
                2 -> DeductionTab(
                    input = input,
                    onChange = { input = it },
                    currency = currency,
                    onBack = { selectedTab = 1 },
                    onViewCalculation = {
                        taxCalculator.saveCalculatorInput(input)
                        result = TaxCalculationEngine.calculate(input)
                    }
                )
            }
        }
    }

    result?.let { res ->
        TaxResultDialog(
            result = res,
            currency = currency,
            onDismiss = { result = null }
        )
    }
}

// ---------------- Tab 1: Basic details ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasicDetailsTab(
    input: TaxCalculatorInput,
    onChange: (TaxCalculatorInput) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LabeledDropdown(
            label = "Financial year",
            selected = input.financialYear.label,
            options = FinancialYear.entries.map { it.label },
            onSelect = { label ->
                FinancialYear.entries.firstOrNull { it.label == label }?.let {
                    onChange(input.copy(financialYear = it))
                }
            }
        )

        LabeledDropdown(
            label = "Age group",
            selected = input.ageGroup.label,
            options = AgeGroup.entries.map { it.label },
            onSelect = { label ->
                AgeGroup.entries.firstOrNull { it.label == label }?.let {
                    onChange(input.copy(ageGroup = it))
                }
            }
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Income Tax Slab Rates",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
        )

        SlabTable(
            title = "New Regime Slab Rates",
            rows = listOf(
                "Up to 4 lakh" to "Nil",
                "4 lakh to 8 lakh" to "5%",
                "8 lakh to 12 lakh" to "10%",
                "12 lakh to 16 lakh" to "15%",
                "16 lakh to 20 lakh" to "20%",
                "20 lakh to 24 lakh" to "25%",
                "Above 24 lakh" to "30%"
            )
        )

        SlabTable(
            title = "Old Regime Slab Rates",
            rows = listOf(
                "Up to 2.5 lakh" to "Nil",
                "2.5 lakh - 5 lakh" to "5%",
                "5 lakh - 10 lakh" to "20%",
                "Above 10 lakh" to "30%"
            )
        )
        Text(
            "* Slab rates vary for resident senior and super senior citizens.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onContinue,
            modifier = Modifier.align(Alignment.End)
        ) { Text("Continue") }
    }
}

@Composable
private fun SlabTable(title: String, rows: List<Pair<String, String>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Income Tax Slabs", modifier = Modifier.weight(2f), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium)
                Text("Rate", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium)
            }
            rows.forEach { (slab, rate) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(slab, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium)
                    Text(rate, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ---------------- Tab 2: Income details ----------------

@Composable
private fun IncomeDetailsTab(
    input: TaxCalculatorInput,
    onChange: (TaxCalculatorInput) -> Unit,
    currency: NumberFormat,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    FormScaffold(
        primaryLabel = "Continue",
        onPrimary = onContinue,
        onBack = onBack
    ) {
        MoneyField("Income from Salary", input.salaryIncome) { onChange(input.copy(salaryIncome = it)) }
        MoneyField("Exempt allowances", input.exemptAllowances) { onChange(input.copy(exemptAllowances = it)) }
        MoneyField("Income from interest", input.interestIncome) { onChange(input.copy(interestIncome = it)) }
        MoneyField("Rental income received", input.rentalIncome) { onChange(input.copy(rentalIncome = it)) }
        MoneyField("Income from digital assets", input.digitalAssetIncome) { onChange(input.copy(digitalAssetIncome = it)) }
        MoneyField("Interest on home loan - Self occupied", input.homeLoanInterestSelfOccupied) { onChange(input.copy(homeLoanInterestSelfOccupied = it)) }
        MoneyField("Interest on Home Loan - Let Out", input.homeLoanInterestLetOut) { onChange(input.copy(homeLoanInterestLetOut = it)) }
        MoneyField("Other income", input.otherIncome) { onChange(input.copy(otherIncome = it)) }
    }
}

// ---------------- Tab 3: Deduction ----------------

@Composable
private fun DeductionTab(
    input: TaxCalculatorInput,
    onChange: (TaxCalculatorInput) -> Unit,
    currency: NumberFormat,
    onBack: () -> Unit,
    onViewCalculation: () -> Unit
) {
    FormScaffold(
        primaryLabel = "View Calculation",
        onPrimary = onViewCalculation,
        onBack = onBack
    ) {
        MoneyField("Basic deductions - 80C", input.deduction80C) { onChange(input.copy(deduction80C = it)) }
        MoneyField("Interest from deposits - 80TTA", input.deduction80TTA) { onChange(input.copy(deduction80TTA = it)) }
        MoneyField("Medical insurance - 80D", input.deduction80D) { onChange(input.copy(deduction80D = it)) }
        MoneyField("Donations to charity - 80G", input.deduction80G) { onChange(input.copy(deduction80G = it)) }
        MoneyField("Interest on housing loan - 80EEA", input.deduction80EEA) { onChange(input.copy(deduction80EEA = it)) }
        MoneyField("Employee's contribution to NPS - 80CCD", input.deduction80CCD1B) { onChange(input.copy(deduction80CCD1B = it)) }
        MoneyField("Employer's contribution to NPS - 80CCD(2)", input.deduction80CCD2Employer) { onChange(input.copy(deduction80CCD2Employer = it)) }
        MoneyField("Any other deduction", input.deductionOther) { onChange(input.copy(deductionOther = it)) }
    }
}

// ---------------- Shared building blocks ----------------

@Composable
private fun FormScaffold(
    primaryLabel: String,
    onPrimary: () -> Unit,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        content()
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onPrimary) { Text(primaryLabel) }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MoneyField(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit
) {
    // Keep raw text locally so the field doesn't fight the user while typing.
    var text by remember(label) { mutableStateOf(if (value > 0) value.toLong().toString() else "") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }
                text = digits
                onValueChange(digits.toDoubleOrNull() ?: 0.0)
            },
            leadingIcon = { Text("₹") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ---------------- Result dialog ----------------

@Composable
private fun TaxResultDialog(
    result: TaxCalculationResult,
    currency: NumberFormat,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        title = { Text("Tax Calculation") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RegimeRow("Old Regime", result.oldRegime.totalTax, result.recommendedRegime == "OLD", currency)
                RegimeRow("New Regime", result.newRegime.totalTax, result.recommendedRegime == "NEW", currency)

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "You save ${currency.format(result.savings)} with the " +
                            "${result.recommendedRegime.lowercase().replaceFirstChar { it.uppercase() }} Regime.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Text(
                    "⚠️ Estimate for planning only. Actual liability may differ (surcharge, marginal relief, HRA and capital-gains specifics are simplified). Consult a CA for filing.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun RegimeRow(
    label: String,
    tax: Double,
    recommended: Boolean,
    currency: NumberFormat
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (recommended) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary) {
                    Text(
                        "RECOMMENDED",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
        Text(
            currency.format(tax),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
