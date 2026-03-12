package com.ubcsc.checkout.ui.crew

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.ubcsc.checkout.ui.util.forceShowSoftKeyboard
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubcsc.checkout.ui.theme.CardBlue
import com.ubcsc.checkout.ui.theme.DeepOcean
import com.ubcsc.checkout.ui.theme.DigitalCheckoutTheme
import com.ubcsc.checkout.ui.theme.DividerColor
import com.ubcsc.checkout.ui.theme.TealLight
import com.ubcsc.checkout.ui.theme.TealMid
import com.ubcsc.checkout.ui.theme.TextMuted
import com.ubcsc.checkout.ui.theme.TextSecondary
import com.ubcsc.checkout.ui.theme.UnavailableRed
import com.ubcsc.checkout.viewmodel.CheckoutUiState
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import com.ubcsc.checkout.viewmodel.Craft
import com.ubcsc.checkout.viewmodel.CrewEntry
import com.ubcsc.checkout.viewmodel.Member
import kotlinx.coroutines.delay

private const val CREW_INACTIVITY_TIMEOUT_MS = 60_000L

@Composable
fun AddCrewScreen(uiState: CheckoutUiState, viewModel: CheckoutViewModel) {
    LaunchedEffect(Unit) {
        delay(CREW_INACTIVITY_TIMEOUT_MS)
        viewModel.resetToIdle()
    }
    when (uiState) {
        is CheckoutUiState.AddingCrew ->
            AddCrewContent(
                memberName    = uiState.member.name,
                craftName     = uiState.craft.displayName,
                crew          = uiState.crew,
                isAwaitingNfc = false,
                onAddByName   = { name -> viewModel.onAddCrewByName(uiState, name) },
                onAddGuest    = { viewModel.onAddCrewAsGuest(uiState) },
                onScanCard    = { viewModel.onScanForCrew(uiState) },
                onRemove      = { idx -> viewModel.onRemoveCrew(uiState, idx) },
                onDone        = { viewModel.onCrewDone(uiState, null) },
                onCancel      = { viewModel.goBack() }
            )
        is CheckoutUiState.AwaitingCrewCard ->
            AddCrewContent(
                memberName    = uiState.member.name,
                craftName     = uiState.craft.displayName,
                crew          = uiState.crew,
                isAwaitingNfc = true,
                onAddByName   = {},
                onAddGuest    = {},
                onScanCard    = {},
                onRemove      = {},
                onDone        = {},
                onCancel      = { viewModel.onCancelCrewScan() }
            )
        else -> Unit
    }
}

@Composable
private fun AddCrewContent(
    memberName:    String,
    craftName:     String,
    crew:          List<CrewEntry>,
    isAwaitingNfc: Boolean,
    onAddByName:   (String) -> Unit,
    onAddGuest:    () -> Unit,
    onScanCard:    () -> Unit,
    onRemove:      (Int) -> Unit,
    onDone:        () -> Unit,
    onCancel:      () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepOcean)
    ) {
        // Top accent bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Brush.horizontalGradient(listOf(TealMid, TealLight, TealMid)))
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
                    .padding(horizontal = 28.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text       = "Add Crew",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                    Text(
                        text          = "$craftName  ·  $memberName",
                        style         = MaterialTheme.typography.bodyMedium,
                        color         = TealLight,
                        letterSpacing = 0.5.sp
                    )
                }
                TextButton(onClick = onCancel) {
                    Text(
                        if (isAwaitingNfc) "Cancel Scan" else "← Back",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .height(1.dp)
                    .background(DividerColor)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Responsive body
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                val isPortrait = maxWidth < 600.dp

                if (isPortrait) {
                    // -----------------------------------------------------------
                    // Portrait: input at top, crew panel (with Continue) below
                    // -----------------------------------------------------------
                    Column(modifier = Modifier.fillMaxSize()) {

                        // NFC wait OR input controls
                        AnimatedVisibility(
                            visible = isAwaitingNfc,
                            enter   = fadeIn(tween(200)),
                            exit    = fadeOut(tween(200))
                        ) {
                            NfcWaitPanel()
                        }
                        AnimatedVisibility(
                            visible = !isAwaitingNfc,
                            enter   = fadeIn(tween(200)),
                            exit    = fadeOut(tween(200))
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                NameInputField(onSubmit = onAddByName)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick  = onScanCard,
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape    = RoundedCornerShape(12.dp),
                                        colors   = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = TealMid.copy(alpha = 0.15f),
                                            contentColor   = TealLight
                                        )
                                    ) {
                                        Icon(Icons.Filled.CreditCard, null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Scan Card", fontWeight = FontWeight.Medium)
                                    }
                                    OutlinedButton(
                                        onClick  = onAddGuest,
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape    = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.PersonAdd, null,
                                            tint     = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Guest", color = TextSecondary, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Crew panel: list + Continue button — takes remaining space
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(CardBlue)
                                .border(1.dp, DividerColor, RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text          = "Crew  (${crew.size})",
                                style         = MaterialTheme.typography.labelMedium,
                                color         = TealLight,
                                letterSpacing = 1.5.sp,
                                fontWeight    = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            if (crew.isEmpty()) {
                                Box(
                                    modifier         = Modifier.fillMaxWidth().weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Add at least one crew member to continue",
                                        style     = MaterialTheme.typography.bodyMedium,
                                        color     = TextMuted
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier            = Modifier.fillMaxWidth().weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(crew) { index, entry ->
                                        CrewRow(entry = entry, onRemove = { onRemove(index) })
                                        if (index < crew.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                color    = DividerColor
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            ElevatedButton(
                                onClick   = onDone,
                                enabled   = !isAwaitingNfc && crew.isNotEmpty(),
                                modifier  = Modifier.fillMaxWidth().height(52.dp),
                                shape     = RoundedCornerShape(12.dp),
                                colors    = ButtonDefaults.elevatedButtonColors(
                                    containerColor         = TealMid,
                                    contentColor           = Color.White,
                                    disabledContainerColor = TealMid.copy(alpha = 0.2f),
                                    disabledContentColor   = TextMuted
                                ),
                                elevation = ButtonDefaults.elevatedButtonElevation(8.dp)
                            ) {
                                Text(
                                    "Continue  →",
                                    fontWeight = FontWeight.SemiBold,
                                    style      = MaterialTheme.typography.titleMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {
                    // -----------------------------------------------------------
                    // Landscape: crew panel (with Continue) on left, controls on right
                    // -----------------------------------------------------------
                    Row(
                        modifier              = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Left panel: crew list + Continue button
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(CardBlue)
                                .border(1.dp, DividerColor, RoundedCornerShape(16.dp))
                                .padding(20.dp)
                        ) {
                            Text(
                                text          = "Crew  (${crew.size})",
                                style         = MaterialTheme.typography.labelMedium,
                                color         = TealLight,
                                letterSpacing = 1.5.sp,
                                fontWeight    = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (crew.isEmpty()) {
                                Box(
                                    modifier         = Modifier.fillMaxWidth().weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Add at least one crew member to continue",
                                        style     = MaterialTheme.typography.bodyMedium,
                                        color     = TextMuted
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier            = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(crew) { index, entry ->
                                        CrewRow(entry = entry, onRemove = { onRemove(index) })
                                        if (index < crew.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                color    = DividerColor
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            ElevatedButton(
                                onClick   = onDone,
                                enabled   = !isAwaitingNfc && crew.isNotEmpty(),
                                modifier  = Modifier.fillMaxWidth().height(56.dp),
                                shape     = RoundedCornerShape(12.dp),
                                colors    = ButtonDefaults.elevatedButtonColors(
                                    containerColor         = TealMid,
                                    contentColor           = Color.White,
                                    disabledContainerColor = TealMid.copy(alpha = 0.2f),
                                    disabledContentColor   = TextMuted
                                ),
                                elevation = ButtonDefaults.elevatedButtonElevation(8.dp)
                            ) {
                                Text(
                                    "Continue  →",
                                    fontWeight = FontWeight.SemiBold,
                                    style      = MaterialTheme.typography.titleMedium
                                )
                            }
                        }

                        // Right panel: add controls only
                        Column(
                            modifier            = Modifier.width(260.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AnimatedVisibility(
                                visible = isAwaitingNfc,
                                enter   = fadeIn(tween(200)),
                                exit    = fadeOut(tween(200))
                            ) {
                                NfcWaitPanel()
                            }

                            AnimatedVisibility(
                                visible = !isAwaitingNfc,
                                enter   = fadeIn(tween(200)),
                                exit    = fadeOut(tween(200))
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    NameInputField(onSubmit = onAddByName)

                                    FilledTonalButton(
                                        onClick  = onScanCard,
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        shape    = RoundedCornerShape(12.dp),
                                        colors   = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = TealMid.copy(alpha = 0.15f),
                                            contentColor   = TealLight
                                        )
                                    ) {
                                        Icon(Icons.Filled.CreditCard, null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Scan Member Card", fontWeight = FontWeight.Medium)
                                    }

                                    OutlinedButton(
                                        onClick  = onAddGuest,
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        shape    = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.PersonAdd, null,
                                            tint     = TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Add Guest", color = TextSecondary, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrewRow(entry: CrewEntry, onRemove: () -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (entry.isGuest) UnavailableRed.copy(0.15f) else TealMid.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person, null,
                    tint     = if (entry.isGuest) UnavailableRed else TealLight,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text       = entry.name,
                    style      = MaterialTheme.typography.bodyLarge,
                    color      = Color.White,
                    fontWeight = FontWeight.Medium
                )
                if (entry.isGuest) {
                    Text("Guest", style = MaterialTheme.typography.labelSmall, color = UnavailableRed)
                } else if (entry.cardUid != null) {
                    Text(
                        "Card: …${entry.cardUid.takeLast(4)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, "Remove", tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun NameInputField(onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    fun submit() {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) { onSubmit(trimmed); text = "" }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value         = text,
            onValueChange = { text = it },
            placeholder   = { Text("Crew member name…", color = TextMuted) },
            leadingIcon   = { Icon(Icons.Filled.Person, null, tint = TextMuted, modifier = Modifier.size(20.dp)) },
            singleLine    = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction      = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).forceShowSoftKeyboard(),
            shape  = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = TealMid,
                unfocusedBorderColor = DividerColor,
                focusedTextColor     = Color.White,
                unfocusedTextColor   = Color.White,
                cursorColor          = TealLight
            )
        )
        ElevatedButton(
            onClick  = { submit() },
            enabled  = text.trim().isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.elevatedButtonColors(
                containerColor         = TealMid.copy(alpha = 0.9f),
                contentColor           = Color.White,
                disabledContainerColor = TealMid.copy(alpha = 0.2f),
                disabledContentColor   = TextMuted
            )
        ) { Text("Add Name", fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun NfcWaitPanel() {
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "nfc_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TealMid.copy(alpha = 0.08f))
            .border(1.dp, TealMid.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Nfc, null, tint = TealLight.copy(alpha = alpha), modifier = Modifier.size(56.dp))
            Text(
                "Tap crew member's card",
                style      = MaterialTheme.typography.titleMedium,
                color      = TealLight.copy(alpha = alpha),
                fontWeight = FontWeight.SemiBold
            )
            Text("Or tap Cancel Scan to go back", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun AddCrewPreviewLandscape() {
    DigitalCheckoutTheme {
        AddCrewContent(
            memberName = "Alex Sailor", craftName = "Vanguard #1",
            crew = listOf(CrewEntry("Jordan Lee", false), CrewEntry("Guest", true)),
            isAwaitingNfc = false,
            onAddByName = {}, onAddGuest = {}, onScanCard = {},
            onRemove = {}, onDone = {}, onCancel = {}
        )
    }
}

@Preview(widthDp = 400, heightDp = 860, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun AddCrewPreviewPortrait() {
    DigitalCheckoutTheme {
        AddCrewContent(
            memberName = "Alex Sailor", craftName = "Vanguard #1",
            crew = listOf(CrewEntry("Jordan Lee", false), CrewEntry("Guest", true)),
            isAwaitingNfc = false,
            onAddByName = {}, onAddGuest = {}, onScanCard = {},
            onRemove = {}, onDone = {}, onCancel = {}
        )
    }
}
