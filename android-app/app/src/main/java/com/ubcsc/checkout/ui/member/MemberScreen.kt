package com.ubcsc.checkout.ui.member

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
import com.ubcsc.checkout.ui.theme.GoldYellow
import com.ubcsc.checkout.ui.theme.NavyBlue
import com.ubcsc.checkout.viewmodel.ActiveCheckout
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import com.ubcsc.checkout.viewmodel.Member
import kotlinx.coroutines.delay

private const val INACTIVITY_TIMEOUT_MS = 30_000L

@Composable
fun MemberScreen(
    member: Member,
    viewModel: CheckoutViewModel
) {
    // Auto-return to idle after inactivity
    LaunchedEffect(Unit) {
        delay(INACTIVITY_TIMEOUT_MS)
        viewModel.resetToIdle()
    }

    MemberContent(
        member = member,
        onCheckout = { viewModel.onCheckoutSelected(member) },
        onCheckin = { viewModel.onCheckinSelected(member) },
        onCancel = { viewModel.onCancel() }
    )
}

@Composable
private fun MemberContent(
    member: Member,
    onCheckout: () -> Unit,
    onCheckin: () -> Unit,
    onCancel: () -> Unit
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
            // Member info card
            Card(
                modifier = Modifier.fillMaxWidth(0.65f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavyBlue
                    )

                    member.activeCheckout?.let { checkout ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = GoldYellow.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DirectionsBoat,
                                    contentDescription = null,
                                    tint = GoldYellow,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.member_active_session, checkout.craftName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NavyBlue
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (member.activeCheckout == null) {
                    Button(
                        onClick = onCheckout,
                        modifier = Modifier
                            .height(72.dp)
                            .width(240.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.DirectionsBoat, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.member_checkout_action),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else {
                    Button(
                        onClick = onCheckin,
                        modifier = Modifier
                            .height(72.dp)
                            .width(240.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                    ) {
                        Icon(Icons.Filled.DirectionsBoat, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.member_checkin_action),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .height(72.dp)
                        .width(160.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cancel_button),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true)
@Composable
private fun MemberScreenPreview() {
    DigitalCheckoutTheme {
        MemberContent(
            member = Member("1", "Alex Sailor", activeCheckout = null),
            onCheckout = {}, onCheckin = {}, onCancel = {}
        )
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true)
@Composable
private fun MemberScreenWithCheckoutPreview() {
    DigitalCheckoutTheme {
        MemberContent(
            member = Member(
                "1", "Alex Sailor",
                activeCheckout = ActiveCheckout("s1", "LZ01", "Laser #1")
            ),
            onCheckout = {}, onCheckin = {}, onCancel = {}
        )
    }
}
