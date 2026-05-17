package com.ubcsc.checkout.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ubcsc.checkout.BuildConfig
import com.ubcsc.checkout.data.KioskPreferences
import com.ubcsc.checkout.data.db.AppDatabase
import com.ubcsc.checkout.data.db.entities.CraftEntity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "SyncWorker"

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result {
        val prefs = KioskPreferences(applicationContext)
        val db    = AppDatabase.get(applicationContext)

        // Seed craft from assets on first install (no network needed)
        seedCraftIfEmpty(db)

        // Sync members directly from Wild Apricot
        val memberSuccess = if (prefs.isWaConfigured) {
            runCatching { WaSyncWorker(applicationContext).sync() }
                .onFailure { Log.w(TAG, "WA member sync failed: ${it.message}") }
                .isSuccess
        } else {
            Log.w(TAG, "WA credentials not configured — skipping member sync")
            true  // not configured yet, not a failure
        }

        // Sync fleet status from Pi over Tailscale — failure is non-critical
        if (prefs.isPiConfigured) {
            runCatching { syncFleet(db, prefs.piSyncUrl) }
                .onFailure { Log.w(TAG, "Pi fleet sync unavailable: ${it.message}") }
            // Always continue regardless — app works with last known fleet status
        }

        return if (memberSuccess) Result.success()
               else if (runAttemptCount < 3) Result.retry()
               else Result.failure()
    }

    private suspend fun seedCraftIfEmpty(db: AppDatabase) {
        if (db.craftDao().getAll().isNotEmpty()) return
        try {
            val json    = applicationContext.assets.open("fleet.json").bufferedReader().readText()
            val fleets  = JSONObject(json).getJSONArray("fleets")
            val crafts  = mutableListOf<CraftEntity>()
            var id      = 1
            for (fi in 0 until fleets.length()) {
                val fleet = fleets.getJSONObject(fi)
                val cls   = fleet.getString("class")
                val boats = fleet.getJSONArray("boats")
                for (bi in 0 until boats.length()) {
                    val boat = boats.getJSONObject(bi)
                    crafts += CraftEntity(
                        id               = id++,
                        craftCode        = boat.getString("code"),
                        displayName      = boat.getString("name"),
                        fleetType        = cls,
                        craftClass       = cls,
                        capacity         = null,
                        isActive         = true,
                        requiresCheckout = true,
                        status           = "available",
                        statusReason     = null
                    )
                }
            }
            db.craftDao().upsertAll(crafts)
            Log.i(TAG, "Seeded ${crafts.size} craft from fleet.json")
        } catch (e: Exception) {
            Log.w(TAG, "Could not seed craft from assets: ${e.message}")
        }
    }

    private suspend fun syncFleet(db: AppDatabase, piBaseUrl: String) {
        val req = Request.Builder()
            .url("$piBaseUrl/api/v1/sync/fleet")
            .addHeader("X-Kiosk-Key", BuildConfig.KIOSK_API_KEY)
            .build()
        val body = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Fleet sync HTTP ${resp.code}")
                resp.body?.string() ?: error("Empty fleet response")
            }
        }
        val array  = JSONObject(body).getJSONArray("craft")
        val crafts = (0 until array.length()).map { i ->
            val c = array.getJSONObject(i)
            CraftEntity(
                id               = c.getInt("id"),
                craftCode        = c.getString("craft_code"),
                displayName      = c.getString("display_name"),
                fleetType        = c.getString("fleet_type"),
                craftClass       = c.optString("craft_class").ifBlank { null },
                capacity         = if (c.isNull("capacity")) null else c.getInt("capacity"),
                isActive         = c.optBoolean("is_active", true),
                requiresCheckout = c.optBoolean("requires_checkout", true),
                status           = c.optString("status", "available"),
                statusReason     = c.optString("status_reason").ifBlank { null }
            )
        }
        db.craftDao().upsertAll(crafts)
    }

    companion object {
        private const val WORK_NAME = "hourly_sync"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).build()
            )
        }

        const val SYNC_NOW_NAME = "sync_now"

        fun syncNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                SYNC_NOW_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().build()
            )
        }
    }
}
