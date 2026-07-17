package com.renovation.ledger.domain.importing

import com.renovation.ledger.data.autosave.AutosaveCsvCodec
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

object DcjzCsvImporter {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        isLenient = false
    }

    fun parse(csvText: String): List<ImportedLineDraft> {
        val lines = csvText
            .removePrefix("\uFEFF")
            .split("\r\n", "\n", "\r")
            .map { it.trimEnd() }
            .filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()
        if (lines.first() == AutosaveCsvCodec.MAGIC) {
            return parseAutosave(csvText)
        }

        val header = parseCsvLine(lines.first()).map { it.trim() }
        return when {
            isNativeHeader(header) -> parseNative(lines.drop(1), header)
            isLegacyHeader(header) -> parseLegacy(lines.drop(1))
            else -> throw IllegalArgumentException(
                "无法识别的 CSV 表头。请使用本 App「导出 CSV」，或旧装修记账导出（含记账日期）",
            )
        }
    }

    private fun parseAutosave(csvText: String): List<ImportedLineDraft> {
        val snapshot = AutosaveCsvCodec().decode(csvText)
            ?: throw IllegalArgumentException("自动备份 CSV 无法解析")
        val paymentsByItemId = snapshot.payments.groupBy { it.budgetItemId }
        return snapshot.items.map { item ->
            ImportedLineDraft(
                name = item.name,
                amountCents = item.budgetAmount,
                recordedDate = item.recordedDate,
                stage = item.stage,
                category = item.category,
                space = item.space,
                merchant = item.merchant,
                remark = item.remark,
                budgetCents = item.budgetAmount,
                contractCents = item.contractAmount,
                payments = paymentsByItemId[item.id].orEmpty().map { payment ->
                    ImportedPaymentDraft(
                        type = payment.type,
                        amountCents = payment.amount,
                        status = payment.status,
                        paidAtEpochMs = payment.paidAtEpochMs,
                        note = payment.note,
                        createdBy = payment.createdBy,
                    )
                },
            )
        }
    }

    fun yuanToCents(yuan: Double): Long = (yuan * 100.0).roundToLong()

    /** Simple CSV split supporting quoted fields. */
    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    private fun isLegacyHeader(header: List<String>): Boolean =
        header.getOrNull(0)?.contains("日期") == true &&
            header.none { it.contains("项名称") }

    private fun isNativeHeader(header: List<String>): Boolean =
        header.any { it.contains("项名称") } ||
            (header.any { it.contains("预算") } && header.any { it.contains("阶段") })

    private fun parseLegacy(dataLines: List<String>): List<ImportedLineDraft> =
        dataLines.mapNotNull { raw ->
            val cols = parseCsvLine(raw)
            if (cols.size < 4) return@mapNotNull null
            val date = cols[0].trim().ifBlank { null }
            val stage = cols[1].trim()
            if (stage.isEmpty()) return@mapNotNull null
            val rawName = cols.getOrNull(2)?.trim().orEmpty()
            val name = rawName.ifBlank { stage }
            val amountYuan = cols[3].trim().toDoubleOrNull() ?: return@mapNotNull null
            val remark = cols.getOrNull(4)?.trim().orEmpty()
            val cents = yuanToCents(amountYuan)
            ImportedLineDraft(
                name = name,
                amountCents = cents,
                recordedDate = date,
                stage = stage,
                category = stage,
                remark = remark,
                budgetCents = cents,
                contractCents = cents,
            )
        }

    /**
     * 本 App 导出：`项名称,阶段,分类,预算元,合同元,状态,付款类型,付款金额元,付款状态,日期,记账人`
     * 多付款行合并为一项；预算/合同分列保留；付款明细一并还原。
     */
    private fun parseNative(
        dataLines: List<String>,
        header: List<String>,
    ): List<ImportedLineDraft> {
        val idx = { keys: List<String> ->
            header.indexOfFirst { h -> keys.any { key -> h == key || h.contains(key) } }
        }
        val nameIdx = idx(listOf("项名称", "名称")).takeIf { it >= 0 } ?: 0
        val stageIdx = idx(listOf("阶段")).takeIf { it >= 0 } ?: 1
        val categoryIdx = idx(listOf("分类"))
        val budgetIdx = idx(listOf("预算元", "预算"))
        val contractIdx = idx(listOf("合同元", "合同"))
        val payTypeIdx = idx(listOf("付款类型"))
        val payAmountIdx = idx(listOf("付款金额"))
        val payStatusIdx = idx(listOf("付款状态"))
        // 「日期」避免命中「记账日期」里误匹配：优先精确「日期」
        val dateIdx = header.indexOf("日期").takeIf { it >= 0 } ?: idx(listOf("日期"))
        val payeeIdx = idx(listOf("记账人"))
        val remarkIdx = idx(listOf("备注"))

        val seen = LinkedHashMap<String, Accumulator>()
        dataLines.forEach { raw ->
            val cols = parseCsvLine(raw)
            if (cols.size <= nameIdx) return@forEach
            val name = cols.getOrNull(nameIdx)?.trim().orEmpty()
            if (name.isEmpty()) return@forEach
            val stage = cols.getOrNull(stageIdx)?.trim().orEmpty()
                .ifBlank { cols.getOrNull(categoryIdx)?.trim().orEmpty() }
                .ifBlank { "未分类" }
            val category = cols.getOrNull(categoryIdx)?.trim().orEmpty().ifBlank { stage }
            val budgetYuan = cols.getOrNull(budgetIdx)?.trim()?.toDoubleOrNull()
            val contractYuan = cols.getOrNull(contractIdx)?.trim()?.toDoubleOrNull()
            // 预算优先；仅无预算时才退回合同（兼容缺列）
            val budgetCents = when {
                budgetYuan != null -> yuanToCents(budgetYuan)
                contractYuan != null -> yuanToCents(contractYuan)
                else -> return@forEach
            }
            val contractCents = contractYuan?.let { yuanToCents(it) }
            val date = cols.getOrNull(dateIdx)?.trim()?.ifBlank { null }
            val remark = cols.getOrNull(remarkIdx)?.trim().orEmpty()
            val key = "$name|$stage"
            val acc = seen.getOrPut(key) {
                Accumulator(
                    name = name,
                    stage = stage,
                    category = category,
                    budgetCents = budgetCents,
                    contractCents = contractCents,
                    recordedDate = date,
                    remark = remark,
                )
            }
            // 多行取最新出现的预算/合同（同导出重复写入）
            acc.budgetCents = budgetCents
            if (contractCents != null) acc.contractCents = contractCents
            if (date != null) acc.recordedDate = date
            if (remark.isNotEmpty()) acc.remark = remark

            val payAmountYuan = cols.getOrNull(payAmountIdx)?.trim()?.toDoubleOrNull()
            val payTypeLabel = cols.getOrNull(payTypeIdx)?.trim().orEmpty()
            if (payAmountYuan != null && payAmountYuan > 0 && payTypeLabel.isNotEmpty()) {
                val statusLabel = cols.getOrNull(payStatusIdx)?.trim().orEmpty()
                acc.payments.add(
                    ImportedPaymentDraft(
                        type = parsePaymentType(payTypeLabel),
                        amountCents = yuanToCents(payAmountYuan),
                        status = parsePaymentStatus(statusLabel),
                        paidAtEpochMs = date?.let { parseDateEpoch(it) },
                        createdBy = cols.getOrNull(payeeIdx)?.trim().orEmpty(),
                    ),
                )
            }
        }
        return seen.values.map { it.toDraft() }
    }

    private fun parsePaymentType(label: String): PaymentType = when {
        label.contains("定金") -> PaymentType.DEPOSIT
        label.contains("全款") -> PaymentType.FULL
        label.contains("尾款") -> PaymentType.FINAL
        else -> PaymentType.OTHER
    }

    private fun parsePaymentStatus(label: String): PaymentStatus = when {
        label.contains("未付") -> PaymentStatus.UNPAID
        else -> PaymentStatus.PAID
    }

    private fun parseDateEpoch(date: String): Long? = runCatching {
        dateFormat.parse(date)?.time
    }.getOrNull()

    private class Accumulator(
        val name: String,
        val stage: String,
        var category: String,
        var budgetCents: Long,
        var contractCents: Long?,
        var recordedDate: String?,
        var remark: String,
        val payments: MutableList<ImportedPaymentDraft> = mutableListOf(),
    ) {
        fun toDraft(): ImportedLineDraft = ImportedLineDraft(
            name = name,
            amountCents = budgetCents,
            recordedDate = recordedDate,
            stage = stage,
            category = category,
            remark = remark,
            budgetCents = budgetCents,
            contractCents = contractCents,
            payments = payments.toList(),
        )
    }
}
