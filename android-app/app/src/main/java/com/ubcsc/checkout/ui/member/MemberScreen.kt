package com.ubcsc.checkout.ui.member

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubcsc.checkout.R
import com.ubcsc.checkout.ui.theme.ActiveAmber
import com.ubcsc.checkout.ui.theme.CardBlue
import com.ubcsc.checkout.ui.theme.DeepOcean
import com.ubcsc.checkout.ui.theme.DigitalCheckoutTheme
import com.ubcsc.checkout.ui.theme.DividerColor
import com.ubcsc.checkout.ui.theme.TealLight
import com.ubcsc.checkout.ui.theme.TealMid
import com.ubcsc.checkout.ui.theme.TextMuted
import com.ubcsc.checkout.ui.theme.TextSecondary
import com.ubcsc.checkout.ui.theme.UnavailableRed
import com.ubcsc.checkout.viewmodel.ActiveCheckout
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import com.ubcsc.checkout.viewmodel.Member
import kotlinx.coroutines.delay

private const val INACTIVITY_TIMEOUT_MS = 30_000L

private fun enterTransition() = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }

@Composable
fun MemberScreen(member: Member, viewModel: CheckoutViewModel) {
    LaunchedEffect(Unit) {
        delay(INACTIVITY_TIMEOUT_MS)
        viewModel.resetToIdle()
    }
    MemberContent(
        member            = member,
        onCheckout        = { viewModel.onCheckoutSelected(member) },
        onCheckin         = { viewModel.onCheckinSelected(member) },
        onCheckinForOther = { viewModel.onCheckinForOther(member) },
        onEditCheckout    = { viewModel.onEditCheckoutSelected(member) },
        onCancel          = { viewModel.onCancel() }
    )
}

@Composable
private fun MemberContent(
    member:            Member,
    onCheckout:        () -> Unit,
    onCheckin:         () -> Unit,
    onCheckinForOther: () -> Unit,
    onEditCheckout:    () -> Unit,
    onCancel:          () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepOcean),
        contentAlignment = Alignment.Center
    ) {
        // Subtle top accent bar
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    Brush.horizontalGradient(listOf(TealMid, TealLight, TealMid))
                )
        )

        // Cancel — top right
        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.cancel_button),
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        AnimatedVisibility(visible = visible, enter = enterTransition()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(horizontal = 48.dp, vertical = 4.dp)
                    .offset(y = (-60).dp)
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(CardBlue)
                        .border(2.dp, TealMid, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = TealLight,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Welcome back",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )

                Text(
                    text = member.name,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Active checkout badge
                member.activeCheckout?.let { checkout ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(ActiveAmber.copy(alpha = 0.15f))
                                .border(1.dp, ActiveAmber.copy(alpha = 0.4f), RoundedCornerShape(50.dp))
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DirectionsBoat,
                                contentDescription = null,
                                tint = ActiveAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Currently out: ${checkout.craftName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ActiveAmber,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = onEditCheckout) {
                            Text("Edit", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Divider
                Box(
                    modifier = Modifier
                        .width(280.dp)
                        .height(1.dp)
                        .background(DividerColor)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons — shifted up ~1 cm so they sit above centre on portrait tablet
                if (member.activeCheckout == null) {
                    // No own checkout: primary = Check Out, secondary = Check In for Someone
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ElevatedButton(
                            onClick = onCheckout,
                            modifier = Modifier
                                .height(72.dp)
                                .width(260.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = TealMid,
                                contentColor   = Color.White
                            ),
                            elevation = ButtonDefaults.elevatedButtonElevation(8.dp)
                        ) {
                            Icon(Icons.Filled.DirectionsBoat, null, Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.member_checkout_action),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        FilledTonalButton(
                            onClick = onCheckinForOther,
                            modifier = Modifier
                                .height(52.dp)
                                .width(260.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = CardBlue,
                                contentColor   = TextSecondary
                            )
                        ) {
                            Icon(Icons.Filled.DirectionsBoat, null, Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Check In a Boat",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    // Has own active checkout: primary = Check In
                    ElevatedButton(
                        onClick = onCheckin,
                        modifier = Modifier
                            .height(72.dp)
                            .width(260.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = UnavailableRed,
                            contentColor   = Color.White
                        ),
                        elevation = ButtonDefaults.elevatedButtonElevation(8.dp)
                    ) {
                        Icon(Icons.Filled.DirectionsBoat, null, Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.member_checkin_action),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun MemberPreviewNoCheckout() {
    DigitalCheckoutTheme {
        MemberContent(Member("1", "Alex Sailor", ""), {}, {}, {}, {}, {})
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun MemberPreviewWithCheckout() {
    DigitalCheckoutTheme {
        MemberContent(
            Member("1", "Alex Sailor", "", ActiveCheckout(1, "LZ01", "Laser #1")),
            {}, {}, {}, {}, {}
        )
    }
}
