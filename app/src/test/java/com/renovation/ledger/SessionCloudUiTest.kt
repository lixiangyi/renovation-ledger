package com.renovation.ledger

import com.renovation.ledger.domain.ledger.SessionCloudUi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCloudUiTest {
    @Test
    fun clear_when_user_changes() {
        assertTrue(SessionCloudUi.shouldClearSessionUi("u1", "u2"))
        assertTrue(SessionCloudUi.shouldClearSessionUi("u1", null))
        assertTrue(SessionCloudUi.shouldClearSessionUi(null, "u2"))
    }

    @Test
    fun keep_when_same_or_both_null() {
        assertFalse(SessionCloudUi.shouldClearSessionUi("u1", "u1"))
        assertFalse(SessionCloudUi.shouldClearSessionUi(null, null))
    }
}
