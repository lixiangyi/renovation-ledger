package com.renovation.ledger.voice.tool.executors

import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.LedgerOperationTimes
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import com.renovation.ledger.domain.taxonomy.Taxonomy
import com.renovation.ledger.ui.detail.parseYuanToFen
import com.renovation.ledger.voice.tool.PreviewField
import com.renovation.ledger.voice.tool.RiskLevel
import com.renovation.ledger.voice.tool.ToolExecutor
import com.renovation.ledger.voice.tool.ToolPreview
import com.renovation.ledger.voice.tool.ToolResult
import com.renovation.ledger.voice.tool.ToolSchema
import com.renovation.ledger.voice.tool.asBoolean
import com.renovation.ledger.voice.tool.asDouble
import com.renovation.ledger.voice.tool.asString
import com.renovation.ledger.voice.tool.toCurrencyWithPaidFlag
import com.renovation.ledger.voice.tool.toYuanText
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

interface VoiceLedgerStore {
    suspend fun currentProjectId(): String
    suspend fun nickname(): String
    suspend fun upsertItem(item: BudgetItem)
    suspend fun upsertPayment(payment: Payment)
}

class VoiceLedgerStoreImpl @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val userPrefs: UserPrefs,
) : VoiceLedgerStore {
    override suspend fun currentProjectId(): String =
        projectRepository.snapshotCurrentProjectWithItems().first.id

    override suspend fun nickname(): String = userPrefs.userProfile.first().nickname

    override suspend fun upsertItem(item: BudgetItem) {
        projectRepository.upsertItem(item)
    }

    override suspend fun upsertPayment(payment: Payment) {
        projectRepository.upsertPayment(payment)
    }
}

class AddLedgerEntryExecutor @Inject constructor(
    private val store: VoiceLedgerStore,
) : ToolExecutor {
    override val toolName: String = "add_ledger_entry"
    override val risk: RiskLevel = RiskLevel.HIGH
    override val schema: ToolSchema = ToolSchema(
        name = toolName,
        description = "新增装修记账，可拆分定金和尾款",
        parametersJson = """
            {"type":"object","required":["name","amount"],"properties":{
              "name":{"type":"string"},
              "category":{"type":"string"},
              "stage":{"type":"string"},
              "space":{"type":"string"},
              "amount":{"type":"number"},
              "deposit":{"type":"number"},
              "depositPaid":{"type":"boolean"},
              "finalPayment":{"type":"number"},
              "finalPaid":{"type":"boolean"}
            }}
        """.trimIndent(),
        risk = risk,
    )

    override fun preview(params: Map<String, Any?>): ToolPreview {
        val amount = params["amount"].toYuanText()
        val deposit = params["deposit"]
        val finalPayment = params["finalPayment"] ?: inferredFinal(params)
        return ToolPreview(
            title = "新增记账",
            fields = listOf(
                PreviewField("名称", params["name"].asString(), editable = true, key = "name"),
                PreviewField("大类", params["category"].asString(), editable = true, key = "category"),
                PreviewField("总价", if (amount.isEmpty()) "" else "¥$amount", editable = true, key = "amount"),
                PreviewField("定金", deposit.toCurrencyWithPaidFlag(params["depositPaid"]), editable = true, key = "deposit"),
                PreviewField("尾款", finalPayment.toCurrencyWithPaidFlag(params["finalPaid"]), editable = true, key = "finalPayment"),
            ),
        )
    }

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val name = params["name"].asString()
        if (name.isEmpty()) return ToolResult(false, "", "缺少名称")
        val amount = params["amount"].asDouble()
        val deposit = params["deposit"].asDouble()
        val finalPayment = params["finalPayment"].asDouble() ?: inferredFinal(params).asDouble()
        if (amount == null && deposit == null && finalPayment == null) {
            return ToolResult(false, "", "缺少金额")
        }
        val projectId = store.currentProjectId()
        val nickname = store.nickname()
        val itemId = UUID.randomUUID().toString()
        val budgetFen = parseYuanToFen((amount ?: deposit ?: finalPayment ?: 0.0).toYuanText()) ?: 0L
        store.upsertItem(
            BudgetItem(
                id = itemId,
                projectId = projectId,
                name = name,
                stage = params["stage"].asString().ifBlank { Taxonomy.STAGES.first() },
                category = params["category"].asString(),
                space = params["space"].asString(),
                budgetAmount = budgetFen,
                isNewAddition = true,
            ),
        )
        val payments = buildPayments(params, amount, deposit, finalPayment)
        payments.forEach { payload ->
            val amountFen = parseYuanToFen(payload.amountYuan) ?: return@forEach
            val now = System.currentTimeMillis()
            val today = LedgerOperationTimes.today(now)
            val (paidOnDate, paidAt) = LedgerOperationTimes.newPaymentTimes(payload.status, now, today)
            store.upsertPayment(
                Payment(
                    id = UUID.randomUUID().toString(),
                    budgetItemId = itemId,
                    type = payload.type,
                    amount = amountFen,
                    status = payload.status,
                    paidAtEpochMs = paidAt,
                    paidOnDate = paidOnDate,
                    note = "语音助手",
                    createdBy = nickname,
                ),
            )
        }
        return ToolResult(true, "记账已保存")
    }

    private fun inferredFinal(params: Map<String, Any?>): Any? {
        val amount = params["amount"].asDouble() ?: return params["finalPayment"]
        val deposit = params["deposit"].asDouble() ?: return params["finalPayment"]
        val remain = amount - deposit
        return if (remain > 0) remain else params["finalPayment"]
    }

    private fun buildPayments(
        params: Map<String, Any?>,
        amount: Double?,
        deposit: Double?,
        finalPayment: Double?,
    ): List<VoicePaymentDraft> {
        val drafts = mutableListOf<VoicePaymentDraft>()
        if (deposit != null && deposit > 0) {
            drafts += VoicePaymentDraft(
                amountYuan = deposit.toYuanText(),
                type = PaymentType.DEPOSIT,
                status = if (params["depositPaid"].asBoolean(default = true)) PaymentStatus.PAID else PaymentStatus.UNPAID,
            )
            val finalAmt = finalPayment ?: ((amount ?: 0.0) - deposit)
            if (finalAmt > 0) {
                drafts += VoicePaymentDraft(
                    amountYuan = finalAmt.toYuanText(),
                    type = PaymentType.FINAL,
                    status = if (params["finalPaid"].asBoolean(default = false)) PaymentStatus.PAID else PaymentStatus.UNPAID,
                )
            }
        } else if (amount != null && amount > 0) {
            drafts += VoicePaymentDraft(
                amountYuan = amount.toYuanText(),
                type = PaymentType.FINAL,
                status = if (params["finalPaid"].asBoolean(default = true)) PaymentStatus.PAID else PaymentStatus.UNPAID,
            )
        }
        return drafts
    }

    private data class VoicePaymentDraft(
        val amountYuan: String,
        val type: PaymentType,
        val status: PaymentStatus,
    )
}
