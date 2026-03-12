package com.ubcsc.checkout.ui.util

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Forces the soft (on-screen) keyboard to appear when this composable gains focus,
 * even when a hardware keyboard (e.g. USB FOB reader) is connected.
 *
 * Uses both the Compose keyboard controller and the legacy InputMethodManager with
 * SHOW_FORCED so it works on Fire OS (Android 11) with external USB keyboards.
 */
@Suppress("DEPRECATION")   // showSoftInput(view, SHOW_FORCED) needed for hardware-keyboard suppression
fun Modifier.forceShowSoftKeyboard(): Modifier = composed {
    val context           = LocalContext.current
    val view              = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope             = rememberCoroutineScope()

    onFocusChanged { state ->
        if (state.isFocused) {
            scope.launch {
                delay(80)   // brief delay so the view is fully laid out
                keyboardController?.show()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
            }
        }
    }
}
