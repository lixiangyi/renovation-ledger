package com.renovation.ledger.domain.ledger

object LedgerRoleGates {
    /** EDITOR 隐藏健康色编辑；OWNER / 未绑云本地可编辑。 */
    fun canManageInviteAndHealth(role: String?, loggedIn: Boolean, hasCloudId: Boolean): Boolean {
        if (!hasCloudId) return true
        return !role.equals("EDITOR", ignoreCase = true)
    }

    /** 仅已登录、已绑云、且为 OWNER 时显示生成邀请码。 */
    fun showCreateInvite(role: String?, loggedIn: Boolean, hasCloudId: Boolean): Boolean {
        if (!loggedIn || !hasCloudId) return false
        return role.equals("OWNER", ignoreCase = true)
    }

    fun roleOf(
        cloudLedgerId: String?,
        summaries: List<com.renovation.ledger.data.remote.LedgerSummaryDto>,
    ): String? {
        val id = cloudLedgerId?.trim().orEmpty()
        if (id.isEmpty()) return null
        return summaries.firstOrNull { it.id == id }?.role
    }
}
