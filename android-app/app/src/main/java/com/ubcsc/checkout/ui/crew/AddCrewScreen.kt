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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.zIndex
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.ubcsc.checkout.ui.theme.LocalKioskColors
import com.ubcsc.checkout.ui.theme.TextMuted
import com.ubcsc.checkout.ui.theme.UnavailableRed
import com.ubcsc.checkout.viewmodel.CheckoutUiState
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import com.ubcsc.checkout.viewmodel.Craft
import com.ubcsc.checkout.viewmodel.CrewEntry
import com.ubcsc.checkout.viewmodel.Member
import com.ubcsc.checkout.viewmodel.MemberSummary
import kotlinx.coroutines.delay

private const val CREW_INACTIVITY_TIMEOUT_MS = 60_000L

@Composable
fun AddCrewScreen(uiState: CheckoutUiState, viewModel: CheckoutViewModel) {
    val memberList by viewModel.memberList.collectAsState()
    LaunchedEffect(Unit) {
        delay(CREW_INACTIVITY_TIMEOUT_MS)
        viewModel.resetToIdle()
    }
    when (uiState) {
        is CheckoutUiState.AddingCrew ->
            AddCrewContent(
                memberName     = uiState.member.name,
                craftName      = uiState.craft.displayName,
                crew           = uiState.crew,
                memberList     = memberList,
                isAwaitingNfc  = false,
                onAddByName    = { name -> viewModel.onAddCrewByName(uiState, name) },
                onAddByMember  = { id, name -> viewModel.onAddCrewByMember(uiState, id, name) },
                onAddGuest     = { viewModel.onAddCrewAsGuest(uiState) },
                onScanCard     = { viewModel.onScanForCrew(uiState) },
                onRemove       = { idx -> viewModel.onRemoveCrew(uiState, idx) },
                onDone         = { viewModel.onCrewDone(uiState, null) },
                onCancel       = { viewModel.goBack() }
            )
        is CheckoutUiState.AwaitingCrewCard ->
            AddCrewContent(
                memberName     = uiState.member.name,
                craftName      = uiState.craft.displayName,
                crew           = uiState.crew,
                memberList     = memberList,
                isAwaitingNfc  = true,
                onAddByName    = {},
                onAddByMember  = { _, _ -> },
                onAddGuest     = {},
                onScanCard     = {},
                onRemove       = {},
                onDone         = {},
                onCancel       = { viewModel.onCancelCrewScan() }
            )
        else -> Unit
    }
}

@Composable
private fun AddCrewContent(
    memberName:    String,
    craftName:     String,
    crew:          List<CrewEntry>,
    memberList:    List<MemberSummary> = emptyList(),
    isAwaitingNfc: Boolean,
    onAddByName:   (String) -> Unit,
    onAddByMember: (Int, String) -> Unit,
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
                .background(Brush.horizontalGradient(listOf(LocalKioskColors.current.accentMid, LocalKioskColors.current.accent, LocalKioskColors.current.accentMid)))
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
                        color         = LocalKioskColors.current.accent,
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
                                MemberSearchField(
                                    memberList    = memberList,
                                    onAddByMember = onAddByMember,
                                    onAddByName   = onAddByName
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick  = onScanCard,
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape    = RoundedCornerShape(12.dp),
                                        colors   = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = LocalKioskColors.current.accentMid.copy(alpha = 0.15f),
                                            contentColor   = LocalKioskColors.current.accent
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
                                            tint     = LocalKioskColors.current.textWarm,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Guest", color = LocalKioskColors.current.textWarm, fontWeight = FontWeight.Medium)
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
                                color         = LocalKioskColors.current.accent,
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
                                modifier  = Modifier.fillMaxWidth().height(72.dp),
                                shape     = RoundedCornerShape(12.dp),
                                colors    = ButtonDefaults.elevatedButtonColors(
                                    containerColor         = LocalKioskColors.current.accentMid,
                                    contentColor           = Color.White,
                                    disabledContainerColor = LocalKioskColors.current.accentMid.copy(alpha = 0.2f),
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
                                color         = LocalKioskColors.current.accent,
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
                                modifier  = Modifier.fillMaxWidth().height(72.dp),
                                shape     = RoundedCornerShape(12.dp),
                                colors    = ButtonDefaults.elevatedButtonColors(
                                    containerColor         = LocalKioskColors.current.accentMid,
                                    contentColor           = Color.White,
                                    disabledContainerColor = LocalKioskColors.current.accentMid.copy(alpha = 0.2f),
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
                                    MemberSearchField(
                                        memberList    = memberList,
                                        onAddByMember = onAddByMember,
                                        onAddByName   = onAddByName
                                    )

                                    FilledTonalButton(
                                        onClick  = onScanCard,
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        shape    = RoundedCornerShape(12.dp),
                                        colors   = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = LocalKioskColors.current.accentMid.copy(alpha = 0.15f),
                                            contentColor   = LocalKioskColors.current.accent
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
                                            tint     = LocalKioskColors.current.textWarm,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Add Guest", color = LocalKioskColors.current.textWarm, fontWeight = FontWeight.Medium)
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
                    .background(if (entry.isGuest) UnavailableRed.copy(0.15f) else LocalKioskColors.current.accentMid.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person, null,
                    tint     = if (entry.isGuest) UnavailableRed else LocalKioskColors.current.accent,
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
private fun MemberSearchField(
    memberList:    List<MemberSummary>,
    onAddByMember: (Int, String) -> Unit,
    onAddByName:   (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    val filtered = remember(text, memberList) {
        val q = text.trim()
        if (q.length < 2) emptyList()
        else memberList
            .filter { it.name.contains(q, ignoreCase = true) }
            .sortedWith(compareBy(
                { !it.name.startsWith(q, ignoreCase = true) },
                { !it.name.split(" ").any { w -> w.startsWith(q, ignoreCase = true) } },
                { it.name.lowercase() }
            ))
            .take(6)
    }

    fun submitFreeText() {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) { onAddByName(trimmed); text = "" }
    }

    Box(Modifier.fillMaxWidth().zIndex(10f)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                placeholder   = { Text("Search or type crew name…", color = TextMuted) },
                leadingIcon   = { Icon(Icons.Filled.Person, null, tint = TextMuted, modifier = Modifier.size(20.dp)) },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction      = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submitFreeText() }),
                modifier = Modifier.fillMaxWidth().forceShowSoftKeyboard(),
                shape  = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = LocalKioskColors.current.accentMid,
                    unfocusedBorderColor = DividerColor,
                    focusedTextColor     = Color.White,
                    unfocusedTextColor   = Color.White,
                    cursorColor          = LocalKioskColors.current.accent
                )
            )
            ElevatedButton(
                onClick  = { submitFreeText() },
                enabled  = text.trim().isNotEmpty() && filtered.isEmpty(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.elevatedButtonColors(
                    containerColor         = LocalKioskColors.current.accentMid.copy(alpha = 0.9f),
                    contentColor           = Color.White,
                    disabledContainerColor = LocalKioskColors.current.accentMid.copy(alpha = 0.2f),
                    disabledContentColor   = TextMuted
                )
            ) { Text("Add as Non-Member", fontWeight = FontWeight.Medium) }
        }

        // Member suggestions float above content below
        if (filtered.isNotEmpty()) {
            Card(
                modifier  = Modifier.fillMaxWidth().padding(top = 60.dp),
                shape     = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                colors    = CardDefaults.cardColors(containerColor = Color(0xFF1A2E42))
            ) {
                filtered.forEach { member ->
                    Text(
                        text     = member.name,
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAddByMember(member.id, member.name); text = "" }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                }
            }
        }
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
            .background(LocalKioskColors.current.accentMid.copy(alpha = 0.08f))
            .border(1.dp, LocalKioskColors.current.accentMid.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Nfc, null, tint = LocalKioskColors.current.accent.copy(alpha = alpha), modifier = Modifier.size(56.dp))
            Text(
                "Tap crew member's card",
                style      = MaterialTheme.typography.titleMedium,
                color      = LocalKioskColors.current.accent.copy(alpha = alpha),
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
            onAddByName = {}, onAddByMember = { _, _ -> }, onAddGuest = {}, onScanCard = {},
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
            onAddByName = {}, onAddByMember = { _, _ -> }, onAddGuest = {}, onScanCard = {},
            onRemove = {}, onDone = {}, onCancel = {}
        )
    }
}
