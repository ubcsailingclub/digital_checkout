package com.ubcsc.checkout.ui.confirm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
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
import com.ubcsc.checkout.ui.theme.OceanSurface
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
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private const val INACTIVITY_TIMEOUT_MS = 30_000L

private fun enterTransition() = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

/** Converts a wall-clock return time to a whole-hours offset from now (minimum 1). */
private fun computeEtrHours(returnTime: LocalTime?): Int? {
    returnTime ?: return null
    var minutes = Duration.between(LocalTime.now(), returnTime).toMinutes()
    if (minutes <= 0) minutes += 24 * 60   // treat as next-day selection
    return if (minutes <= 0) null else ceil(minutes / 60.0).toInt().coerceAtLeast(1)
}

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
                onCancel  = { viewModel.goBack() }
            )
        is CheckoutUiState.ConfirmCheckin ->
            CheckinConfirmContent(
                state     = uiState,
                onConfirm = { viewModel.onConfirmCheckin(uiState.member, uiState.checkout) },
                onCancel  = { viewModel.goBack() }
            )
        is CheckoutUiState.Loading -> LoadingOverlay()
        else -> Unit
    }
}

// ---------------------------------------------------------------------------
// Checkout confirm (with ETR + crew)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckoutConfirmContent(
    state:     CheckoutUiState.ConfirmCheckout,
    onConfirm: (Int?) -> Unit,
    onCancel:  () -> Unit
) {
    // Default return time: now rounded to the next whole hour + 2 h
    val defaultReturnTime = remember {
        LocalTime.now().plusHours(2).withMinute(0).withSecond(0).withNano(0)
    }
    var returnTime by remember { mutableStateOf<LocalTime?>(defaultReturnTime) }
    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour   = defaultReturnTime.hour,
        initialMinute = defaultReturnTime.minute,
        is24Hour      = false
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Time picker dialog
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title            = { Text("Set return time", color = Color.White) },
            text             = {
                TimePicker(
                    state  = timePickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor            = OceanSurface,
                        selectorColor             = TealMid,
                        containerColor            = CardBlue,
                        periodSelectorBorderColor = TealMid,
                        timeSelectorSelectedContainerColor   = TealMid.copy(alpha = 0.3f),
                        timeSelectorUnselectedContainerColor = OceanSurface,
                        timeSelectorSelectedContentColor     = TealLight,
                        timeSelectorUnselectedContentColor   = TextSecondary,
                    )
                )
            },
            confirmButton    = {
                TextButton(onClick = {
                    returnTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("Set", color = TealLight) }
            },
            dismissButton    = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor   = CardBlue
        )
    }

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
                        EtrRow(
                            returnTime  = returnTime,
                            onPickTime  = { showTimePicker = true },
                            onClearTime = { returnTime = null }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    ElevatedButton(
                        onClick   = { onConfirm(computeEtrHours(returnTime)) },
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
                    ) { Text("← Back", color = TextSecondary) }

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
                        EtrRow(
                            returnTime  = returnTime,
                            onPickTime  = { showTimePicker = true },
                            onClearTime = { returnTime = null }
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick  = onCancel,
                                modifier = Modifier.height(52.dp).weight(1f),
                                shape    = RoundedCornerShape(12.dp)
                            ) { Text("← Back", color = TextSecondary) }
                            ElevatedButton(
                                onClick   = { onConfirm(computeEtrHours(returnTime)) },
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
                    ) { Text("← Back", color = TextSecondary) }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
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
                            ) { Text("← Back", color = TextSecondary) }
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
// ETR row — clock button + optional clear
// ---------------------------------------------------------------------------

@Composable
private fun EtrRow(
    returnTime:  LocalTime?,
    onPickTime:  () -> Unit,
    onClearTime: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.AccessTime, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Return by", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.width(12.dp))

        OutlinedButton(
            onClick = onPickTime,
            shape   = RoundedCornerShape(8.dp),
            border  = BorderStroke(1.dp, if (returnTime != null) TealMid else DividerColor),
            colors  = ButtonDefaults.outlinedButtonColors(
                contentColor = if (returnTime != null) TealLight else TextMuted
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text  = returnTime?.format(timeFormatter) ?: "Not set",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (returnTime != null) FontWeight.SemiBold else FontWeight.Normal
            )
        }

        if (returnTime != null) {
            Spacer(Modifier.width(2.dp))
            IconButton(onClick = onClearTime, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Clear return time", tint = TextMuted, modifier = Modifier.size(16.dp))
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
    // Show a secondary hint if it's taking more than 5 seconds
    var showSlowHint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(5_000)
        showSlowHint = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepOcean),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = TealMid, modifier = Modifier.size(64.dp))
            Text(
                text  = "Processing…",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )
            AnimatedVisibility(visible = showSlowHint) {
                Text(
                    text  = "Taking a little longer than usual — please wait",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
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
