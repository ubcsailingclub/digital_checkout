package com.ubcsc.checkout.ui.confirm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ubcsc.checkout.ui.theme.CardBlue
import com.ubcsc.checkout.ui.theme.DeepOcean
import com.ubcsc.checkout.ui.theme.DividerColor
import com.ubcsc.checkout.ui.theme.OceanSurface
import com.ubcsc.checkout.ui.theme.TealLight
import com.ubcsc.checkout.ui.theme.TealMid
import com.ubcsc.checkout.ui.theme.TextMuted
import com.ubcsc.checkout.ui.theme.TextSecondary
import com.ubcsc.checkout.viewmodel.ActiveCheckout
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import com.ubcsc.checkout.viewmodel.Member
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private const val EDIT_INACTIVITY_TIMEOUT_MS = 45_000L
private val editTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun computeEtrHoursFromTime(returnTime: LocalTime?): Int? {
    returnTime ?: return null
    var minutes = Duration.between(LocalTime.now(), returnTime).toMinutes()
    if (minutes <= 0) minutes += 24 * 60
    return if (minutes <= 0) null else ceil(minutes / 60.0).toInt().coerceAtLeast(1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCheckoutScreen(
    member:    Member,
    checkout:  ActiveCheckout,
    viewModel: CheckoutViewModel
) {
    LaunchedEffect(Unit) {
        delay(EDIT_INACTIVITY_TIMEOUT_MS)
        viewModel.resetToIdle()
    }

    val defaultReturnTime = remember {
        LocalTime.now().plusHours(2).withSecond(0).withNano(0)
    }
    var returnTime     by remember { mutableStateOf<LocalTime?>(defaultReturnTime) }
    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour   = defaultReturnTime.hour,
        initialMinute = defaultReturnTime.minute,
        is24Hour      = false
    )

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title            = { Text("Set return time", color = Color.White) },
            text             = {
                TimePicker(
                    state  = timePickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor                               = OceanSurface,
                        selectorColor                                = TealMid,
                        containerColor                               = CardBlue,
                        periodSelectorBorderColor                    = TealMid,
                        timeSelectorSelectedContainerColor           = TealMid.copy(alpha = 0.3f),
                        timeSelectorUnselectedContainerColor         = OceanSurface,
                        timeSelectorSelectedContentColor             = TealLight,
                        timeSelectorUnselectedContentColor           = TextSecondary,
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

    Box(
        modifier         = Modifier.fillMaxSize().background(DeepOcean),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(4.dp)
                .background(Brush.horizontalGradient(listOf(TealMid, TealLight, TealMid)))
        )

        Column(
            modifier            = Modifier
                .fillMaxWidth(fraction = 0.5f)
                .clip(RoundedCornerShape(20.dp))
                .background(CardBlue)
                .border(1.dp, DividerColor, RoundedCornerShape(20.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                text       = "Update Return Time",
                style      = MaterialTheme.typography.titleMedium,
                color      = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text  = checkout.craftName,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = DividerColor)

            // ETR picker + presets
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AccessTime, null, tint = TextSecondary,
                        modifier = Modifier.width(20.dp).height(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Back by", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(
                        onClick        = { showTimePicker = true },
                        shape          = RoundedCornerShape(8.dp),
                        border         = BorderStroke(1.dp, if (returnTime != null) TealMid else DividerColor),
                        colors         = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (returnTime != null) TealLight else TextMuted
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text       = returnTime?.format(editTimeFormatter) ?: "Not set",
                            style      = MaterialTheme.typography.labelLarge,
                            fontWeight = if (returnTime != null) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                    if (returnTime != null) {
                        Spacer(Modifier.width(2.dp))
                        IconButton(onClick = { returnTime = null },
                            modifier = Modifier.width(32.dp).height(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = TextMuted,
                                modifier = Modifier.width(16.dp).height(16.dp))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 4).forEach { hours ->
                        val presetTime = LocalTime.now().plusHours(hours.toLong()).withSecond(0).withNano(0)
                        val isSelected = returnTime?.hour == presetTime.hour && returnTime?.minute == presetTime.minute
                        OutlinedButton(
                            onClick        = { returnTime = presetTime },
                            shape          = RoundedCornerShape(8.dp),
                            border         = BorderStroke(1.dp, if (isSelected) TealMid else DividerColor),
                            colors         = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) TealMid.copy(alpha = 0.15f) else Color.Transparent,
                                contentColor   = if (isSelected) TealLight else TextMuted
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("+${hours}h", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = DividerColor)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick  = { viewModel.goBack() },
                    modifier = Modifier.height(52.dp).weight(1f),
                    shape    = RoundedCornerShape(12.dp)
                ) { Text("← Back", color = TextSecondary) }

                ElevatedButton(
                    onClick   = { viewModel.onUpdateEtr(member, checkout, computeEtrHoursFromTime(returnTime)) },
                    modifier  = Modifier.height(52.dp).weight(1.5f),
                    shape     = RoundedCornerShape(12.dp),
                    colors    = ButtonDefaults.elevatedButtonColors(
                        containerColor = TealMid,
                        contentColor   = Color.White
                    ),
                    elevation = ButtonDefaults.elevatedButtonElevation(8.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
