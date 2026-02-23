package com.ubcsc.checkout.ui.confirm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ubcsc.checkout.R
import com.ubcsc.checkout.ui.theme.CoralRed
import com.ubcsc.checkout.ui.theme.DigitalCheckoutTheme
import com.ubcsc.checkout.ui.theme.NavyBlue
import com.ubcsc.checkout.viewmodel.ActiveCheckout
import com.ubcsc.checkout.viewmodel.CheckoutUiState
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import com.ubcsc.checkout.viewmodel.Craft
import com.ubcsc.checkout.viewmodel.Member
import kotlinx.coroutines.delay

private const val INACTIVITY_TIMEOUT_MS = 30_000L

@Composable
fun ConfirmScreen(
    uiState: CheckoutUiState,
    viewModel: CheckoutViewModel
) {
    LaunchedEffect(Unit) {
        delay(INACTIVITY_TIMEOUT_MS)
        viewModel.resetToIdle()
    }

    when (uiState) {
        is CheckoutUiState.ConfirmCheckout -> ConfirmCheckoutContent(
            member = uiState.member,
            craft = uiState.craft,
            onConfirm = { viewModel.onConfirmCheckout(uiState.member, uiState.craft) },
            onCancel = { viewModel.onCancel() }
        )
        is CheckoutUiState.ConfirmCheckin -> ConfirmCheckinContent(
            member = uiState.member,
            checkout = uiState.checkout,
            onConfirm = { viewModel.onConfirmCheckin(uiState.member, uiState.checkout) },
            onCancel = { viewModel.onCancel() }
        )
        is CheckoutUiState.Loading -> LoadingContent()
        else -> Unit
    }
}

@Composable
private fun ConfirmCheckoutContent(
    member: Member,
    craft: Craft,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    ConfirmLayout(
        title = stringResource(R.string.confirm_checkout_title),
        rows = listOf(
            Pair(Icons.Filled.Person, member.name),
            Pair(Icons.Filled.DirectionsBoat, "${craft.displayName} (${craft.craftClass})")
        ),
        confirmLabel = stringResource(R.string.confirm_button),
        onConfirm = onConfirm,
        onCancel = onCancel,
        confirmColor = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ConfirmCheckinContent(
    member: Member,
    checkout: ActiveCheckout,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    ConfirmLayout(
        title = stringResource(R.string.confirm_checkin_title),
        rows = listOf(
            Pair(Icons.Filled.Person, member.name),
            Pair(Icons.Filled.DirectionsBoat, checkout.craftName)
        ),
        confirmLabel = stringResource(R.string.confirm_button),
        onConfirm = onConfirm,
        onCancel = onCancel,
        confirmColor = CoralRed
    )
}

@Composable
private fun ConfirmLayout(
    title: String,
    rows: List<Pair<androidx.compose.ui.graphics.vector.ImageVector, String>>,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = NavyBlue
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(0.55f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(28.dp)) {
                    rows.forEachIndexed { index, (icon, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleLarge,
                                color = NavyBlue
                            )
                        }
                        if (index < rows.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = Color.LightGray.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .height(64.dp)
                        .width(160.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        stringResource(R.string.cancel_button),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .height(64.dp)
                        .width(200.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = confirmColor)
                ) {
                    Text(
                        confirmLabel,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(64.dp))
        }
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true)
@Composable
private fun ConfirmCheckoutPreview() {
    DigitalCheckoutTheme {
        ConfirmCheckoutContent(
            member = Member("1", "Alex Sailor"),
            craft = Craft("1", "LZ01", "Laser #1", "Laser", true),
            onConfirm = {}, onCancel = {}
        )
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true)
@Composable
private fun ConfirmCheckinPreview() {
    DigitalCheckoutTheme {
        ConfirmCheckinContent(
            member = Member("1", "Alex Sailor"),
            checkout = ActiveCheckout("s1", "LZ01", "Laser #1"),
            onConfirm = {}, onCancel = {}
        )
    }
}
