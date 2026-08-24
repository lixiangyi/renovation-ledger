package com.renovation.ledger.data.sync

/** 邀请码分享文案（Android / 小程序保持一致）。 */
object InviteShareText {
    fun message(code: String): String {
        val trimmed = code.trim()
        return """
【装修记账】邀请你一起记账

我在用「装修记账」管理装修预算与付款，邀请你加入同一个账本一起编辑。

邀请码：$trimmed

打开 App 或微信小程序「装修记账」→ 个人中心 → 输入邀请码加入。
        """.trimIndent()
    }

    /** 支持粘贴纯码，或粘贴整段分享文案后提取邀请码。 */
    fun extractCode(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return text
        Regex("""邀请码[：:]\s*([A-Za-z0-9]{6})""").find(text)?.groupValues?.getOrNull(1)?.let {
            return it
        }
        if (text.matches(Regex("""[A-Za-z0-9]{6}"""))) return text
        return text
    }
}
