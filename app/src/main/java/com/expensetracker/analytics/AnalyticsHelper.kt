package com.expensetracker.analytics

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized analytics helper for tracking key app events via Firebase Analytics.
 *
 * Events tracked:
 * - app_open: User opens the app
 * - sms_scanned: SMS messages scanned for transactions
 * - transaction_added: A transaction is added (manual or auto)
 * - category_changed: User re-categorizes a transaction
 * - budget_set: User sets/updates a budget
 * - ai_query_asked: User asks a question to the AI assistant
 */
@Singleton
class AnalyticsHelper @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics
) {
    object Events {
        const val APP_OPEN = "app_open"
        const val SMS_SCANNED = "sms_scanned"
        const val TRANSACTION_ADDED = "transaction_added"
        const val CATEGORY_CHANGED = "category_changed"
        const val BUDGET_SET = "budget_set"
        const val AI_QUERY_ASKED = "ai_query_asked"
    }

    object Params {
        const val TRANSACTION_TYPE = "transaction_type"
        const val CATEGORY = "category"
        const val AMOUNT = "amount"
        const val SOURCE = "source"
        const val QUERY_LENGTH = "query_length"
        const val SMS_COUNT = "sms_count"
        const val BUDGET_CATEGORY = "budget_category"
        const val BUDGET_AMOUNT = "budget_amount"
    }

    fun logAppOpen() {
        firebaseAnalytics.logEvent(Events.APP_OPEN) {}
    }

    fun logSmsScanned(count: Int) {
        firebaseAnalytics.logEvent(Events.SMS_SCANNED) {
            param(Params.SMS_COUNT, count.toLong())
        }
    }

    fun logTransactionAdded(
        type: String,
        category: String,
        amount: Double,
        source: String = "manual"
    ) {
        firebaseAnalytics.logEvent(Events.TRANSACTION_ADDED) {
            param(Params.TRANSACTION_TYPE, type)
            param(Params.CATEGORY, category)
            param(Params.AMOUNT, amount)
            param(Params.SOURCE, source)
        }
    }

    fun logCategoryChanged(oldCategory: String, newCategory: String) {
        firebaseAnalytics.logEvent(Events.CATEGORY_CHANGED) {
            param("old_category", oldCategory)
            param(Params.CATEGORY, newCategory)
        }
    }

    fun logBudgetSet(category: String, amount: Double) {
        firebaseAnalytics.logEvent(Events.BUDGET_SET) {
            param(Params.BUDGET_CATEGORY, category)
            param(Params.BUDGET_AMOUNT, amount)
        }
    }

    fun logAiQueryAsked(queryLength: Int) {
        firebaseAnalytics.logEvent(Events.AI_QUERY_ASKED) {
            param(Params.QUERY_LENGTH, queryLength.toLong())
        }
    }
}
