package com.ubcsc.checkout.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ubcsc.checkout.BuildConfig

/**
 * Encrypted storage for sensitive configuration:
 *  - Wild Apricot API key + account ID (for direct WA sync)
 *  - Pi Tailscale URL (for fleet status sync)
 */
class KioskPreferences(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "kiosk_secure_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var waApiKey: String
        get() = prefs.getString(KEY_WA_API_KEY, "") ?: ""
        set(v) = prefs.edit().putString(KEY_WA_API_KEY, v).apply()

    var waAccountId: String
        get() = prefs.getString(KEY_WA_ACCOUNT_ID, "") ?: ""
        set(v) = prefs.edit().putString(KEY_WA_ACCOUNT_ID, v).apply()

    /** Tailscale URL of the Pi, e.g. "https://100.x.x.x" or "https://pi.tail-xxxx.ts.net" */
    var piSyncUrl: String
        get() = prefs.getString(KEY_PI_SYNC_URL, "") ?: ""
        set(v) = prefs.edit().putString(KEY_PI_SYNC_URL, v.trimEnd('/')).apply()

    /** Deployed Google Apps Script web app URL for logging checkouts to Google Sheets */
    var sheetsScriptUrl: String
        get() = prefs.getString(KEY_SHEETS_SCRIPT_URL, BuildConfig.SHEETS_SCRIPT_URL) ?: BuildConfig.SHEETS_SCRIPT_URL
        set(v) = prefs.edit().putString(KEY_SHEETS_SCRIPT_URL, v.trim()).apply()

    val isWaConfigured     get() = waApiKey.isNotBlank() && waAccountId.isNotBlank()
    val isPiConfigured     get() = piSyncUrl.isNotBlank()
    val isSheetsConfigured get() = sheetsScriptUrl.isNotBlank()

    private companion object {
        const val KEY_WA_API_KEY         = "wa_api_key"
        const val KEY_WA_ACCOUNT_ID      = "wa_account_id"
        const val KEY_PI_SYNC_URL        = "pi_sync_url"
        const val KEY_SHEETS_SCRIPT_URL  = "sheets_script_url"
    }
}
