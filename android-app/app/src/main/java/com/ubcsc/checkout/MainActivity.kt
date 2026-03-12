package com.ubcsc.checkout

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.ubcsc.checkout.nfc.NfcReader
import com.ubcsc.checkout.ui.AppNavigation
import com.ubcsc.checkout.ui.theme.DigitalCheckoutTheme
import com.ubcsc.checkout.viewmodel.CheckoutViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: CheckoutViewModel by viewModels()

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var nfcPendingIntent: PendingIntent

    // USB HID keyboard-emulating card reader support
    private val usbCardBuffer = StringBuilder()
    private var lastKeyTime = 0L

    companion object {
        // FOB readers type at machine speed. Keystrokes this close together are treated
        // as FOB input (not passed to focused text fields, Enter fires card scan).
        private const val FOB_MAX_INTERVAL_MS = 80L
        private const val BUFFER_STALE_MS     = 2_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        nfcPendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        // Handle cold-start NFC intent
        intent?.let { handleNfcIntent(it) }

        setContent {
            DigitalCheckoutTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    AppNavigation(navController = navController, viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, nfcPendingIntent, null, null)
        startLockTaskIfOwner()
    }

    /**
     * Enter Lock Task Mode (kiosk) if this app is the device owner.
     * Has no effect during normal development — safe to leave in always.
     *
     * One-time ADB setup on the kiosk tablet:
     *   adb shell dpm set-device-owner com.ubcsc.checkout/.DeviceAdminReceiver
     */
    private fun startLockTaskIfOwner() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(packageName)) {
            val admin = ComponentName(this, DeviceAdminReceiver::class.java)
            dpm.setLockTaskPackages(admin, arrayOf(packageName))
            startLockTask()
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    // Called when a new NFC intent arrives while the app is in the foreground
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent) {
        val uid = NfcReader.getCardUid(intent) ?: return
        Log.d("NFC", "Card scanned: $uid")
        viewModel.onCardScanned(uid)
    }

    // Intercept keystrokes from a USB HID keyboard-emulating RFID/FOB reader.
    // The reader "types" the card number (e.g. "6157233") followed by Enter.
    //
    // FOB readers type at machine speed (< FOB_MAX_INTERVAL_MS between keystrokes).
    // Human typing is much slower. We use this timing to:
    //   1. Only consume Enter and fire onCardScanned when the sequence was machine-speed.
    //   2. Suppress rapid digits from leaking into focused text fields.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

        val now      = event.eventTime
        val interval = now - lastKeyTime

        // Discard stale buffer if idle for too long
        if (interval > BUFFER_STALE_MS && usbCardBuffer.isNotEmpty()) {
            Log.d("FOB", "Buffer cleared (stale)")
            usbCardBuffer.clear()
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                val uid = usbCardBuffer.toString().trim()
                usbCardBuffer.clear()
                // Only treat as a FOB scan if the last digit arrived at machine speed
                if (uid.length >= 4 && interval <= FOB_MAX_INTERVAL_MS) {
                    Log.d("FOB", "Card scanned via USB HID reader: $uid")
                    viewModel.onCardScanned(uid)
                    true  // consume Enter so it doesn't activate focused buttons
                } else {
                    super.dispatchKeyEvent(event)
                }
            }
            else -> {
                val char = event.unicodeChar.toChar()
                if (char.isLetterOrDigit()) {
                    val isMachineSpeed = usbCardBuffer.isNotEmpty() && interval < FOB_MAX_INTERVAL_MS
                    usbCardBuffer.append(char)
                    lastKeyTime = now
                    if (isMachineSpeed) {
                        // Rapid succession — FOB mode. Don't pass to focused text fields.
                        true
                    } else {
                        // First digit of a possible FOB sequence, or slow human typing.
                        // Pass through so text fields work normally.
                        super.dispatchKeyEvent(event)
                    }
                } else {
                    super.dispatchKeyEvent(event)
                }
            }
        }
    }
}
