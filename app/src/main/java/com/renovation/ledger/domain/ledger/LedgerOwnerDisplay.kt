package com.renovation.ledger.domain.ledger

import com.renovation.ledger.data.remote.MemberDto

object LedgerOwnerDisplay {
    fun nickname(
        memberNames: List<String>,
        cloudMembers: List<MemberDto> = emptyList(),
    ): String {
        val owner = cloudMembers
            .firstOrNull { it.role.equals("OWNER", ignoreCase = true) }
            ?.nickname
            ?.trim()
        if (!owner.isNullOrBlank()) return owner
        return memberNames.firstOrNull { it.isNotBlank() }.orEmpty()
    }

    fun namesOwnerFirst(members: List<MemberDto>): List<String> {
        return members
            .sortedBy { if (it.role.equals("OWNER", ignoreCase = true)) 0 else 1 }
            .map { it.nickname.trim().ifBlank { "我" } }
            .filter { it.isNotBlank() }
            .distinct()
    }
}
