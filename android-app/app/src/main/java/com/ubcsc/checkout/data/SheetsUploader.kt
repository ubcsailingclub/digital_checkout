package com.ubcsc.checkout.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Posts checkout/checkin/damage events to a deployed Google Apps Script web app,
 * which appends/updates rows in the club's Google Sheet.
 *
 * All calls are fire-and-forget — a failure never blocks the checkout flow.
 */
object SheetsUploader {

    private val client  = OkHttpClient()
    private val JSON    = "application/json".toMediaType()
    private val zone    = ZoneId.systemDefault()
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)

    /** Called immediately after a new checkout session is created. */
    suspend fun logCheckout(
        scriptUrl:     String,
        sessionId:     Int,
        skipper:       String,
        crew:          List<String>,
        craft:         String,
        code:          String,
        checkoutEpoch: Long,
        etaEpoch:      Long?
    ) {
        post(scriptUrl, JSONObject().apply {
            put("type",          "checkout")
            put("sessionId",     sessionId)
            put("skipper",       skipper)
            put("craft",         craft)
            put("code",          code)
            put("partySize",     1 + crew.size)
            put("checkoutEpoch", checkoutEpoch)
            put("etaEpoch",      etaEpoch ?: 0)
        })
    }

    /** Called when a session is checked in. */
    suspend fun logCheckin(
        scriptUrl:     String,
        sessionId:     Int,
        notes:         String?,
        hasDamage:     Boolean,
        checkinEpoch:  Long,
        checkoutEpoch: Long
    ) {
        val durationMin = ((checkinEpoch - checkoutEpoch) / 60_000).coerceAtLeast(0)
        post(scriptUrl, JSONObject().apply {
            put("type",        "checkin")
            put("sessionId",   sessionId)
            put("checkinEpoch", checkinEpoch)
            put("durationMin", durationMin)
            put("notes",       notes ?: "")
            put("damage",      hasDamage)
        })
    }

    /** Called when a checkin includes a damage report. */
    suspend fun logDamage(
        scriptUrl: String,
        sessionId: Int,
        craft:     String,
        skipper:   String,
        notes:     String
    ) {
        post(scriptUrl, JSONObject().apply {
            put("type",      "damage")
            put("sessionId", sessionId)
            put("craft",     craft)
            put("skipper",   skipper)
            put("notes",     notes)
        })
    }

    private suspend fun post(url: String, body: JSONObject) {
        if (url.isBlank()) return
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .post(body.toString().toRequestBody(JSON))
                    .build()
                client.newCall(req).execute().close()
            } catch (_: Exception) {
                // Intentionally swallowed — sheet upload must never block checkout
            }
        }
    }
}
