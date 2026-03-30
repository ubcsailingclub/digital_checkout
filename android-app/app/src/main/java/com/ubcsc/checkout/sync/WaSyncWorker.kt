package com.ubcsc.checkout.sync

import android.content.Context
import android.util.Base64
import com.ubcsc.checkout.data.KioskPreferences
import com.ubcsc.checkout.data.db.AppDatabase
import com.ubcsc.checkout.data.db.normalizeCardUid
import com.ubcsc.checkout.data.db.entities.MemberCardEntity
import com.ubcsc.checkout.data.db.entities.MemberEntity
import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val ALLOWED_MEMBERSHIP_LEVELS = setOf("UBC Student", "General Member")
private const val WA_OAUTH_URL  = "https://oauth.wildapricot.org/auth/token"
private const val WA_API_BASE   = "https://api.wildapricot.org/v2.2"
private const val RATE_DELAY_MS = 600L   // 100 req/min — under WA's 120/min cap

/**
 * Kotlin port of sync_members.py.
 * Fetches active UBC Student / General Member contacts from Wild Apricot
 * and upserts them into the local Room database.
 */
class WaSyncWorker(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val certMap: Map<String, List<String>> by lazy { loadCertMap() }

    suspend fun sync() {
        val prefs = KioskPreferences(context)
        check(prefs.isWaConfigured) { "WA credentials not configured" }

        val token     = fetchToken(prefs.waApiKey)
        val accountId = prefs.waAccountId

        val contacts  = fetchAllContacts(token, accountId)
        val active    = contacts.filter { c ->
            c.optString("Status").equals("Active", ignoreCase = true) &&
            (c.optJSONObject("MembershipLevel")?.optString("Name") ?: "") in ALLOWED_MEMBERSHIP_LEVELS
        }

        val db          = AppDatabase.get(context)
        val allMemberIds = mutableListOf<Int>()
        val batch        = mutableListOf<MemberEntity>()
        val batchCards   = mutableListOf<MemberCardEntity>()

        for (contact in active) {
            val waId = contact.getInt("Id")
            delay(RATE_DELAY_MS)
            val full = fetchContact(token, accountId, waId)

            val firstName = (full.optString("FirstName") ?: "").trim()
            val lastName  = (full.optString("LastName")  ?: "").trim()
            val fullName  = "$firstName $lastName".trim().ifEmpty { "WA#$waId" }

            val cardUidRaw = extractField(full, "custom-11866950")
            val groupNames = extractGroups(full)
            val certs      = groupNames.flatMap { certMap[it] ?: emptyList() }.distinct()
            val certsJson  = if (certs.isEmpty()) null else JSONArray(certs).toString()

            batch += MemberEntity(
                id                 = waId,
                waContactId        = waId,
                fullName           = fullName,
                firstName          = firstName.ifBlank { null },
                lastName           = lastName.ifBlank { null },
                membershipStatus   = "Active",
                isActive           = true,
                certificationsJson = certsJson
            )
            allMemberIds += waId

            if (!cardUidRaw.isNullOrBlank()) {
                runCatching {
                    batchCards += MemberCardEntity(
                        memberId          = waId,
                        cardUidNormalized = normalizeCardUid(cardUidRaw),
                        isActive          = true
                    )
                }
            }

            // Flush to DB every 50 members so partial syncs are useful
            if (batch.size >= 50) {
                db.memberDao().upsertAll(batch)
                db.memberDao().upsertCards(batchCards)
                batch.clear()
                batchCards.clear()
            }
        }

        // Final flush
        if (batch.isNotEmpty()) {
            db.memberDao().upsertAll(batch)
            db.memberDao().upsertCards(batchCards)
        }

        // Deactivate members no longer in WA
        if (allMemberIds.isNotEmpty()) {
            db.memberDao().deactivateMissing(allMemberIds)
        }
    }

    // -----------------------------------------------------------------------
    // WA API helpers
    // -----------------------------------------------------------------------

    private fun fetchToken(apiKey: String): String {
        val credentials = Base64.encodeToString(
            "APIKEY:$apiKey".toByteArray(), Base64.NO_WRAP
        )
        val body = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("scope", "auto")
            .build()
        val req = Request.Builder()
            .url(WA_OAUTH_URL)
            .addHeader("Authorization", "Basic $credentials")
            .post(body)
            .build()
        val json = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("OAuth failed: ${resp.code}")
            JSONObject(resp.body?.string() ?: error("Empty OAuth response"))
        }
        return json.getString("access_token")
    }

    private fun fetchAllContacts(token: String, accountId: String): List<JSONObject> {
        val results = mutableListOf<JSONObject>()
        var skip = 0
        val top  = 100
        while (true) {
            val url = "$WA_API_BASE/Accounts/$accountId/Contacts" +
                      "?\$top=$top&\$skip=$skip&\$async=false" +
                      "&\$filter=Status+eq+%27Active%27"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .build()
            val page = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Contacts fetch failed: ${resp.code}")
                JSONObject(resp.body?.string() ?: error("Empty contacts response"))
            }
            val contacts = page.optJSONArray("Contacts") ?: break
            for (i in 0 until contacts.length()) results += contacts.getJSONObject(i)
            if (contacts.length() < top) break
            skip += top
        }
        return results
    }

    private fun fetchContact(token: String, accountId: String, contactId: Int): JSONObject {
        val req = Request.Builder()
            .url("$WA_API_BASE/Accounts/$accountId/Contacts/$contactId")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Contact $contactId fetch failed: ${resp.code}")
            JSONObject(resp.body?.string() ?: error("Empty response"))
        }
    }

    private fun extractField(contact: JSONObject, systemCode: String): String? {
        val fields = contact.optJSONArray("FieldValues") ?: return null
        for (i in 0 until fields.length()) {
            val f = fields.getJSONObject(i)
            if (f.optString("SystemCode") == systemCode) {
                return f.optString("Value").ifBlank { null }
            }
        }
        return null
    }

    private fun extractGroups(contact: JSONObject): List<String> {
        val fields = contact.optJSONArray("FieldValues") ?: return emptyList()
        for (i in 0 until fields.length()) {
            val f = fields.getJSONObject(i)
            if (f.optString("SystemCode") == "Groups") {
                val arr = f.optJSONArray("Value") ?: return emptyList()
                return (0 until arr.length()).mapNotNull { j ->
                    arr.optJSONObject(j)?.optString("Label")?.ifBlank { null }
                }
            }
        }
        return emptyList()
    }

    private fun loadCertMap(): Map<String, List<String>> {
        val json = context.assets.open("cert_map.json").bufferedReader().readText()
        val obj  = JSONObject(json)
        return obj.keys().asSequence().associateWith { key ->
            val arr = obj.getJSONArray(key)
            (0 until arr.length()).map { arr.getString(it) }
        }
    }
}
