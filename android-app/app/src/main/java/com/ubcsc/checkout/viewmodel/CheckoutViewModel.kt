package com.ubcsc.checkout.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ubcsc.checkout.data.api.ApiClient
import com.ubcsc.checkout.data.api.dto.ActiveSessionDto
import com.ubcsc.checkout.data.api.dto.CheckinRequestDto
import com.ubcsc.checkout.data.api.dto.RecentSessionDto
import com.ubcsc.checkout.data.api.dto.CraftDto
import com.ubcsc.checkout.data.api.dto.CrewInputDto
import com.ubcsc.checkout.data.api.dto.MemberDto
import com.ubcsc.checkout.data.api.dto.SessionCreateDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException

// ---------------------------------------------------------------------------
// Domain models
// ---------------------------------------------------------------------------

data class Member(
    val id: String,
    val name: String,
    val cardUid: String,
    val activeCheckout: ActiveCheckout? = null,
    val certifications: List<String> = emptyList()
)

data class ActiveCheckout(
    val sessionId: Int,
    val craftCode: String,
    val craftName: String
)

data class Craft(
    val id: String,
    val code: String,
    val displayName: String,
    val craftClass: String,
    val isAvailable: Boolean,
    val expectedReturnTime: java.time.LocalTime? = null
)

data class CrewEntry(
    val name: String,
    val isGuest: Boolean,
    val cardUid: String? = null,
    val memberId: Int? = null
)

/** A recent session row shown in the idle-screen logbook. */
data class RecentSession(
    val skipperName: String,
    val crewNames: List<String>,
    val craft: String,                              // display name, e.g. "Venture Keelboat 1"
    val timeOut: String,                            // 24h local time, e.g. "14:30"
    val eta: String,                                // 24h local time, e.g. "16:30", or "—"
    val timeIn: String,                             // 24h local time, e.g. "16:45", or "—"
    val isActive: Boolean,
    val checkoutLocalDate: java.time.LocalDate      // for date-group separators
)

/** Lightweight member record for the name-search dropdown on the idle screen. */
data class MemberSummary(val id: Int, val name: String)

/** A currently active checkout session someone else checked out — used for "check in for someone" flow. */
data class ActiveSession(
    val sessionId: Int,
    val craftCode: String,
    val craftName: String,
    val memberName: String,
    /** How long the boat has been out, formatted for display (e.g. "2h 15m") */
    val timeOut: String,
    val expectedReturnTime: java.time.LocalTime? = null,
    val isOverdue: Boolean = false
)

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

sealed class CheckoutUiState {
    object Idle : CheckoutUiState()
    object Loading : CheckoutUiState()
    data class MemberFound(val member: Member) : CheckoutUiState()
    data class SelectingCraft(val member: Member, val crafts: List<Craft>) : CheckoutUiState()
    data class SelectingBoat(val member: Member, val fleetClass: String, val crafts: List<Craft>) : CheckoutUiState()
    data class AddingCrew(
        val member: Member,
        val craft: Craft,
        val crew: List<CrewEntry> = emptyList()
    ) : CheckoutUiState()
    data class AwaitingCrewCard(
        val member: Member,
        val craft: Craft,
        val crew: List<CrewEntry>
    ) : CheckoutUiState()
    data class ConfirmCheckout(
        val member: Member,
        val craft: Craft,
        val crew: List<CrewEntry> = emptyList(),
        val expectedReturnHours: Int? = null
    ) : CheckoutUiState()
    data class SelectingCheckin(val member: Member, val sessions: List<ActiveSession>) : CheckoutUiState()
    /** Checkin flow started from the idle screen — no member card scanned yet. */
    data class SelectingCheckinIdle(val sessions: List<ActiveSession>) : CheckoutUiState()
    /** Session chosen from idle screen; waiting for any valid member card to authorize. */
    data class AwaitingCheckinCard(val session: ActiveSession) : CheckoutUiState()
    data class ConfirmCheckin(val member: Member, val checkout: ActiveCheckout) : CheckoutUiState()
    data class DamageReport(val member: Member, val checkout: ActiveCheckout) : CheckoutUiState()
    data class Success(val message: String, val isCheckout: Boolean) : CheckoutUiState()
    data class Error(val message: String) : CheckoutUiState()
}

// Craft classes that are single-person — skip the crew screen entirely
private val SOLO_CRAFT_CLASSES = setOf(
    "Laser",
    "Windsurfer L1", "Windsurfer L2", "Windsurfer L2.5", "Windsurfer L3",
    "SUP",
    "Kayak Single"
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class CheckoutViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<CheckoutUiState>(CheckoutUiState.Idle)
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    private val api = ApiClient.api
    private var cachedCrafts: List<Craft> = emptyList()
    private var cachedIdleSessions: List<ActiveSession> = emptyList()

    private val _memberList = MutableStateFlow<List<MemberSummary>>(emptyList())
    val memberList: StateFlow<List<MemberSummary>> = _memberList.asStateFlow()

    // -----------------------------------------------------------------------
    // Idle timeout + recent sessions refresh
    // -----------------------------------------------------------------------

    private companion object {
        const val IDLE_TIMEOUT_MS      = 60_000L
        const val RECENT_REFRESH_MS    = 30_000L
    }
    private var idleTimerJob:      Job? = null
    private var recentSessionsJob: Job? = null

    private val _recentSessions = MutableStateFlow<List<RecentSession>>(emptyList())
    val recentSessions: StateFlow<List<RecentSession>> = _recentSessions.asStateFlow()

    init {
        // Refresh member list every hour so the app picks up sync changes without restarting
        viewModelScope.launch {
            while (true) {
                try {
                    _memberList.value = api.getMemberList().map { MemberSummary(it.id, it.displayName) }
                } catch (_: Exception) { /* silently ignore — name search is optional */ }
                delay(60 * 60 * 1_000L) // 1 hour
            }
        }

        viewModelScope.launch {
            uiState.collect { state ->
                // Idle timeout: restart countdown on every non-idle/non-loading state change
                idleTimerJob?.cancel()
                if (state !is CheckoutUiState.Idle && state !is CheckoutUiState.Loading) {
                    idleTimerJob = viewModelScope.launch {
                        delay(IDLE_TIMEOUT_MS)
                        resetToIdle()
                    }
                }
                // Recent sessions: poll while on the idle screen, stop otherwise
                recentSessionsJob?.cancel()
                if (state is CheckoutUiState.Idle) {
                    recentSessionsJob = viewModelScope.launch {
                        while (true) {
                            fetchRecentSessions()
                            delay(RECENT_REFRESH_MS)
                        }
                    }
                }
            }
        }
    }

    private suspend fun fetchRecentSessions() {
        try {
            _recentSessions.value = api.getRecentSessions().map { it.toDomain() }
        } catch (_: Exception) {
            // Silently ignore — stale data is fine on the idle screen
        }
    }

    // -----------------------------------------------------------------------
    // NFC card scan entry point
    // -----------------------------------------------------------------------

    fun onCardScanned(uid: String) {
        val currentState = _uiState.value
        // If waiting for a crew card, route to crew handler
        if (currentState is CheckoutUiState.AwaitingCrewCard) {
            onCrewCardScanned(currentState, uid)
            return
        }
        // If waiting for the authorizing card in the idle-initiated checkin flow
        if (currentState is CheckoutUiState.AwaitingCheckinCard) {
            onCheckinAuthCardScanned(currentState, uid)
            return
        }
        if (currentState !is CheckoutUiState.Idle) return  // ignore scans mid-flow
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            try {
                val member = api.getMemberByCard(uid).toDomain(uid)
                _uiState.value = CheckoutUiState.MemberFound(member)
            } catch (e: HttpException) {
                _uiState.value = when (e.code()) {
                    404  -> CheckoutUiState.Error("Card not recognized. Please see an exec.")
                    401  -> CheckoutUiState.Error("Kiosk configuration error. Please contact an exec.")
                    else -> CheckoutUiState.Error("Server error (${e.code()}). Please try again.")
                }
            } catch (e: Exception) {
                _uiState.value = CheckoutUiState.Error("Network error. Is the server running?")
            }
        }
    }

    // -----------------------------------------------------------------------
    // Member screen actions
    // -----------------------------------------------------------------------

    fun onCheckoutSelected(member: Member) {
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            try {
                val allCrafts = api.getCrafts().map { it.toDomain() }
                // "*" wildcard = exec / instructor — sees everything
                cachedCrafts = if ("*" in member.certifications) {
                    allCrafts
                } else {
                    allCrafts.filter { craft -> craft.craftClass in member.certifications }
                }
                _uiState.value = CheckoutUiState.SelectingCraft(member, cachedCrafts)
            } catch (e: HttpException) {
                _uiState.value = CheckoutUiState.Error("Failed to load fleet (${e.code()}).")
            } catch (e: Exception) {
                _uiState.value = CheckoutUiState.Error("Network error loading fleet.")
            }
        }
    }

    fun onCheckinSelected(member: Member) {
        val checkout = member.activeCheckout ?: return
        _uiState.value = CheckoutUiState.ConfirmCheckin(member, checkout)
    }

    /** Fetch all active sessions so this member can check in someone else's boat. */
    fun onCheckinForOther(member: Member) {
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            try {
                val sessions = api.getActiveSessions().map { it.toDomain() }
                if (sessions.isEmpty()) {
                    _uiState.value = CheckoutUiState.Error("No boats are currently out.")
                } else {
                    _uiState.value = CheckoutUiState.SelectingCheckin(member, sessions)
                }
            } catch (e: Exception) {
                _uiState.value = CheckoutUiState.Error("Could not load active sessions.")
            }
        }
    }

    /** Member selected from the name dropdown — look them up by ID and begin the flow. */
    fun onMemberSelectedByName(memberId: Int) {
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            try {
                val member = api.getMemberById(memberId).toDomain("")
                _uiState.value = CheckoutUiState.MemberFound(member)
            } catch (e: HttpException) {
                _uiState.value = CheckoutUiState.Error("Member not found (${e.code()}).")
            } catch (e: Exception) {
                _uiState.value = CheckoutUiState.Error("Network error. Is the server running?")
            }
        }
    }

    /** Start the direct-checkin flow from the idle screen (no member card required upfront). */
    fun onCheckinFromIdle() {
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            try {
                val sessions = api.getActiveSessions().map { it.toDomain() }
                if (sessions.isEmpty()) {
                    _uiState.value = CheckoutUiState.Error("No boats are currently out.")
                } else {
                    cachedIdleSessions = sessions
                    _uiState.value = CheckoutUiState.SelectingCheckinIdle(sessions)
                }
            } catch (e: Exception) {
                _uiState.value = CheckoutUiState.Error("Could not load active sessions.")
            }
        }
    }

    /** Session chosen from the idle checkin list — now wait for an authorizing card. */
    fun onSelectSessionIdle(session: ActiveSession) {
        _uiState.value = CheckoutUiState.AwaitingCheckinCard(session)
    }

    /** Member has picked a session from the list — proceed to the damage report step. */
    fun onSelectSessionForCheckin(member: Member, session: ActiveSession) {
        _uiState.value = CheckoutUiState.DamageReport(
            member   = member,
            checkout = ActiveCheckout(
                sessionId = session.sessionId,
                craftCode = session.craftCode,
                craftName = session.craftName
            )
        )
    }

    // -----------------------------------------------------------------------
    // Fleet / boat selection
    // -----------------------------------------------------------------------

    fun onFleetSelected(member: Member, fleetClass: String) {
        val fleetCrafts = cachedCrafts.filter { it.craftClass == fleetClass }
        _uiState.value = CheckoutUiState.SelectingBoat(member, fleetClass, fleetCrafts)
    }

    fun onCraftSelected(member: Member, craft: Craft) {
        // Solo craft classes go straight to confirm — no crew needed
        if (craft.craftClass in SOLO_CRAFT_CLASSES) {
            _uiState.value = CheckoutUiState.ConfirmCheckout(member, craft)
        } else {
            _uiState.value = CheckoutUiState.AddingCrew(member, craft)
        }
    }

    // -----------------------------------------------------------------------
    // Crew screen actions
    // -----------------------------------------------------------------------

    fun onAddCrewByName(state: CheckoutUiState.AddingCrew, name: String) {
        if (name.isBlank()) return
        _uiState.value = state.copy(crew = state.crew + CrewEntry(name.trim(), isGuest = false))
    }

    fun onAddCrewByMember(state: CheckoutUiState.AddingCrew, memberId: Int, name: String) {
        _uiState.value = state.copy(crew = state.crew + CrewEntry(name, isGuest = false, memberId = memberId))
    }

    fun onAddCrewAsGuest(state: CheckoutUiState.AddingCrew) {
        _uiState.value = state.copy(crew = state.crew + CrewEntry("Guest", isGuest = true))
    }

    fun onScanForCrew(state: CheckoutUiState.AddingCrew) {
        _uiState.value = CheckoutUiState.AwaitingCrewCard(state.member, state.craft, state.crew)
    }

    fun onCancelCrewScan() {
        val state = _uiState.value as? CheckoutUiState.AwaitingCrewCard ?: return
        _uiState.value = CheckoutUiState.AddingCrew(state.member, state.craft, state.crew)
    }

    fun onRemoveCrew(state: CheckoutUiState.AddingCrew, index: Int) {
        _uiState.value = state.copy(crew = state.crew.toMutableList().also { it.removeAt(index) })
    }

    fun onCrewDone(state: CheckoutUiState.AddingCrew, expectedReturnHours: Int?) {
        _uiState.value = CheckoutUiState.ConfirmCheckout(
            member              = state.member,
            craft               = state.craft,
            crew                = state.crew,
            expectedReturnHours = expectedReturnHours
        )
    }

    private fun onCheckinAuthCardScanned(state: CheckoutUiState.AwaitingCheckinCard, uid: String) {
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            try {
                val member = api.getMemberByCard(uid).toDomain(uid)
                _uiState.value = CheckoutUiState.DamageReport(
                    member   = member,
                    checkout = ActiveCheckout(
                        sessionId = state.session.sessionId,
                        craftCode = state.session.craftCode,
                        craftName = state.session.craftName
                    )
                )
            } catch (e: HttpException) {
                _uiState.value = when (e.code()) {
                    404  -> CheckoutUiState.Error("Card not recognized. Please see an exec.")
                    else -> CheckoutUiState.Error("Server error (${e.code()}). Please try again.")
                }
            } catch (e: Exception) {
                _uiState.value = CheckoutUiState.Error("Network error. Is the server running?")
            }
        }
    }

    private fun onCrewCardScanned(state: CheckoutUiState.AwaitingCrewCard, uid: String) {
        viewModelScope.launch {
            val name = try {
                api.getMemberByCard(uid).displayName
            } catch (e: Exception) {
                "Member (${uid.takeLast(4)})"  // fallback label for unrecognised cards
            }
            _uiState.value = CheckoutUiState.AddingCrew(
                member = state.member,
                craft  = state.craft,
                crew   = state.crew + CrewEntry(name, isGuest = false, cardUid = uid)
            )
        }
    }

    // -----------------------------------------------------------------------
    // Confirm screen actions
    // -----------------------------------------------------------------------

    fun onConfirmCheckout(member: Member, craft: Craft, crew: List<CrewEntry>, expectedReturnHours: Int?) {
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            try {
                api.checkout(
                    SessionCreateDto(
                        cardUid             = member.cardUid,
                        memberId            = if (member.cardUid.isBlank()) member.id.toIntOrNull() else null,
                        craftId             = craft.id.toInt(),
                        crew                = crew.map { it.toDto() },
                        expectedReturnHours = expectedReturnHours
                    )
                )
                _uiState.value = CheckoutUiState.Success(
                    message    = "Checked out ${craft.displayName} for ${member.name}",
                    isCheckout = true
                )
            } catch (e: HttpException) {
                val detail = parseError(e)
                _uiState.value = CheckoutUiState.Error(detail ?: "Checkout failed (${e.code()}).")
            } catch (e: Exception) {
                _uiState.value = CheckoutUiState.Error("Network error during checkout.")
            }
        }
    }

    fun onConfirmCheckin(member: Member, checkout: ActiveCheckout) {
        // Don't call the API yet — go to the damage report screen first
        _uiState.value = CheckoutUiState.DamageReport(member, checkout)
    }

    fun onSubmitCheckin(member: Member, checkout: ActiveCheckout, notes: String?, hasDamage: Boolean) {
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            try {
                api.checkin(
                    checkout.sessionId,
                    CheckinRequestDto(
                        cardUid        = member.cardUid,
                        memberId       = if (member.cardUid.isBlank()) member.id.toIntOrNull() else null,
                        notesIn        = notes?.takeIf { it.isNotBlank() },
                        damageReported = hasDamage
                    )
                )
                _uiState.value = CheckoutUiState.Success(
                    message    = "${checkout.craftName} returned",
                    isCheckout = false
                )
            } catch (e: HttpException) {
                val detail = parseError(e)
                _uiState.value = CheckoutUiState.Error(detail ?: "Check-in failed (${e.code()}).")
            } catch (e: Exception) {
                _uiState.value = CheckoutUiState.Error("Network error during check-in.")
            }
        }
    }

    // -----------------------------------------------------------------------
    // Navigation helpers
    // -----------------------------------------------------------------------

    fun onCancel() = resetToIdle()

    /**
     * Navigate one step back in the checkout flow instead of resetting to Idle.
     * Each state knows what its logical predecessor is.
     */
    fun goBack() {
        when (val state = _uiState.value) {
            is CheckoutUiState.MemberFound     -> resetToIdle()
            is CheckoutUiState.SelectingCraft  -> _uiState.value = CheckoutUiState.MemberFound(state.member)
            is CheckoutUiState.SelectingBoat   -> _uiState.value = CheckoutUiState.SelectingCraft(state.member, cachedCrafts)
            is CheckoutUiState.AddingCrew      -> {
                val fleetCrafts = cachedCrafts.filter { it.craftClass == state.craft.craftClass }
                _uiState.value = CheckoutUiState.SelectingBoat(state.member, state.craft.craftClass, fleetCrafts)
            }
            is CheckoutUiState.AwaitingCrewCard -> onCancelCrewScan()
            is CheckoutUiState.ConfirmCheckout -> {
                if (state.craft.craftClass in SOLO_CRAFT_CLASSES) {
                    val fleetCrafts = cachedCrafts.filter { it.craftClass == state.craft.craftClass }
                    _uiState.value = CheckoutUiState.SelectingBoat(state.member, state.craft.craftClass, fleetCrafts)
                } else {
                    _uiState.value = CheckoutUiState.AddingCrew(state.member, state.craft, state.crew)
                }
            }
            is CheckoutUiState.ConfirmCheckin       -> _uiState.value = CheckoutUiState.MemberFound(state.member)
            is CheckoutUiState.SelectingCheckin     -> _uiState.value = CheckoutUiState.MemberFound(state.member)
            is CheckoutUiState.DamageReport         -> _uiState.value = CheckoutUiState.MemberFound(state.member)
            is CheckoutUiState.SelectingCheckinIdle -> resetToIdle()
            is CheckoutUiState.AwaitingCheckinCard  -> _uiState.value = CheckoutUiState.SelectingCheckinIdle(cachedIdleSessions)
            else -> resetToIdle()
        }
    }

    fun resetToIdle() {
        _uiState.value = CheckoutUiState.Idle
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Extracts the FastAPI `detail` string from an HTTP error body, if present. */
    private fun parseError(e: HttpException): String? = try {
        val body = e.response()?.errorBody()?.string() ?: return null
        JSONObject(body).optString("detail").takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }
}

// ---------------------------------------------------------------------------
// DTO → Domain mappers (top-level private so they don't pollute the class)
// ---------------------------------------------------------------------------

private fun MemberDto.toDomain(cardUid: String) = Member(
    id             = id.toString(),
    name           = displayName,
    cardUid        = cardUid,
    activeCheckout = activeCheckout?.let {
        ActiveCheckout(
            sessionId = it.sessionId,
            craftCode = it.craftCode,
            craftName = it.craftName
        )
    },
    certifications = certifications
)

private fun CraftDto.toDomain() = Craft(
    id                 = id.toString(),
    code               = code,
    displayName        = displayName,
    craftClass         = craftClass ?: "",
    isAvailable        = isAvailable,
    expectedReturnTime = expectedReturnTime?.let { parseEtrTime(it) }
)

/**
 * Parse an ETR ISO string from the server into a local-timezone [LocalTime].
 * The server stores datetimes as naive UTC (no offset suffix), so we try
 * LocalDateTime first, then fall back to OffsetDateTime if an offset is present.
 */
private fun parseEtrTime(iso: String): java.time.LocalTime? =
    runCatching {
        java.time.LocalDateTime.parse(iso)
            .atOffset(java.time.ZoneOffset.UTC)
            .atZoneSameInstant(java.time.ZoneId.systemDefault())
            .toLocalTime()
    }.recoverCatching {
        java.time.OffsetDateTime.parse(iso)
            .atZoneSameInstant(java.time.ZoneId.systemDefault())
            .toLocalTime()
    }.getOrNull()

private fun CrewEntry.toDto() = CrewInputDto(
    name     = name,
    isGuest  = isGuest,
    cardUid  = cardUid,
    memberId = memberId
)

private fun RecentSessionDto.toDomain(): RecentSession {
    val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    fun fmtTime(iso: String?) = iso?.let { parseEtrTime(it)?.format(fmt) } ?: "—"

    val localDate = runCatching {
        java.time.LocalDateTime.parse(checkoutTime)
            .atOffset(java.time.ZoneOffset.UTC)
            .atZoneSameInstant(java.time.ZoneId.systemDefault())
            .toLocalDate()
    }.getOrElse { java.time.LocalDate.now() }

    return RecentSession(
        skipperName       = skipperName,
        crewNames         = crewNames,
        craft             = craftName,   // display name, not the short code
        timeOut           = fmtTime(checkoutTime),
        eta               = fmtTime(expectedReturnTime),
        timeIn            = fmtTime(checkinTime),
        isActive          = status == "active",
        checkoutLocalDate = localDate
    )
}

private fun ActiveSessionDto.toDomain(): ActiveSession {
    val checkoutUtc = runCatching {
        java.time.LocalDateTime.parse(checkoutTime)
            .atOffset(java.time.ZoneOffset.UTC)
            .toInstant()
    }.getOrNull() ?: java.time.Instant.now()

    val minutesOut = java.time.Duration.between(checkoutUtc, java.time.Instant.now()).toMinutes()
        .coerceAtLeast(0)
    val timeOut = when {
        minutesOut < 60 -> "${minutesOut}m"
        minutesOut % 60 == 0L -> "${minutesOut / 60}h"
        else -> "${minutesOut / 60}h ${minutesOut % 60}m"
    }

    val etr = expectedReturnTime?.let { parseEtrTime(it) }
    val overdue = etr != null && java.time.LocalTime.now().isAfter(etr)

    return ActiveSession(
        sessionId          = sessionId,
        craftCode          = craftCode,
        craftName          = craftName,
        memberName         = memberName,
        timeOut            = timeOut,
        expectedReturnTime = etr,
        isOverdue          = overdue,
    )
}
