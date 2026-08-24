package com.renovation.ledger

import com.renovation.ledger.domain.ledger.LedgerRoleGates
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerRoleGatesTest {
    @Test
    fun ownerCanManage() {
        assertTrue(LedgerRoleGates.canManageInviteAndHealth("OWNER", loggedIn = true, hasCloudId = true))
    }

    @Test
    fun editorCannotManage() {
        assertFalse(LedgerRoleGates.canManageInviteAndHealth("EDITOR", loggedIn = true, hasCloudId = true))
    }

    @Test
    fun localUnboundCanManageHealth() {
        assertTrue(LedgerRoleGates.canManageInviteAndHealth(null, loggedIn = true, hasCloudId = false))
        assertTrue(LedgerRoleGates.canManageInviteAndHealth(null, loggedIn = false, hasCloudId = false))
    }

    @Test
    fun showCreateInviteOnlyWhenOwnerBound() {
        assertTrue(LedgerRoleGates.showCreateInvite("OWNER", loggedIn = true, hasCloudId = true))
        assertFalse(LedgerRoleGates.showCreateInvite("EDITOR", loggedIn = true, hasCloudId = true))
        assertFalse(LedgerRoleGates.showCreateInvite("OWNER", loggedIn = true, hasCloudId = false))
        assertFalse(LedgerRoleGates.showCreateInvite(null, loggedIn = false, hasCloudId = false))
    }
}
