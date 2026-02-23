package com.ubcsc.checkout.ui.idle

import android.nfc.NfcAdapter
import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubcsc.checkout.R
import com.ubcsc.checkout.ui.theme.DigitalCheckoutTheme
import com.ubcsc.checkout.ui.theme.NavyBlue
import com.ubcsc.checkout.ui.theme.OceanBlue
import com.ubcsc.checkout.viewmodel.CheckoutViewModel

@Composable
fun IdleScreen(viewModel: CheckoutViewModel) {
    val context = LocalContext.current
    val nfcAdapter = NfcAdapter.getDefaultAdapter(context)

    val nfcStatusMessage = when {
        nfcAdapter == null -> stringResource(R.string.idle_nfc_unavailable)
        !nfcAdapter.isEnabled -> stringResource(R.string.idle_nfc_disabled)
        else -> null
    }

    IdleContent(nfcStatusMessage = nfcStatusMessage)
}

@Composable
private fun IdleContent(nfcStatusMessage: String?) {
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(NavyBlue, OceanBlue)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(48.dp)
        ) {
            Text(
                text = stringResource(R.string.idle_title),
                style = MaterialTheme.typography.displaySmall,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(48.dp))

            Icon(
                imageVector = Icons.Filled.Nfc,
                contentDescription = "NFC",
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier
                    .size(120.dp)
                    .alpha(alpha)
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (nfcStatusMessage != null) {
                Text(
                    text = nfcStatusMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = androidx.compose.ui.graphics.Color(0xFFFFB300),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = stringResource(R.string.idle_subtitle),
                    style = MaterialTheme.typography.headlineSmall,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp
                )
            }
        }
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true)
@Composable
private fun IdleScreenPreview() {
    DigitalCheckoutTheme {
        IdleContent(nfcStatusMessage = null)
    }
}
