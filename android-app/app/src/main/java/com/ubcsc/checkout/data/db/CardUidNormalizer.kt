package com.ubcsc.checkout.data.db

/**
 * Matches backend normalize_card_uid():
 *  - Pure decimal string → convert to hex (no leading zeros)
 *  - Hex string → strip leading zeros
 *  - "61699", "0000F103", "F103" all → "F103"
 */
fun normalizeCardUid(raw: String): String {
    val s = raw.trim().uppercase()
    return if (s.all { it.isDigit() }) {
        s.toLong().toString(16).uppercase()
    } else {
        s.trimStart('0').ifEmpty { "0" }
    }
}
