package com.renovation.ledger.data.local.mapper

import com.renovation.ledger.data.local.entity.BudgetItemEntity
import com.renovation.ledger.data.local.entity.PaymentEntity
import com.renovation.ledger.data.local.entity.ProjectEntity
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.LedgerOperationTimes
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import com.renovation.ledger.domain.model.Project

private const val MEMBER_NAME_SEPARATOR = ","

fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    name = name,
    memberNames = memberNamesCsv.split(MEMBER_NAME_SEPARATOR).filter { it.isNotBlank() },
    cloudLedgerId = cloudLedgerId,
    cloudRevision = cloudRevision,
    pendingSync = pendingSync,
    cloudLinkedAtEpochMs = cloudLinkedAtEpochMs,
)

fun Project.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    memberNamesCsv = memberNames.joinToString(MEMBER_NAME_SEPARATOR),
    cloudLedgerId = cloudLedgerId,
    cloudRevision = cloudRevision,
    pendingSync = pendingSync,
    cloudLinkedAtEpochMs = cloudLinkedAtEpochMs,
)

fun BudgetItemEntity.toDomain(payments: List<Payment> = emptyList()): BudgetItem = BudgetItem(
    id = id,
    projectId = projectId,
    name = name,
    stage = stage,
    category = category,
    space = space,
    budgetAmount = budgetAmount,
    contractAmount = contractAmount,
    merchant = merchant,
    recordedDate = recordedDate,
    remark = remark,
    isNewAddition = isNewAddition,
    settledOnDate = settledOnDate,
    settledAtEpochMs = settledAtEpochMs,
    payments = payments,
).let { LedgerOperationTimes.backfill(it) }

fun BudgetItem.toEntity(): BudgetItemEntity = BudgetItemEntity(
    id = id,
    projectId = projectId,
    name = name,
    stage = stage,
    category = category,
    space = space,
    budgetAmount = budgetAmount,
    contractAmount = contractAmount,
    merchant = merchant,
    recordedDate = recordedDate,
    remark = remark,
    isNewAddition = isNewAddition,
    settledOnDate = settledOnDate,
    settledAtEpochMs = settledAtEpochMs,
)

fun PaymentEntity.toDomain(): Payment = Payment(
    id = id,
    budgetItemId = budgetItemId,
    type = PaymentType.valueOf(type),
    amount = amount,
    status = PaymentStatus.valueOf(status),
    paidAtEpochMs = paidAtEpochMs,
    paidOnDate = paidOnDate,
    note = note,
    receiptUri = receiptUri,
    createdBy = createdBy,
)

fun Payment.toEntity(): PaymentEntity = PaymentEntity(
    id = id,
    budgetItemId = budgetItemId,
    type = type.name,
    amount = amount,
    status = status.name,
    paidAtEpochMs = paidAtEpochMs,
    paidOnDate = paidOnDate,
    note = note,
    receiptUri = receiptUri,
    createdBy = createdBy,
)
