package com.renovation.ledger

import com.renovation.ledger.data.remote.ApiItemDto
import com.renovation.ledger.data.remote.ApiPaymentDto
import com.renovation.ledger.data.sync.LedgerSnapshotMapper
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerSnapshotMapperTest {
    @Test
    fun itemRoundTripPreservesFenAndPayments() {
        val item = BudgetItem(
            id = "item_1",
            projectId = "proj_1",
            name = "灯具",
            stage = "软装",
            category = "灯具",
            space = "客厅",
            budgetAmount = 10000,
            contractAmount = 12000,
            merchant = "某店",
            recordedDate = "2026-08-01",
            remark = "备注",
            isNewAddition = false,
            settledOnDate = "2026-08-02",
            settledAtEpochMs = 2L,
            payments = listOf(
                Payment(
                    id = "pay_1",
                    budgetItemId = "item_1",
                    type = PaymentType.DEPOSIT,
                    amount = 3000,
                    status = PaymentStatus.PAID,
                    paidAtEpochMs = 1L,
                    paidOnDate = "2026-08-01",
                    note = "定金",
                    receiptUri = null,
                    createdBy = "我",
                ),
            ),
        )
        val dto = LedgerSnapshotMapper.toDto(item)
        val back = LedgerSnapshotMapper.toDomain(dto, projectId = "proj_1")
        assertEquals(item.id, back.id)
        assertEquals(item.budgetAmount, back.budgetAmount)
        assertEquals(item.contractAmount, back.contractAmount)
        assertEquals(1, back.payments.size)
        assertEquals(3000L, back.payments[0].amount)
        assertEquals(PaymentType.DEPOSIT, back.payments[0].type)
        assertEquals(PaymentStatus.PAID, back.payments[0].status)
        assertEquals("2026-08-01", back.payments[0].paidOnDate)
        assertEquals("2026-08-02", back.settledOnDate)
        assertEquals(2L, back.settledAtEpochMs)
    }

    @Test
    fun dtoToDomainMapsCreatedByName() {
        val dto = ApiItemDto(
            id = "i",
            name = "n",
            stage = "s",
            category = "c",
            space = "sp",
            budgetAmount = 1,
            payments = listOf(
                ApiPaymentDto(
                    id = "p",
                    type = "FULL",
                    amount = 1,
                    status = "UNPAID",
                    createdByName = "张三",
                ),
            ),
        )
        val domain = LedgerSnapshotMapper.toDomain(dto, "proj")
        assertEquals("张三", domain.payments[0].createdBy)
    }
}
