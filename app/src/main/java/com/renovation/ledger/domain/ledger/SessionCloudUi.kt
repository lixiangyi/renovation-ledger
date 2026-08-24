package com.renovation.ledger.domain.ledger

object SessionCloudUi {
    /** 账号身份变化（含登出/登入）时清邀请码等会话 UI。 */
    fun shouldClearSessionUi(previousUserId: String?, nextUserId: String?): Boolean {
        val prev = previousUserId?.trim()?.takeIf { it.isNotEmpty() }
        val next = nextUserId?.trim()?.takeIf { it.isNotEmpty() }
        return prev != next
    }
}
