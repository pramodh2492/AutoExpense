package com.expensetracker.data.local

/**
 * Produces a stable key for a merchant so that name variants of the SAME shop collapse
 * to one entry. This is what lets a single "Change category" correction generalize across
 * all the ways a merchant shows up in SMS (e.g. "SWIGGY", "Swiggy Ltd", "swiggy*order123").
 *
 * Intentionally conservative — it must NOT merge genuinely different merchants.
 */
object MerchantKey {

    // Corporate suffixes that don't distinguish a merchant.
    private val suffixes = listOf(
        "private limited", "pvt ltd", "pvt. ltd", "pvt", "ltd", "limited",
        "llp", "inc", "co", "and co"
    )

    fun normalize(rawMerchant: String): String {
        var s = rawMerchant.lowercase().trim()

        // Cut UPI/reference markers and everything after them: "swiggy*order123" -> "swiggy".
        for (marker in listOf("*", "#", "@")) {
            val idx = s.indexOf(marker)
            if (idx > 0) s = s.substring(0, idx)
        }

        s = s
            .replace("&", " and ")
            .replace(Regex("""[._\-/]+"""), " ")   // punctuation used as separators
            .replace(Regex("""\bn\b"""), "and")     // "nuts n spices" -> "nuts and spices"
            .replace(Regex("""\d+"""), " ")          // drop digits (order ids, store numbers)
            .replace(Regex("""\s+"""), " ")
            .trim()

        // Strip a trailing corporate suffix (only at the end, so we don't gut real names).
        for (suffix in suffixes) {
            if (s.endsWith(" $suffix")) {
                s = s.removeSuffix(" $suffix").trim()
                break
            }
        }

        return s.ifBlank { rawMerchant.lowercase().trim() }
    }

    /** Key that also distinguishes by transaction type, e.g. "amazon|CREDIT" vs "amazon|DEBIT". */
    fun typeKey(rawMerchant: String, type: String): String = "${normalize(rawMerchant)}|$type"
}
