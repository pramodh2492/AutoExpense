package com.expensetracker.data.remote

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemotePatternProvider @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
    private val gson: Gson
) {

    companion object {
        private const val TAG = "RemotePatternProvider"
        private const val KEY_BANK_SMS_PATTERNS = "bank_sms_patterns"
        private const val FETCH_INTERVAL_SECONDS = 3600L // 1 hour
    }

    private var bankPatterns: Map<String, BankPatternConfig> = emptyMap()

    fun initialize() {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = FETCH_INTERVAL_SECONDS
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(com.expensetracker.R.xml.remote_config_defaults)

        loadPatternsFromCache()

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Remote config fetched and activated successfully")
                loadPatternsFromCache()
            } else {
                Log.w(TAG, "Remote config fetch failed, using cached/default values")
            }
        }
    }

    private fun loadPatternsFromCache() {
        try {
            val json = remoteConfig.getString(KEY_BANK_SMS_PATTERNS)
            if (json.isNotBlank()) {
                val type = object : TypeToken<Map<String, BankPatternConfig>>() {}.type
                bankPatterns = gson.fromJson(json, type) ?: emptyMap()
                Log.d(TAG, "Loaded patterns for ${bankPatterns.size} banks")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse bank SMS patterns from remote config", e)
            bankPatterns = getLocalFallbackPatterns()
        }
    }

    fun getMerchantPatterns(bankId: String): List<String> {
        return bankPatterns[bankId]?.merchantPatterns ?: getLocalFallbackPatterns()[bankId]?.merchantPatterns ?: emptyList()
    }

    fun getExcludePatterns(): List<String> {
        val allExclude = mutableSetOf<String>()
        bankPatterns.values.forEach { config ->
            allExclude.addAll(config.excludePatterns)
        }
        if (allExclude.isEmpty()) {
            getLocalFallbackPatterns().values.forEach { config ->
                allExclude.addAll(config.excludePatterns)
            }
        }
        return allExclude.toList()
    }

    fun getCreditIndicators(bankId: String): List<String> {
        return bankPatterns[bankId]?.creditIndicators ?: getLocalFallbackPatterns()[bankId]?.creditIndicators ?: emptyList()
    }

    fun getAllBankIds(): Set<String> {
        return bankPatterns.keys.ifEmpty { getLocalFallbackPatterns().keys }
    }

    private fun getLocalFallbackPatterns(): Map<String, BankPatternConfig> {
        return mapOf(
            "AXISBK" to BankPatternConfig(
                merchantPatterns = listOf(
                    "(?i)spent.*?at\\s+(.+?)\\s+on",
                    "(?i)txn.*?at\\s+(.+?)\\s+for",
                    "(?i)payment.*?to\\s+(.+?)\\s+ref"
                ),
                excludePatterns = listOf(
                    "(?i)OTP", "(?i)verification", "(?i)password"
                ),
                creditIndicators = listOf(
                    "(?i)credited", "(?i)received", "(?i)refund"
                )
            ),
            "CUBANK" to BankPatternConfig(
                merchantPatterns = listOf(
                    "(?i)debited.*?towards\\s+(.+?)\\s",
                    "(?i)spent.*?at\\s+(.+?)\\s"
                ),
                excludePatterns = listOf(
                    "(?i)OTP", "(?i)verification"
                ),
                creditIndicators = listOf(
                    "(?i)credited", "(?i)deposited"
                )
            ),
            "HDFCBK" to BankPatternConfig(
                merchantPatterns = listOf(
                    "(?i)at\\s+(.+?)\\s+on\\s+\\d",
                    "(?i)to\\s+(.+?)\\s+Ref",
                    "(?i)txn.*?for\\s+(.+?)\\s+on"
                ),
                excludePatterns = listOf(
                    "(?i)OTP", "(?i)NetBanking", "(?i)password", "(?i)PIN"
                ),
                creditIndicators = listOf(
                    "(?i)credited", "(?i)received", "(?i)cashback", "(?i)refund"
                )
            ),
            "SBIINB" to BankPatternConfig(
                merchantPatterns = listOf(
                    "(?i)transfer to\\s+(.+?)\\s",
                    "(?i)debited.*?towards\\s+(.+?)\\s"
                ),
                excludePatterns = listOf(
                    "(?i)OTP", "(?i)YONO"
                ),
                creditIndicators = listOf(
                    "(?i)credited", "(?i)deposited", "(?i)received"
                )
            ),
            "ICICIB" to BankPatternConfig(
                merchantPatterns = listOf(
                    "(?i)at\\s+(.+?)\\.",
                    "(?i)txn of.*?at\\s+(.+?)\\s+on",
                    "(?i)paid to\\s+(.+?)\\s"
                ),
                excludePatterns = listOf(
                    "(?i)OTP", "(?i)iMobile", "(?i)password"
                ),
                creditIndicators = listOf(
                    "(?i)credited", "(?i)received", "(?i)refund", "(?i)reversed"
                )
            )
        )
    }
}

data class BankPatternConfig(
    val merchantPatterns: List<String> = emptyList(),
    val excludePatterns: List<String> = emptyList(),
    val creditIndicators: List<String> = emptyList()
)
