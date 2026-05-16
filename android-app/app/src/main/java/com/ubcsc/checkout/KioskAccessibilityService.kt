package com.ubcsc.checkout

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class KioskAccessibilityService : AccessibilityService() {

    private var overlay: View? = null
    private var wm: WindowManager? = null
    private var isSnapping = false

    companion object {
        // True while this service is connected. MainActivity checks this to avoid
        // running its own onUserLeaveHint snap-back, which would double-trigger.
        @Volatile var isConnected = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        isConnected = true
    }

    override fun onDestroy() {
        super.onDestroy()
        isConnected = false
        removeOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == packageName || pkg == "com.android.systemui" || pkg == "android") {
            isSnapping = false
            removeOverlay()
            return
        }

        if (MainActivity.suppressReopen || isSnapping) return

        isSnapping = true
        showOverlay()
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        })
    }

    private fun showOverlay() {
        if (overlay != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        )
        val view = View(this).apply { setBackgroundColor(0xFF141618.toInt()) }
        try {
            wm?.addView(view, params)
            overlay = view
        } catch (_: Exception) {}
    }

    private fun removeOverlay() {
        overlay?.let {
            try { wm?.removeView(it) } catch (_: Exception) {}
            overlay = null
        }
    }

    override fun onInterrupt() {}
}
