package com.renovation.ledger

import com.renovation.ledger.domain.ledger.LedgerContentGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerContentGateTest {
    @Test
    fun hidesEmptyCopyUntilLocalDataReady() {
        assertFalse(LedgerContentGate.showEmptyCopy(contentReady = false, isEmpty = true))
        assertFalse(LedgerContentGate.showEmptyCopy(contentReady = false, isEmpty = false))
    }

    @Test
    fun showsEmptyCopyOnlyAfterReadyAndReallyEmpty() {
        assertTrue(LedgerContentGate.showEmptyCopy(contentReady = true, isEmpty = true))
        assertFalse(LedgerContentGate.showEmptyCopy(contentReady = true, isEmpty = false))
    }
}
