package com.ubcsc.checkout.ui.idle

import android.nfc.NfcAdapter
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubcsc.checkout.R
import com.ubcsc.checkout.ui.theme.DeepOcean
import com.ubcsc.checkout.ui.theme.DigitalCheckoutTheme
import com.ubcsc.checkout.ui.theme.OceanSurface
import com.ubcsc.checkout.ui.theme.TealLight
import com.ubcsc.checkout.ui.theme.TealMid
import com.ubcsc.checkout.ui.theme.TextSecondary
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun IdleScreen(viewModel: CheckoutViewModel) {
    val context = LocalContext.current
    val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
    val nfcWarning = when {
        nfcAdapter == null -> stringResource(R.string.idle_nfc_unavailable)
        !nfcAdapter.isEnabled -> stringResource(R.string.idle_nfc_disabled)
        else -> null
    }
    IdleContent(nfcWarning = nfcWarning)
}

@Composable
private fun IdleContent(nfcWarning: String?) {
    // Three ripple rings with staggered offsets
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val rippleDuration = 2400

    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(rippleDuration, easing = LinearEasing)),
        label = "ring1"
    )
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(rippleDuration, 800, easing = LinearEasing)),
        label = "ring2"
    )
    val ring3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(rippleDuration, 1600, easing = LinearEasing)),
        label = "ring3"
    )

    // Icon gentle pulse
    val iconPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "icon_pulse"
    )

    // Live clock
    var timeText by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            timeText = currentTime()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(OceanSurface, DeepOcean),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Ripple rings drawn behind everything
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension * 0.48f

            fun drawRing(progress: Float) {
                val radius = maxRadius * progress
                val alpha = (1f - progress) * 0.35f
                drawCircle(
                    color = TealMid.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                )
            }
            drawRing(ring1)
            drawRing(ring2)
            drawRing(ring3)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 60.dp)
        ) {
            // Club name
            Text(
                text = "UBC SAILING CLUB",
                style = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 6.sp,
                    fontSize = 14.sp
                ),
                color = TealLight,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(28.dp))

            // NFC icon with pulse scale
            Box(contentAlignment = Alignment.Center) {
                // Soft glow behind icon
                Canvas(modifier = Modifier.size(140.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(TealMid.copy(alpha = 0.18f), Color.Transparent)
                        )
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Nfc,
                    contentDescription = "NFC",
                    tint = Color.White,
                    modifier = Modifier.size((96 * iconPulse).dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (nfcWarning != null) {
                Text(
                    text = nfcWarning,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFFFB300),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "Tap your membership card\nto get started",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Light,
                    color = Color.White.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center,
                    lineHeight = 40.sp
                )
            }
        }

        // Clock in bottom-right corner
        Text(
            text = timeText,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        )
    }
}

private fun currentTime(): String =
    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

@Preview(widthDp = 960, heightDp = 600, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun IdlePreview() {
    DigitalCheckoutTheme { IdleContent(nfcWarning = null) }
}
