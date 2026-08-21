# 语音意图操控助手设计

> 日期：2026-08-18
> 范围：Android 第一期；小程序暂不涉及

## 1. 目标

用户语音 → ASR 转文字 → 大模型解析意图 → 执行 App 内操作。架构按"可扩展工具箱"设计：第一期落地记账 + Debug 操控两类，后续加 Tool 不动核心链路。

## 2. 整体架构

```
语音 → [ASR Middleware] → finalText + confidence + segments
                              │
                    confidence < 0.4 → 提示重说
                    confidence 0.4~0.7 → 用户编辑确认
                    confidence ≥ 0.7 → 直接送 LLM
                              │
                              ▼
                     [LLM Intent Parser]
                     (Function Calling)
                              │
                              ▼
                     tool_calls[] (结构化 JSON)
                              │
                              ▼
                     [Tool Orchestrator]
                     按顺序遍历 tool_calls
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
               risk=LOW            risk=HIGH
            直接执行+Toast      弹窗展示预览→用户确认→执行
```

## 3. ASR 中间件

### 3.1 接口

```kotlin
interface AsrEngine {
    suspend fun recognize(): AsrResult
    fun cancel()
    val engineName: String
}

data class AsrResult(
    val finalText: String,
    val confidence: Float,          // 0.0 ~ 1.0
    val segments: List<AsrSegment>,
    val error: AsrError? = null,
)

data class AsrSegment(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float,
)

enum class AsrError {
    NO_PERMISSION, NO_SPEECH, NETWORK_ERROR, ENGINE_UNAVAILABLE, UNKNOWN,
}
```

### 3.2 第一期实现：SystemAsrEngine

- 封装 Android `SpeechRecognizer`，用 `suspendCancellableCoroutine` 包装回调
- 分句时间戳：系统支持有限，v1 按整句返回单个 segment（startMs=0, endMs=录音时长）
- 后续换讯飞/Whisper 时实现同一接口，DI 模块切 provide 即可

### 3.3 Confidence 阈值

| confidence | 行为 |
|---|---|
| ≥ 0.7 | 直接送 LLM |
| 0.4 ~ 0.7 | 展示文本让用户编辑确认后再送 LLM |
| < 0.4 | 提示"没听清，请重说" |

阈值写在 `AsrConfig` 中可调。

## 4. LLM Intent Parser

### 4.1 接口

```kotlin
interface LlmIntentParser {
    suspend fun parse(request: IntentRequest): IntentResult
    val providerName: String
}

data class IntentRequest(
    val text: String,
    val tools: List<ToolSchema>,
    val context: AppContext,
)

data class AppContext(
    val currentEnv: String,
    val isLoggedIn: Boolean,
    val isDebugBuild: Boolean,
    val availableCategories: List<String>,
    val availableStages: List<String>,
)

data class IntentResult(
    val toolCalls: List<ToolCall>,
    val rawResponse: String,
    val error: IntentError? = null,
)

enum class IntentError {
    NETWORK_ERROR, RATE_LIMITED, PARSE_FAILED, NO_MATCH,
}
```

### 4.2 LLM Provider 抽象

```kotlin
interface LlmProvider {
    suspend fun chat(messages: List<ChatMessage>, tools: List<ToolSchema>): LlmResponse
    val name: String
}
```

第一期实现：

| Provider | 模型 | 备注 |
|---|---|---|
| `DeepSeekProvider`（默认） | deepseek-chat | 中文好、便宜、原生 function calling |
| `OpenAiProvider` | gpt-4o-mini | 备选 |

### 4.3 配置

- Debug 包：开发面板新增"AI 模型"卡片，可选 Provider + 填 API Key（存 EncryptedSharedPreferences）+ 测试连通
- 正式包：从 `local.properties` 读默认 key，不暴露切换 UI

### 4.4 System Prompt

```
你是一个装修记账 App 的语音助手。用户会用自然语言描述操作。
你需要将用户意图解析为一个或多个工具调用。

规则：
1. 严格按 tools 定义返回结构化 JSON
2. 一句话包含多个操作时，按逻辑顺序拆成多个 tool_calls
3. 无法匹配任何工具时，返回空 tool_calls 并说明原因
4. 金额统一用数字（元），不要带单位
5. 用户说"定金"→ deposit，"尾款"→ finalPayment
6. "没付/还没付/未付" → paid=false，"已付/付了/付过了" → paid=true
```

### 4.5 网络层

LLM 请求走独立 OkHttpClient，超时 30s，不复用 LedgerApi / ServerEndpoint。

## 5. Tool 体系

### 5.1 ToolSchema 与注册

```kotlin
data class ToolSchema(
    val name: String,
    val description: String,
    val parameters: JsonObject,   // JSON Schema
    val risk: RiskLevel,
)

enum class RiskLevel { HIGH, LOW }
```

每个 Tool 在 `ToolRegistry` 注册 schema。新增 Tool 只需：写 Executor + 注册 schema，不动 parser。

### 5.2 ToolExecutor 接口

```kotlin
interface ToolExecutor {
    val toolName: String
    val risk: RiskLevel
    suspend fun execute(params: Map<String, Any?>): ToolResult
    fun preview(params: Map<String, Any?>): ToolPreview
}

data class ToolResult(val success: Boolean, val message: String, val error: String? = null)
data class ToolPreview(val title: String, val fields: List<PreviewField>)
data class PreviewField(val label: String, val value: String, val editable: Boolean)
```

### 5.3 第一期 Tool 清单

| Tool | 参数 | 风险 | 行为 |
|---|---|---|---|
| `add_ledger_entry` | name, category, amount, deposit?, finalPayment?, depositPaid?, finalPaid?, stage?, space? | HIGH | 弹窗确认 → 写入 DB |
| `switch_env` | env: "dev"\|"prod" | LOW | 切 baseUrl + Toast |
| `wechat_login` | 无 | LOW | 触发微信登录 |
| `dev_login` | label?: String | LOW | 调用 devLogin() |

### 5.4 风险分级规则

- **HIGH**：创建/修改/删除数据 → 弹窗确认
- **LOW**：本地配置/触发登录 → 直接执行 + Toast

## 6. 多步编排（Orchestrator）

```kotlin
class ToolOrchestrator(private val registry: ToolRegistry) {
    fun execute(toolCalls: List<ToolCall>): Flow<OrchestratorEvent>
}

sealed class OrchestratorEvent {
    data class Executed(val tool: String, val result: ToolResult) : OrchestratorEvent()
    data class NeedConfirm(val tool: String, val preview: ToolPreview,
                           val onConfirm: () -> Unit, val onCancel: () -> Unit) : OrchestratorEvent()
    data class AllDone(val summary: String) : OrchestratorEvent()
    data class Failed(val tool: String, val error: String) : OrchestratorEvent()
}
```

执行逻辑：
1. 遍历 tool_calls，查 registry 找 Executor
2. LOW → execute() → 发射 Executed → 继续
3. HIGH → preview() → 发射 NeedConfirm → 挂起等确认
4. 确认 → execute() → 继续；取消 → 跳过
5. 失败 → 发射 Failed → 中止后续

## 7. UI

### 7.1 语音入口

首页 OverviewScreen：主 FAB 左侧新增麦克风小 FAB。点击弹出录音底部面板（BottomSheet）：
- 中间脉动圆圈表示录音中
- 实时显示 partial results
- 停止说话自动结束 / 点击"完成"
- confidence 低 → 面板内编辑文本
- 送 LLM → "正在分析…"
- LOW 直接执行收起面板；HIGH 弹确认弹窗

### 7.2 确认弹窗

```
┌─────────────────────────┐
│  🤖 语音助手 · 新增记账    │
├─────────────────────────┤
│  名称      扫地机器人      │
│  大类      家电            │
│  总价      ¥2,950         │
│  定金      ¥1,000（已付）   │
│  尾款      ¥1,950（未付）   │
├─────────────────────────┤
│      [ 取消 ]  [ 确认 ]    │
└─────────────────────────┘
```

editable=true 的字段可点击修改。

### 7.3 LOW risk 反馈

Snackbar 展示结果。多步时合并：「已切换到正式环境，正在拉起微信登录…」

### 7.4 调试

DebugCloudScreen 底部新增"语音调试"卡片：最近一次 ASR 原文、confidence、LLM 原始返回、tool_calls、执行日志。

## 8. 文件结构

```
com.renovation.ledger
├── voice/
│   ├── asr/
│   │   ├── AsrEngine.kt
│   │   ├── SystemAsrEngine.kt
│   │   └── AsrConfig.kt
│   ├── llm/
│   │   ├── LlmProvider.kt
│   │   ├── LlmIntentParser.kt
│   │   ├── DefaultIntentParser.kt
│   │   ├── DeepSeekProvider.kt
│   │   ├── OpenAiProvider.kt
│   │   └── LlmConfig.kt
│   ├── tool/
│   │   ├── ToolSchema.kt
│   │   ├── ToolExecutor.kt
│   │   ├── ToolRegistry.kt
│   │   ├── ToolOrchestrator.kt
│   │   └── executors/
│   │       ├── AddLedgerEntryExecutor.kt
│   │       ├── SwitchEnvExecutor.kt
│   │       ├── WechatLoginExecutor.kt
│   │       └── DevLoginExecutor.kt
│   ├── ui/
│   │   ├── VoiceAssistantSheet.kt
│   │   ├── VoiceConfirmDialog.kt
│   │   └── VoiceAssistantViewModel.kt
│   └── di/
│       └── VoiceModule.kt
├── ui/
│   ├── overview/OverviewScreen.kt    # 改：加麦克风 FAB
│   └── debug/DebugCloudScreen.kt     # 改：加语音调试卡片
```

## 9. 依赖

无新增三方库，复用现有 OkHttp + Gson。

## 10. 不在第一期

| 功能 | 原因 |
|---|---|
| 小程序同步 | 暂时只做 Android |
| 流式 LLM 返回 | v1 等完整返回 |
| 语音唤醒词 | 需常驻服务，v2 |
| 历史语音记录列表 | v2 |
| ASR 分句时间戳 UI 高亮 | 数据 v1 采集，UI v2 |
