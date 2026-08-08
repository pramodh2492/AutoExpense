package com.expensetracker.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.expensetracker.data.local.UserPreferences
import com.expensetracker.ui.components.GlassBackground
import com.expensetracker.ui.components.GlassCard
import com.expensetracker.ui.components.GradientPillButton
import com.expensetracker.ui.theme.AppTheme

private val TOTAL_PAGES = 4

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    userPreferences: UserPreferences? = null,
    onComplete: () -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val glass = AppTheme.glass

    // Onboarding renders outside the main scaffold, so it paints its own premium
    // gradient + orb backdrop. Text still sits on contrasting glass/onBackground colors.
    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip button at the top
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (currentPage < TOTAL_PAGES - 1) {
                    TextButton(onClick = onComplete) {
                        Text("Skip")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Animated page content
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    (slideInHorizontally { width -> width } + fadeIn())
                        .togetherWith(slideOutHorizontally { width -> -width } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "onboarding_page"
            ) { page ->
                when (page) {
                    0 -> InfoPage(
                        icon = Icons.Default.PhoneAndroid,
                        title = "Auto-Track Expenses",
                        description = "The app reads your bank SMS automatically and records every transaction. No manual entry needed."
                    )
                    1 -> InfoPage(
                        icon = Icons.Default.PieChart,
                        title = "Smart Categories",
                        description = "Expenses are auto-categorized into Food, Transport, Shopping, Bills and more. You can always change the category."
                    )
                    2 -> InfoPage(
                        icon = Icons.Default.Notifications,
                        title = "Set Budgets & Get Alerts",
                        description = "Set spending limits for each category and get notified when you are close to exceeding them."
                    )
                    3 -> AccountSetupPage(userPreferences = userPreferences)
                }
            }

            // Dots indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                repeat(TOTAL_PAGES) { index ->
                    val active = index == currentPage
                    Box(
                        modifier = Modifier
                            .size(if (active) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .then(
                                if (active) {
                                    Modifier.background(Brush.horizontalGradient(glass.accentGradient))
                                } else {
                                    Modifier.background(
                                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                                    )
                                }
                            )
                    )
                }
            }

            // Bottom buttons
            if (currentPage < TOTAL_PAGES - 1) {
                GradientPillButton(
                    text = "Next",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { currentPage++ }
                )
            } else {
                GradientPillButton(
                    text = "Get Started",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onComplete
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoPage(icon: ImageVector, title: String, description: String) {
    val glass = AppTheme.glass
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Accent-gradient circular icon badge.
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(glass.accentGradient)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun AccountSetupPage(userPreferences: UserPreferences?) {
    val glass = AppTheme.glass
    var accountInput by remember { mutableStateOf("") }
    var accounts by remember { mutableStateOf(userPreferences?.userAccountNumbers ?: emptySet()) }
    var salaryInput by remember { mutableStateOf(userPreferences?.monthlySalary?.let { if (it > 0) it.toInt().toString() else "" } ?: "") }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Accent-gradient circular icon badge.
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(glass.accentGradient)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CreditCard,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Your Accounts",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add last 4 digits of all your bank accounts AND credit cards. This helps detect:\n• Transfers between your accounts (not counted as expense)\n• Credit card bill payments (not double-counted)",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Account + salary input area on a refined glass surface.
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Account input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = accountInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) accountInput = it
                        },
                        label = { Text("Last 4 digits") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        // Pin an explicit high-contrast text color so the typed digits are always
                        // visible regardless of the device's default field text color.
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (accountInput.length == 4) {
                                userPreferences?.addAccount(accountInput)
                                accounts = userPreferences?.userAccountNumbers ?: emptySet()
                                accountInput = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }

                // Show added accounts as glassy chips
                if (accounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        accounts.forEach { account ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(glass.glassFill)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "**$account",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Monthly salary input
                Text(
                    text = "Monthly Salary (optional)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Used for tax estimation. You can change this later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = salaryInput,
                    onValueChange = {
                        salaryInput = it.filter { c -> c.isDigit() }
                        it.toDoubleOrNull()?.let { salary ->
                            userPreferences?.monthlySalary = salary
                            userPreferences?.hasSalaryConfigured = true
                        }
                    },
                    label = { Text("Monthly salary (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
