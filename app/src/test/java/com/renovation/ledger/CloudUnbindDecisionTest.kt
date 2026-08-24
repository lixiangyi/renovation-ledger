package com.renovation.ledger

import com.renovation.ledger.domain.ledger.CloudUnbindAction
import com.renovation.ledger.domain.ledger.CloudUnbindDecision
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudUnbindDecisionTest {
    @Test
    fun editor_leaves() {
        assertEquals(CloudUnbindAction.LEAVE, CloudUnbindDecision.actionForRole("EDITOR"))
        assertEquals(CloudUnbindAction.LEAVE, CloudUnbindDecision.actionForRole("editor"))
    }

    @Test
    fun owner_or_blank_softDeletes() {
        assertEquals(CloudUnbindAction.SOFT_DELETE, CloudUnbindDecision.actionForRole("OWNER"))
        assertEquals(CloudUnbindAction.SOFT_DELETE, CloudUnbindDecision.actionForRole(null))
        assertEquals(CloudUnbindAction.SOFT_DELETE, CloudUnbindDecision.actionForRole(""))
    }
}
