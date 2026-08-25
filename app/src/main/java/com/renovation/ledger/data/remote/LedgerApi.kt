package com.renovation.ledger.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.MultipartBody

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

    @Multipart
    @POST("me/avatar")
    suspend fun uploadAvatar(
        @Header("Authorization") auth: String,
        @Part file: MultipartBody.Part,
    ): MeResponseDto

    @DELETE("me/avatar")
    suspend fun deleteAvatar(@Header("Authorization") auth: String): MeResponseDto

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

    @GET("invites/{code}/preview")
    suspend fun previewInvite(
        @Header("Authorization") auth: String,
        @Path("code") code: String,
    ): InvitePreviewDto

    @POST("invites/join")
    suspend fun joinInvite(
        @Header("Authorization") auth: String,
        @Body body: JoinInviteRequestDto,
    ): LedgerSnapshotDto

    @GET("ledgers/{id}/members")
    suspend fun listMembers(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
    ): List<MemberDto>

    /** 所有者软删云账本。 */
    @DELETE("ledgers/{id}")
    suspend fun deleteLedger(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
    )

    /** 协作者退出成员。 */
    @POST("ledgers/{id}/leave")
    suspend fun leaveLedger(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
    )
}
