package com.renovation.ledger.data.remote

data class AuthResponseDto(
    val userId: String,
    val token: String,
    val nickname: String,
    val phone: String? = null,
)

data class WeChatLoginRequestDto(
    val code: String,
    val client: String,
)

data class BindPhoneRequestDto(
    val phoneCode: String,
    val client: String,
)

data class SmsSendRequestDto(
    val phone: String,
)

data class SmsSendResponseDto(
    val expiresInSec: Long,
    val code: String? = null,
)

data class SmsLoginRequestDto(
    val phone: String,
    val code: String,
)

data class HealthResponseDto(
    val ok: Boolean = false,
)

data class MeResponseDto(
    val userId: String,
    val nickname: String,
    val phone: String? = null,
)

data class UpdateMeRequestDto(
    val nickname: String,
)

data class LedgerSummaryDto(
    val id: String,
    val name: String,
    val role: String,
    val revision: Long = 0,
)

data class CreateLedgerRequestDto(
    val name: String,
    val localId: String,
)

data class RenameLedgerRequestDto(
    val name: String,
)

data class ApiTaxonomyDto(
    val stages: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val spaces: List<String> = emptyList(),
    val iconsJson: String = "{}",
)

data class ApiPaymentDto(
    val id: String,
    val type: String,
    val amount: Long,
    val status: String,
    val paidAtEpochMs: Long? = null,
    val paidOnDate: String? = null,
    val note: String = "",
    val receiptUri: String? = null,
    val createdByUserId: String? = null,
    val createdByName: String = "",
)

data class ApiItemDto(
    val id: String,
    val name: String,
    val stage: String,
    val category: String,
    val space: String,
    val budgetAmount: Long,
    val contractAmount: Long? = null,
    val merchant: String = "",
    val recordedDate: String? = null,
    val remark: String = "",
    val isNewAddition: Boolean = false,
    val settledOnDate: String? = null,
    val settledAtEpochMs: Long? = null,
    val payments: List<ApiPaymentDto> = emptyList(),
)

data class ImportLedgerRequestDto(
    val localId: String,
    val name: String,
    val items: List<ApiItemDto> = emptyList(),
    val taxonomy: ApiTaxonomyDto = ApiTaxonomyDto(),
)

data class LedgerSnapshotDto(
    val id: String,
    val name: String,
    val revision: Long,
    val items: List<ApiItemDto> = emptyList(),
    val taxonomy: ApiTaxonomyDto = ApiTaxonomyDto(),
)

data class PutItemRequestDto(
    val baseRevision: Long,
    val item: ApiItemDto,
)

data class JoinInviteRequestDto(
    val code: String,
)

data class InviteCreatedDto(
    val id: String,
    val code: String,
)
