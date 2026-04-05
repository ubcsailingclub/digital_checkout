package com.ubcsc.checkout.ui.checkin

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubcsc.checkout.ui.theme.DeepOcean
import com.ubcsc.checkout.ui.theme.DigitalCheckoutTheme
import com.ubcsc.checkout.ui.theme.LocalKioskColors
import com.ubcsc.checkout.ui.theme.OceanSurface
import com.ubcsc.checkout.ui.theme.TextMuted
import com.ubcsc.checkout.viewmodel.ActiveSession
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import kotlinx.coroutines.delay

private const val INACTIVITY_TIMEOUT_MS = 60_000L

@Composable
fun AwaitingCheckinCardScreen(session: ActiveSession, viewModel: CheckoutViewModel) {
    LaunchedEffect(Unit) {
        delay(INACTIVITY_TIMEOUT_MS)
        viewModel.resetToIdle()
    }
    AwaitingCheckinCardContent(
        craftName = session.craftName,
        onBack    = { viewModel.goBack() }
    )
}

@Composable
private fun AwaitingCheckinCardContent(
    craftName: String,
    onBack:    () -> Unit
) {
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
    val iconPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "icon_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(OceanSurface, DeepOcean), radius = 900f)),
        contentAlignment = Alignment.Center
    ) {
        // Top accent bar
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(4.dp)
                .background(Brush.horizontalGradient(listOf(LocalKioskColors.current.accentMid, LocalKioskColors.current.accent, LocalKioskColors.current.accentMid)))
        )

        // Ripple rings drawn behind content
        Canvas(Modifier.fillMaxSize()) {
            val center    = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension * 0.35f
            fun ring(p: Float) = drawCircle(
                color  = LocalKioskColors.current.accentMid.copy(alpha = (1f - p) * 0.35f),
                radius = maxRadius * p,
                center = center,
                style  = Stroke(width = 3.dp.toPx())
            )
            ring(ring1); ring(ring2); ring(ring3)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            Text(
                text          = "CHECKING IN",
                style         = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 4.sp,
                    fontSize      = 11.sp
                ),
                color      = LocalKioskColors.current.accent,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text       = craftName,
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                textAlign  = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            Icon(
                imageVector        = Icons.Filled.Nfc,
                contentDescription = "Card reader",
                tint               = Color.White,
                modifier           = Modifier.size((72 * iconPulse).dp)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text      = "Scan your Jericho card or card reader to authorize",
                style     = MaterialTheme.typography.bodyLarge,
                color     = LocalKioskColors.current.textWarm,
                textAlign = TextAlign.Center
            )
        }

        // Back button — bottom left
        TextButton(
            onClick  = onBack,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text("← Back", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun AwaitingCheckinCardPreview() {
    DigitalCheckoutTheme {
        AwaitingCheckinCardContent(craftName = "Laser #3", onBack = {})
    }
}
