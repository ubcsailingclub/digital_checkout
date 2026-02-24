package com.ubcsc.checkout.data.api

import com.ubcsc.checkout.data.api.dto.CraftDto
import com.ubcsc.checkout.data.api.dto.CheckinRequestDto
import com.ubcsc.checkout.data.api.dto.MemberDto
import com.ubcsc.checkout.data.api.dto.SessionCreateDto
import com.ubcsc.checkout.data.api.dto.SessionResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface CheckoutApi {

    @GET("crafts")
    suspend fun getCrafts(): List<CraftDto>

    @GET("members/card/{uid}")
    suspend fun getMemberByCard(@Path("uid") uid: String): MemberDto

    @POST("sessions")
    suspend fun checkout(@Body req: SessionCreateDto): SessionResponseDto

    @PATCH("sessions/{sessionId}/checkin")
    suspend fun checkin(
        @Path("sessionId") sessionId: Int,
        @Body req: CheckinRequestDto
    ): SessionResponseDto
}
