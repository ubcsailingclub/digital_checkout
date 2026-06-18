package com.ubcsc.checkout.ui.checkin

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.ubcsc.checkout.ui.util.CraftImageMapper
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubcsc.checkout.ui.theme.ActiveAmber
import com.ubcsc.checkout.ui.theme.AvailableGreen
import com.ubcsc.checkout.ui.theme.CardBlue
import com.ubcsc.checkout.ui.theme.DeepOcean
import com.ubcsc.checkout.ui.theme.DigitalCheckoutTheme
import com.ubcsc.checkout.ui.theme.DividerColor
import com.ubcsc.checkout.ui.theme.LocalKioskColors
import com.ubcsc.checkout.ui.theme.TextMuted
import com.ubcsc.checkout.ui.theme.UnavailableRed
import com.ubcsc.checkout.viewmodel.ActiveSession
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import com.ubcsc.checkout.viewmodel.Member
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

private const val INACTIVITY_TIMEOUT_MS = 45_000L
private val ETR_FORMAT = DateTimeFormatter.ofPattern("h:mm a")

@Composable
fun CheckinSelectScreen(
    member:    Member?,   // null when started from the idle screen (no card scanned yet)
    sessions:  List<ActiveSession>,
    viewModel: CheckoutViewModel
) {
    LaunchedEffect(Unit) {
        delay(INACTIVITY_TIMEOUT_MS)
        viewModel.resetToIdle()
    }
    val isIdleMode = member == null
    CheckinSelectContent(
        memberName      = member?.name,
        sessions        = sessions,
        isIdleMode      = isIdleMode,
        onSelect        = { session ->
            if (member != null) viewModel.onSelectSessionForCheckin(member, session)
            else                viewModel.onSelectSessionIdle(session)
        },
        onSelectMultiple = { selected -> viewModel.onSubmitBulkCheckin(selected) },
        onCancel         = { viewModel.goBack() }
    )
}

@Composable
private fun CheckinSelectContent(
    memberName:       String?,
    sessions:         List<ActiveSession>,
    isIdleMode:       Boolean,
    onSelect:         (ActiveSession) -> Unit,
    onSelectMultiple: (List<ActiveSession>) -> Unit,
    onCancel:         () -> Unit
) {
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }

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
                        text       = "Check In a Boat",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                    if (memberName != null) {
                        Text(
                            text          = memberName,
                            style         = MaterialTheme.typography.bodyMedium,
                            color         = LocalKioskColors.current.accent,
                            letterSpacing = 0.5.sp
                        )
                    } else {
                        Text(
                            text  = "Tap one or more boats to select, then tap Check In",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
                TextButton(onClick = onCancel) {
                    Text(
                        "← Back",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .height(1.dp)
                    .background(DividerColor)
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns               = GridCells.Adaptive(minSize = 220.dp),
                contentPadding        = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement   = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier              = Modifier.weight(1f)
            ) {
                items(sessions, key = { it.sessionId }) { session ->
                    val isSelected = session.sessionId in selectedIds
                    SessionCard(
                        session    = session,
                        isSelected = isIdleMode && isSelected,
                        onSelect   = {
                            if (isIdleMode) {
                                selectedIds = if (isSelected)
                                    selectedIds - session.sessionId
                                else
                                    selectedIds + session.sessionId
                            } else {
                                onSelect(session)
                            }
                        }
                    )
                }
            }

            // Multi-select action bar (idle mode only)
            if (isIdleMode) {
                HorizontalDivider(color = DividerColor)
                val count = selectedIds.size
                Button(
                    onClick  = {
                        val selected = sessions.filter { it.sessionId in selectedIds }
                        when {
                            selected.size == 1 -> onSelect(selected.first())
                            selected.size > 1  -> onSelectMultiple(selected)
                        }
                    },
                    enabled  = count > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .height(56.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = LocalKioskColors.current.accentMid,
                        contentColor           = Color.White,
                        disabledContainerColor = DividerColor,
                        disabledContentColor   = TextMuted
                    )
                ) {
                    Text(
                        text       = if (count == 0) "Select boats to check in"
                                     else "Check In $count Boat${if (count != 1) "s" else ""}",
                        fontSize   = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionCard(
    session:    ActiveSession,
    isSelected: Boolean,
    onSelect:   () -> Unit
) {
    val accentColor = when {
        isSelected       -> LocalKioskColors.current.accent
        session.isOverdue -> UnavailableRed
        else             -> LocalKioskColors.current.accentMid
    }

    Surface(
        onClick        = onSelect,
        modifier       = Modifier.height(160.dp),
        shape          = RoundedCornerShape(16.dp),
        color          = CardBlue,
        tonalElevation = 6.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = if (isSelected) 2.dp else 1.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(accentColor.copy(alpha = if (isSelected) 0.9f else 0.6f),
                               accentColor.copy(alpha = if (isSelected) 0.4f else 0.1f))
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row: icon + craft name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DeepOcean.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter            = painterResource(CraftImageMapper.getDrawableRes(session.craftClass)),
                            contentDescription = null,
                            modifier           = Modifier.size(28.dp),
                            contentScale       = ContentScale.Fit,
                            colorFilter        = if (session.isOverdue) CraftImageMapper.filterUnavailable
                                                 else CraftImageMapper.iconColorFilter(LocalKioskColors.current.accent),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text       = session.craftName,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = Color.White,
                            maxLines   = 1
                        )
                        Text(
                            text  = session.craftCode,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }

                // Skipper name
                Text(
                    text     = session.memberName,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = LocalKioskColors.current.textWarm,
                    maxLines = 1
                )

                // Bottom row: time out + ETR / overdue badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text  = "Out ${session.timeOut}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )

                    when {
                        session.isOverdue -> OverdueBadge()
                        session.expectedReturnTime != null -> EtrBadge(
                            label = "Back by ${session.expectedReturnTime.format(ETR_FORMAT)}"
                        )
                    }
                }
            }

            // Selection checkmark (idle multi-select mode)
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(LocalKioskColors.current.accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint               = Color.White,
                        modifier           = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EtrBadge(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(LocalKioskColors.current.accentMid.copy(alpha = 0.15f))
            .border(1.dp, LocalKioskColors.current.accentMid.copy(alpha = 0.4f), RoundedCornerShape(50.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text       = label,
            style      = MaterialTheme.typography.labelSmall,
            color      = LocalKioskColors.current.accent,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun OverdueBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(UnavailableRed.copy(alpha = 0.15f))
            .border(1.dp, UnavailableRed.copy(alpha = 0.5f), RoundedCornerShape(50.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = Icons.Filled.Warning,
            contentDescription = null,
            tint               = UnavailableRed,
            modifier           = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text       = "Overdue",
            style      = MaterialTheme.typography.labelSmall,
            color      = UnavailableRed,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun CheckinSelectPreview() {
    DigitalCheckoutTheme {
        CheckinSelectContent(
            memberName = "Alex Sailor",
            sessions = listOf(
                ActiveSession(1, "LZ01", "Laser #1",        "Laser",       "Jordan Kim",  "1h 20m", java.time.LocalTime.of(15, 30), isOverdue = false),
                ActiveSession(2, "QT02", "Quest #2",        "RS Quest",    "Sam Chen",    "3h 05m", java.time.LocalTime.of(13, 0),  isOverdue = true),
                ActiveSession(3, "KD01", "Double Kayak #1", "Kayak Double","Riley Park",  "45m",    null,                           isOverdue = false),
                ActiveSession(4, "WS03", "Windsurfer L2",   "Windsurfer",  "Taylor Ng",   "2h",     java.time.LocalTime.of(16, 0),  isOverdue = false),
            ),
            isIdleMode       = false,
            onSelect         = {},
            onSelectMultiple = {},
            onCancel         = {}
        )
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun CheckinSelectIdlePreview() {
    DigitalCheckoutTheme {
        CheckinSelectContent(
            memberName = null,
            sessions = listOf(
                ActiveSession(1, "LZ01", "Laser #1", "Laser",    "Jordan Kim", "1h 20m", java.time.LocalTime.of(15, 30), isOverdue = false),
                ActiveSession(2, "QT02", "Quest #2", "RS Quest", "Sam Chen",   "3h 05m", java.time.LocalTime.of(13, 0),  isOverdue = true),
            ),
            isIdleMode       = true,
            onSelect         = {},
            onSelectMultiple = {},
            onCancel         = {}
        )
    }
}
