package com.ubcsc.checkout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Automatically relaunches MainActivity after the app updates itself via OTA.
 * Without this, Android would leave the kiosk stuck on the "App installed" screen.
 */
class UpdateRelaunchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
    }
}
