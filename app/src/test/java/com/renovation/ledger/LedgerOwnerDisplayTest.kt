package com.renovation.ledger

import com.renovation.ledger.data.remote.MemberDto
import com.renovation.ledger.domain.ledger.LedgerOwnerDisplay
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerOwnerDisplayTest {
    @Test
    fun localUsesFirstMemberAsOwner() {
        assertEquals(
            "汤圆",
            LedgerOwnerDisplay.nickname(listOf("汤圆", "0398")),
        )
    }

    @Test
    fun cloudPrefersOwnerRoleEvenIfNotFirst() {
        val members = listOf(
            MemberDto("e1", "0398", "EDITOR"),
            MemberDto("o1", "汤圆", "OWNER"),
        )
        assertEquals("汤圆", LedgerOwnerDisplay.nickname(listOf("0398", "汤圆"), members))
    }

    @Test
    fun namesOwnerFirstPutsOwnerAhead() {
        val members = listOf(
            MemberDto("e1", "0398", "EDITOR"),
            MemberDto("o1", "汤圆", "OWNER"),
        )
        assertEquals(listOf("汤圆", "0398"), LedgerOwnerDisplay.namesOwnerFirst(members))
    }
}
