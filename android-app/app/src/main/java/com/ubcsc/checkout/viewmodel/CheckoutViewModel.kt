package com.ubcsc.checkout.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Domain models (will be replaced by API response data classes later)
// ---------------------------------------------------------------------------

data class Member(
    val id: String,
    val name: String,
    val activeCheckout: ActiveCheckout? = null
)

data class ActiveCheckout(
    val sessionId: String,
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

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

sealed class CheckoutUiState {
    object Idle : CheckoutUiState()
    object Loading : CheckoutUiState()
    data class MemberFound(val member: Member) : CheckoutUiState()
    data class SelectingCraft(val member: Member, val crafts: List<Craft>) : CheckoutUiState()
    data class ConfirmCheckout(val member: Member, val craft: Craft) : CheckoutUiState()
    data class ConfirmCheckin(val member: Member, val checkout: ActiveCheckout) : CheckoutUiState()
    data class Success(val message: String, val isCheckout: Boolean) : CheckoutUiState()
    data class Error(val message: String) : CheckoutUiState()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class CheckoutViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<CheckoutUiState>(CheckoutUiState.Idle)
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    // -----------------------------------------------------------------------
    // NFC card scan entry point
    // -----------------------------------------------------------------------

    fun onCardScanned(uid: String) {
        if (_uiState.value !is CheckoutUiState.Idle) return  // ignore scans mid-flow
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            // TODO: Replace with real API call: GET /members/card/{uid}
            val member = lookupMemberByCard(uid)
            if (member == null) {
                _uiState.value = CheckoutUiState.Error("Card not recognized. Please see the dock staff.")
            } else {
                _uiState.value = CheckoutUiState.MemberFound(member)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Member screen actions
    // -----------------------------------------------------------------------

    fun onCheckoutSelected(member: Member) {
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            // TODO: Replace with real API call: GET /crafts?available=true
            val crafts = getAvailableCrafts()
            _uiState.value = CheckoutUiState.SelectingCraft(member, crafts)
        }
    }

    fun onCheckinSelected(member: Member) {
        val checkout = member.activeCheckout ?: return
        _uiState.value = CheckoutUiState.ConfirmCheckin(member, checkout)
    }

    // -----------------------------------------------------------------------
    // Craft selection screen actions
    // -----------------------------------------------------------------------

    fun onCraftSelected(member: Member, craft: Craft) {
        _uiState.value = CheckoutUiState.ConfirmCheckout(member, craft)
    }

    // -----------------------------------------------------------------------
    // Confirm screen actions
    // -----------------------------------------------------------------------

    fun onConfirmCheckout(member: Member, craft: Craft) {
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            // TODO: Replace with real API call: POST /sessions
            delay(800)
            _uiState.value = CheckoutUiState.Success(
                message = "Checked out ${craft.displayName} for ${member.name}",
                isCheckout = true
            )
        }
    }

    fun onConfirmCheckin(member: Member, checkout: ActiveCheckout) {
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            // TODO: Replace with real API call: PATCH /sessions/{id}/checkin
            delay(800)
            _uiState.value = CheckoutUiState.Success(
                message = "${checkout.craftName} returned",
                isCheckout = false
            )
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
    // Stub data — replace with Retrofit API calls
    // -----------------------------------------------------------------------

    private suspend fun lookupMemberByCard(uid: String): Member? {
        delay(600)  // simulate network
        // Stub: any card returns a test member; unknown UIDs return null in production
        return if (uid.isNotEmpty()) {
            Member(
                id = "stub-001",
                name = "Alex Sailor",
                activeCheckout = null  // set to an ActiveCheckout to test check-in flow
            )
        } else null
    }

    private suspend fun getAvailableCrafts(): List<Craft> {
        delay(400)  // simulate network
        return listOf(
            Craft("1", "LZ01", "Laser #1", "Laser", isAvailable = true),
            Craft("2", "LZ02", "Laser #2", "Laser", isAvailable = true),
            Craft("3", "LZ03", "Laser #3", "Laser", isAvailable = false),
            Craft("4", "470-01", "470 #1", "470", isAvailable = true),
            Craft("5", "WS01", "Windsurfer #1", "Windsurfer", isAvailable = true),
            Craft("6", "WS02", "Windsurfer #2", "Windsurfer", isAvailable = false),
        )
    }
}
