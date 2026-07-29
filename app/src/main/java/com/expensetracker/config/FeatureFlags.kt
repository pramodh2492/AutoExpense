package com.expensetracker.config

/**
 * Compile-time feature toggles. Flipping a flag here hides a feature from the UI
 * without deleting its code — reversible in one line.
 */
object FeatureFlags {
    /**
     * Tax page. Hidden from the bottom navigation (the manual tax calculator was
     * rarely used). The TaxCalculator/TaxCalculationEngine code and the inline
     * Dashboard tax card remain compiled and injected; only the nav entry is gated.
     * Flip back to true to restore the "Tax" tab.
     */
    const val TAX_ENABLED = false

    /** Split-with-friends: split a debit into shares and track who has paid you back. */
    const val SPLIT_ENABLED = true
}
