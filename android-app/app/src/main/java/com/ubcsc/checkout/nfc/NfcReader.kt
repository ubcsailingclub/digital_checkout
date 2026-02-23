package com.ubcsc.checkout.nfc

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.content.Intent

/**
 * Extracts a normalized hex UID string from an NFC tag intent.
 *
 * The UID is returned as an uppercase hex string with no separators,
 * e.g. "04A3B2C1D0E5F6". This matches the card_uid_normalized format
 * stored in the backend.
 */
object NfcReader {

    /**
     * Returns the card UID from an NFC intent, or null if the intent
     * doesn't carry a valid NFC tag.
     */
    fun getCardUid(intent: Intent): String? {
        val action = intent.action ?: return null
        if (action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED
        ) return null

        val tag: Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return null
        return tag.id.toHexString()
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { byte -> "%02X".format(byte) }
}
