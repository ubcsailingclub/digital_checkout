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
import androidx.compose.foundation.layout.PaddingValues
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
import com.ubcsc.checkout.ui.theme.LocalKioskColors
import com.ubcsc.checkout.ui.theme.OceanSurface
import com.ubcsc.checkout.ui.theme.TextMuted
import com.ubcsc.checkout.ui.theme.UnavailableRed
import com.ubcsc.checkout.ui.util.CraftImageMapper
import com.ubcsc.checkout.viewmodel.ActiveCheckout
import com.ubcsc.checkout.viewmodel.CheckoutUiState
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import com.ubcsc.checkout.viewmodel.Craft
import com.ubcsc.checkout.viewmodel.CrewEntry
import com.ubcsc.checkout.viewmodel.Member
import androidx.compose.runtime.collectAsState
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
    val fleetStatus by viewModel.fleetStatus.collectAsState()

    LaunchedEffect(Unit) {
        delay(INACTIVITY_TIMEOUT_MS)
        viewModel.resetToIdle()
    }
    when (uiState) {
        is CheckoutUiState.ConfirmCheckout -> {
            // Collect all active warnings for this checkout
            val warnings = buildList {
                val fs = fleetStatus
                if (fs != null && fs.fleetGrounded) add(fs.fleetGroundReason)
                val cs = fs?.craft?.get(uiState.craft.code)
                if (cs != null && cs.status != "active") {
                    val label = if (cs.status == "deactivated") "Deactivated" else "Grounded"
                    add("${uiState.craft.displayName} — $label${cs.reason?.let { ": $it" } ?: ""}")
                }
            }
            CheckoutConfirmContent(
                state     = uiState,
                warnings  = warnings,
                onConfirm = { etr -> viewModel.onConfirmCheckout(uiState.member, uiState.craft, uiState.crew, etr) },
                onCancel  = { viewModel.goBack() }
            )
        }
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
    warnings:  List<String> = emptyList(),
    onConfirm: (Int?) -> Unit,
    onCancel:  () -> Unit
) {
    // Default return time: now rounded to the next whole hour + 2 h
    val defaultReturnTime = remember {
        LocalTime.now().plusHours(2).withSecond(0).withNano(0)
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
                        selectorColor             = LocalKioskColors.current.accentMid,
                        containerColor            = CardBlue,
                        periodSelectorBorderColor = LocalKioskColors.current.accentMid,
                        timeSelectorSelectedContainerColor   = LocalKioskColors.current.accentMid.copy(alpha = 0.3f),
                        timeSelectorUnselectedContainerColor = OceanSurface,
                        timeSelectorSelectedContentColor     = LocalKioskColors.current.accent,
                        timeSelectorUnselectedContentColor   = LocalKioskColors.current.textWarm,
                    )
                )
            },
            confirmButton    = {
                TextButton(onClick = {
                    returnTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("Set", color = LocalKioskColors.current.accent) }
            },
            dismissButton    = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel", color = LocalKioskColors.current.textWarm)
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
                .background(Brush.horizontalGradient(listOf(LocalKioskColors.current.accentMid, LocalKioskColors.current.accent, LocalKioskColors.current.accentMid)))
        )

        // Fleet / craft warnings — shown above all content when active
        if (warnings.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 4.dp)   // sits flush under the accent bar
            ) {
                warnings.forEach { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFB45309))   // deep amber
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Close,
                            contentDescription = null,
                            tint               = Color.White,
                            modifier           = Modifier.size(18.dp)
                        )
                        Text(
                            text  = "⚠  $msg",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = visible, enter = enterTransition()) {
            if (isPortrait) {
                // -----------------------------------------------------------
                // Portrait: vertical stacked layout
                // -----------------------------------------------------------
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBlue)
                            .border(
                                1.5.dp,
                                Brush.verticalGradient(listOf(LocalKioskColors.current.accent.copy(0.5f), LocalKioskColors.current.accent.copy(0.1f))),
                                RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(CraftImageMapper.getDrawableRes(state.craft.craftClass)),
                            contentDescription = state.craft.craftClass,
                            modifier = Modifier.size(60.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(LocalKioskColors.current.accent)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBlue)
                            .border(1.dp, DividerColor, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Text(
                            text  = stringResource(R.string.confirm_checkout_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = LocalKioskColors.current.textWarm,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        DetailRow(
                            icon = { Icon(Icons.Filled.Person, null, tint = LocalKioskColors.current.accentMid, modifier = Modifier.size(20.dp)) },
                            text = state.member.name
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DividerColor)
                        DetailRow(
                            icon = { Icon(Icons.Filled.DirectionsBoat, null, tint = LocalKioskColors.current.accent, modifier = Modifier.size(20.dp)) },
                            text = "${state.craft.displayName}  ·  ${state.craft.craftClass}"
                        )
                        if (state.crew.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DividerColor)
                            DetailRow(
                                icon = { Icon(Icons.Filled.Group, null, tint = LocalKioskColors.current.accent, modifier = Modifier.size(20.dp)) },
                                text = "${state.crew.size} crew  ·  ${state.crew.joinToString(", ") { it.name }}"
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DividerColor)
                        EtrRow(
                            returnTime  = returnTime,
                            onPickTime  = { showTimePicker = true },
                            onClearTime = { returnTime = null },
                            onSetTime   = { returnTime = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ElevatedButton(
                        onClick   = { onConfirm(computeEtrHours(returnTime)) },
                        modifier  = Modifier.fillMaxWidth().height(60.dp),
                        shape     = RoundedCornerShape(14.dp),
                        colors    = ButtonDefaults.elevatedButtonColors(
                            containerColor = LocalKioskColors.current.accentMid,
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

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick  = onCancel,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(14.dp)
                    ) { Text("← Back", color = LocalKioskColors.current.textWarm) }
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
                                Brush.verticalGradient(listOf(LocalKioskColors.current.accent.copy(0.5f), LocalKioskColors.current.accent.copy(0.1f))),
                                RoundedCornerShape(24.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(CraftImageMapper.getDrawableRes(state.craft.craftClass)),
                            contentDescription = state.craft.craftClass,
                            modifier = Modifier.size(120.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(LocalKioskColors.current.accent)
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
                            color = LocalKioskColors.current.textWarm,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        DetailRow(
                            icon = { Icon(Icons.Filled.Person, null, tint = LocalKioskColors.current.accentMid, modifier = Modifier.size(20.dp)) },
                            text = state.member.name
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = DividerColor)
                        DetailRow(
                            icon = { Icon(Icons.Filled.DirectionsBoat, null, tint = LocalKioskColors.current.accent, modifier = Modifier.size(20.dp)) },
                            text = "${state.craft.displayName}  ·  ${state.craft.craftClass}"
                        )
                        if (state.crew.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = DividerColor)
                            DetailRow(
                                icon = { Icon(Icons.Filled.Group, null, tint = LocalKioskColors.current.accent, modifier = Modifier.size(20.dp)) },
                                text = "${state.crew.size} crew  ·  ${state.crew.joinToString(", ") { it.name }}"
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = DividerColor)
                        EtrRow(
                            returnTime  = returnTime,
                            onPickTime  = { showTimePicker = true },
                            onClearTime = { returnTime = null },
                            onSetTime   = { returnTime = it }
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick  = onCancel,
                                modifier = Modifier.height(60.dp).weight(1f),
                                shape    = RoundedCornerShape(12.dp)
                            ) { Text("← Back", color = LocalKioskColors.current.textWarm) }
                            ElevatedButton(
                                onClick   = { onConfirm(computeEtrHours(returnTime)) },
                                modifier  = Modifier.height(60.dp).weight(1.5f),
                                shape     = RoundedCornerShape(12.dp),
                                colors    = ButtonDefaults.elevatedButtonColors(
                                    containerColor = LocalKioskColors.current.accentMid,
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
                .background(Brush.horizontalGradient(listOf(LocalKioskColors.current.accentMid, LocalKioskColors.current.accent, LocalKioskColors.current.accentMid)))
        )

        AnimatedVisibility(visible = visible, enter = enterTransition()) {
            if (isPortrait) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
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
                            modifier = Modifier.size(60.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(UnavailableRed)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBlue)
                            .border(1.dp, DividerColor, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Text(
                            text  = stringResource(R.string.confirm_checkin_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = LocalKioskColors.current.textWarm,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        DetailRow(
                            icon = { Icon(Icons.Filled.Person, null, tint = LocalKioskColors.current.accentMid, modifier = Modifier.size(20.dp)) },
                            text = state.member.name
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DividerColor)
                        DetailRow(
                            icon = { Icon(Icons.Filled.DirectionsBoat, null, tint = UnavailableRed, modifier = Modifier.size(20.dp)) },
                            text = state.checkout.craftName
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ElevatedButton(
                        onClick   = onConfirm,
                        modifier  = Modifier.fillMaxWidth().height(60.dp),
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

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick  = onCancel,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(14.dp)
                    ) { Text("← Back", color = LocalKioskColors.current.textWarm) }
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
                            color = LocalKioskColors.current.textWarm,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        DetailRow(
                            icon = { Icon(Icons.Filled.Person, null, tint = LocalKioskColors.current.accentMid, modifier = Modifier.size(20.dp)) },
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
                                modifier = Modifier.height(60.dp).weight(1f),
                                shape    = RoundedCornerShape(12.dp)
                            ) { Text("← Back", color = LocalKioskColors.current.textWarm) }
                            ElevatedButton(
                                onClick   = onConfirm,
                                modifier  = Modifier.height(60.dp).weight(1.5f),
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
    onClearTime: () -> Unit,
    onSetTime:   (LocalTime) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AccessTime, null, tint = LocalKioskColors.current.textWarm, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Return by", style = MaterialTheme.typography.bodyMedium, color = LocalKioskColors.current.textWarm)
            Spacer(Modifier.width(12.dp))

            OutlinedButton(
                onClick = onPickTime,
                shape   = RoundedCornerShape(8.dp),
                border  = BorderStroke(1.dp, if (returnTime != null) LocalKioskColors.current.accentMid else DividerColor),
                colors  = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (returnTime != null) LocalKioskColors.current.accent else TextMuted
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
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

        // Quick-select presets
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 3, 4).forEach { hours ->
                val presetTime = LocalTime.now().plusHours(hours.toLong()).withSecond(0).withNano(0)
                val isSelected = returnTime?.hour == presetTime.hour && returnTime?.minute == presetTime.minute
                OutlinedButton(
                    onClick = { onSetTime(presetTime) },
                    shape   = RoundedCornerShape(8.dp),
                    border  = BorderStroke(1.dp, if (isSelected) LocalKioskColors.current.accentMid else DividerColor),
                    colors  = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) LocalKioskColors.current.accentMid.copy(alpha = 0.15f) else Color.Transparent,
                        contentColor   = if (isSelected) LocalKioskColors.current.accent else TextMuted
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("+${hours}h", style = MaterialTheme.typography.labelMedium)
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
            CircularProgressIndicator(color = LocalKioskColors.current.accentMid, modifier = Modifier.size(64.dp))
            Text(
                text  = "Processing…",
                style = MaterialTheme.typography.titleMedium,
                color = LocalKioskColors.current.textWarm
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
