package com.ubcsc.checkout.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ubcsc.checkout.data.KioskPreferences
import com.ubcsc.checkout.data.db.AppDatabase
import com.ubcsc.checkout.data.repository.CheckoutRepository
import com.ubcsc.checkout.data.repository.CraftRepository
import com.ubcsc.checkout.data.repository.MemberRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

data class RecentSession(
    val skipperName: String,
    val crewNames: List<String>,
    val craft: String,
    val timeOut: String,
    val eta: String,
    val timeIn: String,
    val isActive: Boolean,
    val checkoutLocalDate: java.time.LocalDate
)

data class MemberSummary(val id: Int, val name: String)

data class ActiveSession(
    val sessionId: Int,
    val craftCode: String,
    val craftName: String,
    val craftClass: String,
    val memberName: String,
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
    data class SelectingCheckinIdle(val sessions: List<ActiveSession>) : CheckoutUiState()
    data class AwaitingCheckinCard(val session: ActiveSession) : CheckoutUiState()
    data class ConfirmCheckin(val member: Member, val checkout: ActiveCheckout) : CheckoutUiState()
    data class DamageReport(val member: Member?, val checkout: ActiveCheckout) : CheckoutUiState()
    data class Success(val message: String, val isCheckout: Boolean) : CheckoutUiState()
    data class Error(val message: String) : CheckoutUiState()
}

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

    private val db         = AppDatabase.get(application)
    private val prefs      = KioskPreferences(application)
    private val members    = MemberRepository(db)
    private val crafts     = CraftRepository(db)
    private val checkouts  = CheckoutRepository(db)

    private val _uiState = MutableStateFlow<CheckoutUiState>(CheckoutUiState.Idle)
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    private var cachedCrafts: List<Craft> = emptyList()
    private var cachedIdleSessions: List<ActiveSession> = emptyList()

    val memberList: StateFlow<List<MemberSummary>> = members.getAllActiveFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private companion object {
        const val IDLE_TIMEOUT_MS   = 60_000L
        const val RECENT_REFRESH_MS = 30_000L
    }
    private var idleTimerJob:      Job? = null
    private var recentSessionsJob: Job? = null

    private val _recentSessions = MutableStateFlow<List<RecentSession>>(emptyList())
    val recentSessions: StateFlow<List<RecentSession>> = _recentSessions.asStateFlow()

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                idleTimerJob?.cancel()
                if (state !is CheckoutUiState.Idle && state !is CheckoutUiState.Loading) {
                    idleTimerJob = viewModelScope.launch {
                        delay(IDLE_TIMEOUT_MS)
                        resetToIdle()
                    }
                }
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
        try { _recentSessions.value = checkouts.getRecentSessions() } catch (_: Exception) {}
    }

    // -----------------------------------------------------------------------
    // NFC / FOB card scan
    // -----------------------------------------------------------------------

    fun onCardScanned(uid: String) {
        val currentState = _uiState.value
        if (currentState is CheckoutUiState.AwaitingCrewCard) { onCrewCardScanned(currentState, uid); return }
        if (currentState is CheckoutUiState.AwaitingCheckinCard) { onCheckinAuthCardScanned(currentState, uid); return }
        if (currentState !is CheckoutUiState.Idle) return
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            try {
                val entity = members.getByCardUid(uid)
                    ?: run { _uiState.value = CheckoutUiState.Error("Card not recognized. Please see an exec."); return@launch }
                if (!entity.isActive) {
                    _uiState.value = CheckoutUiState.Error("Membership is not active. Please see an exec.")
                    return@launch
                }
                val activeCheckout = checkouts.getActiveCheckoutForMember(entity.id)
                _uiState.value = CheckoutUiState.MemberFound(entity.toDomain(uid, activeCheckout))
            } catch (e: Exception) {
                _uiState.value = CheckoutUiState.Error("Database error. Please try again.")
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
                val allCrafts = crafts.getAll()
                cachedCrafts = if ("*" in member.certifications) allCrafts
                               else allCrafts.filter { it.craftClass in member.certifications }
                _uiState.value = CheckoutUiState.SelectingCraft(member, cachedCrafts)
            } catch (e: Exception) {
                _uiState.value = CheckoutUiState.Error("Failed to load fleet.")
            }
        }
    }

    fun onCheckinSelected(member: Member) {
        val checkout = member.activeCheckout ?: return
        _uiState.value = CheckoutUiState.ConfirmCheckin(member, checkout)
    }

    fun onCheckinForOther(member: Member) {
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            try {
                val sessions = checkouts.getAllActiveSessions()
                if (sessions.isEmpty()) _uiState.value = CheckoutUiState.Error("No boats are currently out.")
                else _uiState.value = CheckoutUiState.SelectingCheckin(member, sessions)
            } catch (e: Exception) {
                _uiState.value = CheckoutUiState.Error("Could not load active sessions.")
            }
        }
    }

    fun onMemberSelectedByName(memberId: Int) {
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            try {
                val entity = members.getById(memberId)
                    ?: run { _uiState.value = CheckoutUiState.Error("Member not found."); return@launch }
                val activeCheckout = checkouts.getActiveCheckoutForMember(entity.id)
                _uiState.value = CheckoutUiState.MemberFound(entity.toDomain("", activeCheckout))
            } catch (e: Exception) {
                _uiState.value = CheckoutUiState.Error("Database error.")
            }
        }
    }

    fun onCheckinFromIdle() {
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            try {
                val sessions = checkouts.getAllActiveSessions()
                if (sessions.isEmpty()) _uiState.value = CheckoutUiState.Error("No boats are currently out.")
                else { cachedIdleSessions = sessions; _uiState.value = CheckoutUiState.SelectingCheckinIdle(sessions) }
            } catch (e: Exception) {
                _uiState.value = CheckoutUiState.Error("Could not load active sessions.")
            }
        }
    }

    fun onSelectSessionIdle(session: ActiveSession) {
        _uiState.value = CheckoutUiState.DamageReport(
            member   = null,
            checkout = ActiveCheckout(session.sessionId, session.craftCode, session.craftName)
        )
    }

    fun onSelectSessionForCheckin(member: Member, session: ActiveSession) {
        _uiState.value = CheckoutUiState.DamageReport(
            member   = member,
            checkout = ActiveCheckout(session.sessionId, session.craftCode, session.craftName)
        )
    }

    // -----------------------------------------------------------------------
    // Fleet / boat selection
    // -----------------------------------------------------------------------

    fun onFleetSelected(member: Member, fleetClass: String) {
        _uiState.value = CheckoutUiState.SelectingBoat(member, fleetClass, cachedCrafts.filter { it.craftClass == fleetClass })
    }

    fun onCraftSelected(member: Member, craft: Craft) {
        _uiState.value = if (craft.craftClass in SOLO_CRAFT_CLASSES)
            CheckoutUiState.ConfirmCheckout(member, craft)
        else
            CheckoutUiState.AddingCrew(member, craft)
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
        _uiState.value = CheckoutUiState.ConfirmCheckout(state.member, state.craft, state.crew, expectedReturnHours)
    }

    private fun onCheckinAuthCardScanned(state: CheckoutUiState.AwaitingCheckinCard, uid: String) {
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            try {
                val entity = members.getByCardUid(uid)
                    ?: run { _uiState.value = CheckoutUiState.Error("Card not recognized. Please see an exec."); return@launch }
                val activeCheckout = checkouts.getActiveCheckoutForMember(entity.id)
                _uiState.value = CheckoutUiState.DamageReport(
                    member   = entity.toDomain(uid, activeCheckout),
                    checkout = ActiveCheckout(state.session.sessionId, state.session.craftCode, state.session.craftName)
                )
            } catch (e: Exception) {
                _uiState.value = CheckoutUiState.Error("Database error. Please try again.")
            }
        }
    }

    private fun onCrewCardScanned(state: CheckoutUiState.AwaitingCrewCard, uid: String) {
        viewModelScope.launch {
            val entity = try { members.getByCardUid(uid) } catch (_: Exception) { null }
            val name = entity?.let { listOfNotNull(it.firstName, it.lastName).joinToString(" ").ifBlank { it.fullName } }
                ?: "Member (${uid.takeLast(4)})"
            _uiState.value = CheckoutUiState.AddingCrew(
                member = state.member,
                craft  = state.craft,
                crew   = state.crew + CrewEntry(name, isGuest = false, cardUid = uid, memberId = entity?.id)
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
                checkouts.createCheckout(member, craft.id.toInt(), crew, expectedReturnHours, prefs.sheetsScriptUrl)
                _uiState.value = CheckoutUiState.Success("Checked out ${craft.displayName} for ${member.name}", isCheckout = true)
            } catch (e: Exception) {
                _uiState.value = CheckoutUiState.Error("Checkout failed: ${e.message}")
            }
        }
    }

    fun onConfirmCheckin(member: Member, checkout: ActiveCheckout) {
        _uiState.value = CheckoutUiState.DamageReport(member, checkout)
    }

    fun onSubmitCheckin(member: Member?, checkout: ActiveCheckout, notes: String?, hasDamage: Boolean) {
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            try {
                checkouts.completeCheckin(checkout.sessionId, notes, hasDamage, prefs.sheetsScriptUrl)
                _uiState.value = CheckoutUiState.Success("${checkout.craftName} returned", isCheckout = false)
            } catch (e: Exception) {
                _uiState.value = CheckoutUiState.Error("Check-in failed: ${e.message}")
            }
        }
    }

    // -----------------------------------------------------------------------
    // Navigation helpers
    // -----------------------------------------------------------------------

    fun onCancel() = resetToIdle()

    fun goBack() {
        when (val state = _uiState.value) {
            is CheckoutUiState.MemberFound          -> resetToIdle()
            is CheckoutUiState.SelectingCraft       -> _uiState.value = CheckoutUiState.MemberFound(state.member)
            is CheckoutUiState.SelectingBoat        -> _uiState.value = CheckoutUiState.SelectingCraft(state.member, cachedCrafts)
            is CheckoutUiState.AddingCrew           -> _uiState.value = CheckoutUiState.SelectingBoat(state.member, state.craft.craftClass, cachedCrafts.filter { it.craftClass == state.craft.craftClass })
            is CheckoutUiState.AwaitingCrewCard     -> onCancelCrewScan()
            is CheckoutUiState.ConfirmCheckout      -> {
                if (state.craft.craftClass in SOLO_CRAFT_CLASSES)
                    _uiState.value = CheckoutUiState.SelectingBoat(state.member, state.craft.craftClass, cachedCrafts.filter { it.craftClass == state.craft.craftClass })
                else
                    _uiState.value = CheckoutUiState.AddingCrew(state.member, state.craft, state.crew)
            }
            is CheckoutUiState.ConfirmCheckin       -> _uiState.value = CheckoutUiState.MemberFound(state.member)
            is CheckoutUiState.SelectingCheckin     -> _uiState.value = CheckoutUiState.MemberFound(state.member)
            is CheckoutUiState.DamageReport         -> _uiState.value =
                if (state.member != null) CheckoutUiState.MemberFound(state.member)
                else CheckoutUiState.SelectingCheckinIdle(cachedIdleSessions)
            is CheckoutUiState.SelectingCheckinIdle -> resetToIdle()
            is CheckoutUiState.AwaitingCheckinCard  -> _uiState.value = CheckoutUiState.SelectingCheckinIdle(cachedIdleSessions)
            else -> resetToIdle()
        }
    }

    fun resetToIdle() { _uiState.value = CheckoutUiState.Idle }
}

// ---------------------------------------------------------------------------
// Entity → domain mapper
// ---------------------------------------------------------------------------

private fun com.ubcsc.checkout.data.db.entities.MemberEntity.toDomain(
    cardUid:       String,
    activeCheckout: ActiveCheckout?
) = Member(
    id             = id.toString(),
    name           = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { fullName },
    cardUid        = cardUid,
    activeCheckout = activeCheckout,
    certifications = certificationsJson?.let {
        runCatching { org.json.JSONArray(it).let { arr -> (0 until arr.length()).map { i -> arr.getString(i) } } }.getOrElse { emptyList() }
    } ?: emptyList()
)
