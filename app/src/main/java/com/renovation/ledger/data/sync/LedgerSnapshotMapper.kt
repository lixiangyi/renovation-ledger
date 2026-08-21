package com.renovation.ledger.data.sync

import com.google.gson.reflect.TypeToken
import com.renovation.ledger.data.remote.ApiItemDto
import com.renovation.ledger.data.remote.ApiPaymentDto
import com.renovation.ledger.data.remote.ApiTaxonomyDto
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import com.renovation.ledger.domain.taxonomy.Taxonomy
import com.renovation.ledger.domain.taxonomy.TaxonomyCatalog
import com.renovation.ledger.domain.taxonomy.TaxonomyIconRef
import com.renovation.ledger.dsl.gson

object LedgerSnapshotMapper {
    fun toDto(item: BudgetItem): ApiItemDto = ApiItemDto(
        id = item.id,
        name = item.name,
        stage = item.stage,
        category = item.category,
        space = item.space,
        budgetAmount = item.budgetAmount,
        contractAmount = item.contractAmount,
        merchant = item.merchant,
        recordedDate = item.recordedDate,
        remark = item.remark,
        isNewAddition = item.isNewAddition,
        settledOnDate = item.settledOnDate,
        settledAtEpochMs = item.settledAtEpochMs,
        payments = item.payments.map { p ->
            ApiPaymentDto(
                id = p.id,
                type = p.type.name,
                amount = p.amount,
                status = p.status.name,
                paidAtEpochMs = p.paidAtEpochMs,
                paidOnDate = p.paidOnDate,
                note = p.note,
                receiptUri = p.receiptUri,
                createdByName = p.createdBy,
            )
        },
    )

    fun toDomain(dto: ApiItemDto, projectId: String): BudgetItem = BudgetItem(
        id = dto.id,
        projectId = projectId,
        name = dto.name,
        stage = dto.stage,
        category = dto.category,
        space = dto.space,
        budgetAmount = dto.budgetAmount,
        contractAmount = dto.contractAmount,
        merchant = dto.merchant,
        recordedDate = dto.recordedDate,
        remark = dto.remark,
        isNewAddition = dto.isNewAddition,
        settledOnDate = dto.settledOnDate,
        settledAtEpochMs = dto.settledAtEpochMs,
        payments = dto.payments.map { p ->
            Payment(
                id = p.id,
                budgetItemId = dto.id,
                type = runCatching { PaymentType.valueOf(p.type) }.getOrDefault(PaymentType.OTHER),
                amount = p.amount,
                status = runCatching { PaymentStatus.valueOf(p.status) }.getOrDefault(PaymentStatus.UNPAID),
                paidAtEpochMs = p.paidAtEpochMs,
                paidOnDate = p.paidOnDate,
                note = p.note,
                receiptUri = p.receiptUri,
                createdBy = p.createdByName,
            )
        },
    )

    fun toTaxonomyDto(catalog: TaxonomyCatalog): ApiTaxonomyDto {
        val icons = mapOf(
            "stages" to catalog.stageIcons,
            "categories" to catalog.categoryIcons,
            "spaces" to catalog.spaceIcons,
        )
        return ApiTaxonomyDto(
            stages = catalog.stages,
            categories = catalog.categories,
            spaces = catalog.spaces,
            iconsJson = gson.toJson(icons),
        )
    }

    fun toCatalog(dto: ApiTaxonomyDto): TaxonomyCatalog {
        val type = object : TypeToken<Map<String, Map<String, TaxonomyIconRef>>>() {}.type
        val icons = runCatching {
            if (dto.iconsJson.isBlank() || dto.iconsJson == "{}") {
                emptyMap()
            } else {
                gson.fromJson<Map<String, Map<String, TaxonomyIconRef>>>(dto.iconsJson, type)
            }
        }.getOrNull() ?: emptyMap()
        return TaxonomyCatalog(
            stages = dto.stages.ifEmpty { Taxonomy.STAGES },
            categories = dto.categories.ifEmpty { Taxonomy.CATEGORIES },
            spaces = dto.spaces.ifEmpty { Taxonomy.SPACES },
            stageIcons = icons["stages"] ?: emptyMap(),
            categoryIcons = icons["categories"] ?: emptyMap(),
            spaceIcons = icons["spaces"] ?: emptyMap(),
        )
    }
}
