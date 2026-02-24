package com.ubcsc.checkout.ui.confirm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.ubcsc.checkout.ui.theme.TextMuted
import com.ubcsc.checkout.ui.theme.TextSecondary
import com.ubcsc.checkout.ui.theme.UnavailableRed
import com.ubcsc.checkout.ui.util.CraftImageMapper
import com.ubcsc.checkout.viewmodel.ActiveCheckout
import com.ubcsc.checkout.viewmodel.CheckoutUiState
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import com.ubcsc.checkout.viewmodel.Craft
import com.ubcsc.checkout.viewmodel.CrewEntry
import com.ubcsc.checkout.viewmodel.Member
import kotlinx.coroutines.delay

private const val INACTIVITY_TIMEOUT_MS = 30_000L

private fun enterTransition() = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }

// Null = no ETR set
private val etrOptions: List<Int?> = listOf(null, 1, 2, 3, 4)
private fun etrLabel(hours: Int?) = if (hours == null) "No ETR" else "+${hours}h"

@Composable
fun ConfirmScreen(uiState: CheckoutUiState, viewModel: CheckoutViewModel) {
    LaunchedEffect(Unit) {
        delay(INACTIVITY_TIMEOUT_MS)
        viewModel.resetToIdle()
    }
    when (uiState) {
        is CheckoutUiState.ConfirmCheckout ->
            CheckoutConfirmContent(
                state     = uiState,
                onConfirm = { etr -> viewModel.onConfirmCheckout(uiState.member, uiState.craft, uiState.crew, etr) },
                onCancel  = { viewModel.onCancel() }
            )
        is CheckoutUiState.ConfirmCheckin ->
            CheckinConfirmContent(
                state     = uiState,
                onConfirm = { viewModel.onConfirmCheckin(uiState.member, uiState.checkout) },
                onCancel  = { viewModel.onCancel() }
            )
        is CheckoutUiState.Loading -> LoadingOverlay()
        else -> Unit
    }
}

// ---------------------------------------------------------------------------
// Checkout confirm (with ETR + crew)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckoutConfirmContent(
    state:     CheckoutUiState.ConfirmCheckout,
    onConfirm: (Int?) -> Unit,
    onCancel:  () -> Unit
) {
    var selectedEtr by remember { mutableIntStateOf(2) }  // default +2h (index 2)
    var visible     by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepOcean),
        contentAlignment = Alignment.Center
    ) {
        val isPortrait = maxWidth < 600.dp

        // Top accent bar
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(4.dp)
                .background(Brush.horizontalGradient(listOf(TealMid, TealLight, TealMid)))
        )

        AnimatedVisibility(visible = visible, enter = enterTransition()) {
            if (isPortrait) {
                // -----------------------------------------------------------
                // Portrait: vertical stacked layout
                // -----------------------------------------------------------
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Compact boat image
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBlue)
                            .border(
                                1.5.dp,
                                Brush.verticalGradient(listOf(TealLight.copy(0.5f), TealLight.copy(0.1f))),
                                RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(CraftImageMapper.getDrawableRes(state.craft.craftClass)),
                            contentDescription = state.craft.craftClass,
                            modifier = Modifier.size(68.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(TealLight)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Details + ETR card — full width
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBlue)
                            .border(1.dp, DividerColor, RoundedCornerShape(20.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Text(
                            text  = stringResource(R.string.confirm_checkout_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        DetailRow(
                            icon = { Icon(Icons.Filled.Person, null, tint = TealMid, modifier = Modifier.size(20.dp)) },
                            text = state.member.name
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = DividerColor)
                        DetailRow(
                            icon = { Icon(Icons.Filled.DirectionsBoat, null, tint = TealLight, modifier = Modifier.size(20.dp)) },
                            text = "${state.craft.displayName}  ·  ${state.craft.craftClass}"
                        )
                        if (state.crew.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = DividerColor)
                            DetailRow(
                                icon = { Icon(Icons.Filled.Group, null, tint = TealLight, modifier = Modifier.size(20.dp)) },
                                text = "${state.crew.size} crew  ·  ${state.crew.joinToString(", ") { it.name }}"
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = DividerColor)
                        // ETR picker
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccessTime, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Return by", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                etrOptions.forEachIndexed { idx, hours ->
                                    FilterChip(
                                        selected = selectedEtr == idx,
                                        onClick  = { selectedEtr = idx },
                                        label    = { Text(etrLabel(hours), style = MaterialTheme.typography.labelMedium) },
                                        colors   = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TealMid.copy(alpha = 0.25f),
                                            selectedLabelColor     = TealLight,
                                            labelColor             = TextMuted
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled             = true,
                                            selected            = selectedEtr == idx,
                                            selectedBorderColor = TealMid,
                                            borderColor         = DividerColor
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Large confirm button — full width
                    ElevatedButton(
                        onClick   = { onConfirm(etrOptions[selectedEtr]) },
                        modifier  = Modifier.fillMaxWidth().height(58.dp),
                        shape     = RoundedCornerShape(14.dp),
                        colors    = ButtonDefaults.elevatedButtonColors(
                            containerColor = TealMid,
                            contentColor   = Color.White
                        ),
                        elevation = ButtonDefaults.elevatedButtonElevation(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.confirm_button),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick  = onCancel,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(14.dp)
                    ) { Text(stringResource(R.string.cancel_button), color = TextSecondary) }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                // -----------------------------------------------------------
                // Landscape: horizontal side-by-side layout
                // -----------------------------------------------------------
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
                                Brush.verticalGradient(listOf(TealLight.copy(0.5f), TealLight.copy(0.1f))),
                                RoundedCornerShape(24.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(CraftImageMapper.getDrawableRes(state.craft.craftClass)),
                            contentDescription = state.craft.craftClass,
                            modifier = Modifier.size(120.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(TealLight)
                        )
                    }

                    // Details card — weight(1f) instead of hardcoded width
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBlue)
                            .border(1.dp, DividerColor, RoundedCornerShape(20.dp))
                            .padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Text(
                            text  = stringResource(R.string.confirm_checkout_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        DetailRow(
                            icon = { Icon(Icons.Filled.Person, null, tint = TealMid, modifier = Modifier.size(20.dp)) },
                            text = state.member.name
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = DividerColor)
                        DetailRow(
                            icon = { Icon(Icons.Filled.DirectionsBoat, null, tint = TealLight, modifier = Modifier.size(20.dp)) },
                            text = "${state.craft.displayName}  ·  ${state.craft.craftClass}"
                        )
                        if (state.crew.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = DividerColor)
                            DetailRow(
                                icon = { Icon(Icons.Filled.Group, null, tint = TealLight, modifier = Modifier.size(20.dp)) },
                                text = "${state.crew.size} crew  ·  ${state.crew.joinToString(", ") { it.name }}"
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = DividerColor)
                        // ETR picker
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccessTime, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Return by", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                etrOptions.forEachIndexed { idx, hours ->
                                    FilterChip(
                                        selected = selectedEtr == idx,
                                        onClick  = { selectedEtr = idx },
                                        label    = { Text(etrLabel(hours), style = MaterialTheme.typography.labelMedium) },
                                        colors   = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TealMid.copy(alpha = 0.25f),
                                            selectedLabelColor     = TealLight,
                                            labelColor             = TextMuted
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled             = true,
                                            selected            = selectedEtr == idx,
                                            selectedBorderColor = TealMid,
                                            borderColor         = DividerColor
                                        )
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick  = onCancel,
                                modifier = Modifier.height(52.dp).weight(1f),
                                shape    = RoundedCornerShape(12.dp)
                            ) { Text(stringResource(R.string.cancel_button), color = TextSecondary) }
                            ElevatedButton(
                                onClick   = { onConfirm(etrOptions[selectedEtr]) },
                                modifier  = Modifier.height(52.dp).weight(1.5f),
                                shape     = RoundedCornerShape(12.dp),
                                colors    = ButtonDefaults.elevatedButtonColors(
                                    containerColor = TealMid,
                                    contentColor   = Color.White
                                ),
                                elevation = ButtonDefaults.elevatedButtonElevation(8.dp)
                            ) { Text(stringResource(R.string.confirm_button), fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Checkin confirm (simple — no ETR, no crew)
// ---------------------------------------------------------------------------

@Composable
private fun CheckinConfirmContent(
    state:     CheckoutUiState.ConfirmCheckin,
    onConfirm: () -> Unit,
    onCancel:  () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepOcean),
        contentAlignment = Alignment.Center
    ) {
        val isPortrait = maxWidth < 600.dp

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(4.dp)
                .background(Brush.horizontalGradient(listOf(TealMid, TealLight, TealMid)))
        )

        AnimatedVisibility(visible = visible, enter = enterTransition()) {
            if (isPortrait) {
                // -----------------------------------------------------------
                // Portrait: vertical stacked layout
                // -----------------------------------------------------------
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBlue)
                            .border(
                                1.5.dp,
                                Brush.verticalGradient(listOf(UnavailableRed.copy(0.5f), UnavailableRed.copy(0.1f))),
                                RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(CraftImageMapper.getDrawableRes(state.checkout.craftCode)),
                            contentDescription = null,
                            modifier = Modifier.size(68.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(UnavailableRed)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBlue)
                            .border(1.dp, DividerColor, RoundedCornerShape(20.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Text(
                            text  = stringResource(R.string.confirm_checkin_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        DetailRow(
                            icon = { Icon(Icons.Filled.Person, null, tint = TealMid, modifier = Modifier.size(20.dp)) },
                            text = state.member.name
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = DividerColor)
                        DetailRow(
                            icon = { Icon(Icons.Filled.DirectionsBoat, null, tint = UnavailableRed, modifier = Modifier.size(20.dp)) },
                            text = state.checkout.craftName
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    ElevatedButton(
                        onClick   = onConfirm,
                        modifier  = Modifier.fillMaxWidth().height(58.dp),
                        shape     = RoundedCornerShape(14.dp),
                        colors    = ButtonDefaults.elevatedButtonColors(
                            containerColor = UnavailableRed,
                            contentColor   = Color.White
                        ),
                        elevation = ButtonDefaults.elevatedButtonElevation(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.confirm_button),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick  = onCancel,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(14.dp)
                    ) { Text(stringResource(R.string.cancel_button), color = TextSecondary) }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                // -----------------------------------------------------------
                // Landscape: horizontal side-by-side layout
                // -----------------------------------------------------------
                Row(
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 60.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(CardBlue)
                            .border(
                                1.5.dp,
                                Brush.verticalGradient(listOf(UnavailableRed.copy(0.5f), UnavailableRed.copy(0.1f))),
                                RoundedCornerShape(24.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(CraftImageMapper.getDrawableRes(state.checkout.craftCode)),
                            contentDescription = null,
                            modifier = Modifier.size(120.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(UnavailableRed)
                        )
                    }

                    // Details card — weight(1f) instead of hardcoded width
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBlue)
                            .border(1.dp, DividerColor, RoundedCornerShape(20.dp))
                            .padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Text(
                            text  = stringResource(R.string.confirm_checkin_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        DetailRow(
                            icon = { Icon(Icons.Filled.Person, null, tint = TealMid, modifier = Modifier.size(20.dp)) },
                            text = state.member.name
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = DividerColor)
                        DetailRow(
                            icon = { Icon(Icons.Filled.DirectionsBoat, null, tint = UnavailableRed, modifier = Modifier.size(20.dp)) },
                            text = state.checkout.craftName
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick  = onCancel,
                                modifier = Modifier.height(52.dp).weight(1f),
                                shape    = RoundedCornerShape(12.dp)
                            ) { Text(stringResource(R.string.cancel_button), color = TextSecondary) }
                            ElevatedButton(
                                onClick   = onConfirm,
                                modifier  = Modifier.height(52.dp).weight(1.5f),
                                shape     = RoundedCornerShape(12.dp),
                                colors    = ButtonDefaults.elevatedButtonColors(
                                    containerColor = UnavailableRed,
                                    contentColor   = Color.White
                                ),
                                elevation = ButtonDefaults.elevatedButtonElevation(8.dp)
                            ) { Text(stringResource(R.string.confirm_button), fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

@Composable
private fun DetailRow(icon: @Composable () -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text  = text,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
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

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(widthDp = 960, heightDp = 600, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun ConfirmCheckoutPreviewLandscape() {
    DigitalCheckoutTheme {
        CheckoutConfirmContent(
            state = CheckoutUiState.ConfirmCheckout(
                member = Member("1", "Alex Sailor", ""),
                craft  = Craft("5", "LZ01", "Laser #1", "Laser", true),
                crew   = emptyList()
            ),
            onConfirm = {}, onCancel = {}
        )
    }
}

@Preview(widthDp = 400, heightDp = 860, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun ConfirmCheckoutPreviewPortrait() {
    DigitalCheckoutTheme {
        CheckoutConfirmContent(
            state = CheckoutUiState.ConfirmCheckout(
                member = Member("1", "Alex Sailor", ""),
                craft  = Craft("5", "LZ01", "Laser #1", "Laser", true),
                crew   = emptyList()
            ),
            onConfirm = {}, onCancel = {}
        )
    }
}

@Preview(widthDp = 400, heightDp = 860, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun ConfirmCheckoutWithCrewPreviewPortrait() {
    DigitalCheckoutTheme {
        CheckoutConfirmContent(
            state = CheckoutUiState.ConfirmCheckout(
                member = Member("1", "Alex Sailor", ""),
                craft  = Craft("5", "VG01", "Vanguard #1", "Vanguard 15", true),
                crew   = listOf(
                    CrewEntry("Jordan Lee", isGuest = false),
                    CrewEntry("Guest", isGuest = true)
                )
            ),
            onConfirm = {}, onCancel = {}
        )
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun ConfirmCheckinPreviewLandscape() {
    DigitalCheckoutTheme {
        CheckinConfirmContent(
            state = CheckoutUiState.ConfirmCheckin(
                member   = Member("1", "Alex Sailor", ""),
                checkout = ActiveCheckout(1, "WS01", "L1 Board #1")
            ),
            onConfirm = {}, onCancel = {}
        )
    }
}

@Preview(widthDp = 400, heightDp = 860, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun ConfirmCheckinPreviewPortrait() {
    DigitalCheckoutTheme {
        CheckinConfirmContent(
            state = CheckoutUiState.ConfirmCheckin(
                member   = Member("1", "Alex Sailor", ""),
                checkout = ActiveCheckout(1, "WS01", "L1 Board #1")
            ),
            onConfirm = {}, onCancel = {}
        )
    }
}
