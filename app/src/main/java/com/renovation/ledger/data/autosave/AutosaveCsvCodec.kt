package com.renovation.ledger.data.autosave

import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import com.renovation.ledger.domain.model.Project
import com.renovation.ledger.dsl.catchException
import javax.inject.Inject

class AutosaveCsvCodec @Inject constructor() {

    fun encode(snapshot: AutosaveSnapshot): String {
        val lines = buildList {
            add(MAGIC)
            add(HEADER)
            add(encodeProjectRow(snapshot.project))
            snapshot.items.forEach { add(encodeItemRow(it)) }
            snapshot.payments.forEach { add(encodePaymentRow(it)) }
        }
        return "\uFEFF" + lines.joinToString("\n") + "\n"
    }

    fun decode(csvText: String): AutosaveSnapshot? = catchException(
        isPrintStackTrace = false,
        onError = { null },
    ) {
        val lines = csvText
            .removePrefix("\uFEFF")
            .split("\r\n", "\n", "\r")
            .map { it.trimEnd() }
            .filter { it.isNotEmpty() }
        if (lines.isEmpty() || lines.first() != MAGIC) return@catchException null

        var project: Project? = null
        val items = mutableListOf<BudgetItem>()
        val payments = mutableListOf<Payment>()

        for (line in lines.drop(2)) {
            val cols = parseCsvLine(line)
            when (cols.getOrNull(0)) {
                "project" -> {
                    val parsed = parseProjectRow(cols) ?: return@catchException null
                    project = parsed
                }
                "item" -> {
                    val parsed = parseItemRow(cols) ?: return@catchException null
                    items.add(parsed)
                }
                "payment" -> {
                    val parsed = parsePaymentRow(cols) ?: return@catchException null
                    payments.add(parsed)
                }
            }
        }

        project?.let { AutosaveSnapshot(it, items, payments) }
    }

    fun summarize(csvText: String): AutosaveSummary? {
        val lines = csvText
            .removePrefix("\uFEFF")
            .split("\r\n", "\n", "\r")
            .map { it.trimEnd() }
            .filter { it.isNotEmpty() }
        if (lines.isEmpty() || lines.first() != MAGIC) return null

        var itemCount = 0
        var paymentCount = 0
        for (line in lines.drop(2)) {
            val recordType = parseCsvLine(line).getOrNull(0)
            when (recordType) {
                "item" -> itemCount++
                "payment" -> paymentCount++
            }
        }
        return AutosaveSummary(itemCount = itemCount, paymentCount = paymentCount)
    }

    private fun encodeProjectRow(project: Project): String = formatRow(
        "project",
        project.id,
        project.name,
        project.memberNames.joinToString("|"),
    )

    private fun encodeItemRow(item: BudgetItem): String = formatRow(
        "item",
        item.projectId,
        "",
        "",
        item.id,
        item.name,
        item.stage,
        item.category,
        item.space,
        item.budgetAmount.toString(),
        item.contractAmount?.toString().orEmpty(),
        item.merchant,
        item.recordedDate.orEmpty(),
        item.remark,
        if (item.isNewAddition) "1" else "0",
    )

    private fun encodePaymentRow(payment: Payment): String = formatRow(
        "payment",
        "",
        "",
        "",
        payment.budgetItemId,
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        payment.id,
        payment.type.name,
        payment.amount.toString(),
        payment.status.name,
        payment.paidAtEpochMs?.toString().orEmpty(),
        payment.note,
        payment.createdBy,
    )

    private fun parseProjectRow(cols: List<String>): Project? {
        val id = cols.getOrNull(1)?.trim().orEmpty()
        val name = cols.getOrNull(2)?.trim().orEmpty()
        if (id.isEmpty()) return null
        val members = cols.getOrNull(3)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.split("|")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        return Project(id = id, name = name, memberNames = members)
    }

    private fun parseItemRow(cols: List<String>): BudgetItem? {
        val projectId = cols.getOrNull(1)?.trim().orEmpty()
        val id = cols.getOrNull(4)?.trim().orEmpty()
        val name = cols.getOrNull(5)?.trim().orEmpty()
        val stage = cols.getOrNull(6)?.trim().orEmpty()
        if (projectId.isEmpty() || id.isEmpty() || stage.isEmpty()) return null

        val budgetFen = cols.getOrNull(9)?.trim()?.toLongOrNull() ?: return null
        val contractFen = cols.getOrNull(10)?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()
        val isNewAddition = when (cols.getOrNull(14)?.trim()) {
            "1" -> true
            "0" -> false
            "", null -> false
            else -> return null
        }

        return BudgetItem(
            id = id,
            projectId = projectId,
            name = name,
            stage = stage,
            category = cols.getOrNull(7)?.trim().orEmpty(),
            space = cols.getOrNull(8)?.trim().orEmpty(),
            budgetAmount = budgetFen,
            contractAmount = contractFen,
            merchant = cols.getOrNull(11)?.trim().orEmpty(),
            recordedDate = cols.getOrNull(12)?.trim()?.takeIf { it.isNotEmpty() },
            remark = cols.getOrNull(13)?.trim().orEmpty(),
            isNewAddition = isNewAddition,
            payments = emptyList(),
        )
    }

    private fun parsePaymentRow(cols: List<String>): Payment? {
        val budgetItemId = cols.getOrNull(4)?.trim().orEmpty()
        val id = cols.getOrNull(15)?.trim().orEmpty()
        if (budgetItemId.isEmpty() || id.isEmpty()) return null

        val type = runCatching {
            PaymentType.valueOf(cols.getOrNull(16)?.trim().orEmpty())
        }.getOrNull() ?: return null

        val amount = cols.getOrNull(17)?.trim()?.toLongOrNull() ?: return null

        val status = runCatching {
            PaymentStatus.valueOf(cols.getOrNull(18)?.trim().orEmpty())
        }.getOrNull() ?: return null

        val paidAtEpochMs = cols.getOrNull(19)?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()

        return Payment(
            id = id,
            budgetItemId = budgetItemId,
            type = type,
            amount = amount,
            status = status,
            paidAtEpochMs = paidAtEpochMs,
            note = cols.getOrNull(20)?.trim().orEmpty(),
            createdBy = cols.getOrNull(21)?.trim().orEmpty(),
        )
    }

    private fun formatRow(vararg fields: String): String =
        fields.joinToString(",") { escapeCsvField(it) }

    private fun escapeCsvField(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            return value
        }
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun parseCsvLine(line: String): List<String> {
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

    companion object {
        const val MAGIC = "#renovation_ledger_autosave_v1"
        const val HEADER =
            "record_type,project_id,project_name,member_names,item_id,item_name,stage,category,space,budget_fen,contract_fen,merchant,recorded_date,remark,is_new_addition,payment_id,payment_type,payment_amount_fen,payment_status,paid_at_epoch_ms,payment_note,created_by"
    }
}
