package com.ubcsc.checkout.ui.damage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubcsc.checkout.ui.theme.ActiveAmber
import com.ubcsc.checkout.ui.theme.AvailableGreen
import com.ubcsc.checkout.ui.theme.CardBlue
import com.ubcsc.checkout.ui.theme.DeepOcean
import com.ubcsc.checkout.ui.theme.DigitalCheckoutTheme
import com.ubcsc.checkout.ui.theme.DividerColor
import com.ubcsc.checkout.ui.theme.TealMid
import com.ubcsc.checkout.ui.theme.TextMuted
import com.ubcsc.checkout.ui.theme.TextSecondary
import com.ubcsc.checkout.viewmodel.ActiveCheckout
import com.ubcsc.checkout.viewmodel.CheckoutUiState
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import com.ubcsc.checkout.viewmodel.Member
import kotlinx.coroutines.delay

private const val AUTO_RESET_MS = 60_000L

@Composable
fun DamageReportScreen(uiState: CheckoutUiState, viewModel: CheckoutViewModel) {
    val state = uiState as? CheckoutUiState.DamageReport ?: return

    // Auto-reset to idle after 60 s of inactivity
    LaunchedEffect(Unit) {
        delay(AUTO_RESET_MS)
        viewModel.resetToIdle()
    }

    DamageReportContent(
        craftName  = state.checkout.craftName,
        onAllGood  = { viewModel.onSubmitCheckin(state.member, state.checkout, null, false) },
        onReport   = { notes -> viewModel.onSubmitCheckin(state.member, state.checkout, notes, true) }
    )
}

@Composable
private fun DamageReportContent(
    craftName : String,
    onAllGood : () -> Unit,
    onReport  : (String) -> Unit,
) {
    var notes by remember { mutableStateOf("") }

    // Countdown progress
    var progress by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        val steps     = 120
        val stepDelay = AUTO_RESET_MS / steps
        for (i in 1..steps) {
            delay(stepDelay)
            progress = 1f - (i.toFloat() / steps)
        }
    }

    // Icon pop-in
    val iconScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        iconScale.animateTo(1.1f, tween(320))
        iconScale.animateTo(1f,   tween(120))
    }

    Box(
        modifier          = Modifier
            .fillMaxSize()
            .background(DeepOcean),
        contentAlignment  = Alignment.Center
    ) {
        Column(
            modifier              = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
        ) {
            // ── Returned indicator ─────────────────────────────────────────
            Box(
                modifier         = Modifier
                    .scale(iconScale.value)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(AvailableGreen.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Filled.Check,
                    contentDescription = null,
                    tint               = AvailableGreen,
                    modifier           = Modifier.size(40.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text       = "$craftName returned",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                textAlign  = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text      = "Before you go — did you encounter any issues?",
                style     = MaterialTheme.typography.bodyLarge,
                color     = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // ── Notes input ────────────────────────────────────────────────
            OutlinedTextField(
                value         = notes,
                onValueChange = { notes = it },
                modifier      = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                placeholder   = { Text("Describe any damage, broken equipment, or other issues…", color = TextMuted) },
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = TealMid,
                    unfocusedBorderColor = DividerColor,
                    focusedTextColor     = Color.White,
                    unfocusedTextColor   = Color.White,
                    cursorColor          = TealMid,
                    focusedContainerColor   = CardBlue,
                    unfocusedContainerColor = CardBlue,
                ),
                shape         = RoundedCornerShape(12.dp),
                maxLines      = 5
            )

            Spacer(Modifier.weight(1f))

            // ── Action buttons ─────────────────────────────────────────────
            Row(
                modifier            = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // All Good
                ElevatedButton(
                    onClick   = onAllGood,
                    modifier  = Modifier
                        .weight(1f)
                        .height(72.dp),
                    colors    = ButtonDefaults.elevatedButtonColors(
                        containerColor = AvailableGreen,
                        contentColor   = Color.White
                    ),
                    shape     = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("All Good", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }

                // Report Issue
                ElevatedButton(
                    onClick   = { onReport(notes) },
                    enabled   = notes.isNotBlank(),
                    modifier  = Modifier
                        .weight(1f)
                        .height(72.dp),
                    colors    = ButtonDefaults.elevatedButtonColors(
                        containerColor         = ActiveAmber,
                        contentColor           = Color(0xFF1A1200),
                        disabledContainerColor = ActiveAmber.copy(alpha = 0.25f),
                        disabledContentColor   = Color.White.copy(alpha = 0.35f)
                    ),
                    shape     = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Report Issue", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Countdown bar ──────────────────────────────────────────────
            Text(
                text  = "Returning to idle screen…",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress      = { progress },
                modifier      = Modifier
                    .fillMaxWidth(0.35f)
                    .height(3.dp),
                color         = TealMid,
                trackColor    = TealMid.copy(alpha = 0.15f),
                strokeCap     = StrokeCap.Round
            )
        }
    }
}

@Preview(widthDp = 400, heightDp = 860, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun DamageReportPortraitPreview() {
    DigitalCheckoutTheme {
        DamageReportContent(
            craftName = "Quest #3",
            onAllGood = {},
            onReport  = {}
        )
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun DamageReportLandscapePreview() {
    DigitalCheckoutTheme {
        DamageReportContent(
            craftName = "Quest #3",
            onAllGood = {},
            onReport  = {}
        )
    }
}
