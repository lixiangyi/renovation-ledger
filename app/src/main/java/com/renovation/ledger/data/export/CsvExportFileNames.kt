package com.renovation.ledger.data.export

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExportFileNames {

    private val stampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)

    fun sanitize(ledgerName: String): String {
        val trimmed = ledgerName.trim().ifBlank { "装修账本" }
        return trimmed
            .replace(Regex("""[\\/:*?"<>|\s]+"""), "_")
            .trim('_')
            .take(40)
            .ifBlank { "装修账本" }
    }

    /** 例：我家装修_20260714_172530.csv */
    fun fileName(ledgerName: String, atMillis: Long = System.currentTimeMillis()): String {
        val stamp = stampFormat.format(Date(atMillis))
        return "${sanitize(ledgerName)}_$stamp.csv"
    }

    fun isSameLedgerExport(fileName: String, ledgerName: String): Boolean {
        val safe = Regex.escape(sanitize(ledgerName))
        return Regex("""^${safe}_\d{8}_\d{6}\.csv$""").matches(fileName)
    }
}
