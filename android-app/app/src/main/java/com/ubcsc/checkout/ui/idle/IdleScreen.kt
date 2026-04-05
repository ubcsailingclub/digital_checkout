package com.ubcsc.checkout.ui.idle

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.work.WorkManager
import com.ubcsc.checkout.BuildConfig
import com.ubcsc.checkout.data.KioskPreferences
import com.ubcsc.checkout.sync.SyncWorker
import com.ubcsc.checkout.ui.admin.DbViewerDialog
import com.ubcsc.checkout.ui.theme.DeepOcean
import com.ubcsc.checkout.ui.theme.DigitalCheckoutTheme
import com.ubcsc.checkout.ui.theme.OceanSurface
import com.ubcsc.checkout.ui.theme.TealLight
import com.ubcsc.checkout.ui.theme.TealMid
import com.ubcsc.checkout.ui.theme.TextSecondary
import com.ubcsc.checkout.ui.util.forceShowSoftKeyboard
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import com.ubcsc.checkout.viewmodel.MemberSummary
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

private fun normalizeAccents(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

private fun filterMembers(query: String, memberList: List<MemberSummary>): List<MemberSummary> {
    val q = normalizeAccents(query.trim())
    if (q.length < 2) return emptyList()
    return memberList
        .filter { normalizeAccents(it.name).contains(q, ignoreCase = true) }
        .sortedWith(compareBy(
            { !normalizeAccents(it.name).startsWith(q, ignoreCase = true) },
            { !normalizeAccents(it.name).split(" ").any { w -> w.startsWith(q, ignoreCase = true) } },
            { it.name.lowercase() }
        ))
        .take(6)
}

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
fun IdleScreen(viewModel: CheckoutViewModel, onAdminExit: () -> Unit = {}) {
    val recentSessions by viewModel.recentSessions.collectAsState()
    val memberList     by viewModel.memberList.collectAsState()
    val fleetStatus    by viewModel.fleetStatus.collectAsState()

    // Keep screen on while idle — this is a kiosk, it should never sleep
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    IdleContent(
        recentSessions          = recentSessions,
        memberList              = memberList,
        fleetGrounded           = fleetStatus?.fleetGrounded ?: false,
        fleetGroundReason       = fleetStatus?.fleetGroundReason ?: "",
        onMemberSelectedByName  = viewModel::onMemberSelectedByName,
        onCheckinFromIdle       = viewModel::onCheckinFromIdle,
        onAdminExit             = onAdminExit
    )
}

// ---------------------------------------------------------------------------
// Root layout: notebook left | search prompt right
// ---------------------------------------------------------------------------

@Composable
private fun IdleContent(
    recentSessions:         List<RecentSession>,
    memberList:             List<MemberSummary>   = emptyList(),
    fleetGrounded:          Boolean               = false,
    fleetGroundReason:      String                = "",
    onMemberSelectedByName: ((Int) -> Unit)?      = null,
    onCheckinFromIdle:      (() -> Unit)?          = null,
    onAdminExit:            () -> Unit            = {}
) {
    var timeText by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) { delay(1_000L); timeText = currentTime() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        if (!isLandscape) {
            Column(Modifier.fillMaxSize()) {
                SearchPromptPanel(
                    timeText               = timeText,
                    isLandscape            = false,
                    memberList             = memberList,
                    onMemberSelectedByName = onMemberSelectedByName,
                    modifier               = Modifier.fillMaxWidth().weight(0.48f)
                )
                NotebookPanel(
                    recentSessions    = recentSessions,
                    onCheckinFromIdle = onCheckinFromIdle,
                    onAdminExit       = onAdminExit,
                    modifier          = Modifier.fillMaxWidth().weight(0.52f)
                )
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                NotebookPanel(
                    recentSessions    = recentSessions,
                    onCheckinFromIdle = onCheckinFromIdle,
                    onAdminExit       = onAdminExit,
                    modifier          = Modifier.weight(0.58f).fillMaxHeight()
                )
                SearchPromptPanel(
                    timeText               = timeText,
                    isLandscape            = true,
                    memberList             = memberList,
                    onMemberSelectedByName = onMemberSelectedByName,
                    modifier               = Modifier.weight(0.42f).fillMaxHeight()
                )
            }
        }
        // Fleet grounding banner — overlaid at top of entire idle screen
        if (fleetGrounded) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(20f)
                    .background(Color(0xFFB45309))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                androidx.compose.material3.Text(
                    text       = "⚠  Fleet Grounded — ${fleetGroundReason.ifBlank { "Conditions have been deemed unsafe. You may still proceed, but sail at your own risk." }}",
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    textAlign  = TextAlign.Center
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Left: Notebook / logbook panel
// ---------------------------------------------------------------------------

@Composable
private fun NotebookPanel(
    recentSessions:    List<RecentSession>,
    onCheckinFromIdle: (() -> Unit)? = null,
    onAdminExit:       () -> Unit    = {},
    modifier:          Modifier      = Modifier
) {
    val today   = LocalDate.now()
    val entries = groupByDate(recentSessions)

    var showAdminDialog  by remember { mutableStateOf(false) }
    var showAdminMenu    by remember { mutableStateOf(false) }
    var showSyncSettings by remember { mutableStateOf(false) }
    var showDbViewer     by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    if (showAdminDialog) {
        AdminCodeDialog(
            onDismiss = { showAdminDialog = false },
            onSuccess = { showAdminDialog = false; showAdminMenu = true }
        )
    }
    if (showAdminMenu) {
        AdminMenuDialog(
            onDismiss      = { showAdminMenu = false },
            onExit         = { showAdminMenu = false; onAdminExit() },
            onSyncSettings = { showAdminMenu = false; showSyncSettings = true },
            onDbViewer     = { showAdminMenu = false; showDbViewer = true },
            onUpdate       = { showAdminMenu = false; showUpdateDialog = true }
        )
    }
    if (showUpdateDialog) {
        UpdateDialog(onDismiss = { showUpdateDialog = false })
    }
    if (showDbViewer) {
        DbViewerDialog(onDismiss = { showDbViewer = false })
    }
    if (showSyncSettings) {
        SyncSettingsDialog(onDismiss = { showSyncSettings = false })
    }

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
                .padding(start = 56.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)
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
                    fontSize      = 18.sp,
                    letterSpacing = 0.4.sp,
                    modifier      = Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = { showAdminDialog = true })
                    }
                )
                Text(
                    text      = today.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                    color     = InkMid,
                    fontSize  = 15.sp,
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

            // ── Grouped session rows (scrollable) ──────────────────────────
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
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
            }

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
                    fontSize  = 15.sp,
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
                        color     = InkMid.copy(alpha = 0.75f),
                        fontSize  = 15.sp,
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
            fontSize  = 13.sp,
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
    val size   = 16.sp

    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
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
// Right: Search prompt panel
// ---------------------------------------------------------------------------

@Composable
private fun SearchPromptPanel(
    timeText:               String,
    isLandscape:            Boolean              = false,
    memberList:             List<MemberSummary>  = emptyList(),
    onMemberSelectedByName: ((Int) -> Unit)?     = null,
    modifier:               Modifier             = Modifier
) {
    val iconPulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue  = 0.92f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label         = "icon_pulse"
    )

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Box(
        modifier.background(
            Brush.radialGradient(listOf(OceanSurface, DeepOcean), radius = 900f)
        ).pointerInput(Unit) {
            detectTapGestures { focusManager.clearFocus() }
        }
    ) {
        if (isLandscape && onMemberSelectedByName != null && memberList.isNotEmpty()) {
            // ── Landscape: same look as portrait but search pinned near top,
            //               dropdown opens upward so keyboard can never cover it ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Spacer(Modifier.height(150.dp))
                Text(
                    text          = "UBC SAILING CLUB",
                    style         = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 5.sp,
                        fontSize      = 16.sp
                    ),
                    color      = TealLight,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                NameSearchField(
                    memberList             = memberList,
                    onMemberSelectedByName = onMemberSelectedByName,
                    dropUp                 = true,
                    forceKeyboard          = false
                )
                Spacer(Modifier.height(18.dp))
                // Decorative — gets clipped by keyboard, acceptable
                Icon(
                    imageVector        = Icons.Filled.Search,
                    contentDescription = null,
                    tint               = Color.White.copy(alpha = 0.25f),
                    modifier           = Modifier.size((48 * iconPulse).dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text      = "Search your name to check out",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = Color.White.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // ── Portrait (or no member list): original centred layout ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text          = "UBC SAILING CLUB",
                    style         = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 5.sp,
                        fontSize      = 16.sp
                    ),
                    color      = TealLight,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(14.dp))
                Icon(
                    imageVector        = Icons.Filled.Search,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size((60 * iconPulse).dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text      = "Search your name to check out",
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
                if (onMemberSelectedByName != null && memberList.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    NameSearchField(
                        memberList             = memberList,
                        onMemberSelectedByName = onMemberSelectedByName
                    )
                }
            }
        }

        Text(
            text     = timeText,
            style    = MaterialTheme.typography.bodyMedium,
            color    = TextSecondary.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Name search field (shown in NFC panel when member list is loaded)
// ---------------------------------------------------------------------------

@Composable
private fun NameSearchField(
    memberList:             List<MemberSummary>,
    onMemberSelectedByName: (Int) -> Unit,
    dropUp:                 Boolean = false,
    forceKeyboard:          Boolean = true
) {
    var searchText by remember { mutableStateOf("") }
    val filtered   = remember(searchText, memberList) { filterMembers(searchText, memberList) }

    // Each result row is ~48 dp tall; card offset is negative to appear above the field.
    val cardOffsetY = if (dropUp && filtered.isNotEmpty())
        -(filtered.size * 48 + 8)  // dp — moves card above the text field
    else 0

    // Box lets the results Card float without pushing surrounding layout
    Box(Modifier.width(230.dp).zIndex(10f)) {
        OutlinedTextField(
            value         = searchText,
            onValueChange = { searchText = it },
            placeholder   = { Text("Search by name…", color = Color.White.copy(alpha = 0.4f)) },
            leadingIcon   = { Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.5f)) },
            singleLine    = true,
            modifier      = if (forceKeyboard) Modifier.fillMaxWidth().forceShowSoftKeyboard()
                            else Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedTextColor        = Color.White,
                unfocusedTextColor      = Color.White.copy(alpha = 0.8f),
                focusedBorderColor      = TealLight,
                unfocusedBorderColor    = Color.White.copy(alpha = 0.25f),
                cursorColor             = TealLight,
                focusedContainerColor   = Color.White.copy(alpha = 0.06f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
            )
        )

        if (filtered.isNotEmpty()) {
            val shape = if (dropUp)
                RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            else
                RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)

            Card(
                modifier  = if (dropUp)
                    Modifier.fillMaxWidth().offset(y = cardOffsetY.dp)
                else
                    Modifier.fillMaxWidth().padding(top = 56.dp),
                shape     = shape,
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
                            .clickable {
                                onMemberSelectedByName(member.id)
                                searchText = ""
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Admin code dialog
// ---------------------------------------------------------------------------

@Composable
private fun AdminCodeDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var code     by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Admin Access", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value               = code,
                    onValueChange       = { code = it; hasError = false },
                    label               = { Text("Enter admin code") },
                    singleLine          = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError             = hasError,
                    modifier            = Modifier.fillMaxWidth(),
                    colors              = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = TealMid,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        cursorColor          = TealLight,
                        errorBorderColor     = Color(0xFFCF6679),
                        errorLabelColor      = Color(0xFFCF6679)
                    )
                )
                if (hasError) {
                    Text(
                        "Incorrect code",
                        color = Color(0xFFCF6679),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (code == BuildConfig.ADMIN_CODE) onSuccess()
                else { hasError = true; code = "" }
            }) { Text("Unlock", color = TealLight) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = Color(0xFF1A2E42)
    )
}

// ---------------------------------------------------------------------------
// Admin menu dialog
// ---------------------------------------------------------------------------

@Composable
private fun AdminMenuDialog(
    onDismiss:      () -> Unit,
    onExit:         () -> Unit,
    onSyncSettings: () -> Unit,
    onDbViewer:     () -> Unit,
    onUpdate:       () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Admin Menu", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSyncSettings, modifier = Modifier.fillMaxWidth()) { Text("Sync Settings") }
                Button(onClick = onDbViewer,     modifier = Modifier.fillMaxWidth()) { Text("Database") }
                Button(onClick = onUpdate,       modifier = Modifier.fillMaxWidth()) { Text("Update App") }
                Button(onClick = onExit,         modifier = Modifier.fillMaxWidth()) { Text("Exit App") }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = Color(0xFF1A2E42)
    )
}

// ---------------------------------------------------------------------------
// OTA update dialog
// ---------------------------------------------------------------------------

@Composable
private fun UpdateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    // "idle" | "checking" | "up_to_date" | "downloading" | "installing" | "error"
    var phase    by remember { mutableStateOf("idle") }
    var progress by remember { mutableStateOf(0) }
    var errorMsg by remember { mutableStateOf("") }

    // Kick off check immediately on open
    LaunchedEffect(Unit) {
        phase = "checking"
        val info = com.ubcsc.checkout.data.AppUpdater.checkForUpdate()
        if (info == null) {
            phase = "up_to_date"
        } else {
            phase = "downloading"
            val apk = com.ubcsc.checkout.data.AppUpdater.downloadApk(context, info.downloadUrl) { pct ->
                progress = pct
            }
            if (apk == null) {
                phase = "error"; errorMsg = "Download failed. Check your network connection."
            } else {
                phase = "installing"
                com.ubcsc.checkout.data.AppUpdater.installApk(context, apk) { ok ->
                    if (!ok) { phase = "error"; errorMsg = "Install failed." }
                    // On success Android relaunches the app — dialog will be gone
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (phase != "downloading" && phase != "installing") onDismiss() },
        title = { Text("Update App", color = Color.White) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                when (phase) {
                    "checking" -> {
                        CircularProgressIndicator(color = TealLight)
                        Text("Checking for updates…", color = TextSecondary)
                    }
                    "up_to_date" -> {
                        Text("✓  App is up to date", color = TealLight, fontWeight = FontWeight.SemiBold)
                        Text("Version ${com.ubcsc.checkout.BuildConfig.VERSION_CODE}", color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    "downloading" -> {
                        CircularProgressIndicator(
                            progress   = { progress / 100f },
                            color      = TealLight,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                        Text("Downloading… $progress%", color = TextSecondary)
                    }
                    "installing" -> {
                        CircularProgressIndicator(color = TealLight)
                        Text("Installing…", color = TextSecondary)
                    }
                    "error" -> {
                        Text("Update failed", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
                        Text(errorMsg, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (phase != "downloading" && phase != "installing") {
                TextButton(onClick = onDismiss) { Text("Close", color = TextSecondary) }
            }
        },
        containerColor = Color(0xFF1A2E42)
    )
}

// ---------------------------------------------------------------------------
// Sync settings dialog
// ---------------------------------------------------------------------------

@Composable
private fun SyncSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs   = remember { KioskPreferences(context) }

    var waApiKey         by remember { mutableStateOf(prefs.waApiKey) }
    var waAccountId      by remember { mutableStateOf(prefs.waAccountId) }
    var piSyncUrl        by remember { mutableStateOf(prefs.piSyncUrl) }
    var sheetsScriptUrl  by remember { mutableStateOf(prefs.sheetsScriptUrl) }

    // "idle" | "queued"
    var syncStatus  by remember { mutableStateOf("idle") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = TealMid,
        unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
        focusedTextColor     = Color.White,
        unfocusedTextColor   = Color.White,
        cursorColor          = TealLight
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync Settings", color = Color.White) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Wild Apricot", color = TealLight, style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value         = waApiKey,
                    onValueChange = { waApiKey = it },
                    label         = { Text("API Key") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = fieldColors
                )
                OutlinedTextField(
                    value         = waAccountId,
                    onValueChange = { waAccountId = it },
                    label         = { Text("Account ID") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = fieldColors
                )
                Text("Pi (Tailscale)", color = TealLight, style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value         = piSyncUrl,
                    onValueChange = { piSyncUrl = it },
                    label         = { Text("Pi URL  e.g. https://100.x.x.x") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = fieldColors
                )
                Text("Google Sheets", color = TealLight, style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value         = sheetsScriptUrl,
                    onValueChange = { sheetsScriptUrl = it },
                    label         = { Text("Apps Script URL") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = fieldColors
                )

                if (syncStatus == "queued") {
                    Text(
                        "Sync queued — running in background, safe to close.",
                        color = TealLight,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        prefs.waApiKey        = waApiKey.trim()
                        prefs.waAccountId     = waAccountId.trim()
                        prefs.piSyncUrl       = piSyncUrl.trim()
                        prefs.sheetsScriptUrl = sheetsScriptUrl.trim()
                        SyncWorker.syncNow(context)
                        syncStatus = "queued"
                    },
                    enabled  = syncStatus != "queued",
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (syncStatus == "queued") "Sync queued — safe to close" else "Save & Sync Now") }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TextSecondary) }
        },
        containerColor = Color(0xFF1A2E42)
    )
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
        IdleContent(recentSessions = previewSessions)
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun IdleEmptyPreview() {
    DigitalCheckoutTheme {
        IdleContent(recentSessions = emptyList())
    }
}
