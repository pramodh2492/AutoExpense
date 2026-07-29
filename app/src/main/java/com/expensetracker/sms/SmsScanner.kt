package com.expensetracker.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.expensetracker.data.model.Transaction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsParser: SmsParser
) {

    data class ScanResult(
        val transactions: List<Transaction>,
        val maxTimestamp: Long,
        // Merchant names harvested from non-bank loyalty/points SMS (e.g. "Nuts n Spices"),
        // used to enrich bank debits whose own merchant came out as "Unknown".
        val merchantHints: List<SmsParser.MerchantHint> = emptyList()
    )

    fun scanExistingSms(sinceTimestamp: Long = 0L, daysBack: Int = 90): ScanResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return ScanResult(emptyList(), sinceTimestamp)
        }

        val transactions = mutableListOf<Transaction>()
        val merchantHints = mutableListOf<SmsParser.MerchantHint>()
        // Use sinceTimestamp if available (subsequent scans), else fall back to daysBack (first scan)
        val cutoff = if (sinceTimestamp > 0L) {
            sinceTimestamp
        } else {
            System.currentTimeMillis() - (daysBack.toLong() * 24 * 60 * 60 * 1000)
        }
        var maxTimestampFound = sinceTimestamp

        try {
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"),
                "date > ?",
                arrayOf(cutoff.toString()),
                "date DESC"
            )

            cursor?.use {
                val addressIdx = it.getColumnIndexOrThrow("address")
                val bodyIdx = it.getColumnIndexOrThrow("body")
                val dateIdx = it.getColumnIndexOrThrow("date")

                while (it.moveToNext()) {
                    val sender = it.getString(addressIdx) ?: continue
                    val body = it.getString(bodyIdx) ?: continue
                    val dateMillis = it.getLong(dateIdx)

                    if (dateMillis > maxTimestampFound) {
                        maxTimestampFound = dateMillis
                    }

                    val timestamp = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(dateMillis),
                        ZoneId.systemDefault()
                    )

                    val transaction = smsParser.parse(sender, body, timestamp)
                    if (transaction != null) {
                        transactions.add(transaction)
                    } else {
                        // Not a bank transaction — but might be a loyalty/points SMS
                        // that names the merchant for a debit from a different sender.
                        smsParser.extractMerchantHint(sender, body, timestamp)?.let {
                            merchantHints.add(it)
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            return ScanResult(emptyList(), sinceTimestamp)
        }

        return ScanResult(transactions, maxTimestampFound, merchantHints)
    }
}
