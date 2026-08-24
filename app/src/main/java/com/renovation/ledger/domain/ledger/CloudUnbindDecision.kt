package com.renovation.ledger.domain.ledger

enum class CloudUnbindAction {
    /** 所有者：服务端软删账本。 */
    SOFT_DELETE,
    /** 协作者：退出成员。 */
    LEAVE,
}

object CloudUnbindDecision {
    fun actionForRole(role: String?): CloudUnbindAction =
        when (role?.trim()?.uppercase()) {
            "EDITOR" -> CloudUnbindAction.LEAVE
            else -> CloudUnbindAction.SOFT_DELETE
        }
}
