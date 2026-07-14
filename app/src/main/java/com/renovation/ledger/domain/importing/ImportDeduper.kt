package com.renovation.ledger.domain.importing

object ImportDeduper {
    fun dedupe(lines: List<ImportedLineDraft>): List<ImportedLineDraft> {
        val seen = mutableSetOf<String>()
        return lines.map { line ->
            val key = "${line.name.trim()}|${line.amountCents}|${line.recordedDate.orEmpty()}"
            if (seen.add(key)) {
                line.copy(isDuplicate = false, selected = true)
            } else {
                line.copy(isDuplicate = true, selected = false)
            }
        }
    }
}
