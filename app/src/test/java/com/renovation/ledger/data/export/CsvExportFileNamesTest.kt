package com.renovation.ledger.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportFileNamesTest {

    @Test
    fun fileName_usesLedgerAndStamp() {
        val name = CsvExportFileNames.fileName("我家装修", atMillis = 1_720_944_000_000L)
        assertTrue(name.startsWith("我家装修_"))
        assertTrue(name.endsWith(".csv"))
        assertTrue(Regex("""^我家装修_\d{8}_\d{6}\.csv$""").matches(name))
    }

    @Test
    fun sanitize_replacesIllegalChars() {
        assertEquals("客厅_厨房", CsvExportFileNames.sanitize("客厅/厨房"))
        assertEquals("装修账本", CsvExportFileNames.sanitize("   "))
    }

    @Test
    fun isSameLedgerExport_matchesOnlySameLedger() {
        assertTrue(CsvExportFileNames.isSameLedgerExport("我家装修_20260714_172530.csv", "我家装修"))
        assertFalse(CsvExportFileNames.isSameLedgerExport("我家装修_20260714_172530.csv", "别的账本"))
        assertFalse(CsvExportFileNames.isSameLedgerExport("我家装修备份.csv", "我家装修"))
    }
}
