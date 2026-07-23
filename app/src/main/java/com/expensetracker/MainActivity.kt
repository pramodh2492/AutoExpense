package com.expensetracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.expensetracker.analytics.AnalyticsHelper
import com.expensetracker.data.local.RateAppManager
import com.expensetracker.data.local.UserPreferences
import com.expensetracker.tax.TaxCalculator
import com.expensetracker.ui.components.RateDialog
import com.expensetracker.ui.navigation.NavGraph
import com.expensetracker.ui.navigation.Screen
import com.expensetracker.ui.screens.OnboardingScreen
import com.expensetracker.ui.screens.SplashScreen
import com.expensetracker.ui.theme.ExpenseTrackerTheme
import com.expensetracker.notification.NotificationHelper
import com.expensetracker.viewmodel.ExpenseViewModel
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var budgetPreferences: com.expensetracker.data.local.BudgetPreferences
    @Inject lateinit var appLockManager: com.expensetracker.security.AppLockManager
    @Inject lateinit var authManager: com.expensetracker.security.AuthManager
    @Inject lateinit var rateAppManager: RateAppManager
    @Inject lateinit var analyticsHelper: AnalyticsHelper
    @Inject lateinit var taxCalculator: TaxCalculator

    private var onPermissionGranted: (() -> Unit)? = null
    private var signedInEmail = mutableStateOf<String?>(null)
    private var signedInName = mutableStateOf<String?>(null)
    private var signedInPhoto = mutableStateOf<String?>(null)

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_SMS] == true &&
            permissions[Manifest.permission.RECEIVE_SMS] == true
        ) {
            onPermissionGranted?.invoke()
        }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                lifecycleScope.launch {
                    if (authManager.firebaseAuthWithGoogle(idToken)) {
                        signedInEmail.value = authManager.userEmail
                        signedInName.value = authManager.userName
                        signedInPhoto.value = authManager.userPhotoUrl
                    }
                }
            }
        } catch (_: Exception) {}
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        signedInEmail.value = authManager.userEmail
        signedInName.value = authManager.userName
        signedInPhoto.value = authManager.userPhotoUrl
        requestSmsPermissions()

        // Track app open
        rateAppManager.recordAppOpen()
        analyticsHelper.logAppOpen()

        setContent {
            ExpenseTrackerTheme {
                // Splash screen
                var showSplash by remember { mutableStateOf(true) }

                if (showSplash) {
                    SplashScreen(onSplashFinished = { showSplash = false })
                    return@ExpenseTrackerTheme
                }

                // Onboarding for new users (shown before everything else)
                var hasCompletedOnboarding by remember {
                    mutableStateOf(userPreferences.hasCompletedOnboarding)
                }

                if (!hasCompletedOnboarding) {
                    OnboardingScreen(
                        userPreferences = userPreferences,
                        onComplete = {
                            userPreferences.hasCompletedOnboarding = true
                            hasCompletedOnboarding = true
                        }
                    )
                    return@ExpenseTrackerTheme
                }

                var isUnlocked by remember {
                    mutableStateOf(!appLockManager.isAppLockEnabled)
                }

                if (!isUnlocked) {
                    com.expensetracker.security.LockScreen(
                        appLockManager = appLockManager,
                        onUnlocked = { isUnlocked = true }
                    )
                    return@ExpenseTrackerTheme
                }

                // Google Sign-In prompt (non-mandatory, shows once)
                var showSignInPrompt by remember {
                    mutableStateOf(authManager.currentUser == null && !userPreferences.hasSeenSignInPrompt)
                }

                if (showSignInPrompt) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = {
                            showSignInPrompt = false
                            userPreferences.hasSeenSignInPrompt = true
                        },
                        title = { Text("Sign in to backup") },
                        text = { Text("Sign in with Google to backup your data and sync across devices. You can always do this later in Settings.") },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                showSignInPrompt = false
                                userPreferences.hasSeenSignInPrompt = true
                                try {
                                    googleSignInLauncher.launch(authManager.getSignInIntent())
                                } catch (_: Exception) {}
                            }) { Text("Sign in") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                showSignInPrompt = false
                                userPreferences.hasSeenSignInPrompt = true
                            }) { Text("Skip") }
                        }
                    )
                }

                val navController = rememberNavController()
                val viewModel: ExpenseViewModel = hiltViewModel()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Rate dialog state
                var showRateDialog by remember {
                    mutableStateOf(rateAppManager.shouldShowRateDialog())
                }

                if (showRateDialog) {
                    RateDialog(
                        onRate = {
                            rateAppManager.onUserRated()
                            showRateDialog = false
                            openPlayStoreListing()
                        },
                        onLater = {
                            rateAppManager.onUserLater()
                            showRateDialog = false
                        },
                        onNever = {
                            rateAppManager.onUserNever()
                            showRateDialog = false
                        }
                    )
                }

                LaunchedEffect(Unit) {
                    viewModel.scanExistingSms()
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Dashboard, contentDescription = "Home") },
                                label = { Text("Home", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                selected = currentRoute == Screen.Dashboard.route,
                                onClick = {
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                                    }
                                }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.List, contentDescription = "Txns") },
                                label = { Text("Txns", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                selected = currentRoute == Screen.Transactions.route,
                                onClick = {
                                    navController.navigate(Screen.Transactions.route) {
                                        popUpTo(Screen.Dashboard.route)
                                    }
                                }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Budget") },
                                label = { Text("Budget", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                selected = currentRoute == Screen.Budget.route,
                                onClick = {
                                    navController.navigate(Screen.Budget.route) {
                                        popUpTo(Screen.Dashboard.route)
                                    }
                                }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Analytics, contentDescription = "Tax") },
                                label = { Text("Tax", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                selected = currentRoute == Screen.Tax.route,
                                onClick = {
                                    navController.navigate(Screen.Tax.route) {
                                        popUpTo(Screen.Dashboard.route)
                                    }
                                }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                selected = currentRoute == Screen.Settings.route,
                                onClick = {
                                    navController.navigate(Screen.Settings.route) {
                                        popUpTo(Screen.Dashboard.route)
                                    }
                                }
                            )
                        }
                    },
                    floatingActionButton = {
                        if (currentRoute == Screen.Dashboard.route ||
                            currentRoute == Screen.Transactions.route
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    navController.navigate(Screen.AddTransaction.route)
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Expense")
                            }
                        }
                    }
                ) { paddingValues ->
                    NavGraph(
                        navController = navController,
                        viewModel = viewModel,
                        userPreferences = userPreferences,
                        budgetPreferences = budgetPreferences,
                        notificationHelper = notificationHelper,
                        appLockManager = appLockManager,
                        taxCalculator = taxCalculator,
                        onSignIn = {
                            try {
                                googleSignInLauncher.launch(authManager.getSignInIntent())
                            } catch (e: Exception) {
                                signedInEmail.value = null
                            }
                        },
                        onSignOut = {
                            authManager.signOut()
                            signedInEmail.value = null
                            signedInName.value = null
                            signedInPhoto.value = null
                        },
                        signedInEmail = signedInEmail.value,
                        signedInName = signedInName.value,
                        signedInPhoto = signedInPhoto.value,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }
    }

    private fun openPlayStoreListing() {
        val uri = Uri.parse("market://details?id=com.autoexpense.app")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.autoexpense.app")
                )
            )
        }
    }

    private fun requestSmsPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needsPermission = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needsPermission) {
            smsPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
