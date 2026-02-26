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

    // Intercept keystrokes from a USB HID keyboard-emulating RFID reader.
    // The reader "types" the card number followed by Enter. We buffer the
    // digits and fire onCardScanned when Enter is received.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

        val now = event.eventTime

        // Discard stale buffer if more than 2 seconds have passed since the last keypress
        if (now - lastKeyTime > 2000L && usbCardBuffer.isNotEmpty()) {
            Log.d("NFC", "USB buffer cleared (stale)")
            usbCardBuffer.clear()
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                val uid = usbCardBuffer.toString().trim()
                usbCardBuffer.clear()
                if (uid.length >= 4) {
                    Log.d("NFC", "Card scanned via USB HID reader: $uid")
                    viewModel.onCardScanned(uid)
                    true  // consume the Enter so it doesn't activate focused buttons
                } else {
                    super.dispatchKeyEvent(event)
                }
            }
            else -> {
                val char = event.unicodeChar.toChar()
                if (char.isLetterOrDigit()) {
                    usbCardBuffer.append(char)
                    lastKeyTime = now
                }
                super.dispatchKeyEvent(event)
            }
        }
    }
}
