package com.expensetracker.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.expensetracker.data.local.UserPreferences
import com.expensetracker.notification.NotificationHelper
import com.expensetracker.security.AppLockManager
import com.expensetracker.ui.components.GlassCard
import com.expensetracker.ui.components.GlassSectionHeader
import com.expensetracker.ui.components.GradientPillButton
import com.expensetracker.ui.theme.AppTheme

@Composable
fun SettingsScreen(
    userPreferences: UserPreferences,
    appLockManager: AppLockManager? = null,
    notificationHelper: NotificationHelper? = null,
    onBackup: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    onSignIn: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
    signedInEmail: String? = null,
    signedInName: String? = null,
    signedInPhoto: String? = null,
    syncStatus: String = "",
    onRescan: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val glass = AppTheme.glass
    var salaryAccount by remember { mutableStateOf(userPreferences.salaryAccountLast4) }
    var newAccount by remember { mutableStateOf("") }
    var accounts by remember { mutableStateOf(userPreferences.userAccountNumbers) }
    var minSalary by remember { mutableStateOf(userPreferences.minSalaryAmount.toInt().toString()) }
    var upiId by remember { mutableStateOf(userPreferences.upiId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Salary Account
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                GlassSectionHeader(title = "Salary Account")
                Text(
                    text = "Last 4 digits of your salary account. Credits to this account above the minimum amount will be tagged as Salary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                OutlinedTextField(
                    value = salaryAccount,
                    onValueChange = {
                        if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                            salaryAccount = it
                            userPreferences.salaryAccountLast4 = it
                            if (it.length == 4) {
                                userPreferences.addAccount(it)
                                accounts = userPreferences.userAccountNumbers
                            }
                        }
                    },
                    label = { Text("Last 4 digits") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = minSalary,
                    onValueChange = {
                        if (it.all { c -> c.isDigit() }) {
                            minSalary = it
                            it.toDoubleOrNull()?.let { amt ->
                                userPreferences.minSalaryAmount = amt
                            }
                        }
                    },
                    label = { Text("Minimum salary amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Other Accounts
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                GlassSectionHeader(title = "Your Accounts")
                Text(
                    text = "Add last 4 digits of all your bank accounts and cards. Transfers between these will be auto-detected as self-transfers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newAccount,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                newAccount = it
                            }
                        },
                        label = { Text("Last 4 digits") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (newAccount.length == 4) {
                                userPreferences.addAccount(newAccount)
                                accounts = userPreferences.userAccountNumbers
                                newAccount = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add account")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accounts.forEach { account ->
                        AssistChip(
                            onClick = { },
                            label = { Text("**$account") },
                            trailingIcon = {
                                IconButton(onClick = {
                                    userPreferences.removeAccount(account)
                                    accounts = userPreferences.userAccountNumbers
                                }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.padding(0.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // App Lock
        if (appLockManager != null) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    GlassSectionHeader(title = "App Lock")
                    Text(
                        text = if (appLockManager.isAppLockEnabled) "PIN + Biometric lock is ON"
                            else "Protect your expense data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    var pinInput by remember { mutableStateOf("") }

                    if (!appLockManager.isAppLockEnabled) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                            label = { Text("Set 4-digit PIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (pinInput.length == 4) {
                                    appLockManager.setLock(pinInput, biometric = true)
                                    pinInput = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = pinInput.length == 4
                        ) {
                            Text("Enable Lock")
                        }
                    } else {
                        Button(
                            onClick = { appLockManager.removeLock() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Remove Lock")
                        }
                    }
                }
            }
        }

        // Account
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                if (signedInEmail != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (signedInPhoto != null) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Brush.linearGradient(glass.accentGradient))
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                coil.compose.AsyncImage(
                                    model = signedInPhoto,
                                    contentDescription = "Profile",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Brush.linearGradient(glass.accentGradient)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (signedInName ?: signedInEmail).take(1).uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = signedInName ?: "User",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = signedInEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onSignOut?.invoke() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sign out")
                    }
                } else {
                    GlassSectionHeader(title = "Account")
                    Text(
                        text = "Sign in with Google to backup & sync across devices",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    if (onSignIn != null) {
                        GradientPillButton(
                            text = "Sign in with Google",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onSignIn
                        )
                    }
                }
            }
        }

        // UPI ID for split messages
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                GlassSectionHeader(title = "Payments")
                Text(
                    text = "Your UPI ID is included in split messages so friends can pay you directly. Leave blank to send plain messages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                OutlinedTextField(
                    value = upiId,
                    onValueChange = {
                        upiId = it
                        userPreferences.upiId = it
                    },
                    label = { Text("Your UPI ID (e.g. name@upi)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Cloud Backup & Restore
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                GlassSectionHeader(title = "Cloud Backup")
                Text(
                    text = "Your data syncs automatically. Use Restore on a new device to recover everything.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                if (syncStatus.isNotBlank()) {
                    Text(
                        text = syncStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onBackup != null) {
                        OutlinedButton(
                            onClick = onBackup,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Backup Now")
                        }
                    }
                    if (onRestore != null) {
                        Button(
                            onClick = onRestore,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Restore")
                        }
                    }
                }
            }
        }

        // Re-scan button
        Button(
            onClick = onRescan,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Re-scan SMS with new settings")
        }

        Spacer(modifier = Modifier.height(24.dp))
        // Privacy Policy link — required by Google Play for apps accessing sensitive data.
        // Update PRIVACY_POLICY_URL to your actual hosted policy page.
        val privacyPolicyUrl = "https://pramodh2492.github.io/AutoExpense/"
        Text(
            text = "Privacy Policy",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl))
                    )
                },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "AutoExpense by LazySloth",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        // Version stamp — read from the actually-installed package so you can confirm
        // at a glance whether the device is running the latest build.
        val appVersion = remember {
            try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                @Suppress("DEPRECATION")
                "Version ${pInfo.versionName} (${pInfo.versionCode})"
            } catch (_: Exception) { "" }
        }
        if (appVersion.isNotBlank()) {
            Text(
                text = appVersion,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Text(
            text = "© 2026 LazySloth. All rights reserved.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

    }
}
