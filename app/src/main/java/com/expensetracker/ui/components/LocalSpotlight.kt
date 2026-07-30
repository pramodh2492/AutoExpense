package com.expensetracker.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * Ambient handle to the running feature-discovery tour. Provided once at the app scaffold so
 * any screen can register [Modifier.spotlightTarget]s without threading the state through
 * navigation. Null when no tour host is present (e.g. previews).
 */
val LocalSpotlight = compositionLocalOf<SpotlightState?> { null }

/** Stable keys for spotlight targets, shared between the screens that register them and the
 *  tour that highlights them. */
object SpotlightTargets {
    const val PERIOD_CHIPS = "dashboard_period_chips"
    const val BALANCE_CARD = "dashboard_balance_card"
    const val REFRESH = "dashboard_refresh"
    const val NAV_TRANSACTIONS = "nav_transactions"
    const val NAV_BUDGET = "nav_budget"
    const val ADD_FAB = "add_fab"
    // The first row on the Transactions list — the tour spotlights it to demo the
    // tap-to-recategorize and split-with-friends actions.
    const val FIRST_TRANSACTION = "first_transaction"
}
