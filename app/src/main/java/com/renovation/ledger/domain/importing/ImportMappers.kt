package com.renovation.ledger.domain.importing

import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import java.util.UUID

fun ImportedLineDraft.toBudgetItem(projectId: String): BudgetItem {
    val itemId = UUID.randomUUID().toString()
    val categoryValue = category.ifBlank { stage }
    return BudgetItem(
        id = itemId,
        projectId = projectId,
        name = name,
        // 旧账本「所属类别」语义是费用大类 → category；stage 同步写入便于清单按阶段折叠
        stage = stage,
        category = categoryValue,
        budgetAmount = budgetCents,
        contractAmount = contractCents,
        recordedDate = recordedDate,
        remark = remark,
        isNewAddition = true,
        payments = payments.map { pay ->
            Payment(
                id = UUID.randomUUID().toString(),
                budgetItemId = itemId,
                type = pay.type,
                amount = pay.amountCents,
                status = pay.status,
                paidAtEpochMs = pay.paidAtEpochMs,
                createdBy = pay.createdBy,
            )
        },
    )
}
