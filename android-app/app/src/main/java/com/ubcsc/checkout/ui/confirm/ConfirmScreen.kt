package com.ubcsc.checkout.ui.confirm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.plus
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ubcsc.checkout.R
import com.ubcsc.checkout.ui.theme.CardBlue
import com.ubcsc.checkout.ui.theme.DeepOcean
import com.ubcsc.checkout.ui.theme.DigitalCheckoutTheme
import com.ubcsc.checkout.ui.theme.DividerColor
import com.ubcsc.checkout.ui.theme.TealLight
import com.ubcsc.checkout.ui.theme.TealMid
import com.ubcsc.checkout.ui.theme.TextSecondary
import com.ubcsc.checkout.ui.theme.UnavailableRed
import com.ubcsc.checkout.ui.util.CraftImageMapper
import com.ubcsc.checkout.viewmodel.ActiveCheckout
import com.ubcsc.checkout.viewmodel.CheckoutUiState
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import com.ubcsc.checkout.viewmodel.Craft
import com.ubcsc.checkout.viewmodel.Member
import kotlinx.coroutines.delay

private const val INACTIVITY_TIMEOUT_MS = 30_000L

private fun enterTransition() = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }

@Composable
fun ConfirmScreen(uiState: CheckoutUiState, viewModel: CheckoutViewModel) {
    LaunchedEffect(Unit) {
        delay(INACTIVITY_TIMEOUT_MS)
        viewModel.resetToIdle()
    }
    when (uiState) {
        is CheckoutUiState.ConfirmCheckout ->
            ConfirmLayout(
                title        = stringResource(R.string.confirm_checkout_title),
                memberName   = uiState.member.name,
                craftName    = "${uiState.craft.displayName}  ·  ${uiState.craft.craftClass}",
                craftClass   = uiState.craft.craftClass,
                isCheckin    = false,
                onConfirm    = { viewModel.onConfirmCheckout(uiState.member, uiState.craft) },
                onCancel     = { viewModel.onCancel() }
            )
        is CheckoutUiState.ConfirmCheckin ->
            ConfirmLayout(
                title        = stringResource(R.string.confirm_checkin_title),
                memberName   = uiState.member.name,
                craftName    = uiState.checkout.craftName,
                craftClass   = uiState.checkout.craftCode,  // code used as fallback class hint
                isCheckin    = true,
                onConfirm    = { viewModel.onConfirmCheckin(uiState.member, uiState.checkout) },
                onCancel     = { viewModel.onCancel() }
            )
        is CheckoutUiState.Loading -> LoadingOverlay()
        else -> Unit
    }
}

@Composable
private fun ConfirmLayout(
    title: String,
    memberName: String,
    craftName: String,
    craftClass: String,
    isCheckin: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val confirmColor = if (isCheckin) UnavailableRed else TealMid
    val iconTint     = if (isCheckin) UnavailableRed else TealLight

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepOcean),
        contentAlignment = Alignment.Center
    ) {
        // Top accent bar
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(4.dp)
                .background(Brush.horizontalGradient(listOf(TealMid, TealLight, TealMid)))
        )

        AnimatedVisibility(visible = visible, enter = enterTransition()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 60.dp)
            ) {
                // Boat icon panel
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(CardBlue)
                        .border(
                            1.5.dp,
                            Brush.verticalGradient(
                                listOf(iconTint.copy(0.5f), iconTint.copy(0.1f))
                            ),
                            RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(CraftImageMapper.getDrawableRes(craftClass)),
                        contentDescription = craftClass,
                        modifier = Modifier.size(120.dp),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(iconTint)
                    )
                }

                // Details card
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardBlue)
                        .border(1.dp, DividerColor, RoundedCornerShape(20.dp))
                        .padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Member row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Person,
                            null,
                            tint = TealMid,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = memberName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = DividerColor
                    )

                    // Boat row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.DirectionsBoat,
                            null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = craftName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .height(52.dp)
                                .weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                stringResource(R.string.cancel_button),
                                color = TextSecondary
                            )
                        }

                        ElevatedButton(
                            onClick = onConfirm,
                            modifier = Modifier
                                .height(52.dp)
                                .weight(1.5f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = confirmColor,
                                contentColor   = Color.White
                            ),
                            elevation = ButtonDefaults.elevatedButtonElevation(8.dp)
                        ) {
                            Text(
                                stringResource(R.string.confirm_button),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepOcean),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = TealMid, modifier = Modifier.size(64.dp))
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun ConfirmCheckoutPreview() {
    DigitalCheckoutTheme {
        ConfirmLayout(
            title = "Confirm Checkout",
            memberName = "Alex Sailor",
            craftName = "Laser #1  ·  Laser",
            craftClass = "Laser",
            isCheckin = false,
            onConfirm = {}, onCancel = {}
        )
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun ConfirmCheckinPreview() {
    DigitalCheckoutTheme {
        ConfirmLayout(
            title = "Confirm Return",
            memberName = "Alex Sailor",
            craftName = "Windsurfer #1",
            craftClass = "Windsurfer",
            isCheckin = true,
            onConfirm = {}, onCancel = {}
        )
    }
}
