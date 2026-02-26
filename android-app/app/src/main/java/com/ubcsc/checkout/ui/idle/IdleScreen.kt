package com.ubcsc.checkout.ui.idle

import android.nfc.NfcAdapter
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubcsc.checkout.BuildConfig
import com.ubcsc.checkout.ui.theme.DeepOcean
import com.ubcsc.checkout.ui.theme.DigitalCheckoutTheme
import com.ubcsc.checkout.ui.theme.OceanSurface
import com.ubcsc.checkout.ui.theme.TealLight
import com.ubcsc.checkout.ui.theme.TealMid
import com.ubcsc.checkout.ui.theme.TextSecondary
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import com.ubcsc.checkout.viewmodel.RecentSession
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------
// Notebook color palette
// ---------------------------------------------------------------------------

private val PaperBg     = Color(0xFFFFFBF0)   // warm off-white — aged paper
private val PaperMargin = Color(0xFFDB6B6B)   // classic red margin line
private val PaperRule   = Color(0xFFBAD5EC)   // classic blue ruled lines
private val HeaderBg    = Color(0xFFF2E8D5)   // column-header row
private val DateBg      = Color(0xFFEBE2CE)   // date-separator row — slightly darker
private val ActiveTint  = Color(0xFFE6F4EA)   // light green tint for boats still out
private val InkDark     = Color(0xFF1A1A2E)   // near-black — main ink
private val InkMid      = Color(0xFF4A5568)   // secondary ink

// Total number of "row slots" shown (data + date separators + empty filler)
private const val TOTAL_ROWS = 10

// ---------------------------------------------------------------------------
// Log-entry ADT used internally for grouping
// ---------------------------------------------------------------------------

private sealed interface LogEntry {
    data class Session(val session: RecentSession) : LogEntry
    data class DateSep(val date: LocalDate)        : LogEntry
}

private fun groupByDate(sessions: List<RecentSession>): List<LogEntry> {
    val result   = mutableListOf<LogEntry>()
    var lastDate: LocalDate? = null
    for (session in sessions) {
        val date = session.checkoutLocalDate
        if (date != lastDate) {
            result.add(LogEntry.DateSep(date))
            lastDate = date
        }
        result.add(LogEntry.Session(session))
    }
    return result
}

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

@Composable
fun IdleScreen(viewModel: CheckoutViewModel) {
    val recentSessions by viewModel.recentSessions.collectAsState()
    val context = LocalContext.current
    val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
    // Only warn when NFC is physically present but turned off.
    // Absence of NFC is not a warning — the USB reader may be in use.
    val nfcWarning = if (nfcAdapter != null && !nfcAdapter.isEnabled) "NFC is disabled" else null

    IdleContent(
        recentSessions    = recentSessions,
        nfcWarning        = nfcWarning,
        onDebugScan       = if (BuildConfig.DEBUG) viewModel::onCardScanned else null,
        onCheckinFromIdle = viewModel::onCheckinFromIdle
    )
}

// ---------------------------------------------------------------------------
// Root layout: notebook left | NFC prompt right
// ---------------------------------------------------------------------------

@Composable
private fun IdleContent(
    recentSessions:    List<RecentSession>,
    nfcWarning:        String?,
    onDebugScan:       ((String) -> Unit)? = null,
    onCheckinFromIdle: (() -> Unit)?       = null
) {
    var timeText by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) { delay(1_000L); timeText = currentTime() }
    }

    Row(Modifier.fillMaxSize()) {
        NotebookPanel(
            recentSessions    = recentSessions,
            onCheckinFromIdle = onCheckinFromIdle,
            modifier          = Modifier.weight(0.65f).fillMaxHeight()
        )
        NfcPromptPanel(
            timeText    = timeText,
            nfcWarning  = nfcWarning,
            onDebugScan = onDebugScan,
            modifier    = Modifier.weight(0.35f).fillMaxHeight()
        )
    }
}

// ---------------------------------------------------------------------------
// Left: Notebook / logbook panel
// ---------------------------------------------------------------------------

@Composable
private fun NotebookPanel(
    recentSessions:    List<RecentSession>,
    onCheckinFromIdle: (() -> Unit)? = null,
    modifier:          Modifier      = Modifier
) {
    val today   = LocalDate.now()
    val entries = groupByDate(recentSessions)
    val emptyCount = (TOTAL_ROWS - entries.size).coerceAtLeast(0)

    Box(modifier.background(PaperBg)) {

        // Red margin line
        Box(
            Modifier
                .fillMaxHeight()
                .width(2.dp)
                .offset(x = 44.dp)
                .background(PaperMargin)
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 56.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)
        ) {

            // ── Title bar ──────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text          = "UBC Sailing Club  ·  Checkout Log",
                    color         = InkDark,
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 15.sp,
                    letterSpacing = 0.4.sp
                )
                Text(
                    text      = today.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                    color     = InkMid,
                    fontSize  = 12.sp,
                    fontStyle = FontStyle.Italic
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Column headers ─────────────────────────────────────────────
            Box(Modifier.fillMaxWidth().background(HeaderBg)) {
                LogRow(
                    skipper  = "Skipper",
                    crew     = "Crew",
                    craft    = "Craft",
                    timeOut  = "Out",
                    eta      = "ETA",
                    timeIn   = "In",
                    isHeader = true
                )
            }
            HorizontalDivider(color = PaperRule, thickness = 1.dp)

            // ── Grouped session rows ────────────────────────────────────────
            entries.forEach { entry ->
                when (entry) {
                    is LogEntry.DateSep -> {
                        DateSeparatorRow(entry.date, today)
                    }
                    is LogEntry.Session -> {
                        val s = entry.session
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(if (s.isActive) ActiveTint else Color.Transparent)
                        ) {
                            LogRow(
                                skipper = s.skipperName,
                                crew    = s.crewNames.joinToString(", ").ifBlank { "—" },
                                craft   = s.craft,
                                timeOut = s.timeOut,
                                eta     = s.eta,
                                timeIn  = s.timeIn
                            )
                        }
                    }
                }
                HorizontalDivider(color = PaperRule, thickness = 1.dp)
            }

            // ── Empty rows ─────────────────────────────────────────────────
            repeat(emptyCount) {
                LogRow(skipper = "", crew = "", craft = "", timeOut = "", eta = "", timeIn = "")
                HorizontalDivider(color = PaperRule, thickness = 1.dp)
            }

            Spacer(Modifier.weight(1f))

            // ── Footer instruction ─────────────────────────────────────────
            HorizontalDivider(color = PaperRule, thickness = 1.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text      = "▶  Tap your Jericho card or card reader to begin",
                    color     = InkMid,
                    fontSize  = 13.sp,
                    fontStyle = FontStyle.Italic
                )
            }
            // Subtle secondary action — small and dimmed so it isn't tapped by accident
            if (onCheckinFromIdle != null) {
                TextButton(
                    onClick        = onCheckinFromIdle,
                    modifier       = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = 4.dp, vertical = 0.dp
                    )
                ) {
                    Text(
                        text      = "↩  Check in a boat directly",
                        color     = InkMid.copy(alpha = 0.4f),
                        fontSize  = 11.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Date separator row
// ---------------------------------------------------------------------------

@Composable
private fun DateSeparatorRow(date: LocalDate, today: LocalDate) {
    val label = when (date) {
        today                -> "Today"
        today.minusDays(1)   -> "Yesterday"
        else                 -> date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(DateBg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text      = "── $label ──",
            color     = InkMid,
            fontSize  = 11.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium
        )
    }
}

// ---------------------------------------------------------------------------
// Single logbook row
// ---------------------------------------------------------------------------

@Composable
private fun LogRow(
    skipper:  String,
    crew:     String,
    craft:    String,
    timeOut:  String,
    eta:      String,
    timeIn:   String,
    isHeader: Boolean = false
) {
    val color  = if (isHeader) InkDark  else InkMid
    val weight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal
    val size   = 13.sp

    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LogCell(skipper, Modifier.weight(0.20f), color, weight, size)
        ColumnDivider()
        LogCell(crew,    Modifier.weight(0.26f), color, weight, size)
        ColumnDivider()
        LogCell(craft,   Modifier.weight(0.20f), color, weight, size)
        ColumnDivider()
        LogCell(timeOut, Modifier.weight(0.12f), color, weight, size, TextAlign.Center)
        ColumnDivider()
        LogCell(eta,     Modifier.weight(0.11f), color, weight, size, TextAlign.Center)
        ColumnDivider()
        LogCell(timeIn,  Modifier.weight(0.11f), color, weight, size, TextAlign.Center)
    }
}

@Composable
private fun LogCell(
    text:       String,
    modifier:   Modifier,
    color:      Color,
    fontWeight: FontWeight,
    fontSize:   androidx.compose.ui.unit.TextUnit,
    align:      TextAlign = TextAlign.Start
) {
    Text(
        text       = text,
        color      = color,
        fontWeight = fontWeight,
        fontSize   = fontSize,
        maxLines   = 1,
        overflow   = TextOverflow.Ellipsis,
        textAlign  = align,
        modifier   = modifier.padding(horizontal = 5.dp)
    )
}

@Composable
private fun ColumnDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(26.dp)
            .background(PaperRule)
    )
}

// ---------------------------------------------------------------------------
// Right: NFC prompt panel (retains the ocean theme)
// ---------------------------------------------------------------------------

@Composable
private fun NfcPromptPanel(
    timeText:    String,
    nfcWarning:  String?,
    onDebugScan: ((String) -> Unit)?,
    modifier:    Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val rippleDuration = 2400

    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(rippleDuration, easing = LinearEasing)),
        label = "ring1"
    )
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(rippleDuration, 800, easing = LinearEasing)),
        label = "ring2"
    )
    val ring3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(rippleDuration, 1600, easing = LinearEasing)),
        label = "ring3"
    )
    val iconPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "icon_pulse"
    )

    Box(
        modifier.background(
            Brush.radialGradient(listOf(OceanSurface, DeepOcean), radius = 900f)
        ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center    = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension * 0.44f
            fun ring(p: Float) = drawCircle(
                color  = TealMid.copy(alpha = (1f - p) * 0.35f),
                radius = maxRadius * p,
                center = center,
                style  = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
            )
            ring(ring1); ring(ring2); ring(ring3)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text          = "UBC SAILING CLUB",
                style         = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 4.sp,
                    fontSize      = 11.sp
                ),
                color      = TealLight,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(18.dp))
            Icon(
                imageVector        = Icons.Filled.Nfc,
                contentDescription = "Card reader",
                tint               = Color.White,
                modifier           = Modifier.size((68 * iconPulse).dp)
            )
            Spacer(Modifier.height(14.dp))
            if (nfcWarning != null) {
                Text(
                    text      = nfcWarning,
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = Color(0xFFFFB300),
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        Text(
            text     = timeText,
            style    = MaterialTheme.typography.bodyMedium,
            color    = TextSecondary.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )

        if (onDebugScan != null) {
            var debugUid by remember { mutableStateOf("") }
            Row(
                Modifier.align(Alignment.BottomStart).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("DEV", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFB300))
                OutlinedTextField(
                    value         = debugUid,
                    onValueChange = { debugUid = it },
                    label         = { Text("UID") },
                    singleLine    = true,
                    modifier      = Modifier.width(130.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedTextColor        = Color.White,
                        unfocusedTextColor      = Color.White.copy(alpha = 0.7f),
                        focusedBorderColor      = TealLight,
                        unfocusedBorderColor    = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor       = TealLight,
                        unfocusedLabelColor     = Color.White.copy(alpha = 0.5f),
                        focusedContainerColor   = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    )
                )
                Button(
                    onClick = { onDebugScan(debugUid.trim()); debugUid = "" },
                    enabled = debugUid.isNotBlank()
                ) { Text("Scan") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun currentTime(): String =
    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private val today     = LocalDate.now()
private val yesterday = LocalDate.now().minusDays(1)

private val previewSessions = listOf(
    RecentSession("Robert Smith", listOf("Alice K."),            "Venture Keelboat 1", "10:15", "12:00", "12:03", false, today),
    RecentSession("Carol T.",     emptyList(),                   "Windsurfer 3",       "13:30", "15:30", "—",     true,  today),
    RecentSession("James L.",     listOf("Dana M.", "Priya S."), "Laser 2",            "14:00", "16:00", "—",     true,  today),
    RecentSession("Mei W.",       emptyList(),                   "SUP 1",              "09:00", "11:00", "11:15", false, yesterday),
    RecentSession("Tom B.",       listOf("Sara N."),             "Kayak Double 2",     "15:00", "—",     "17:20", false, yesterday),
)

@Preview(widthDp = 960, heightDp = 600, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun IdlePreview() {
    DigitalCheckoutTheme {
        IdleContent(recentSessions = previewSessions, nfcWarning = null)
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun IdleEmptyPreview() {
    DigitalCheckoutTheme {
        IdleContent(recentSessions = emptyList(), nfcWarning = null)
    }
}
