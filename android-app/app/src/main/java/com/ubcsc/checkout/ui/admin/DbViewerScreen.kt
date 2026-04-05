package com.ubcsc.checkout.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ubcsc.checkout.data.db.AppDatabase
import com.ubcsc.checkout.data.db.entities.CheckoutSessionEntity
import com.ubcsc.checkout.data.db.entities.CraftEntity
import com.ubcsc.checkout.data.db.entities.MemberEntity
import com.ubcsc.checkout.ui.theme.CardBlue
import com.ubcsc.checkout.ui.theme.DeepOcean
import com.ubcsc.checkout.ui.theme.DividerColor
import com.ubcsc.checkout.ui.theme.LocalKioskColors
import com.ubcsc.checkout.ui.theme.TextMuted
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val fmt = DateTimeFormatter.ofPattern("MMM d  HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun DbViewerDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier      = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape         = RoundedCornerShape(16.dp),
            color         = DeepOcean,
            tonalElevation = 8.dp
        ) {
            DbViewerContent(onDismiss)
        }
    }
}

@Composable
private fun DbViewerContent(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val db      = remember { AppDatabase.get(context) }
    val scope   = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Members", "Craft", "Sessions")

    var members  by remember { mutableStateOf<List<MemberEntity>>(emptyList()) }
    var craft    by remember { mutableStateOf<List<CraftEntity>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<CheckoutSessionEntity>>(emptyList()) }
    var loading  by remember { mutableStateOf(true) }
    var query    by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loading = true
        members  = db.memberDao().getAllActive()
        craft    = db.craftDao().getAll()
        sessions = db.checkoutSessionDao().getRecent(100)
        loading  = false
    }

    fun refresh() {
        scope.launch {
            loading = true
            members  = db.memberDao().getAllActive()
            craft    = db.craftDao().getAll()
            sessions = db.checkoutSessionDao().getRecent(100)
            loading  = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Title bar
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .background(CardBlue)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                "Database",
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                style      = MaterialTheme.typography.titleLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { refresh() }) {
                    Text("Refresh", color = LocalKioskColors.current.accent)
                }
                TextButton(onClick = onDismiss) {
                    Text("Close", color = LocalKioskColors.current.textWarm)
                }
            }
        }

        // Tab row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor   = CardBlue,
            contentColor     = LocalKioskColors.current.accent,
            divider          = { HorizontalDivider(color = DividerColor) }
        ) {
            tabs.forEachIndexed { i, title ->
                val count = when (i) {
                    0 -> members.size
                    1 -> craft.size
                    else -> sessions.size
                }
                Tab(
                    selected = selectedTab == i,
                    onClick  = { selectedTab = i; query = "" },
                    text     = {
                        Text(
                            "$title ($count)",
                            color = if (selectedTab == i) LocalKioskColors.current.accent else TextMuted,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LocalKioskColors.current.accent)
            }
            return@Column
        }

        // Search bar
        OutlinedTextField(
            value         = query,
            onValueChange = { query = it },
            placeholder   = { Text("Filter…", color = TextMuted) },
            singleLine    = true,
            modifier      = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape  = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = LocalKioskColors.current.accentMid,
                unfocusedBorderColor = DividerColor,
                focusedTextColor     = Color.White,
                unfocusedTextColor   = Color.White,
                cursorColor          = LocalKioskColors.current.accent
            )
        )

        when (selectedTab) {
            0 -> MembersTab(members, query)
            1 -> CraftTab(craft, query)
            2 -> SessionsTab(sessions, members, craft, query)
        }
    }
}

@Composable
private fun MembersTab(members: List<MemberEntity>, query: String) {
    val filtered = remember(members, query) {
        if (query.isBlank()) members
        else members.filter {
            val name = "${it.firstName} ${it.lastName} ${it.fullName}".lowercase()
            query.lowercase() in name
        }
    }
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(filtered, key = { it.id }) { m ->
            DbRow {
                val name = listOfNotNull(m.firstName, m.lastName).joinToString(" ").ifBlank { m.fullName }
                Column {
                    Text(name, color = Color.White, fontWeight = FontWeight.Medium)
                    val certs = m.certificationsJson?.let {
                        runCatching {
                            val arr = org.json.JSONArray(it)
                            (0 until arr.length()).joinToString(", ") { i -> arr.getString(i) }
                        }.getOrElse { "?" }
                    } ?: "None"
                    Text("ID ${m.id}  ·  ${m.membershipStatus}", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    Text("Certs: $certs", color = LocalKioskColors.current.textWarm, style = MaterialTheme.typography.labelSmall, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun CraftTab(craft: List<CraftEntity>, query: String) {
    val filtered = remember(craft, query) {
        if (query.isBlank()) craft
        else craft.filter { query.lowercase() in it.displayName.lowercase() || query.lowercase() in it.craftCode.lowercase() }
    }
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(filtered, key = { it.id }) { c ->
            DbRow {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        Text(c.displayName, color = Color.White, fontWeight = FontWeight.Medium)
                        Text("${c.craftCode}  ·  ${c.craftClass ?: c.fleetType}", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    val (statusColor, statusText) = when (c.status) {
                        "available"   -> LocalKioskColors.current.accent  to "Available"
                        "checked_out" -> Color(0xFFFFA94D) to "Out"
                        "grounded"    -> Color(0xFFFF6B6B) to "Grounded"
                        else          -> TextMuted to c.status
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            statusText,
                            color    = statusColor,
                            style    = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (c.statusReason != null) {
                    Text("Reason: ${c.statusReason}", color = LocalKioskColors.current.textWarm, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SessionsTab(
    sessions: List<CheckoutSessionEntity>,
    members:  List<MemberEntity>,
    craft:    List<CraftEntity>,
    query:    String
) {
    val memberMap = remember(members) { members.associateBy { it.id } }
    val craftMap  = remember(craft)   { craft.associateBy { it.id } }

    val filtered = remember(sessions, query) {
        if (query.isBlank()) sessions
        else sessions.filter { s ->
            val memberName = memberMap[s.memberId]?.let { "${it.firstName} ${it.lastName} ${it.fullName}" } ?: ""
            val craftName  = craftMap[s.craftId]?.displayName ?: ""
            query.lowercase() in memberName.lowercase() || query.lowercase() in craftName.lowercase()
        }
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(filtered, key = { it.id }) { s ->
            val member = memberMap[s.memberId]
            val boat   = craftMap[s.craftId]
            val memberName = member?.let { listOfNotNull(it.firstName, it.lastName).joinToString(" ").ifBlank { it.fullName } } ?: "Member #${s.memberId}"
            val craftName  = boat?.displayName ?: "Craft #${s.craftId}"
            DbRow {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(memberName, color = Color.White, fontWeight = FontWeight.Medium)
                        Text(craftName, color = LocalKioskColors.current.accent, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Out: ${fmt.format(Instant.ofEpochMilli(s.checkoutTime))}",
                            color = TextMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (s.checkinTime != null) {
                            Text(
                                "In:  ${fmt.format(Instant.ofEpochMilli(s.checkinTime))}",
                                color = TextMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (!s.notesIn.isNullOrBlank()) {
                            Text("Notes: ${s.notesIn}", color = LocalKioskColors.current.textWarm, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    val (sc, st) = if (s.status == "active") Color(0xFFFFA94D) to "ACTIVE" else TextMuted to "Done"
                    Surface(shape = RoundedCornerShape(6.dp), color = sc.copy(alpha = 0.15f)) {
                        Text(
                            st,
                            color    = sc,
                            style    = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (s.damageReported) {
                    Text("Damage reported", color = Color(0xFFFF6B6B), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun DbRow(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBlue, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}
