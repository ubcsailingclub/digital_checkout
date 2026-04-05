package com.ubcsc.checkout.ui.result

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ubcsc.checkout.R
import com.ubcsc.checkout.ui.theme.AvailableGreen
import com.ubcsc.checkout.ui.theme.DeepOcean
import com.ubcsc.checkout.ui.theme.DigitalCheckoutTheme
import com.ubcsc.checkout.ui.theme.LocalKioskColors
import com.ubcsc.checkout.ui.theme.UnavailableRed
import com.ubcsc.checkout.viewmodel.CheckoutUiState
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import kotlinx.coroutines.delay

private const val AUTO_DISMISS_MS = 4_000L

@Composable
fun ResultScreen(uiState: CheckoutUiState, viewModel: CheckoutViewModel) {
    LaunchedEffect(Unit) {
        delay(AUTO_DISMISS_MS)
        viewModel.resetToIdle()
    }
    when (uiState) {
        is CheckoutUiState.Success ->
            ResultContent(
                headline  = if (uiState.isCheckout)
                    stringResource(R.string.result_success_checkout)
                else
                    stringResource(R.string.result_success_checkin),
                detail    = uiState.message,
                isSuccess = true
            )
        is CheckoutUiState.Error ->
            ResultContent(
                headline  = stringResource(R.string.result_error_title),
                detail    = uiState.message,
                isSuccess = false
            )
        else -> Unit
    }
}

@Composable
private fun ResultContent(headline: String, detail: String, isSuccess: Boolean) {
    val accentColor = if (isSuccess) AvailableGreen else UnavailableRed
    val icon        = if (isSuccess) Icons.Filled.Check else Icons.Filled.Close

    // Icon pop-in
    val iconScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        iconScale.animateTo(1.1f, tween(320))
        iconScale.animateTo(1f,   tween(120))
    }

    // Countdown progress bar
    var progress by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        val steps = 80
        val stepDelay = AUTO_DISMISS_MS / steps
        for (i in 1..steps) {
            delay(stepDelay)
            progress = 1f - (i.toFloat() / steps)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepOcean),
        contentAlignment = Alignment.Center
    ) {
        // Radial glow behind icon
        Box(
            modifier = Modifier
                .size(320.dp)
                .background(
                    Brush.radialGradient(
                        listOf(accentColor.copy(alpha = 0.12f), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(48.dp)
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .scale(iconScale.value)
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector  = icon,
                    contentDescription = null,
                    tint         = accentColor,
                    modifier     = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text       = headline,
                style      = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                textAlign  = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text  = detail,
                style = MaterialTheme.typography.titleMedium,
                color = LocalKioskColors.current.textWarm,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text  = stringResource(R.string.result_returning),
                style = MaterialTheme.typography.bodySmall,
                color = LocalKioskColors.current.textWarm.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Countdown progress bar
            LinearProgressIndicator(
                progress          = { progress },
                modifier          = Modifier
                    .fillMaxWidth(0.35f)
                    .height(3.dp),
                color             = accentColor,
                trackColor        = accentColor.copy(alpha = 0.15f),
                strokeCap         = StrokeCap.Round
            )
        }
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun SuccessPreview() {
    DigitalCheckoutTheme {
        ResultContent("Enjoy your sail!", "Checked out Laser #1 for Alex Sailor", true)
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun ErrorPreview() {
    DigitalCheckoutTheme {
        ResultContent("Something went wrong", "Card not recognized. Please contact an exec.", false)
    }
}
