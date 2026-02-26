package com.ubcsc.checkout

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Required for Android Lock Task Mode (kiosk).
 *
 * After installing the app, run once via ADB to set it as device owner:
 *   adb shell dpm set-device-owner com.ubcsc.checkout/.DeviceAdminReceiver
 *
 * Once set, the app will call startLockTask() on resume and the device
 * cannot leave the app without going through Settings or ADB.
 *
 * To remove device owner (e.g. for development):
 *   adb shell dpm remove-active-admin com.ubcsc.checkout/.DeviceAdminReceiver
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) = Unit
    override fun onDisabled(context: Context, intent: Intent) = Unit
}
