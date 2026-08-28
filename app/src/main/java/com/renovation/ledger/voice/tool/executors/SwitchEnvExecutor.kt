package com.renovation.ledger.voice.tool.executors

import com.renovation.ledger.BuildConfig
import com.renovation.ledger.data.remote.CloudEnv
import com.renovation.ledger.di.ServerEndpoint
import com.renovation.ledger.voice.tool.PreviewField
import com.renovation.ledger.voice.tool.RiskLevel
import com.renovation.ledger.voice.tool.ToolExecutor
import com.renovation.ledger.voice.tool.ToolPreview
import com.renovation.ledger.voice.tool.ToolResult
import com.renovation.ledger.voice.tool.ToolSchema
import com.renovation.ledger.voice.tool.asString
import javax.inject.Inject

interface CloudEnvStore {
    suspend fun apply(kind: CloudEnv.Kind, url: String)
}

class CloudEnvStoreImpl @Inject constructor(
    private val userPrefs: com.renovation.ledger.data.prefs.UserPrefs,
    private val serverEndpoint: ServerEndpoint,
) : CloudEnvStore {
    override suspend fun apply(kind: CloudEnv.Kind, url: String) {
        userPrefs.setCloudEnv(kind, url)
        userPrefs.setJwt(null, null)
        serverEndpoint.baseUrl = url
    }
}

class SwitchEnvExecutor(
    private val cloudEnvStore: CloudEnvStore,
    private val serverEndpoint: ServerEndpoint,
    private val allowSwitch: Boolean = BuildConfig.ENABLE_DEBUG_PANEL,
) : ToolExecutor {
    @Inject
    constructor(
        cloudEnvStore: CloudEnvStore,
        serverEndpoint: ServerEndpoint,
    ) : this(cloudEnvStore, serverEndpoint, BuildConfig.ENABLE_DEBUG_PANEL)

    override val toolName: String = "switch_env"
    override val risk: RiskLevel = RiskLevel.LOW
    override val schema: ToolSchema = ToolSchema(
        name = toolName,
        description = "切换云环境到开发或正式",
        parametersJson = """{"type":"object","required":["env"],"properties":{"env":{"type":"string","enum":["dev","prod"]}}}""",
        risk = risk,
    )

    override fun preview(params: Map<String, Any?>): ToolPreview = ToolPreview(
        title = "切换环境",
        fields = listOf(PreviewField("环境", params["env"].asString(), editable = false, key = "env")),
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        if (!allowSwitch) {
            return ToolResult(false, "", "当前安装包不能切换环境")
        }
        val env = params["env"].asString().lowercase()
        val kind = when (env) {
            "prod", "正式", "生产" -> CloudEnv.Kind.PROD
            "dev", "开发" -> CloudEnv.Kind.DEV
            else -> return ToolResult(false, "", "不支持的环境 $env")
        }
        val url = CloudEnv.urlOf(kind)
        cloudEnvStore.apply(kind, url)
        serverEndpoint.baseUrl = url
        return ToolResult(
            success = true,
            message = if (kind == CloudEnv.Kind.PROD) "已切换到正式环境" else "已切换到开发环境",
        )
    }
}
