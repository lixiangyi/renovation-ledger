package com.renovation.ledger.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface LedgerApi {
    @GET("health")
    suspend fun health(): HealthResponseDto

    @POST("auth/wechat")
    suspend fun wechatLogin(@Body body: WeChatLoginRequestDto): AuthResponseDto

    @POST("auth/sms/send")
    suspend fun smsSend(@Body body: SmsSendRequestDto): SmsSendResponseDto

    @POST("auth/sms/login")
    suspend fun smsLogin(@Body body: SmsLoginRequestDto): AuthResponseDto

    @POST("auth/bind-phone")
    suspend fun bindPhone(
        @Header("Authorization") auth: String,
        @Body body: BindPhoneRequestDto,
    ): AuthResponseDto

    @GET("me")
    suspend fun getMe(@Header("Authorization") auth: String): MeResponseDto

    @PATCH("me")
    suspend fun updateMe(
        @Header("Authorization") auth: String,
        @Body body: UpdateMeRequestDto,
    ): MeResponseDto

    @GET("ledgers")
    suspend fun listLedgers(@Header("Authorization") auth: String): List<LedgerSummaryDto>

    @POST("ledgers")
    suspend fun createLedger(
        @Header("Authorization") auth: String,
        @Body body: CreateLedgerRequestDto,
    ): LedgerSnapshotDto

    @POST("ledgers/import")
    suspend fun importLedger(
        @Header("Authorization") auth: String,
        @Body body: ImportLedgerRequestDto,
    ): LedgerSnapshotDto

    @GET("ledgers/{id}")
    suspend fun getLedger(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
    ): LedgerSnapshotDto

    @PATCH("ledgers/{id}")
    suspend fun renameLedger(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body body: RenameLedgerRequestDto,
    ): LedgerSnapshotDto

    @PUT("ledgers/{id}/items/{itemId}")
    suspend fun putItem(
        @Header("Authorization") auth: String,
        @Path("id") ledgerId: String,
        @Path("itemId") itemId: String,
        @Body body: PutItemRequestDto,
    ): Response<LedgerSnapshotDto>

    @DELETE("ledgers/{id}/items/{itemId}")
    suspend fun deleteItem(
        @Header("Authorization") auth: String,
        @Path("id") ledgerId: String,
        @Path("itemId") itemId: String,
        @Query("baseRevision") baseRevision: Long,
    ): Response<LedgerSnapshotDto>

    @POST("ledgers/{id}/invites")
    suspend fun createInvite(
        @Header("Authorization") auth: String,
        @Path("id") ledgerId: String,
    ): InviteCreatedDto

    @POST("invites/join")
    suspend fun joinInvite(
        @Header("Authorization") auth: String,
        @Body body: JoinInviteRequestDto,
    ): LedgerSnapshotDto
}
