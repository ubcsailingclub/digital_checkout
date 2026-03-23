package com.ubcsc.checkout.data.api.dto

import com.google.gson.annotations.SerializedName

// ---------------------------------------------------------------------------
// Responses (server → app)
// ---------------------------------------------------------------------------

data class CraftDto(
    @SerializedName("id")                   val id: Int,
    @SerializedName("code")                 val code: String,
    @SerializedName("display_name")         val displayName: String,
    @SerializedName("craft_class")          val craftClass: String?,
    @SerializedName("is_available")         val isAvailable: Boolean,
    @SerializedName("expected_return_time") val expectedReturnTime: String? = null
)

data class ActiveCheckoutDto(
    @SerializedName("session_id") val sessionId: Int,
    @SerializedName("craft_code") val craftCode: String,
    @SerializedName("craft_name") val craftName: String
)

data class ActiveSessionDto(
    @SerializedName("session_id")           val sessionId: Int,
    @SerializedName("craft_id")             val craftId: Int,
    @SerializedName("craft_code")           val craftCode: String,
    @SerializedName("craft_name")           val craftName: String,
    @SerializedName("member_name")          val memberName: String,
    @SerializedName("checkout_time")        val checkoutTime: String,
    @SerializedName("expected_return_time") val expectedReturnTime: String? = null
)

data class RecentSessionDto(
    @SerializedName("session_id")           val sessionId: Int,
    @SerializedName("skipper_name")         val skipperName: String,
    @SerializedName("crew_names")           val crewNames: List<String> = emptyList(),
    @SerializedName("craft_name")           val craftName: String,
    @SerializedName("craft_code")           val craftCode: String,
    @SerializedName("checkout_time")        val checkoutTime: String,
    @SerializedName("expected_return_time") val expectedReturnTime: String? = null,
    @SerializedName("checkin_time")         val checkinTime: String? = null,
    @SerializedName("status")               val status: String
)

data class MemberDto(
    @SerializedName("id")                   val id: Int,
    @SerializedName("display_name")         val displayName: String,
    @SerializedName("has_active_checkout")  val hasActiveCheckout: Boolean,
    @SerializedName("active_checkout")      val activeCheckout: ActiveCheckoutDto?,
    @SerializedName("certifications")       val certifications: List<String>
)

data class MemberSummaryDto(
    @SerializedName("id")           val id: Int,
    @SerializedName("display_name") val displayName: String
)

data class SessionResponseDto(
    @SerializedName("session_id") val sessionId: Int,
    @SerializedName("status")     val status: String,
    @SerializedName("message")    val message: String
)

// ---------------------------------------------------------------------------
// Requests (app → server)
// ---------------------------------------------------------------------------

data class CrewInputDto(
    @SerializedName("name")      val name: String,
    @SerializedName("is_guest")  val isGuest: Boolean,
    @SerializedName("card_uid")  val cardUid: String? = null,
    @SerializedName("member_id") val memberId: Int? = null
)

data class SessionCreateDto(
    @SerializedName("card_uid")              val cardUid: String = "",
    @SerializedName("member_id")             val memberId: Int? = null,
    @SerializedName("craft_id")              val craftId: Int,
    @SerializedName("crew")                  val crew: List<CrewInputDto> = emptyList(),
    @SerializedName("expected_return_hours") val expectedReturnHours: Int? = null
)

data class CheckinRequestDto(
    @SerializedName("card_uid")        val cardUid: String = "",
    @SerializedName("member_id")       val memberId: Int? = null,
    @SerializedName("notes_in")        val notesIn: String? = null,
    @SerializedName("damage_reported") val damageReported: Boolean = false
)
