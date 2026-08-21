package com.renovation.ledger.voice.tool.executors

import android.app.Activity
import com.renovation.ledger.data.auth.WeChatAppAuth
import com.renovation.ledger.voice.tool.RiskLevel
import com.renovation.ledger.voice.tool.ToolExecutor
import com.renovation.ledger.voice.tool.ToolPreview
import com.renovation.ledger.voice.tool.ToolResult
import com.renovation.ledger.voice.tool.ToolSchema
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceHostHolder @Inject constructor() {
    @Volatile
    var activity: Activity? = null
}

class WechatLoginExecutor @Inject constructor(
    private val hostHolder: VoiceHostHolder,
) : ToolExecutor {
    override val toolName: String = "wechat_login"
    override val risk: RiskLevel = RiskLevel.LOW
    override val schema: ToolSchema = ToolSchema(
        name = toolName,
        description = "拉起微信登录",
        parametersJson = """{"type":"object","properties":{}}""",
        risk = risk,
    )

    override fun preview(params: Map<String, Any?>): ToolPreview = ToolPreview("微信登录", emptyList())

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val activity = hostHolder.activity
            ?: return ToolResult(false, "", "当前页面无法拉起微信登录，请到「我的」手动登录")
        val err = WeChatAppAuth.sendAuth(activity)
        return if (err == null) {
            ToolResult(true, "正在拉起微信登录")
        } else {
            ToolResult(false, "", err)
        }
    }
}
