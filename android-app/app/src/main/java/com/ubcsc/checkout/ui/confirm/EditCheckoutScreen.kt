package com.ubcsc.checkout.ui.confirm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubcsc.checkout.ui.theme.CardBlue
import com.ubcsc.checkout.ui.theme.DeepOcean
import com.ubcsc.checkout.ui.theme.DividerColor
import com.ubcsc.checkout.ui.theme.LocalKioskColors
import com.ubcsc.checkout.ui.theme.OceanSurface
import com.ubcsc.checkout.ui.theme.TextMuted
import com.ubcsc.checkout.ui.theme.UnavailableRed
import com.ubcsc.checkout.ui.util.CraftImageMapper
import com.ubcsc.checkout.viewmodel.CheckoutUiState
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import com.ubcsc.checkout.viewmodel.Craft
import com.ubcsc.checkout.viewmodel.CrewEntry
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private const val EDIT_INACTIVITY_TIMEOUT_MS = 60_000L
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
    state:     CheckoutUiState.EditingCheckout,
    viewModel: CheckoutViewModel
) {
    LaunchedEffect(Unit) {
        delay(EDIT_INACTIVITY_TIMEOUT_MS)
        viewModel.resetToIdle()
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager       = LocalFocusManager.current
    fun dismissKeyboard()  { keyboardController?.hide(); focusManager.clearFocus() }

    // ── Local editable state ─────────────────────────────────────────────────
    val crewList     = remember { mutableStateListOf<CrewEntry>().also { it.addAll(state.crew) } }
    var selectedCraft by remember { mutableStateOf(state.selectedCraft) }
    var returnTime    by remember { mutableStateOf(state.checkout.expectedReturnTime) }
    var newCrewName   by remember { mutableStateOf("") }
    var showTimePicker by remember { mutableStateOf(false) }

    val initialTime   = remember { returnTime ?: LocalTime.now().plusHours(2).withSecond(0).withNano(0) }
    val timePickerState = rememberTimePickerState(
        initialHour   = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour      = false
    )

    // ── Time picker dialog ───────────────────────────────────────────────────
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title            = { Text("Set return time", color = Color.White) },
            text             = {
                TimePicker(
                    state  = timePickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor                               = OceanSurface,
                        selectorColor                                = LocalKioskColors.current.accentMid,
                        containerColor                               = CardBlue,
                        periodSelectorBorderColor                    = LocalKioskColors.current.accentMid,
                        timeSelectorSelectedContainerColor           = LocalKioskColors.current.accentMid.copy(alpha = 0.3f),
                        timeSelectorUnselectedContainerColor         = OceanSurface,
                        timeSelectorSelectedContentColor             = LocalKioskColors.current.accent,
                        timeSelectorUnselectedContentColor           = LocalKioskColors.current.textWarm,
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

    // ── Full-screen layout ───────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepOcean)
    ) {
        // Accent bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Brush.horizontalGradient(
                    listOf(LocalKioskColors.current.accentMid, LocalKioskColors.current.accent, LocalKioskColors.current.accentMid)
                ))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text       = "Edit Checkout",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                    Text(
                        text  = state.member.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalKioskColors.current.accent
                    )
                }
                TextButton(onClick = { dismissKeyboard(); viewModel.goBack() }) {
                    Text("← Back", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp), color = DividerColor)

            // ── Two-column body ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // LEFT: Craft selector + ETR
                Column(
                    modifier  = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CraftSection(
                        crafts        = state.crafts,
                        selectedCraft = selectedCraft,
                        onSelect      = { selectedCraft = it },
                        modifier      = Modifier.weight(1f)
                    )
                    EtrSection(
                        returnTime      = returnTime,
                        onSetTime       = { returnTime = it },
                        onShowPicker    = { showTimePicker = true },
                        onClear         = { returnTime = null }
                    )
                }

                // RIGHT: Crew
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    CrewSection(
                        crewList    = crewList,
                        newName     = newCrewName,
                        onNameChange = { newCrewName = it },
                        onAddName   = {
                            val n = newCrewName.trim()
                            if (n.isNotBlank()) { crewList.add(CrewEntry(n, isGuest = false)); newCrewName = "" }
                        },
                        onAddGuest  = { crewList.add(CrewEntry("Guest", isGuest = true)) },
                        onRemove    = { crewList.removeAt(it) },
                        modifier    = Modifier.weight(1f)
                    )
                }
            }

            // ── Bottom action bar ────────────────────────────────────────────
            HorizontalDivider(color = DividerColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick  = { dismissKeyboard(); viewModel.goBack() },
                    modifier = Modifier.height(52.dp).weight(1f),
                    shape    = RoundedCornerShape(12.dp)
                ) { Text("← Back", color = LocalKioskColors.current.textWarm) }

                ElevatedButton(
                    onClick   = {
                        dismissKeyboard()
                        val craft = selectedCraft ?: return@ElevatedButton
                        viewModel.onSaveCheckoutEdit(
                            member              = state.member,
                            checkout            = state.checkout,
                            craft               = craft,
                            crew                = crewList.toList(),
                            expectedReturnHours = computeEtrHoursFromTime(returnTime)
                        )
                    },
                    enabled   = selectedCraft != null,
                    modifier  = Modifier.height(52.dp).weight(2f),
                    shape     = RoundedCornerShape(12.dp),
                    colors    = ButtonDefaults.elevatedButtonColors(
                        containerColor         = LocalKioskColors.current.accentMid,
                        contentColor           = Color.White,
                        disabledContainerColor = DividerColor,
                        disabledContentColor   = TextMuted
                    ),
                    elevation = ButtonDefaults.elevatedButtonElevation(8.dp)
                ) {
                    Text("Save Changes", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Craft section ────────────────────────────────────────────────────────────

@Composable
private fun CraftSection(
    crafts:        List<Craft>,
    selectedCraft: Craft?,
    onSelect:      (Craft) -> Unit,
    modifier:      Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionLabel("CRAFT")
        Spacer(Modifier.height(8.dp))

        val grouped = crafts
            .groupBy { it.craftClass }
            .entries
            .sortedBy { it.key }

        if (grouped.isEmpty()) {
            Text("No authorized crafts", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                grouped.forEach { (cls, craftsInClass) ->
                    item(key = "header_$cls") {
                        Text(
                            text      = cls.uppercase(),
                            style     = MaterialTheme.typography.labelSmall,
                            color     = LocalKioskColors.current.accent,
                            fontWeight = FontWeight.Bold,
                            modifier  = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(craftsInClass, key = { it.id }) { craft ->
                        CraftRow(
                            craft      = craft,
                            isSelected = craft.id == selectedCraft?.id,
                            onSelect   = { onSelect(craft) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CraftRow(
    craft:      Craft,
    isSelected: Boolean,
    onSelect:   () -> Unit
) {
    val accentColor = if (isSelected) LocalKioskColors.current.accent else LocalKioskColors.current.accentMid
    val alpha       = if (craft.isAvailable || isSelected) 1f else 0.5f

    Surface(
        onClick = onSelect,
        shape   = RoundedCornerShape(10.dp),
        color   = if (isSelected) LocalKioskColors.current.accentMid.copy(alpha = 0.15f) else CardBlue,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .alpha(alpha)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accentColor.copy(alpha = 0.8f) else DividerColor,
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        Row(
            modifier          = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter            = painterResource(CraftImageMapper.getDrawableRes(craft.craftClass)),
                contentDescription = null,
                modifier           = Modifier.size(22.dp),
                contentScale       = ContentScale.Fit,
                colorFilter        = when {
                    isSelected         -> CraftImageMapper.iconColorFilter(LocalKioskColors.current.accent)
                    craft.isAvailable  -> CraftImageMapper.iconColorFilter(LocalKioskColors.current.accentMid)
                    else               -> CraftImageMapper.filterUnavailable
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = craft.displayName,
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = if (isSelected) LocalKioskColors.current.accent else Color.White,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines   = 1
                )
            }
            Text(
                text  = craft.code,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            if (!craft.isAvailable && !isSelected) {
                Text(
                    text  = "out",
                    style = MaterialTheme.typography.labelSmall,
                    color = UnavailableRed.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ── ETR section ──────────────────────────────────────────────────────────────

@Composable
private fun EtrSection(
    returnTime:   LocalTime?,
    onSetTime:    (LocalTime) -> Unit,
    onShowPicker: () -> Unit,
    onClear:      () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("RETURN TIME")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.AccessTime, null,
                tint     = LocalKioskColors.current.textWarm,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Back by", style = MaterialTheme.typography.bodyMedium, color = LocalKioskColors.current.textWarm)
            Spacer(Modifier.width(10.dp))
            OutlinedButton(
                onClick        = onShowPicker,
                shape          = RoundedCornerShape(8.dp),
                border         = BorderStroke(1.dp, if (returnTime != null) LocalKioskColors.current.accentMid else DividerColor),
                colors         = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (returnTime != null) LocalKioskColors.current.accent else TextMuted
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
                IconButton(
                    onClick  = onClear,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.Close, "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 3, 4).forEach { hours ->
                val presetTime = LocalTime.now().plusHours(hours.toLong()).withSecond(0).withNano(0)
                val isSelected = returnTime?.hour == presetTime.hour && returnTime?.minute == presetTime.minute
                OutlinedButton(
                    onClick        = { onSetTime(presetTime) },
                    shape          = RoundedCornerShape(8.dp),
                    border         = BorderStroke(1.dp, if (isSelected) LocalKioskColors.current.accentMid else DividerColor),
                    colors         = ButtonDefaults.outlinedButtonColors(
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

// ── Crew section ─────────────────────────────────────────────────────────────

@Composable
private fun CrewSection(
    crewList:     List<CrewEntry>,
    newName:      String,
    onNameChange: (String) -> Unit,
    onAddName:    () -> Unit,
    onAddGuest:   () -> Unit,
    onRemove:     (Int) -> Unit,
    modifier:     Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager       = LocalFocusManager.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("CREW")
            if (crewList.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "(${crewList.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalKioskColors.current.accent
                )
            }
        }

        // Crew list
        LazyColumn(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (crewList.isEmpty()) {
                item {
                    Text(
                        text  = "No crew — solo trip",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            items(crewList.size, key = { it }) { index ->
                val entry = crewList[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(OceanSurface)
                        .border(1.dp, DividerColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (entry.isGuest) {
                            Text(
                                "GUEST",
                                style     = MaterialTheme.typography.labelSmall,
                                color     = LocalKioskColors.current.accentMid,
                                fontWeight = FontWeight.Bold,
                                modifier  = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(LocalKioskColors.current.accentMid.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text  = entry.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                    IconButton(
                        onClick  = { onRemove(index) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Filled.Close, "Remove", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Add crew controls
        HorizontalDivider(color = DividerColor)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = newName,
                onValueChange = onNameChange,
                modifier      = Modifier.weight(1f).height(52.dp),
                placeholder   = { Text("Crew member name", color = TextMuted, fontSize = 13.sp) },
                singleLine    = true,
                shape         = RoundedCornerShape(10.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = LocalKioskColors.current.accentMid,
                    unfocusedBorderColor = DividerColor,
                    focusedTextColor     = Color.White,
                    unfocusedTextColor   = Color.White,
                    cursorColor          = LocalKioskColors.current.accent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onAddName()
                })
            )
            ElevatedButton(
                onClick   = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onAddName()
                },
                modifier  = Modifier.height(52.dp),
                shape     = RoundedCornerShape(10.dp),
                colors    = ButtonDefaults.elevatedButtonColors(
                    containerColor = LocalKioskColors.current.accentMid,
                    contentColor   = Color.White
                )
            ) {
                Icon(Icons.Filled.PersonAdd, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add", fontWeight = FontWeight.SemiBold)
            }
        }
        OutlinedButton(
            onClick = onAddGuest,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape    = RoundedCornerShape(10.dp),
            border   = BorderStroke(1.dp, DividerColor),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
        ) {
            Text("+ Add Guest", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text          = text,
        style         = MaterialTheme.typography.labelSmall,
        color         = LocalKioskColors.current.accent,
        fontWeight    = FontWeight.Bold,
        letterSpacing = 1.5.sp
    )
}
