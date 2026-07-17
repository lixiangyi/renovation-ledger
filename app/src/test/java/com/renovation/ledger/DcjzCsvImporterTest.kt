package com.renovation.ledger

import com.renovation.ledger.domain.importing.DcjzCsvImporter
import com.renovation.ledger.domain.importing.ImportDeduper
import com.renovation.ledger.domain.importing.ImportedLineDraft
import com.renovation.ledger.data.autosave.AutosaveCsvCodec
import com.renovation.ledger.data.autosave.AutosaveSnapshot
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import com.renovation.ledger.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DcjzCsvImporterTest {
    @Test
    fun parses_sample_csv_66_rows_and_sum() {
        val text = javaClass.getResourceAsStream("/dcjz_export_sample.csv")!!
            .bufferedReader(Charsets.UTF_8)
            .readText()
        val drafts = DcjzCsvImporter.parse(text)
        assertEquals(66, drafts.size)
        assertEquals(50_634_749L, drafts.sumOf { it.amountCents })
    }

    @Test
    fun empty_name_uses_stage() {
        val csv = """
            记账日期,所属类别,建材名称,金额,备注
            2026-02-09,全屋智能,,22100.0,已交2w
        """.trimIndent()
        val drafts = DcjzCsvImporter.parse(csv)
        assertEquals(1, drafts.size)
        assertEquals("全屋智能", drafts[0].name)
        assertEquals("全屋智能", drafts[0].stage)
        assertEquals("已交2w", drafts[0].remark)
        assertEquals(2_210_000L, drafts[0].amountCents)
    }

    @Test
    fun parses_native_app_export_and_merges_payment_rows() {
        val csv = """
            项名称,阶段,分类,预算元,合同元,状态,付款类型,付款金额元,付款状态,日期,记账人
            窗帘,软装,软装,9000,8000,付款中,定金,2000,已付,2026-07-01,我
            窗帘,软装,软装,9000,8000,付款中,尾款,6000,未付,,我
            电视,家电,家电,22000,20000,已结清,全款,20000,已付,2026-06-01,我
        """.trimIndent()
        val drafts = DcjzCsvImporter.parse(csv)
        assertEquals(2, drafts.size)
        val curtain = drafts.first { it.name == "窗帘" }
        assertEquals(900_000L, curtain.amountCents)
        assertEquals(900_000L, curtain.budgetCents)
        assertEquals(800_000L, curtain.contractCents)
        assertEquals("软装", curtain.stage)
        assertEquals(2, curtain.payments.size)
        assertEquals(PaymentType.DEPOSIT, curtain.payments[0].type)
        assertEquals(PaymentStatus.PAID, curtain.payments[0].status)
        assertEquals(PaymentType.FINAL, curtain.payments[1].type)
        assertEquals(PaymentStatus.UNPAID, curtain.payments[1].status)
        val tv = drafts.first { it.name == "电视" }
        assertEquals(2_200_000L, tv.budgetCents)
        assertEquals(2_000_000L, tv.contractCents)
        assertEquals(1, tv.payments.size)
        assertEquals(PaymentType.FULL, tv.payments[0].type)
    }

    @Test
    fun parses_autosave_export_format() {
        val csv = AutosaveCsvCodec().encode(
            AutosaveSnapshot(
                project = Project("p1", "我家装修", listOf("我")),
                items = listOf(
                    BudgetItem(
                        id = "i1",
                        projectId = "p1",
                        name = "橱柜",
                        stage = "主材",
                        category = "全屋定制",
                        space = "厨房",
                        budgetAmount = 25_000_00L,
                        contractAmount = 23_000_00L,
                        merchant = "本地商家",
                        recordedDate = "2026-07-15",
                        remark = "含五金",
                    ),
                ),
                payments = listOf(
                    Payment(
                        id = "pay1",
                        budgetItemId = "i1",
                        type = PaymentType.DEPOSIT,
                        amount = 5_000_00L,
                        status = PaymentStatus.PAID,
                        paidAtEpochMs = 1_783_955_200_000L,
                        note = "首付款",
                        createdBy = "我",
                    ),
                ),
            ),
        )

        val drafts = DcjzCsvImporter.parse(csv)

        assertEquals(1, drafts.size)
        val item = drafts.single()
        assertEquals("橱柜", item.name)
        assertEquals("厨房", item.space)
        assertEquals("本地商家", item.merchant)
        assertEquals(25_000_00L, item.budgetCents)
        assertEquals(23_000_00L, item.contractCents)
        assertEquals("含五金", item.remark)
        assertEquals(1, item.payments.size)
        assertEquals("首付款", item.payments.single().note)
    }

    @Test
    fun rejects_unknown_header() {
        val csv = "foo,bar\n1,2"
        try {
            DcjzCsvImporter.parse(csv)
            throw AssertionError("expected exception")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("无法识别"))
        }
    }
}

class ImportDeduperTest {
    @Test
    fun marks_duplicates_unselected() {
        val lines = listOf(
            ImportedLineDraft("纱窗", 140_000, "2026-05-03", "硬装"),
            ImportedLineDraft("纱窗", 140_000, "2026-05-03", "硬装"),
            ImportedLineDraft("美缝", 350_000, "2026-02-09", "硬装"),
        )
        val result = ImportDeduper.dedupe(lines)
        assertFalse(result[0].isDuplicate)
        assertTrue(result[0].selected)
        assertTrue(result[1].isDuplicate)
        assertFalse(result[1].selected)
        assertFalse(result[2].isDuplicate)
    }
}
