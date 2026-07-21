package com.expensetracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.expensetracker.data.local.BudgetPreferences
import com.expensetracker.data.local.UserPreferences
import com.expensetracker.notification.NotificationHelper
import com.expensetracker.security.AppLockManager
import com.expensetracker.ui.screens.AddTransactionScreen
import com.expensetracker.ui.screens.BudgetScreen
import com.expensetracker.ui.screens.DashboardScreen
import com.expensetracker.ui.screens.SettingsScreen
import com.expensetracker.ui.screens.StatsScreen
import com.expensetracker.ui.screens.TransactionListScreen
import com.expensetracker.viewmodel.ExpenseViewModel

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Transactions : Screen("transactions")
    data object Stats : Screen("stats")
    data object Budget : Screen("budget")
    data object AddTransaction : Screen("add_transaction")
    data object Settings : Screen("settings")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    viewModel: ExpenseViewModel,
    userPreferences: UserPreferences,
    budgetPreferences: BudgetPreferences,
    notificationHelper: NotificationHelper,
    appLockManager: AppLockManager,
    onSignIn: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
    signedInEmail: String? = null,
    signedInName: String? = null,
    signedInPhoto: String? = null,
    modifier: Modifier = Modifier
) {
    NavHost(navController = navController, startDestination = Screen.Dashboard.route, modifier = modifier) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(viewModel = viewModel, navController = navController)
        }
        composable(Screen.Transactions.route) {
            TransactionListScreen(viewModel = viewModel)
        }
        composable(Screen.Stats.route) {
            StatsScreen(viewModel = viewModel)
        }
        composable(Screen.Budget.route) {
            BudgetScreen(viewModel = viewModel, budgetPreferences = budgetPreferences)
        }
        composable(Screen.AddTransaction.route) {
            AddTransactionScreen(
                viewModel = viewModel,
                onDone = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            val syncStatus by viewModel.syncStatus.collectAsState()
            SettingsScreen(
                userPreferences = userPreferences,
                appLockManager = appLockManager,
                notificationHelper = notificationHelper,
                onBackup = { viewModel.backupToCloud() },
                onRestore = { viewModel.restoreFromCloud() },
                onSignIn = onSignIn,
                onSignOut = onSignOut,
                signedInEmail = signedInEmail,
                signedInName = signedInName,
                signedInPhoto = signedInPhoto,
                syncStatus = syncStatus,
                onRescan = {
                    viewModel.scanExistingSms()
                    navController.popBackStack()
                }
            )
        }
    }
}
