package com.ubcsc.checkout.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ubcsc.checkout.R
import com.ubcsc.checkout.ui.theme.CoralRed
import com.ubcsc.checkout.ui.theme.DigitalCheckoutTheme
import com.ubcsc.checkout.ui.theme.SeaGreen
import com.ubcsc.checkout.viewmodel.CheckoutUiState
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import kotlinx.coroutines.delay

private const val AUTO_DISMISS_MS = 4_000L

@Composable
fun ResultScreen(
    uiState: CheckoutUiState,
    viewModel: CheckoutViewModel
) {
    LaunchedEffect(Unit) {
        delay(AUTO_DISMISS_MS)
        viewModel.resetToIdle()
    }

    when (uiState) {
        is CheckoutUiState.Success -> SuccessContent(
            headline = if (uiState.isCheckout)
                stringResource(R.string.result_success_checkout)
            else
                stringResource(R.string.result_success_checkin),
            detail = uiState.message
        )
        is CheckoutUiState.Error -> ErrorContent(message = uiState.message)
        else -> Unit
    }
}

@Composable
private fun SuccessContent(headline: String, detail: String) {
    Surface(modifier = Modifier.fillMaxSize(), color = SeaGreen.copy(alpha = 0.08f)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = SeaGreen,
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = SeaGreen,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.titleMedium,
                color = Color.DarkGray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.result_returning),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String) {
    Surface(modifier = Modifier.fillMaxSize(), color = CoralRed.copy(alpha = 0.08f)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = null,
                tint = CoralRed,
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.result_error_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = CoralRed,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = Color.DarkGray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.result_returning),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true)
@Composable
private fun SuccessPreview() {
    DigitalCheckoutTheme {
        SuccessContent(headline = "Enjoy your sail!", detail = "Checked out Laser #1 for Alex Sailor")
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true)
@Composable
private fun ErrorPreview() {
    DigitalCheckoutTheme {
        ErrorContent(message = "Card not recognized. Please see the dock staff.")
    }
}
