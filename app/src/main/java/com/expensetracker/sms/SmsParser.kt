package com.expensetracker.sms

import com.expensetracker.data.local.KnownMerchants
import com.expensetracker.data.local.UserPreferences
import com.expensetracker.data.model.PaymentSource
import com.expensetracker.data.model.Transaction
import com.expensetracker.data.model.TransactionCategory
import com.expensetracker.data.model.TransactionType
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsParser @Inject constructor(
    private val userPreferences: UserPreferences
) {

    // Indian bank SMS formats:
    // "Rs.500.00 debited from A/c XX1234 on 15-Jul-25 to VPA swiggy@axl (UPI Ref 123456)"
    // "INR 1,200 spent on HDFC CC XX9876 at AMAZON on 15-Jul-25"
    // "Your a/c XX5678 is debited by Rs 5000 for NACH/ECS - HDFC HOME LOAN"
    // "Rs 200.00 paid to Zomato via UPI from A/c XX1234. UPI Ref: 123"
    // "Dear Customer, Rs.2500 has been debited from your A/c XX4321 towards EMI"

    private val amountPatterns = listOf(
        Regex("""(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
        Regex("""([\d,]+\.?\d*)\s*(?:Rs\.?|INR|₹)""", RegexOption.IGNORE_CASE),
    )

    private val debitKeywords = listOf(
        "debited", "debit", "spent", "paid", "sent", "transferred",
        "withdrawn", "purchase", "payment", "charged", "auto-debit",
        "ecs", "nach", "emi", "mandate", "deducted"
    )

    private val creditKeywords = listOf(
        "credited", "credit", "received", "refund", "cashback", "reversed"
    )

    private val bankSenders = listOf(
        "HDFCBK", "SBIINB", "ICICIB", "AXISBK", "KOTAKB", "PNBSMS",
        "BOIIND", "CANBNK", "UNIONB", "IABORC", "YESBNK", "INDUSB",
        "FEDBNK", "SCBANK", "CITIBK", "AMEXIN", "HSBCIN", "RBLBNK",
        "IDFCFB", "AUBANK", "BARODQ", "CENTBK", "IDBI", "MAHABK",
        "JUPBNK", "FIBNK", "SLCBNK", "PAYTMB"
    )

    // Ordered by specificity — first match wins
    private val merchantExtractors = listOf(
        // AXIS BANK: "Info: UPI/refno/Payee Name/vpa@bank" — payee is 3rd slash-segment
        Regex("""Info[:\s]*UPI/[^/]+/([^/]+)/""", RegexOption.IGNORE_CASE),

        // AXIS BANK: "Info: UPI-merchant@bank-Payee Name" (alternate format)
        Regex("""Info[:\s]*UPI-[^-]+-(.+?)(?:\s*$)""", RegexOption.IGNORE_CASE),

        // AXIS BANK: "Info: NEFT/IMPS/refno/PAYEE NAME"
        Regex("""Info[:\s]*(?:NEFT|IMPS|RTGS)/[^/]+/([^/]+)""", RegexOption.IGNORE_CASE),

        // AXIS BANK: "Info: BIL/BPAY/refno/PAYEE NAME"
        Regex("""Info[:\s]*(?:BIL|BPAY)/[^/]+/([^/]+)""", RegexOption.IGNORE_CASE),

        // AXIS BANK: "Info: NACH/refno/PAYEE" or "Info: ECS/PAYEE"
        Regex("""Info[:\s]*(?:NACH|ECS)/[^/]*/([^/]+)""", RegexOption.IGNORE_CASE),

        // AXIS BANK: "Info: ATM-WDL" or "Info: something" (generic Info catch-all)
        Regex("""Info[:\s]+(?:(?:UPI|NEFT|IMPS|RTGS|BIL|BPAY|NACH|ECS|ATM)[^A-Za-z]*)?([A-Za-z][A-Za-z0-9\s&.'-]{1,40}?)(?:\s*$|\s*/|\.)""", RegexOption.IGNORE_CASE),

        // "purchase @ Merchant Name" or "purchase at Merchant" (Apollo Pharmacy style)
        Regex("""purchase\s*[@at]+\s*([A-Za-z][A-Za-z0-9\s&.'-]{1,35}?)(?:\s*[.!]|\s+(?:Click|on|Ref|$))""", RegexOption.IGNORE_CASE),

        // "towards policy no." / "towards POLICY_NAME"
        Regex("""towards\s+([A-Za-z][A-Za-z0-9\s&.'-]{1,35}?)(?:\s+(?:no|policy|on|\.|$))""", RegexOption.IGNORE_CASE),

        // "NACH debit towards TP ACH HDF" — extract what comes after "towards"
        Regex("""(?:NACH|ECS)\s+(?:debit\s+)?towards\s+([A-Za-z][A-Za-z0-9\s&.'-]{1,35})""", RegexOption.IGNORE_CASE),

        // Generic: "to VPA merchant@bank" → extract merchant name before @
        Regex("""(?:to\s+)?VPA\s*[:\-]?\s*([a-zA-Z0-9._-]+)@""", RegexOption.IGNORE_CASE),

        // "paid to Merchant Name" / "sent to Merchant Name"
        Regex("""(?:paid|sent|transferred)\s+to\s+([A-Za-z0-9][A-Za-z0-9\s&.'-]{1,35}?)(?:\s+(?:on|via|UPI|from|Ref|\(|$))""", RegexOption.IGNORE_CASE),

        // "at MERCHANT NAME" (credit card / purchase style) — more relaxed
        Regex("""[@]\s*([A-Za-z][A-Za-z0-9\s&.'-]{1,35}?)(?:\s*[.!]|\s+(?:Click|on|Ref|$))""", RegexOption.IGNORE_CASE),
        Regex("""\bat\s+([A-Za-z0-9][A-Za-z0-9\s&.'-]{1,35}?)(?:\s+(?:on|dated|Click|\.|\s*$))""", RegexOption.IGNORE_CASE),

        // "to MERCHANT NAME on" / "to MERCHANT NAME."
        Regex("""\bto\s+([A-Za-z0-9][A-Za-z0-9\s&.'-]{1,35}?)(?:\s+(?:on|via|UPI|Ref|w\.e\.f|\.|\(|\s*$))""", RegexOption.IGNORE_CASE),

        // "for NACH/ECS - REASON" or "for EMI - REASON" or "towards REASON"
        Regex("""(?:for|towards)\s+(?:NACH|ECS|EMI|SI)?\s*[-/:]?\s*([A-Za-z0-9][A-Za-z0-9\s&.'-]{1,35}?)(?:\s+(?:on|Ref|no\.?|\.|\s*$))""", RegexOption.IGNORE_CASE),

        // "for MERCHANT/REASON"
        Regex("""\bfor\s+([A-Za-z0-9][A-Za-z0-9\s&.'-]{1,35}?)(?:\s+(?:on|via|UPI|Ref|w\.e\.f|\.|\s*$))""", RegexOption.IGNORE_CASE),

        // "with UMRN" — extract what comes before (NACH style)
        Regex("""(?:for|towards)\s+(.+?)\s+(?:with\s+UMRN|for\s+INR)""", RegexOption.IGNORE_CASE),

        // Slash-separated: "UPI/P2M/refno/Merchant Name" — grab last meaningful segment
        Regex("""UPI/[^/]+/[^/]+/([A-Za-z][A-Za-z0-9\s&.'-]{1,35})""", RegexOption.IGNORE_CASE),
    )

    private val upiIndicators = listOf(
        Regex("""UPI""", RegexOption.IGNORE_CASE),
        Regex("""VPA""", RegexOption.IGNORE_CASE),
        Regex("""GPay|PhonePe|Paytm|BHIM|Google Pay""", RegexOption.IGNORE_CASE),
        Regex("""\w+@\w+"""),
    )

    private val creditCardIndicators = listOf(
        Regex("""(?:credit\s*card|CC)\s*(?:ending|xx|XX|no\.?)?\s*(\d{4})""", RegexOption.IGNORE_CASE),
        Regex("""card\s*(?:ending|no\.?|xx|XX)\s*(\d{4})""", RegexOption.IGNORE_CASE),
    )

    private val ecsIndicators = listOf(
        Regex("""(?:ECS|NACH|auto[- ]?debit|mandate|SI\s)""", RegexOption.IGNORE_CASE),
        Regex("""(?:standing instruction|recurring payment)""", RegexOption.IGNORE_CASE),
    )

    // Messages that should NOT be treated as transactions
    private val excludePatterns = listOf(
        // Security/OTP messages
        Regex("""BEWARE""", RegexOption.IGNORE_CASE),
        Regex("""DO NOT GIVE""", RegexOption.IGNORE_CASE),
        Regex("""DO NOT SHARE""", RegexOption.IGNORE_CASE),
        Regex("""OTP|otp|One Time Password"""),

        // Balance/passbook/statement messages (not transactions)
        Regex("""(?:available|avl\.?|avail)\s*(?:bal|balance)""", RegexOption.IGNORE_CASE),
        Regex("""balance\s*(?:is|:)\s*(?:Rs\.?|INR|₹)""", RegexOption.IGNORE_CASE),
        Regex("""passbook""", RegexOption.IGNORE_CASE),
        Regex("""passbo""", RegexOption.IGNORE_CASE),
        Regex("""account\s+statement""", RegexOption.IGNORE_CASE),
        Regex("""mini\s*statement""", RegexOption.IGNORE_CASE),
        Regex("""your\s+balance""", RegexOption.IGNORE_CASE),
        Regex("""contribution\s+of\s+Rs""", RegexOption.IGNORE_CASE),
        Regex("""(?:PF|EPF|provident\s+fund)\s+(?:balance|contribution)""", RegexOption.IGNORE_CASE),

        // Failed/declined transactions
        Regex("""(?:has\s+)?failed""", RegexOption.IGNORE_CASE),
        Regex("""(?:could\s+not|cannot|unable)\s+(?:be\s+)?(?:processed|completed|debited)""", RegexOption.IGNORE_CASE),
        Regex("""unsuccessful""", RegexOption.IGNORE_CASE),
        Regex("""declined""", RegexOption.IGNORE_CASE),
        Regex("""reversal\s+(?:failed|unsuccessful)""", RegexOption.IGNORE_CASE),
        Regex("""(?:will be|to be)\s+reversed""", RegexOption.IGNORE_CASE),

        // Reminders and payment-due messages (NOT actual debits)
        Regex("""reminder""", RegexOption.IGNORE_CASE),
        Regex("""is\s+due\b""", RegexOption.IGNORE_CASE),
        Regex("""(?:payment|emi|bill)\s+(?:is\s+)?due""", RegexOption.IGNORE_CASE),
        Regex("""due\s+on\s+\d""", RegexOption.IGNORE_CASE),
        Regex("""pay\s+(?:by|before|on)\s+\d""", RegexOption.IGNORE_CASE),
        Regex("""please\s+pay""", RegexOption.IGNORE_CASE),
        Regex("""kindly\s+pay""", RegexOption.IGNORE_CASE),
        Regex("""amount\s+due""", RegexOption.IGNORE_CASE),
        Regex("""overdue""", RegexOption.IGNORE_CASE),
        Regex("""outstanding""", RegexOption.IGNORE_CASE),
        Regex("""minimum\s+(?:amount\s+)?due""", RegexOption.IGNORE_CASE),
        Regex("""total\s+(?:amount\s+)?due""", RegexOption.IGNORE_CASE),
        Regex("""avoid\s+late\s+fee""", RegexOption.IGNORE_CASE),
        Regex("""last\s+date\s+(?:to|of)\s+pay""", RegexOption.IGNORE_CASE),
        Regex("""request\s+(?:you\s+)?to\s+pay""", RegexOption.IGNORE_CASE),

        // Promotional/informational
        Regex("""promotional|offer|reward points|congratulations""", RegexOption.IGNORE_CASE),
        Regex("""pre[- ]?approved""", RegexOption.IGNORE_CASE),
        Regex("""loan\s+offer""", RegexOption.IGNORE_CASE),
        Regex("""credit\s+limit\s+(?:increased|enhanced)""", RegexOption.IGNORE_CASE),

        // Advertisements / marketing (real-estate, sales pitches) — never real transactions.
        // "Price starts Rs.X /EMI onwards", "Why pay rent", "T&C apply", "call ... to book"
        Regex("""\bt&c\s+appl(?:y|ies)""", RegexOption.IGNORE_CASE),
        Regex("""(?:price|emi|rent|starting)\s+starts?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bonwards\b""", RegexOption.IGNORE_CASE),
        Regex("""why\s+pay\s+rent""", RegexOption.IGNORE_CASE),
        Regex("""\b\d+\s*bhk\b""", RegexOption.IGNORE_CASE),
        Regex("""ready[- ]to[- ]occupy""", RegexOption.IGNORE_CASE),
        Regex("""(?:book|call)\s+now""", RegexOption.IGNORE_CASE),

        // Wallet/app credits (not real bank credits)
        Regex("""(?:wallet|account)\s+(?:has been\s+)?credited.*(?:use|shop|valid|expir)""", RegexOption.IGNORE_CASE),
        Regex("""(?:cashback|reward|bonus|coupon|coins?)\s+(?:of\s+)?(?:Rs\.?|INR|₹)""", RegexOption.IGNORE_CASE),
        Regex("""credited\s+to\s+(?:your\s+)?(?:wallet|account).*(?:shop|order|app|code)""", RegexOption.IGNORE_CASE),
        Regex("""(?:bewakoof|myntra|flipkart|amazon|meesho|ajio|nykaa).*(?:wallet|credit|cashback|reward)""", RegexOption.IGNORE_CASE),
    )

    // Non-bank sender IDs that should be ignored even if they match the format
    private val excludedSenders = listOf(
        "BEWKOF", "MYNTRA", "FLPKRT", "AMAZIN", "MEESHO", "AJIOOO",
        "NYKAA", "SWIGGY", "ZOMATO", "DUNZO", "CRED", "PHONEPE",
        "PAYTM", "GPAY", "OLACAB", "RAPIDO", "UBER"
    )

    fun isTransactionalSms(sender: String, body: String): Boolean {
        // TRAI DLT headers end with a category code: -P (Promotional), -T (Transactional),
        // -S (Service), -G (Government). Real bank debit/credit alerts are never Promotional.
        // Check the ORIGINAL sender (with dashes) before we strip them below.
        // e.g. "CP-RDBEST-P" -> promotional real-estate ad, must be rejected.
        if (Regex("""-P$""", RegexOption.IGNORE_CASE).containsMatchIn(sender.trim())) return false

        val senderUpper = sender.uppercase().replace(Regex("[^A-Z0-9]"), "")

        // Exclude known non-bank senders (shopping apps, wallets)
        if (excludedSenders.any { senderUpper.contains(it) }) return false

        val isBankSender = bankSenders.any { senderUpper.contains(it) } ||
                senderUpper.contains("BANK") ||
                Regex("""[A-Z]{2}[A-Z]{4,}""").containsMatchIn(senderUpper)

        if (!isBankSender) return false

        // Exclude failed/declined/promotional messages
        if (excludePatterns.any { it.containsMatchIn(body) }) return false

        val hasAmount = amountPatterns.any { it.containsMatchIn(body) }
        val hasTransactionKeyword = (debitKeywords + creditKeywords).any {
            body.contains(it, ignoreCase = true)
        }

        return hasAmount && hasTransactionKeyword
    }

    fun reparseType(rawSms: String): TransactionType {
        return determineTransactionType(rawSms)
    }

    fun parse(sender: String, body: String, receivedAt: LocalDateTime): Transaction? {
        if (!isTransactionalSms(sender, body)) return null

        val amount = extractAmount(body) ?: return null
        val type = determineTransactionType(body)
        val source = determinePaymentSource(body)
        val merchant = extractMerchant(body)
        val accountInfo = extractAccountInfo(body)
        val category = categorize(merchant, body, source)
        val isSalary = isSalaryCredit(body, type, amount)
        val hash = generateHash(body, receivedAt)

        return Transaction(
            amount = amount,
            merchant = if (isSalary) "Salary" else merchant,
            category = if (isSalary) TransactionCategory.SALARY else category,
            type = type,
            source = source,
            accountInfo = accountInfo,
            rawSms = body,
            smsHash = hash,
            timestamp = receivedAt,
            isSelfTransfer = detectSelfTransfer(body)
        )
    }

    private fun generateHash(body: String, timestamp: LocalDateTime): String {
        val raw = "${body.trim()}|${timestamp}"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(raw.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    }

    private fun isSalaryCredit(body: String, type: TransactionType, amount: Double): Boolean {
        if (type != TransactionType.CREDIT) return false
        val salaryAccount = userPreferences.salaryAccountLast4
        if (salaryAccount.isBlank()) return false

        val isToSalaryAccount = body.contains(salaryAccount)
        val lower = body.lowercase()
        val hasSalaryKeywords = lower.containsAny("salary", "sal ", "neft cr", "credited")
        val minSalary = userPreferences.minSalaryAmount
        return isToSalaryAccount && hasSalaryKeywords && amount >= minSalary
    }

    private fun detectSelfTransfer(body: String): Boolean {
        val lower = body.lowercase()
        if (lower.containsAny("self", "own account", "self transfer", "self tfr")) return true

        // Credit card bill payments (money going to your own CC = not a real expense)
        if (lower.containsAny("credit card bill", "cc bill", "card payment", "cc payment",
                "towards credit card", "towards your card", "paid to.*credit card",
                "credit card.*payment")) {
            val userAccounts = userPreferences.userAccountNumbers
            // If any of user's account numbers appear in the SMS, it's paying own CC
            if (userAccounts.any { body.contains(it) }) return true
        }

        val userAccounts = userPreferences.userAccountNumbers
        if (userAccounts.isEmpty()) return false

        // If SMS mentions 2+ of user's accounts = transfer between own accounts
        val mentionedAccounts = userAccounts.count { body.contains(it) }
        if (mentionedAccounts >= 2) return true

        // If SMS says "credited to" or "debited from" another of user's accounts
        // Pattern: "credited to a/c no. XXXX1234" where 1234 is user's account
        val destinationPattern = Regex("""(?:credited\s+to|transferred\s+to|sent\s+to)\s+.*?(\d{4})""", RegexOption.IGNORE_CASE)
        val destinationMatch = destinationPattern.find(body)
        if (destinationMatch != null) {
            val destAccount = destinationMatch.groupValues[1]
            if (destAccount in userAccounts) return true
        }

        // Pattern: "debited from a/c no. XXXX1234" where both source and dest are user's
        val sourcePattern = Regex("""(?:debited\s+from|from\s+a/c).*?(\d{4})""", RegexOption.IGNORE_CASE)
        val sourceMatch = sourcePattern.find(body)
        if (sourceMatch != null) {
            val sourceAccount = sourceMatch.groupValues[1]
            if (sourceAccount in userAccounts && mentionedAccounts >= 1) return true
        }

        return false
    }

    private fun extractAmount(body: String): Double? {
        for (pattern in amountPatterns) {
            val match = pattern.find(body)
            if (match != null) {
                val amountStr = match.groupValues[1].replace(",", "")
                val amount = amountStr.toDoubleOrNull()
                if (amount != null && amount > 0) return amount
            }
        }
        return null
    }

    private fun determineTransactionType(body: String): TransactionType {
        val lowerBody = body.lowercase()

        // Check "is credited" and "is debited" — the definitive indicator for YOUR account
        val isCredited = lowerBody.indexOf("is credited")
        val isDebited = lowerBody.indexOf("is debited")

        if (isCredited >= 0 && isDebited >= 0) {
            return if (isCredited < isDebited) TransactionType.CREDIT else TransactionType.DEBIT
        }
        if (isDebited >= 0) return TransactionType.DEBIT
        if (isCredited >= 0) return TransactionType.CREDIT

        // "debited from your" or "debited from a/c" (at the start) = DEBIT
        if (Regex("""(?:debited|debit)\s+(?:from|for|by)""").containsMatchIn(lowerBody)) return TransactionType.DEBIT

        // "credited to your" or "credited to a/c" (at the start) = CREDIT
        if (Regex("""(?:credited|credit)\s+(?:to|for|into)""").containsMatchIn(lowerBody)) {
            // But "credited to a/c" AFTER a debit statement means it's the other person receiving
            // Only count as credit if it appears before any debit keyword
            val creditPos = Regex("""credited\s+(?:to|for|into)""").find(lowerBody)?.range?.first ?: Int.MAX_VALUE
            val debitPos = Regex("""debited|debit|spent|paid""").find(lowerBody)?.range?.first ?: Int.MAX_VALUE
            if (creditPos < debitPos) return TransactionType.CREDIT
        }

        // Clear credit keywords (standalone, not part of "credited to other account")
        if (lowerBody.contains("received")) return TransactionType.CREDIT
        if (lowerBody.contains("refund")) return TransactionType.CREDIT
        if (lowerBody.contains("cashback")) return TransactionType.CREDIT
        if (lowerBody.contains("reversed")) return TransactionType.CREDIT

        return TransactionType.DEBIT
    }

    private fun determinePaymentSource(body: String): PaymentSource {
        return when {
            upiIndicators.any { it.containsMatchIn(body) } -> PaymentSource.UPI
            creditCardIndicators.any { it.containsMatchIn(body) } -> PaymentSource.CREDIT_CARD
            ecsIndicators.any { it.containsMatchIn(body) } -> PaymentSource.ECS_NACH
            body.contains("debit card", ignoreCase = true) -> PaymentSource.DEBIT_CARD
            body.contains("net banking", ignoreCase = true) ||
                body.contains("NEFT", ignoreCase = true) ||
                body.contains("IMPS", ignoreCase = true) -> PaymentSource.NET_BANKING
            body.contains("wallet", ignoreCase = true) -> PaymentSource.WALLET
            else -> PaymentSource.UNKNOWN
        }
    }

    private fun extractMerchant(body: String): String {
        for (pattern in merchantExtractors) {
            val match = pattern.find(body)
            if (match != null) {
                val raw = match.groupValues[1].trim()
                if (raw.length >= 2) {
                    return normalizeMerchant(raw)
                }
            }
        }
        return "Unknown"
    }

    private fun normalizeMerchant(raw: String): String {
        var cleaned = raw
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""[*#]+"""), "")
            .replace(Regex("""^\d+\s*"""), "")
            // Remove common noise phrases that leak into merchant names
            .replace(Regex("""\s*Not you.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*Click here.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*Call\s+\d+.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*Dial\s+\d+.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*If not.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*Visit\s+http.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*https?://\S+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*Dear\s+(Customer|MR\w+).*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+for\s+INR.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+INR\s+[\d,]+.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+Rs\.?\s*[\d,]+.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+with\s+UMRN.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+on\s+\d{2}[-/].*""", RegexOption.IGNORE_CASE), "")
            .trim()

        // If it's a VPA-style name like "swiggy" or "zomatoorder", make it readable
        cleaned = vpaToReadableName(cleaned)

        return cleaned.replaceFirstChar { it.uppercase() }
    }

    private fun vpaToReadableName(vpa: String): String {
        // Map known VPA handles to proper names
        val knownVpas = mapOf(
            "swiggy" to "Swiggy",
            "zomato" to "Zomato",
            "zomatoorder" to "Zomato",
            "amazonpay" to "Amazon",
            "amazon" to "Amazon",
            "flipkart" to "Flipkart",
            "paytm" to "Paytm",
            "phonepe" to "PhonePe",
            "olacabs" to "Ola",
            "uber" to "Uber",
            "rapido" to "Rapido",
            "bigbasket" to "BigBasket",
            "blinkit" to "Blinkit",
            "zepto" to "Zepto",
            "netflix" to "Netflix",
            "spotify" to "Spotify",
            "hotstar" to "Hotstar",
            "jio" to "Jio",
            "airtel" to "Airtel",
            "gpay" to "Google Pay",
            "googlepay" to "Google Pay",
            "cred" to "CRED",
            "slice" to "Slice",
            "myntra" to "Myntra",
            "nykaa" to "Nykaa",
            "dunzo" to "Dunzo",
            "makemytrip" to "MakeMyTrip",
            "goibibo" to "Goibibo",
            "irctc" to "IRCTC",
            "bookmyshow" to "BookMyShow",
            "practo" to "Practo",
            "pharmeasy" to "PharmEasy",
            "1mg" to "1mg",
            "meesho" to "Meesho",
            "ajio" to "AJIO",
        )

        val lower = vpa.lowercase().replace(Regex("""[._-]"""), "")
        for ((key, name) in knownVpas) {
            if (lower.startsWith(key) || lower.contains(key)) {
                return name
            }
        }

        // If VPA looks like "merchantname123", strip trailing numbers
        return vpa.replace(Regex("""\d+$"""), "")
    }

    private fun extractAccountInfo(body: String): String {
        val accountPattern = Regex("""(?:A/c|Acct|Account|a/c)\s*(?:no\.?|#)?\s*[*xX]*(\d{4,})""", RegexOption.IGNORE_CASE)
        val cardPattern = Regex("""(?:card|CC)\s*(?:ending|no\.?|xx|XX)\s*(\d{4})""", RegexOption.IGNORE_CASE)

        accountPattern.find(body)?.let { return "A/c **${it.groupValues[1]}" }
        cardPattern.find(body)?.let { return "Card **${it.groupValues[1]}" }

        return ""
    }

    private fun categorize(merchant: String, body: String, source: PaymentSource): TransactionCategory {
        // Check known merchants database first (also checks SMS body as fallback)
        val knownCategory = KnownMerchants.categorize(merchant, body)
        if (knownCategory != null) return knownCategory

        val combined = "$merchant $body".lowercase()

        return when {
            source == PaymentSource.ECS_NACH && combined.containsAny("emi", "loan", "housing", "home loan", "car loan", "personal loan") ->
                TransactionCategory.EMI

            source == PaymentSource.ECS_NACH ->
                TransactionCategory.BILLS_UTILITIES

            combined.containsAny("swiggy", "zomato", "food", "restaurant", "cafe", "pizza", "burger", "biryani", "dominos", "mcdonalds", "kfc", "subway", "starbucks", "chaayos", "haldiram", "barbeque", "juice", "bakery", "tea ", "chai", "coffee", "ice cream", "snack", "hotel", "mess", "canteen", "tiffin", "dhaba") ->
                TransactionCategory.FOOD_DINING

            combined.containsAny("bigbasket", "blinkit", "zepto", "dmart", "grocery", "supermarket", "reliance fresh", "more megastore", "jiomart", "grofers", "nature basket", "spencer", "mart", "store", "provision", "kirana", "vegetables", "fruits", "spice", "organic", "fresh", "dairy", "milk", "general store", "departmental") ->
                TransactionCategory.GROCERIES

            combined.containsAny("uber", "ola", "rapido", "metro", "irctc", "redbus", "bus", "cab", "auto", "parking", "toll", "fastag") ->
                TransactionCategory.TRANSPORT

            combined.containsAny("amazon", "flipkart", "myntra", "meesho", "ajio", "nykaa", "shopping", "croma", "reliance digital", "vijay sales") ->
                TransactionCategory.SHOPPING

            combined.containsAny("electricity", "water", "gas", "broadband", "jio", "airtel", "vi ", "bsnl", "wifi", "dth", "tata play", "bill payment", "bescom", "torrent", "adani gas", "mahanagar gas", "insurance", "policy", "premium", "star health", "policy bazaar", "policybazaar", "digit insurance", "acko") ->
                TransactionCategory.BILLS_UTILITIES

            combined.containsAny("netflix", "hotstar", "prime video", "spotify", "youtube", "movie", "pvr", "inox", "bookmyshow", "zee5", "sonyliv", "mxplayer", "apple music") ->
                TransactionCategory.ENTERTAINMENT

            combined.containsAny("hospital", "pharmacy", "medical", "apollo", "practo", "1mg", "netmeds", "doctor", "lab", "diagnostic", "pharmeasy", "medplus", "fortis", "max health") ->
                TransactionCategory.HEALTH

            combined.containsAny("school", "college", "university", "course", "udemy", "unacademy", "byju", "tuition", "coaching", "upgrad", "coursera", "skillshare") ->
                TransactionCategory.EDUCATION

            combined.containsAny("sip", "mutual fund", "mf purchase", "groww", "zerodha", "kuvera", "coin", "ppf", "nps", "fixed deposit", "fd ", "recurring deposit", "rd ", "sbi mf", "hdfc mf", "icici pru", "axis mf", "nippon", "sbi life", "lic", "investment", "smallcase") ->
                TransactionCategory.SAVINGS

            combined.containsAny("petrol", "diesel", "fuel", "hp petrol", "hp pump", "iocl", "bpcl", "indian oil", "bharat petroleum", "shell", "nayara") ->
                TransactionCategory.FUEL

            combined.containsAny("makemytrip", "goibibo", "hotel", "flight", "oyo", "airbnb", "booking.com", "cleartrip", "yatra", "easemytrip", "ixigo") ->
                TransactionCategory.TRAVEL

            combined.containsAny("subscription", "renewal", "membership", "annual plan", "monthly plan") ->
                TransactionCategory.SUBSCRIPTION

            combined.containsAny("atm", "withdrawal", "cash withdrawal", "atm-cum") ->
                TransactionCategory.ATM_WITHDRAWAL

            combined.containsAny("emi", "loan", "bajaj finserv", "home credit") ->
                TransactionCategory.EMI

            combined.containsAny("transfer", "neft", "imps", "rtgs", "sent to", "paid to") && !combined.containsAny("swiggy", "zomato", "amazon", "flipkart") ->
                TransactionCategory.TRANSFER

            else -> TransactionCategory.OTHER
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it, ignoreCase = true) }
    }
}
