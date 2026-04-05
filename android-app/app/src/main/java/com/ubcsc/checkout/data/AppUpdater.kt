package com.ubcsc.checkout.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import com.ubcsc.checkout.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private const val REPO = "ubcsailingclub/digital_checkout"
private const val INSTALL_ACTION = "com.ubcsc.checkout.INSTALL_RESULT"

data class UpdateInfo(val versionCode: Int, val downloadUrl: String)

object AppUpdater {

    /** Returns update info if a newer release exists on GitHub, null otherwise. */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val json = java.net.URL("https://api.github.com/repos/$REPO/releases/latest").readText()
            val obj  = JSONObject(json)
            val tag  = obj.getString("tag_name")          // "v42"
            val remote = tag.trimStart('v').toIntOrNull() ?: return@withContext null
            if (remote <= BuildConfig.VERSION_CODE) return@withContext null

            val assets = obj.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    return@withContext UpdateInfo(remote, asset.getString("browser_download_url"))
                }
            }
            null
        } catch (_: Exception) { null }
    }

    /** Downloads the APK to cache, reporting [0..100] progress. Returns the file or null on failure. */
    suspend fun downloadApk(
        context:    Context,
        url:        String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val conn  = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connect()
            val total = conn.contentLength
            val file  = File(context.cacheDir, "update.apk")
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var downloaded = 0L
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) onProgress((downloaded * 100 / total).toInt())
                    }
                }
            }
            file
        } catch (_: Exception) { null }
    }

    /**
     * Installs [apkFile] via PackageInstaller.
     *
     * If the app is device owner the install is silent — no user prompt.
     * If not, Android falls back to showing the system installer dialog.
     *
     * [onResult] is called with true on success, false on failure.
     */
    fun installApk(context: Context, apkFile: File, onResult: (Boolean) -> Unit) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)

        // Register receiver before committing so we never miss the callback
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                ctx.unregisterReceiver(this)
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
                when (status) {
                    PackageInstaller.STATUS_SUCCESS -> onResult(true)
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        // Device is not device-owner — show system installer dialog
                        @Suppress("DEPRECATION")
                        val confirmIntent = if (Build.VERSION.SDK_INT >= 33)
                            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        else
                            intent.getParcelableExtra(Intent.EXTRA_INTENT)
                        confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ?.let { ctx.startActivity(it) }
                        onResult(true)
                    }
                    else -> onResult(false)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, IntentFilter(INSTALL_ACTION), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(INSTALL_ACTION))
        }

        try {
            packageInstaller.openSession(sessionId).use { session ->
                session.openWrite("app", 0, apkFile.length()).use { out ->
                    apkFile.inputStream().copyTo(out)
                    session.fsync(out)
                }
                val pi = android.app.PendingIntent.getBroadcast(
                    context, sessionId,
                    Intent(INSTALL_ACTION),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                )
                session.commit(pi.intentSender)
            }
        } catch (e: Exception) {
            context.unregisterReceiver(receiver)
            onResult(false)
        }
    }
}
