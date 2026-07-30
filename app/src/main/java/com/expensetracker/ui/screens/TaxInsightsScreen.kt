package com.expensetracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.expensetracker.ui.components.GlassCard
import com.expensetracker.ui.components.GlassSectionHeader
import com.expensetracker.tax.ManualDeduction
import com.expensetracker.tax.TaxCalculator
import com.expensetracker.tax.TaxInsights
import com.expensetracker.tax.TaxItem
import com.expensetracker.tax.TaxSection
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxInsightsScreen(
    taxCalculator: TaxCalculator,
    userPreferences: com.expensetracker.data.local.UserPreferences? = null
) {
    val scope = rememberCoroutineScope()
    var taxInsights by remember { mutableStateOf<TaxInsights?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showSalaryInput by remember { mutableStateOf(false) }
    var salaryText by remember { mutableStateOf("") }
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    LaunchedEffect(Unit) {
        // If salary not configured, show input prompt
        if (userPreferences != null && !userPreferences.hasSalaryConfigured) {
            showSalaryInput = true
            isLoading = false
        } else {
            taxInsights = taxCalculator.calculateTaxInsights()
            isLoading = false
        }
    }

    // Salary input dialog
    if (showSalaryInput) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { },
            title = { Text("What's your monthly salary?") },
            text = {
                Column {
                    Text(
                        "Enter your monthly take-home salary (in-hand). This is used for tax estimation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = salaryText,
                        onValueChange = { salaryText = it.filter { c -> c.isDigit() } },
                        label = { Text("Monthly salary (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val salary = salaryText.toDoubleOrNull() ?: 0.0
                    if (salary > 0 && userPreferences != null) {
                        userPreferences.monthlySalary = salary
                        userPreferences.hasSalaryConfigured = true
                        showSalaryInput = false
                        scope.launch {
                            taxInsights = taxCalculator.calculateTaxInsights()
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSalaryInput = false
                }) { Text("Skip") }
            }
        )
        if (taxInsights == null) return
    }

    if (isLoading) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Calculating tax insights...", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val insights = taxInsights ?: return

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tax Insights",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "FY ${insights.financialYear}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Annual Income card
        item {
            AnnualIncomeCard(
                income = insights.annualIncome,
                currencyFormat = currencyFormat,
                onIncomeUpdated = { newAnnualIncome ->
                    if (userPreferences != null) {
                        userPreferences.monthlySalary = newAnnualIncome / 12.0
                        userPreferences.hasSalaryConfigured = true
                    }
                    scope.launch {
                        taxInsights = taxCalculator.calculateTaxInsights()
                    }
                }
            )
        }

        // Tax sections
        item {
            TaxSectionCard(
                section = insights.section80C,
                sectionKey = "80C",
                taxCalculator = taxCalculator,
                currencyFormat = currencyFormat,
                onDeductionChanged = {
                    scope.launch { taxInsights = taxCalculator.calculateTaxInsights() }
                }
            )
        }

        item {
            TaxSectionCard(
                section = insights.section80D,
                sectionKey = "80D",
                taxCalculator = taxCalculator,
                currencyFormat = currencyFormat,
                onDeductionChanged = {
                    scope.launch { taxInsights = taxCalculator.calculateTaxInsights() }
                }
            )
        }

        item {
            TaxSectionCard(
                section = insights.section80CCD,
                sectionKey = "80CCD",
                taxCalculator = taxCalculator,
                currencyFormat = currencyFormat,
                onDeductionChanged = {
                    scope.launch { taxInsights = taxCalculator.calculateTaxInsights() }
                }
            )
        }

        item {
            TaxSectionCard(
                section = insights.section24B,
                sectionKey = "24B",
                taxCalculator = taxCalculator,
                currencyFormat = currencyFormat,
                onDeductionChanged = {
                    scope.launch { taxInsights = taxCalculator.calculateTaxInsights() }
                }
            )
        }

        if (insights.section80E.items.isNotEmpty()) {
            item {
                TaxSectionCard(
                    section = insights.section80E,
                    sectionKey = "80E",
                    taxCalculator = taxCalculator,
                    currencyFormat = currencyFormat,
                    showProgressBar = false,
                    onDeductionChanged = {
                        scope.launch { taxInsights = taxCalculator.calculateTaxInsights() }
                    }
                )
            }
        }

        // Tax Estimate card
        item {
            TaxEstimateCard(
                oldRegimeTax = insights.oldRegimeTax,
                newRegimeTax = insights.newRegimeTax,
                recommendedRegime = insights.recommendedRegime,
                annualIncome = insights.annualIncome,
                totalDeductions = insights.section80C.utilized + insights.section80D.utilized +
                    insights.section80CCD.utilized + insights.section24B.utilized + insights.section80E.utilized,
                currencyFormat = currencyFormat
            )
        }

        // Tips card
        if (insights.tips.isNotEmpty()) {
            item {
                TipsCard(tips = insights.tips)
            }
        }

        // Disclaimer
        item {
            Text(
                text = "⚠️ This is an estimate for planning purposes only. Actual tax liability may differ. Please consult a Chartered Accountant for filing.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }

        // Bottom spacing
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun AnnualIncomeCard(
    income: Double,
    currencyFormat: NumberFormat,
    onIncomeUpdated: (Double) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editedIncome by remember { mutableStateOf("") }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Annual Income (Estimated)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = {
                    isEditing = !isEditing
                    editedIncome = income.toLong().toString()
                }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit income", modifier = Modifier.size(20.dp))
                }
            }

            if (isEditing) {
                OutlinedTextField(
                    value = editedIncome,
                    onValueChange = { editedIncome = it.filter { c -> c.isDigit() } },
                    label = { Text("Annual Income (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = {
                    val newIncome = editedIncome.toDoubleOrNull() ?: 0.0
                    if (newIncome > 0) {
                        onIncomeUpdated(newIncome)
                        isEditing = false
                    }
                }) {
                    Text("Save")
                }
            } else {
                Text(
                    text = currencyFormat.format(income),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Monthly: ${currencyFormat.format(income / 12)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaxSectionCard(
    section: TaxSection,
    sectionKey: String,
    taxCalculator: TaxCalculator,
    currencyFormat: NumberFormat,
    showProgressBar: Boolean = true,
    onDeductionChanged: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = section.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (section.limit != Double.MAX_VALUE) {
                    Text(
                        text = "Limit: ${currencyFormat.format(section.limit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            if (showProgressBar && section.limit != Double.MAX_VALUE) {
                val progress = if (section.limit > 0) (section.utilized / section.limit).toFloat().coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Used: ${currencyFormat.format(section.utilized)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (section.remaining > 0 && section.limit != Double.MAX_VALUE) {
                        Text(
                            text = "Remaining: ${currencyFormat.format(section.remaining)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Auto-detected items only (manual shown separately with delete button)
            section.items.filter { it.isAutoDetected }.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (item.isAutoDetected) {
                            Badge(containerColor = Color(0xFF4CAF50)) {
                                Text("Auto", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text(
                        text = currencyFormat.format(item.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Manual deductions with delete
            val manualDeductions = taxCalculator.getManualDeductions().filter { it.section == sectionKey }
            manualDeductions.forEach { deduction ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = deduction.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currencyFormat.format(deduction.amount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    taxCalculator.removeManualDeduction(deduction.id)
                                    onDeductionChanged()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFFFF5252)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Add button
            TextButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Deduction")
            }
        }
    }

    if (showAddDialog) {
        AddDeductionDialog(
            sectionName = section.name,
            onDismiss = { showAddDialog = false },
            onAdd = { description, amount ->
                scope.launch {
                    taxCalculator.addManualDeduction(
                        ManualDeduction(
                            id = UUID.randomUUID().toString(),
                            section = sectionKey,
                            description = description,
                            amount = amount
                        )
                    )
                    onDeductionChanged()
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddDeductionDialog(
    sectionName: String,
    onDismiss: () -> Unit,
    onAdd: (String, Double) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to $sectionName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amountValue = amount.toDoubleOrNull()
                    if (description.isNotBlank() && amountValue != null && amountValue > 0) {
                        onAdd(description, amountValue)
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TaxEstimateCard(
    oldRegimeTax: Double,
    newRegimeTax: Double,
    recommendedRegime: String,
    annualIncome: Double,
    totalDeductions: Double,
    currencyFormat: NumberFormat
) {
    var showExplanation by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Tax Estimate",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Old Regime
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Old Regime", style = MaterialTheme.typography.bodyLarge)
                    if (recommendedRegime == "OLD") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF4CAF50)
                        ) {
                            Text(
                                "RECOMMENDED",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
                Text(
                    text = currencyFormat.format(oldRegimeTax),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // New Regime
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("New Regime", style = MaterialTheme.typography.bodyLarge)
                    if (recommendedRegime == "NEW") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF4CAF50)
                        ) {
                            Text(
                                "RECOMMENDED",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
                Text(
                    text = currencyFormat.format(newRegimeTax),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expandable "Why?" section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showExplanation = !showExplanation },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Why ${recommendedRegime.lowercase().replaceFirstChar { it.uppercase() }} Regime?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    if (showExplanation) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle explanation",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = showExplanation) {
                val saving = kotlin.math.abs(oldRegimeTax - newRegimeTax)
                val explanation = if (recommendedRegime == "NEW") {
                    "New Regime saves you ${currencyFormat.format(saving)} because your total deductions " +
                        "(${currencyFormat.format(totalDeductions)}) are less than the benefit of lower slab rates " +
                        "in New Regime. Old Regime becomes better when total deductions exceed approximately " +
                        "${currencyFormat.format(375000.0)}."
                } else {
                    "Old Regime saves you ${currencyFormat.format(saving)} because your total deductions " +
                        "(${currencyFormat.format(totalDeductions)}) significantly reduce your taxable income. " +
                        "The deduction benefits outweigh the lower slab rates offered by New Regime."
                }
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun TipsCard(tips: List<String>) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFFFAB40),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tax Saving Tips",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            tips.forEach { tip ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
