package com.expensetracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.expensetracker.data.local.UserPreferences
import com.expensetracker.data.model.TransactionType
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.notification.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var smsParser: SmsParser
    @Inject lateinit var repository: TransactionRepository
    @Inject lateinit var notificationHelper: NotificationHelper

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }
        val timestamp = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(messages[0].timestampMillis),
            ZoneId.systemDefault()
        )

        val transaction = smsParser.parse(sender, body, timestamp) ?: return

        val pendingResult = goAsync()
        scope.launch {
            try {
                repository.insert(transaction)

                if (transaction.type == TransactionType.DEBIT) {
                    if (transaction.merchant == "Unknown") {
                        notificationHelper.showUnknownMerchantPrompt(
                            transactionId = System.currentTimeMillis(),
                            rawSms = body.take(100),
                            amount = transaction.amount
                        )
                    } else {
                        notificationHelper.showTransactionNotification(
                            merchant = transaction.merchant,
                            amount = transaction.amount,
                            category = transaction.category.displayName
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
