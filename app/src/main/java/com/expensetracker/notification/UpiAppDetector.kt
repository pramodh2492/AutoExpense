package com.expensetracker.notification

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpiAppDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val upiPackages = setOf(
        "com.google.android.apps.nbu.paisa.user",  // GPay
        "com.phonepe.app",                          // PhonePe
        "net.one97.paytm",                          // Paytm
        "in.org.npci.upiapp",                       // BHIM
        "com.whatsapp",                             // WhatsApp Pay
        "com.amazon.mShop.android.shopping",        // Amazon Pay
        "com.mobikwik_new",                         // MobiKwik
        "com.freecharge.android",                   // Freecharge
        "com.cred.android",                         // CRED
        "com.slice",                                // Slice
    )

    fun wasUpiAppUsedRecently(withinMinutes: Int = 5): Boolean {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
                    as? UsageStatsManager ?: return false

            val endTime = System.currentTimeMillis()
            val startTime = endTime - (withinMinutes * 60 * 1000L)

            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                    event.packageName in upiPackages
                ) {
                    return true
                }
            }
            false
        } catch (e: SecurityException) {
            false
        }
    }

    fun getLastUsedUpiApp(withinMinutes: Int = 5): String? {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
                    as? UsageStatsManager ?: return null

            val endTime = System.currentTimeMillis()
            val startTime = endTime - (withinMinutes * 60 * 1000L)

            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            var lastUpiApp: String? = null
            var lastTime = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                    event.packageName in upiPackages &&
                    event.timeStamp > lastTime
                ) {
                    lastUpiApp = event.packageName
                    lastTime = event.timeStamp
                }
            }

            lastUpiApp?.let { packageToName(it) }
        } catch (e: SecurityException) {
            null
        }
    }

    private fun packageToName(pkg: String): String {
        return when (pkg) {
            "com.google.android.apps.nbu.paisa.user" -> "GPay"
            "com.phonepe.app" -> "PhonePe"
            "net.one97.paytm" -> "Paytm"
            "in.org.npci.upiapp" -> "BHIM"
            "com.whatsapp" -> "WhatsApp"
            "com.amazon.mShop.android.shopping" -> "Amazon"
            "com.cred.android" -> "CRED"
            "com.slice" -> "Slice"
            else -> "UPI App"
        }
    }
}
