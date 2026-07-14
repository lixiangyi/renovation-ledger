package com.renovation.ledger

import com.renovation.ledger.data.autosave.AutosaveCsvCodec
import com.renovation.ledger.data.autosave.AutosaveSnapshot
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import com.renovation.ledger.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutosaveCsvCodecTest {
    private val codec = AutosaveCsvCodec()

    @Test
    fun round_trip_preserves_settle_payment() {
        val project = Project("p1", "我家装修", listOf("一口吞", "汤圆"))
        val item = BudgetItem(
            id = "i1",
            projectId = "p1",
            name = "全屋定制",
            stage = "全屋定制",
            category = "全屋定制",
            space = "全屋",
            budgetAmount = 105500_00L,
            contractAmount = 105500_00L,
            merchant = "",
            recordedDate = "2026-03-03",
            remark = "含衣柜",
            isNewAddition = true,
        )
        val payment = Payment(
            id = "pay1",
            budgetItemId = "i1",
            type = PaymentType.OTHER,
            amount = 105500_00L,
            status = PaymentStatus.PAID,
            paidAtEpochMs = 1_700_000_000_000L,
            note = "结清补差",
            createdBy = "一口吞",
        )
        val snapshot = AutosaveSnapshot(project, listOf(item), listOf(payment))
        val csv = codec.encode(snapshot)
        assertTrue(csv.startsWith("\uFEFF") || csv.contains("#renovation_ledger_autosave_v1"))
        val parsed = codec.decode(csv)!!
        assertEquals(project, parsed.project)
        assertEquals(1, parsed.items.size)
        assertEquals(item.copy(payments = emptyList()), parsed.items.single().copy(payments = emptyList()))
        assertEquals(payment, parsed.payments.single())
    }

    @Test
    fun decode_rejects_non_autosave() {
        assertNull(codec.decode("记账日期,所属类别,建材名称,金额,备注\n2026-01-01,家具,桌,1,\n"))
    }

    @Test
    fun summarize_counts_items_and_payments() {
        val csv = codec.encode(
            AutosaveSnapshot(
                Project("p", "x", listOf("我")),
                listOf(
                    BudgetItem("i", "p", "a", "s", "c", "", 100, 100, isNewAddition = true),
                ),
                listOf(
                    Payment("p1", "i", PaymentType.OTHER, 100, PaymentStatus.PAID, note = "结清补差"),
                ),
            ),
        )
        val summary = codec.summarize(csv)!!
        assertEquals(1, summary.itemCount)
        assertEquals(1, summary.paymentCount)
    }
}
