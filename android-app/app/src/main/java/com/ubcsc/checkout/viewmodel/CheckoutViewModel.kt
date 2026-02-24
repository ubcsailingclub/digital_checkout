package com.ubcsc.checkout.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ubcsc.checkout.data.api.ApiClient
import com.ubcsc.checkout.data.api.dto.CheckinRequestDto
import com.ubcsc.checkout.data.api.dto.CraftDto
import com.ubcsc.checkout.data.api.dto.CrewInputDto
import com.ubcsc.checkout.data.api.dto.MemberDto
import com.ubcsc.checkout.data.api.dto.SessionCreateDto
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
    val isAvailable: Boolean
)

data class CrewEntry(
    val name: String,
    val isGuest: Boolean,
    val cardUid: String? = null
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
    data class ConfirmCheckin(val member: Member, val checkout: ActiveCheckout) : CheckoutUiState()
    data class Success(val message: String, val isCheckout: Boolean) : CheckoutUiState()
    data class Error(val message: String) : CheckoutUiState()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class CheckoutViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<CheckoutUiState>(CheckoutUiState.Idle)
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    private val api = ApiClient.api
    private var cachedCrafts: List<Craft> = emptyList()

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
        if (currentState !is CheckoutUiState.Idle) return  // ignore scans mid-flow
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            try {
                val member = api.getMemberByCard(uid).toDomain(uid)
                _uiState.value = CheckoutUiState.MemberFound(member)
            } catch (e: HttpException) {
                _uiState.value = when (e.code()) {
                    404  -> CheckoutUiState.Error("Card not recognized. Please see the dock staff.")
                    401  -> CheckoutUiState.Error("Kiosk configuration error. Please contact staff.")
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

    // -----------------------------------------------------------------------
    // Fleet / boat selection
    // -----------------------------------------------------------------------

    fun onFleetSelected(member: Member, fleetClass: String) {
        val fleetCrafts = cachedCrafts.filter { it.craftClass == fleetClass }
        _uiState.value = CheckoutUiState.SelectingBoat(member, fleetClass, fleetCrafts)
    }

    fun onCraftSelected(member: Member, craft: Craft) {
        // Laser is single-handed only — skip crew screen
        if (craft.craftClass == "Laser") {
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
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            try {
                api.checkin(checkout.sessionId, CheckinRequestDto(cardUid = member.cardUid))
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
    id          = id.toString(),
    code        = code,
    displayName = displayName,
    craftClass  = craftClass ?: "",
    isAvailable = isAvailable
)

private fun CrewEntry.toDto() = CrewInputDto(
    name    = name,
    isGuest = isGuest,
    cardUid = cardUid
)
