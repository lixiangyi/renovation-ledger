package com.renovation.ledger.domain.ledger

data class DeleteLedgerDialogCopy(
    val title: String,
    val body: String,
    val confirm: String,
)

object DeleteLedgerCopy {
    fun forRole(role: String?, ledgerName: String, hasCloudId: Boolean): DeleteLedgerDialogCopy {
        val name = ledgerName.ifBlank { "账本" }
        val action = CloudUnbindDecision.actionForRole(if (hasCloudId) role else null)
        return when {
            hasCloudId && action == CloudUnbindAction.LEAVE -> DeleteLedgerDialogCopy(
                title = "解绑账本",
                body = "将「$name」移入垃圾箱，并退出该账本协作（云端账本仍保留，拥有者不受影响）。\n" +
                    "会先导出备份，之后可从垃圾箱恢复；永久删除前仍可找回。",
                confirm = "解绑",
            )
            hasCloudId -> DeleteLedgerDialogCopy(
                title = "删除账本",
                body = "将「$name」移入垃圾箱，并删除云端账本（协作成员将无法再访问）。\n" +
                    "会先导出备份，之后可从垃圾箱恢复；永久删除前仍可找回。",
                confirm = "删除",
            )
            else -> DeleteLedgerDialogCopy(
                title = "删除账本",
                body = "将「$name」移入垃圾箱。会先导出备份，之后可从垃圾箱恢复；永久删除前仍可找回。",
                confirm = "删除",
            )
        }
    }
}
