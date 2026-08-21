# Voice Intent Assistant Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Android App 中实现第一期语音助手链路：系统 ASR 转文字，大模型解析为结构化工具调用，按风险直接执行或弹窗确认，支持记账与 Debug 操控两类动作。

**Architecture:** 新增 `voice/` 模块，分成 `asr`、`llm`、`tool`、`ui`、`di` 五层。ASR 与 LLM 都做成可替换接口；ToolRegistry 负责暴露 schema 与 executor；ToolOrchestrator 负责串行执行、多步暂停确认与统一日志；Overview 入口与 Debug 面板只消费 ViewModel 状态，不直接耦合具体 provider。

**Tech Stack:** Android Compose、Hilt、Coroutines/Flow、OkHttp、Gson、Android `SpeechRecognizer`、现有 `ProjectRepository` / `LedgerSyncRepository` / `UserPrefs` / `ServerEndpoint`。完成实现后按仓库规则执行 `sh oneClickSetup`。

**Git：** 工作区禁止自动 git。本计划 **不** 执行 commit。

**Spec：** `/Users/beike/Projects/renovation-ledger/docs/superpowers/specs/2026-08-18-voice-intent-assistant-design.md`

---

## File map

| 路径 | 职责 |
|---|---|
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/asr/AsrEngine.kt` | 定义 ASR 接口、结果模型、错误枚举 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/asr/AsrConfig.kt` | confidence 阈值与识别配置 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/asr/SystemAsrEngine.kt` | `SpeechRecognizer` 适配实现 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/llm/LlmModels.kt` | `IntentRequest`、`IntentResult`、`ToolCall`、`AppContext`、provider 公共模型 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/llm/LlmProvider.kt` | provider 接口 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/llm/LlmIntentParser.kt` | parser 接口 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/llm/LlmConfig.kt` | provider 选择、system prompt、超时配置 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/llm/DeepSeekProvider.kt` | 默认 provider 的 HTTP 调用与 function calling 解析 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/llm/OpenAiProvider.kt` | 备选 provider 的 HTTP 调用与 function calling 解析 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/llm/DefaultIntentParser.kt` | 组装 prompt/context/tools 并产出 `IntentResult` |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/tool/ToolContracts.kt` | `RiskLevel`、`ToolSchema`、`ToolResult`、`ToolPreview`、`PreviewField` |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/tool/ToolExecutor.kt` | executor 接口 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/tool/ToolRegistry.kt` | schema/executor 注册、按 toolName 查找 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/tool/ToolOrchestrator.kt` | 多步执行、确认暂停、汇总结果 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/tool/executors/AddLedgerEntryExecutor.kt` | 记账预览与真正写入 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/tool/executors/SwitchEnvExecutor.kt` | 切 `CloudEnv` / `ServerEndpoint` |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/tool/executors/WechatLoginExecutor.kt` | 触发微信登录 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/tool/executors/DevLoginExecutor.kt` | 触发开发登录 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/ui/VoiceAssistantViewModel.kt` | 串起 ASR → 解析 → 编排 → UI 状态 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/ui/VoiceAssistantSheet.kt` | 录音与解析 bottom sheet |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/ui/VoiceConfirmDialog.kt` | HIGH risk 预览确认弹窗 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/ui/VoiceDebugModels.kt` | 调试面板展示模型 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/di/VoiceModule.kt` | Hilt provider 绑定 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/data/prefs/UserPrefs.kt` | 保存 AI provider、API key、语音调试快照 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/ui/debug/DebugCloudViewModel.kt` | 暴露 AI 配置与最近一次调试数据 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/ui/debug/DebugCloudScreen.kt` | 新增 AI 模型配置与语音调试卡片 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/ui/overview/OverviewScreen.kt` | 新增麦克风 FAB、sheet/dialog 容器 |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/ui/entry/ConfirmEntryViewModel.kt` | 提取语音记账保存复用逻辑，去掉写死 voice draft |
| `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/ui/mine/MineViewModel.kt` | 提供微信登录入口供 executor 复用 |
| `/Users/beike/Projects/renovation-ledger/app/src/test/java/com/renovation/ledger/voice/DefaultIntentParserTest.kt` | parser、JSON/tool_call 解析测试 |
| `/Users/beike/Projects/renovation-ledger/app/src/test/java/com/renovation/ledger/voice/ToolOrchestratorTest.kt` | 风险分级、暂停确认、多步执行测试 |
| `/Users/beike/Projects/renovation-ledger/app/src/test/java/com/renovation/ledger/voice/AddLedgerEntryExecutorTest.kt` | 记账参数到实体映射测试 |
| `/Users/beike/Projects/renovation-ledger/app/src/test/java/com/renovation/ledger/voice/VoiceAssistantViewModelTest.kt` | 低置信度、失败、直接执行、需确认状态机测试 |

---

### Task 1: 定义 voice 基础契约

**Files:**
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/asr/AsrEngine.kt`
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/asr/AsrConfig.kt`
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/llm/LlmModels.kt`
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/llm/LlmProvider.kt`
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/llm/LlmIntentParser.kt`
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/llm/LlmConfig.kt`
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/tool/ToolContracts.kt`
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/tool/ToolExecutor.kt`
- Test: `/Users/beike/Projects/renovation-ledger/app/src/test/java/com/renovation/ledger/voice/DefaultIntentParserTest.kt`

- [ ] **Step 1: 写 parser 基础失败测试，先锁定 tool JSON 格式**

```kotlin
package com.renovation.ledger.voice

import com.google.common.truth.Truth.assertThat
import com.renovation.ledger.voice.llm.AppContext
import com.renovation.ledger.voice.llm.DefaultIntentParser
import com.renovation.ledger.voice.llm.FakeLlmProvider
import com.renovation.ledger.voice.tool.RiskLevel
import com.renovation.ledger.voice.tool.ToolSchema
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultIntentParserTest {

    @Test
    fun parseReturnsToolCallsFromProviderJson() = runTest {
        val provider = FakeLlmProvider(
            raw = """
                {
                  "tool_calls": [
                    {"tool": "switch_env", "params": {"env": "prod"}},
                    {"tool": "wechat_login", "params": {}}
                  ]
                }
            """.trimIndent(),
        )
        val parser = DefaultIntentParser(provider)
        val result = parser.parse(
            text = "切换到正式环境并启用微信登录",
            tools = listOf(
                ToolSchema("switch_env", "切环境", """{"type":"object"}""", RiskLevel.LOW),
                ToolSchema("wechat_login", "微信登录", """{"type":"object"}""", RiskLevel.LOW),
            ),
            context = AppContext(currentEnv = "dev", isLoggedIn = false, isDebugBuild = true, availableCategories = listOf("家电"), availableStages = listOf("主材")),
        )

        assertThat(result.error).isNull()
        assertThat(result.toolCalls).hasSize(2)
        assertThat(result.toolCalls[0].tool).isEqualTo("switch_env")
        assertThat(result.toolCalls[1].tool).isEqualTo("wechat_login")
    }
}
```

- [ ] **Step 2: 跑测试，确认缺少类型/实现而失败**

Run:

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew testDebugUnitTest --tests com.renovation.ledger.voice.DefaultIntentParserTest
```

Expected: FAIL，提示 `DefaultIntentParser`、`ToolSchema`、`AppContext` 等类型不存在。

- [ ] **Step 3: 写最小契约实现**

```kotlin
package com.renovation.ledger.voice.tool

enum class RiskLevel { HIGH, LOW }

data class ToolSchema(
    val name: String,
    val description: String,
    val parametersJson: String,
    val risk: RiskLevel,
)
```

```kotlin
package com.renovation.ledger.voice.llm

data class AppContext(
    val currentEnv: String,
    val isLoggedIn: Boolean,
    val isDebugBuild: Boolean,
    val availableCategories: List<String>,
    val availableStages: List<String>,
)

data class ToolCall(val tool: String, val params: Map<String, Any?>)

data class IntentResult(
    val toolCalls: List<ToolCall>,
    val rawResponse: String,
    val error: IntentError? = null,
)

enum class IntentError { NETWORK_ERROR, RATE_LIMITED, PARSE_FAILED, NO_MATCH }

interface LlmProvider {
    val name: String
    suspend fun chat(prompt: String, tools: List<com.renovation.ledger.voice.tool.ToolSchema>): String
}

class FakeLlmProvider(private val raw: String) : LlmProvider {
    override val name: String = "fake"
    override suspend fun chat(prompt: String, tools: List<com.renovation.ledger.voice.tool.ToolSchema>): String = raw
}

class DefaultIntentParser(private val provider: LlmProvider) {
    suspend fun parse(text: String, tools: List<com.renovation.ledger.voice.tool.ToolSchema>, context: AppContext): IntentResult {
        return IntentResult(toolCalls = emptyList(), rawResponse = provider.chat(text + context.currentEnv, tools), error = IntentError.PARSE_FAILED)
    }
}
```

```kotlin
package com.renovation.ledger.voice.asr

interface AsrEngine {
    suspend fun recognize(): AsrResult
    fun cancel()
    val engineName: String
}

data class AsrResult(
    val finalText: String,
    val confidence: Float,
    val segments: List<AsrSegment>,
    val error: AsrError? = null,
)

data class AsrSegment(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float,
)

enum class AsrError { NO_PERMISSION, NO_SPEECH, NETWORK_ERROR, ENGINE_UNAVAILABLE, UNKNOWN }
```

- [ ] **Step 4: 跑测试，确认断言失败而不是编译失败**

Run same command as Step 2.  
Expected: FAIL，`result.error` 为 `PARSE_FAILED`，说明基础类型已连通。

---

### Task 2: 实现 parser JSON 解析与错误映射

**Files:**
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/llm/LlmModels.kt`
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/llm/LlmIntentParser.kt`
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/llm/DefaultIntentParser.kt`
- Test: `/Users/beike/Projects/renovation-ledger/app/src/test/java/com/renovation/ledger/voice/DefaultIntentParserTest.kt`

- [ ] **Step 1: 补三类失败测试：无匹配、脏 JSON、provider 异常**

```kotlin
@Test
fun parseReturnsNoMatchWhenToolCallsEmpty() = runTest {
    val parser = DefaultIntentParser(FakeLlmProvider("""{"tool_calls": []}"""))
    val result = parser.parse("今天天气怎么样", emptyList(), demoContext())
    assertThat(result.error).isEqualTo(IntentError.NO_MATCH)
}

@Test
fun parseReturnsParseFailedWhenJsonBroken() = runTest {
    val parser = DefaultIntentParser(FakeLlmProvider("""{"tool_calls":["""))
    val result = parser.parse("切环境", emptyList(), demoContext())
    assertThat(result.error).isEqualTo(IntentError.PARSE_FAILED)
}

@Test
fun parseReturnsNetworkErrorWhenProviderThrows() = runTest {
    val provider = object : LlmProvider {
        override val name = "fake"
        override suspend fun chat(prompt: String, tools: List<ToolSchema>): String = error("timeout")
    }
    val result = DefaultIntentParser(provider).parse("切环境", emptyList(), demoContext())
    assertThat(result.error).isEqualTo(IntentError.NETWORK_ERROR)
}
```

- [ ] **Step 2: 跑测试，确认新增断言失败**

Run:

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew testDebugUnitTest --tests com.renovation.ledger.voice.DefaultIntentParserTest
```

Expected: FAIL，`NO_MATCH` / `NETWORK_ERROR` 用例不通过。

- [ ] **Step 3: 实现 parser**

```kotlin
class DefaultIntentParser(
    private val provider: LlmProvider,
    private val gson: Gson = Gson(),
    private val config: LlmConfig = LlmConfig(),
) : LlmIntentParser {

    override val providerName: String get() = provider.name

    override suspend fun parse(request: IntentRequest): IntentResult {
        val prompt = buildPrompt(request)
        val raw = runCatching { provider.chat(prompt, request.tools) }
            .getOrElse {
                return IntentResult(emptyList(), rawResponse = "", error = IntentError.NETWORK_ERROR)
            }
        val response = runCatching { gson.fromJson(raw, ProviderToolCallEnvelope::class.java) }
            .getOrElse { return IntentResult(emptyList(), raw, IntentError.PARSE_FAILED) }
        val calls = response.toolCalls.orEmpty().map { ToolCall(it.tool.orEmpty(), it.params ?: emptyMap()) }
            .filter { it.tool.isNotBlank() }
        return when {
            calls.isEmpty() -> IntentResult(emptyList(), raw, IntentError.NO_MATCH)
            else -> IntentResult(calls, rawResponse = raw)
        }
    }
}
```

- [ ] **Step 4: 跑测试，期望 PASS**

Run same command as Step 2.  
Expected: PASS。

---

### Task 3: 实现 ToolRegistry 与 Orchestrator

**Files:**
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/tool/ToolRegistry.kt`
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/tool/ToolOrchestrator.kt`
- Test: `/Users/beike/Projects/renovation-ledger/app/src/test/java/com/renovation/ledger/voice/ToolOrchestratorTest.kt`

- [ ] **Step 1: 写执行顺序与 HIGH risk 暂停测试**

```kotlin
@Test
fun lowRiskRunsSequentiallyThenHighRiskWaitsConfirm() = runTest {
    val env = fakeExecutor("switch_env", RiskLevel.LOW, "已切到正式环境")
    val add = fakeExecutor("add_ledger_entry", RiskLevel.HIGH, "已保存")
    val registry = ToolRegistry(listOf(env, add))
    val orchestrator = ToolOrchestrator(registry)

    val events = orchestrator.execute(
        listOf(
            ToolCall("switch_env", mapOf("env" to "prod")),
            ToolCall("add_ledger_entry", mapOf("name" to "扫地机器人")),
        ),
    ).toList()

    assertThat(events[0]).isInstanceOf(OrchestratorEvent.Executed::class.java)
    assertThat(events[1]).isInstanceOf(OrchestratorEvent.NeedConfirm::class.java)
}
```

```kotlin
@Test
fun confirmContinuesRemainingSteps() = runTest {
    val executed = mutableListOf<String>()
    val high = confirmableExecutor("add_ledger_entry", executed)
    val low = fakeExecutor("wechat_login", RiskLevel.LOW, "拉起微信")
    val orchestrator = ToolOrchestrator(ToolRegistry(listOf(high, low)))

    val events = orchestrator.execute(
        listOf(
            ToolCall("add_ledger_entry", mapOf("name" to "扫地机器人")),
            ToolCall("wechat_login", emptyMap()),
        ),
    ).toList()

    val confirm = events.first { it is OrchestratorEvent.NeedConfirm } as OrchestratorEvent.NeedConfirm
    confirm.onConfirm()

    assertThat(executed).containsExactly("add_ledger_entry")
}
```

- [ ] **Step 2: 跑测试，确认 `ToolRegistry` / `ToolOrchestrator` 缺失**

Run:

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew testDebugUnitTest --tests com.renovation.ledger.voice.ToolOrchestratorTest
```

Expected: FAIL，缺少 `ToolRegistry` / `OrchestratorEvent`。

- [ ] **Step 3: 写最小实现**

```kotlin
class ToolRegistry(private val executors: List<ToolExecutor>) {
    private val byName = executors.associateBy { it.toolName }
    fun find(toolName: String): ToolExecutor? = byName[toolName]
    fun schemas(): List<ToolSchema> = executors.map { it.schema }
}
```

```kotlin
sealed class OrchestratorEvent {
    data class Executed(val tool: String, val result: ToolResult) : OrchestratorEvent()
    data class NeedConfirm(val tool: String, val preview: ToolPreview, val onConfirm: suspend () -> ToolResult, val onCancel: () -> Unit) : OrchestratorEvent()
    data class Failed(val tool: String, val error: String) : OrchestratorEvent()
    data class AllDone(val summary: String) : OrchestratorEvent()
}
```

```kotlin
class ToolOrchestrator(private val registry: ToolRegistry) {
    fun execute(toolCalls: List<ToolCall>): Flow<OrchestratorEvent> = flow {
        val messages = mutableListOf<String>()
        toolCalls.forEach { call ->
            val executor = registry.find(call.tool)
                ?: return@flow emit(OrchestratorEvent.Failed(call.tool, "未找到工具 ${call.tool}"))
            if (executor.risk == RiskLevel.HIGH) {
                emit(
                    OrchestratorEvent.NeedConfirm(
                        tool = call.tool,
                        preview = executor.preview(call.params),
                        onConfirm = {
                            val result = executor.execute(call.params)
                            messages += result.message
                            result
                        },
                        onCancel = {},
                    ),
                )
                return@flow
            }
            val result = executor.execute(call.params)
            messages += result.message
            emit(OrchestratorEvent.Executed(call.tool, result))
        }
        emit(OrchestratorEvent.AllDone(messages.joinToString("，")))
    }
}
```

- [ ] **Step 4: 跑测试，确认 HIGH risk 确认继续逻辑仍失败**

Run same command as Step 2.  
Expected: FAIL，说明还需把“确认后继续剩余步骤”做成显式状态机。

---

### Task 4: 完成 Orchestrator 的确认恢复状态机

**Files:**
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/tool/ToolOrchestrator.kt`
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/test/java/com/renovation/ledger/voice/ToolOrchestratorTest.kt`

- [ ] **Step 1: 增加取消跳过、未知 tool、后续继续执行测试**

```kotlin
@Test
fun cancelSkipsHighRiskAndContinuesNextLowRisk() = runTest {
    val executed = mutableListOf<String>()
    val high = confirmableExecutor("add_ledger_entry", executed)
    val low = fakeExecutor("wechat_login", RiskLevel.LOW, "拉起微信")
    val orchestrator = ToolOrchestrator(ToolRegistry(listOf(high, low)))

    val session = orchestrator.start(
        listOf(
            ToolCall("add_ledger_entry", mapOf("name" to "扫地机器人")),
            ToolCall("wechat_login", emptyMap()),
        ),
    )

    assertThat(session.next()).isInstanceOf(OrchestratorEvent.NeedConfirm::class.java)
    session.cancelCurrent()
    val afterCancel = session.next()
    assertThat(afterCancel).isInstanceOf(OrchestratorEvent.Executed::class.java)
}
```

- [ ] **Step 2: 跑测试，确认 session API 尚不存在**

Run:

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew testDebugUnitTest --tests com.renovation.ledger.voice.ToolOrchestratorTest
```

Expected: FAIL，提示 `start()` / `next()` / `cancelCurrent()` 未定义。

- [ ] **Step 3: 引入 session 状态机，去掉一次性 `Flow` 方案**

```kotlin
class ToolOrchestrator(private val registry: ToolRegistry) {
    fun start(toolCalls: List<ToolCall>): ToolExecutionSession = ToolExecutionSession(toolCalls, registry)
}

class ToolExecutionSession(
    private val toolCalls: List<ToolCall>,
    private val registry: ToolRegistry,
) {
    private var index: Int = 0
    private var pendingHighRisk: Pair<ToolCall, ToolExecutor>? = null
    private val summaries = mutableListOf<String>()

    suspend fun next(): OrchestratorEvent {
        pendingHighRisk?.let { (call, executor) ->
            return OrchestratorEvent.NeedConfirm(call.tool, executor.preview(call.params))
        }
        while (index < toolCalls.size) {
            val call = toolCalls[index]
            val executor = registry.find(call.tool)
                ?: return OrchestratorEvent.Failed(call.tool, "未找到工具 ${call.tool}")
            if (executor.risk == RiskLevel.HIGH) {
                pendingHighRisk = call to executor
                return OrchestratorEvent.NeedConfirm(call.tool, executor.preview(call.params))
            }
            val result = executor.execute(call.params)
            summaries += result.message
            index += 1
            return OrchestratorEvent.Executed(call.tool, result)
        }
        return OrchestratorEvent.AllDone(summaries.joinToString("，"))
    }

    suspend fun confirmCurrent(): OrchestratorEvent {
        val (call, executor) = pendingHighRisk ?: return next()
        val result = executor.execute(call.params)
        summaries += result.message
        pendingHighRisk = null
        index += 1
        return OrchestratorEvent.Executed(call.tool, result)
    }

    fun cancelCurrent() {
        pendingHighRisk = null
        index += 1
    }
}
```

- [ ] **Step 4: 跑测试，期望 PASS**

Run same command as Step 2.  
Expected: PASS。

---

### Task 5: 实现 AddLedgerEntryExecutor 并提取复用保存逻辑

**Files:**
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/tool/executors/AddLedgerEntryExecutor.kt`
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/ui/entry/ConfirmEntryViewModel.kt`
- Test: `/Users/beike/Projects/renovation-ledger/app/src/test/java/com/renovation/ledger/voice/AddLedgerEntryExecutorTest.kt`

- [ ] **Step 1: 写映射测试，覆盖“定金已付 + 尾款未付”**

```kotlin
@Test
fun executeCreatesTwoPaymentsFromDepositAndFinalFields() = runTest {
    val repo = FakeProjectRepository()
    val executor = AddLedgerEntryExecutor(repo, FakeUserPrefs("开发者"))

    executor.execute(
        mapOf(
            "name" to "扫地机器人",
            "category" to "家电",
            "stage" to "主材",
            "amount" to 2950,
            "deposit" to 1000,
            "depositPaid" to true,
            "finalPayment" to 1950,
            "finalPaid" to false,
        ),
    )

    assertThat(repo.upsertedItems.single().name).isEqualTo("扫地机器人")
    assertThat(repo.upsertedPayments).hasSize(2)
    assertThat(repo.upsertedPayments[0].status.name).isEqualTo("PAID")
    assertThat(repo.upsertedPayments[1].status.name).isEqualTo("UNPAID")
}
```

- [ ] **Step 2: 跑测试，确认 executor 缺失**

Run:

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew testDebugUnitTest --tests com.renovation.ledger.voice.AddLedgerEntryExecutorTest
```

Expected: FAIL，缺少 `AddLedgerEntryExecutor`。

- [ ] **Step 3: 从 `ConfirmEntryViewModel` 提取可复用保存入口**

```kotlin
data class VoiceEntryPayload(
    val itemName: String,
    val category: String,
    val stage: String,
    val space: String,
    val budgetYuan: String?,
    val payments: List<VoicePaymentPayload>,
)

data class VoicePaymentPayload(
    val amountYuan: String,
    val paymentType: PaymentType,
    val paymentStatus: PaymentStatus,
)

suspend fun saveVoicePayload(projectId: String, payload: VoiceEntryPayload) {
    val itemId = UUID.randomUUID().toString()
    projectRepository.upsertItem(
        BudgetItem(
            id = itemId,
            projectId = projectId,
            name = payload.itemName.trim(),
            stage = payload.stage.trim(),
            category = payload.category.trim(),
            space = payload.space.trim(),
            budgetAmount = payload.budgetYuan?.let(::parseYuanToFen) ?: 0L,
            isNewAddition = true,
        ),
    )
    payload.payments.forEach { payment ->
        val amountFen = parseYuanToFen(payment.amountYuan) ?: return@forEach
        projectRepository.upsertPayment(
            Payment(
                id = UUID.randomUUID().toString(),
                budgetItemId = itemId,
                type = payment.paymentType,
                amount = amountFen,
                status = payment.paymentStatus,
                paidAtEpochMs = if (payment.paymentStatus == PaymentStatus.PAID) System.currentTimeMillis() else null,
                note = "语音助手",
                createdBy = userPrefs.userProfile.first().nickname,
            ),
        )
    }
}
```

- [ ] **Step 4: 实现 executor**

```kotlin
class AddLedgerEntryExecutor(
    private val confirmEntrySaver: ConfirmEntryVoiceSaver,
) : ToolExecutor {
    override val toolName: String = "add_ledger_entry"
    override val risk: RiskLevel = RiskLevel.HIGH
    override val schema: ToolSchema = ToolSchema(
        name = toolName,
        description = "新增装修记账并拆分定金尾款",
        parametersJson = """
            {"type":"object","required":["name","amount"],"properties":{
              "name":{"type":"string"},
              "category":{"type":"string"},
              "stage":{"type":"string"},
              "space":{"type":"string"},
              "amount":{"type":"number"},
              "deposit":{"type":"number"},
              "depositPaid":{"type":"boolean"},
              "finalPayment":{"type":"number"},
              "finalPaid":{"type":"boolean"}
            }}
        """.trimIndent(),
        risk = risk,
    )

    override fun preview(params: Map<String, Any?>): ToolPreview = ToolPreview(
        title = "新增记账",
        fields = listOf(
            PreviewField("名称", params["name"].toString(), editable = true),
            PreviewField("大类", params["category"].orEmptyString(), editable = true),
            PreviewField("总价", params["amount"].toCurrencyText(), editable = true),
            PreviewField("定金", params["deposit"].toCurrencyWithPaidFlag(params["depositPaid"]), editable = true),
            PreviewField("尾款", params["finalPayment"].toCurrencyWithPaidFlag(params["finalPaid"]), editable = true),
        ),
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        confirmEntrySaver.saveFromVoice(params)
        return ToolResult(success = true, message = "记账已保存")
    }
}
```

- [ ] **Step 5: 跑测试，期望 PASS**

Run same command as Step 2.  
Expected: PASS。

---

### Task 6: 实现 Debug 工具执行器

**Files:**
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/tool/executors/SwitchEnvExecutor.kt`
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/tool/executors/WechatLoginExecutor.kt`
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/tool/executors/DevLoginExecutor.kt`
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/ui/debug/DebugCloudViewModel.kt`
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/ui/mine/MineViewModel.kt`
- Test: `/Users/beike/Projects/renovation-ledger/app/src/test/java/com/renovation/ledger/voice/ToolOrchestratorTest.kt`

- [ ] **Step 1: 写 LOW risk 成功与失败测试**

```kotlin
@Test
fun switchEnvExecutorUpdatesPrefsAndEndpoint() = runTest {
    val prefs = FakeUserPrefs()
    val endpoint = ServerEndpoint()
    val executor = SwitchEnvExecutor(prefs, endpoint)

    val result = executor.execute(mapOf("env" to "prod"))

    assertThat(result.success).isTrue()
    assertThat(endpoint.baseUrl).isEqualTo(CloudEnv.PROD_URL)
}

@Test
fun unknownEnvReturnsFailure() = runTest {
    val result = SwitchEnvExecutor(FakeUserPrefs(), ServerEndpoint()).execute(mapOf("env" to "oops"))
    assertThat(result.success).isFalse()
}
```

- [ ] **Step 2: 跑测试，确认 executor 缺失**

Run:

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew testDebugUnitTest --tests com.renovation.ledger.voice.ToolOrchestratorTest
```

Expected: FAIL，缺少 `SwitchEnvExecutor`。

- [ ] **Step 3: 实现三个 executor，并抽窄复用入口**

```kotlin
class SwitchEnvExecutor(
    private val userPrefs: UserPrefs,
    private val serverEndpoint: ServerEndpoint,
) : ToolExecutor {
    override val toolName = "switch_env"
    override val risk = RiskLevel.LOW
    override val schema = ToolSchema(toolName, "切换云环境", """{"type":"object","required":["env"],"properties":{"env":{"type":"string","enum":["dev","prod"]}}}""", risk)
    override fun preview(params: Map<String, Any?>): ToolPreview = ToolPreview("切换环境", emptyList())
    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val env = params["env"]?.toString() ?: return ToolResult(false, "", "缺少 env")
        val kind = if (env == "prod") CloudEnv.Kind.PROD else if (env == "dev") CloudEnv.Kind.DEV else return ToolResult(false, "", "不支持的环境 $env")
        val url = CloudEnv.urlOf(kind)
        userPrefs.setCloudEnv(kind, url)
        userPrefs.setJwt(null, null)
        serverEndpoint.baseUrl = url
        return ToolResult(true, if (kind == CloudEnv.Kind.PROD) "已切换到正式环境" else "已切换到开发环境")
    }
}
```

```kotlin
fun MineViewModel.startWechatLogin(activity: Activity?) {
    wechatLogin(activity)
}

fun DebugCloudViewModel.runDevLogin(label: String) {
    viewModelScope.launch {
        runCatching { ledgerSync.devLogin(label) }
            .onSuccess { message.value = "开发登录成功" }
            .onFailure { message.value = ApiErrorMessages.fromThrowable(it) }
    }
}
```

- [ ] **Step 4: 跑相关单测**

Run:

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew testDebugUnitTest --tests com.renovation.ledger.voice.ToolOrchestratorTest --tests com.renovation.ledger.voice.AddLedgerEntryExecutorTest
```

Expected: PASS。

---

### Task 7: 实现 SystemAsrEngine

**Files:**
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/asr/SystemAsrEngine.kt`
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/main/AndroidManifest.xml`
- Test: `/Users/beike/Projects/renovation-ledger/app/src/test/java/com/renovation/ledger/voice/VoiceAssistantViewModelTest.kt`

- [ ] **Step 1: 写 ViewModel 侧低置信度测试，先锁定 ASR 行为**

```kotlin
@Test
fun lowConfidenceShowsEditableTranscript() = runTest {
    val vm = buildViewModel(
        asr = FakeAsrEngine(AsrResult("切换到正式环境", confidence = 0.55f, segments = listOf(AsrSegment("切换到正式环境", 0, 1000, 0.55f)))),
    )

    vm.startVoice()

    assertThat(vm.uiState.value.mode).isEqualTo(VoiceAssistantMode.EDIT_TRANSCRIPT)
    assertThat(vm.uiState.value.transcript).isEqualTo("切换到正式环境")
}
```

- [ ] **Step 2: 跑测试，确认 `VoiceAssistantViewModel` / `FakeAsrEngine` 缺失**

Run:

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew testDebugUnitTest --tests com.renovation.ledger.voice.VoiceAssistantViewModelTest
```

Expected: FAIL。

- [ ] **Step 3: 写 `SystemAsrEngine`**

```kotlin
class SystemAsrEngine(
    private val context: Context,
    private val locale: Locale = Locale.SIMPLIFIED_CHINESE,
) : AsrEngine {
    override val engineName: String = "android_speech_recognizer"
    private var speechRecognizer: SpeechRecognizer? = null

    override suspend fun recognize(): AsrResult = suspendCancellableCoroutine { cont ->
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            cont.resume(AsrResult("", 0f, emptyList(), AsrError.ENGINE_UNAVAILABLE))
            return@suspendCancellableCoroutine
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer
        val startedAt = System.currentTimeMillis()
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onError(error: Int) {
                cont.resume(AsrResult("", 0f, emptyList(), error.toAsrError()))
            }
            override fun onResults(results: Bundle) {
                val texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val scores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                val text = texts.firstOrNull().orEmpty()
                val confidence = scores?.firstOrNull() ?: 0.5f
                cont.resume(
                    AsrResult(
                        finalText = text,
                        confidence = confidence,
                        segments = listOf(AsrSegment(text, 0, System.currentTimeMillis() - startedAt, confidence)),
                    ),
                )
            }
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
        cont.invokeOnCancellation { recognizer.cancel(); recognizer.destroy() }
    }

    override fun cancel() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
```

- [ ] **Step 4: 加权限并做 smoke 编译**

Manifest:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

Run:

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL。

---

### Task 8: 实现 provider 与 AI 配置持久化

**Files:**
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/data/prefs/UserPrefs.kt`
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/llm/DeepSeekProvider.kt`
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/llm/OpenAiProvider.kt`
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/di/VoiceModule.kt`
- Test: `/Users/beike/Projects/renovation-ledger/app/src/test/java/com/renovation/ledger/voice/DefaultIntentParserTest.kt`

- [ ] **Step 1: 写 provider 选择测试**

```kotlin
@Test
fun voiceModuleUsesDeepSeekByDefault() {
    val provider = VoiceModule.provideLlmProvider(selected = "deepseek", deepSeek = FakeDeepSeekProvider(), openAi = FakeOpenAiProvider())
    assertThat(provider.name).isEqualTo("deepseek")
}
```

- [ ] **Step 2: 跑测试，确认 `VoiceModule` API 未实现**

Run:

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew testDebugUnitTest --tests com.renovation.ledger.voice.DefaultIntentParserTest
```

Expected: FAIL。

- [ ] **Step 3: 在 `UserPrefs` 增加 voice 配置**

```kotlin
val aiProvider: Flow<String> = dataStore.data.map { it[aiProviderKey] ?: "deepseek" }
val aiApiKey: Flow<String> = dataStore.data.map { it[aiApiKey] ?: "" }

suspend fun setAiProvider(value: String) {
    dataStore.edit { it[aiProviderKey] = value }
}

suspend fun setAiApiKey(value: String) {
    dataStore.edit { it[aiApiKey] = value.trim() }
}
```

- [ ] **Step 4: 实现 provider HTTP 调用**

```kotlin
class DeepSeekProvider(
    private val client: OkHttpClient,
    private val apiKeyProvider: suspend () -> String,
    private val gson: Gson = Gson(),
) : LlmProvider {
    override val name: String = "deepseek"
    override suspend fun chat(prompt: String, tools: List<ToolSchema>): String {
        val body = gson.toJson(
            mapOf(
                "model" to "deepseek-chat",
                "messages" to listOf(mapOf("role" to "system", "content" to prompt)),
                "tools" to tools.map { mapOf("type" to "function", "function" to mapOf("name" to it.name, "description" to it.description, "parameters" to gson.fromJson(it.parametersJson, Map::class.java))) },
            ),
        )
        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .header("Authorization", "Bearer ${apiKeyProvider()}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return client.newCall(request).await().body.string()
    }
}
```

- [ ] **Step 5: 跑 parser 单测**

Run same command as Step 2.  
Expected: PASS。

---

### Task 9: 实现 VoiceAssistantViewModel 状态机

**Files:**
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/ui/VoiceDebugModels.kt`
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/ui/VoiceAssistantViewModel.kt`
- Test: `/Users/beike/Projects/renovation-ledger/app/src/test/java/com/renovation/ledger/voice/VoiceAssistantViewModelTest.kt`

- [ ] **Step 1: 写四个核心测试**

```kotlin
@Test
fun highConfidenceParsesAndOpensConfirm() = runTest {
    val vm = buildViewModel(
        asr = FakeAsrEngine(
            AsrResult(
                finalText = "增加一笔新记账，家电，扫地机器人，2950，定金1000，尾款未付",
                confidence = 0.92f,
                segments = listOf(AsrSegment("增加一笔新记账，家电，扫地机器人，2950，定金1000，尾款未付", 0, 1800, 0.92f)),
            ),
        ),
        parser = FakeIntentParser(
            IntentResult(
                toolCalls = listOf(
                    ToolCall(
                        tool = "add_ledger_entry",
                        params = mapOf(
                            "name" to "扫地机器人",
                            "category" to "家电",
                            "amount" to 2950,
                            "deposit" to 1000,
                            "depositPaid" to true,
                            "finalPayment" to 1950,
                            "finalPaid" to false,
                        ),
                    ),
                ),
                rawResponse = """{"tool_calls":[{"tool":"add_ledger_entry"}]}""",
            ),
        ),
    )

    vm.startVoice()

    assertThat(vm.uiState.value.mode).isEqualTo(VoiceAssistantMode.NEED_CONFIRM)
    assertThat(vm.uiState.value.confirmPreview?.title).isEqualTo("新增记账")
}

@Test
fun lowConfidenceOnlyEditsTranscript() = runTest {
    val vm = buildViewModel(
        asr = FakeAsrEngine(
            AsrResult(
                finalText = "切换到正式环境",
                confidence = 0.55f,
                segments = listOf(AsrSegment("切换到正式环境", 0, 900, 0.55f)),
            ),
        ),
    )

    vm.startVoice()

    assertThat(vm.uiState.value.mode).isEqualTo(VoiceAssistantMode.EDIT_TRANSCRIPT)
    assertThat(vm.uiState.value.transcript).isEqualTo("切换到正式环境")
}

@Test
fun parserFailureShowsErrorMessage() = runTest {
    val vm = buildViewModel(
        asr = FakeAsrEngine(
            AsrResult(
                finalText = "切换环境",
                confidence = 0.91f,
                segments = listOf(AsrSegment("切换环境", 0, 600, 0.91f)),
            ),
        ),
        parser = FakeIntentParser(IntentResult(toolCalls = emptyList(), rawResponse = "timeout", error = IntentError.NETWORK_ERROR)),
    )

    vm.startVoice()

    assertThat(vm.uiState.value.mode).isEqualTo(VoiceAssistantMode.ERROR)
    assertThat(vm.uiState.value.errorMessage).contains("NETWORK_ERROR")
}

@Test
fun lowRiskActionsAutoExecuteAndClose() = runTest {
    val vm = buildViewModel(
        asr = FakeAsrEngine(
            AsrResult(
                finalText = "切换环境到正式环境并启用微信登录",
                confidence = 0.95f,
                segments = listOf(AsrSegment("切换环境到正式环境并启用微信登录", 0, 1200, 0.95f)),
            ),
        ),
        parser = FakeIntentParser(
            IntentResult(
                toolCalls = listOf(
                    ToolCall("switch_env", mapOf("env" to "prod")),
                    ToolCall("wechat_login", emptyMap()),
                ),
                rawResponse = """{"tool_calls":[{"tool":"switch_env"},{"tool":"wechat_login"}]}""",
            ),
        ),
        session = FakeToolExecutionSession(
            listOf(
                OrchestratorEvent.Executed("switch_env", ToolResult(true, "已切换到正式环境")),
                OrchestratorEvent.Executed("wechat_login", ToolResult(true, "正在拉起微信登录")),
                OrchestratorEvent.AllDone("已切换到正式环境，正在拉起微信登录"),
            ),
        ),
    )

    vm.startVoice()

    assertThat(vm.uiState.value.mode).isEqualTo(VoiceAssistantMode.DONE)
    assertThat(vm.uiState.value.snackMessage).contains("已切换到正式环境")
}
```

- [ ] **Step 2: 跑测试，确认 ViewModel 缺失**

Run:

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew testDebugUnitTest --tests com.renovation.ledger.voice.VoiceAssistantViewModelTest
```

Expected: FAIL。

- [ ] **Step 3: 实现状态模型与 ViewModel**

```kotlin
enum class VoiceAssistantMode { IDLE, LISTENING, EDIT_TRANSCRIPT, ANALYZING, NEED_CONFIRM, EXECUTING, DONE, ERROR }

data class VoiceAssistantUiState(
    val visible: Boolean = false,
    val mode: VoiceAssistantMode = VoiceAssistantMode.IDLE,
    val transcript: String = "",
    val confidence: Float = 0f,
    val confirmPreview: ToolPreview? = null,
    val snackMessage: String? = null,
    val errorMessage: String? = null,
)
```

```kotlin
fun startVoice() = viewModelScope.launch {
    state.update { it.copy(visible = true, mode = VoiceAssistantMode.LISTENING) }
    val asr = asrEngine.recognize()
    val transcript = asr.finalText.trim()
    val debug = VoiceDebugSnapshot(asrText = transcript, asrConfidence = asr.confidence, segments = asr.segments)
    when {
        asr.error != null -> state.update { it.copy(mode = VoiceAssistantMode.ERROR, errorMessage = asr.error.name) }
        asr.confidence < asrConfig.retryThreshold -> state.update { it.copy(mode = VoiceAssistantMode.ERROR, transcript = transcript, errorMessage = "没听清，请重说") }
        asr.confidence < asrConfig.editThreshold -> state.update { it.copy(mode = VoiceAssistantMode.EDIT_TRANSCRIPT, transcript = transcript, confidence = asr.confidence) }
        else -> submitTranscript(transcript, debug)
    }
}
```

- [ ] **Step 4: 跑测试，期望 PASS**

Run same command as Step 2.  
Expected: PASS。

---

### Task 10: 接入 Overview 入口与确认弹窗 UI

**Files:**
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/ui/VoiceAssistantSheet.kt`
- Create: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/ui/VoiceConfirmDialog.kt`
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/ui/overview/OverviewScreen.kt`

- [ ] **Step 1: 写 Compose smoke 用例，锁定三种状态文案**

```kotlin
@Test
fun voiceSheetShowsListeningAndAnalyzingAndEditStates() {
    composeRule.setContent {
        VoiceAssistantSheet(
            state = VoiceAssistantUiState(visible = true, mode = VoiceAssistantMode.LISTENING, transcript = "扫地机器人"),
            onDismiss = {},
            onRetry = {},
            onSubmitEditedTranscript = {},
        )
    }
    composeRule.onNodeWithText("正在听你说…").assertExists()
}
```

- [ ] **Step 2: 跑测试，确认 UI 文件缺失**

Run:

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew testDebugUnitTest --tests com.renovation.ledger.voice.VoiceAssistantViewModelTest
```

Expected: 若未配 Compose UI test，可至少先编译失败，提示 `VoiceAssistantSheet` 未定义。

- [ ] **Step 3: 实现 sheet 与 dialog**

```kotlin
@Composable
fun VoiceAssistantSheet(
    state: VoiceAssistantUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onTranscriptChange: (String) -> Unit,
    onSubmitEditedTranscript: () -> Unit,
) {
    if (!state.visible) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        when (state.mode) {
            VoiceAssistantMode.LISTENING -> Text("正在听你说…")
            VoiceAssistantMode.ANALYZING -> Text("正在分析…")
            VoiceAssistantMode.EDIT_TRANSCRIPT -> {
                Text("识别结果请确认")
                OutlinedTextField(value = state.transcript, onValueChange = onTranscriptChange)
                Button(onClick = onSubmitEditedTranscript) { Text("继续分析") }
            }
            VoiceAssistantMode.ERROR -> {
                Text(state.errorMessage ?: "识别失败")
                TextButton(onClick = onRetry) { Text("重试") }
            }
            else -> Unit
        }
    }
}
```

```kotlin
@Composable
fun VoiceConfirmDialog(preview: ToolPreview, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("语音助手 · ${preview.title}") },
        text = {
            Column {
                preview.fields.forEach { Text("${it.label}  ${it.value}") }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确认") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}
```

- [ ] **Step 4: 在 `OverviewScreen` 接入麦克风 FAB**

```kotlin
var showVoiceAssistant by remember { mutableStateOf(false) }

if (showVoiceAssistant) {
    VoiceAssistantSheet(
        state = voiceUiState,
        onDismiss = { showVoiceAssistant = false; voiceViewModel.dismiss() },
        onRetry = voiceViewModel::startVoice,
        onTranscriptChange = voiceViewModel::updateTranscript,
        onSubmitEditedTranscript = voiceViewModel::submitEditedTranscript,
    )
}

voiceUiState.confirmPreview?.let { preview ->
    VoiceConfirmDialog(
        preview = preview,
        onCancel = voiceViewModel::cancelConfirm,
        onConfirm = voiceViewModel::confirmCurrent,
    )
}

SmallFloatingActionButton(onClick = {
    showVoiceAssistant = true
    voiceViewModel.startVoice()
}) {
    Icon(Icons.Outlined.Mic, contentDescription = "语音助手")
}
```

- [ ] **Step 5: 编译 Overview 相关代码**

Run:

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL。

---

### Task 11: 接入 DebugCloud 面板中的 AI 配置与语音调试

**Files:**
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/ui/debug/DebugCloudViewModel.kt`
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/ui/debug/DebugCloudScreen.kt`
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/data/prefs/UserPrefs.kt`
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/main/java/com/renovation/ledger/voice/ui/VoiceAssistantViewModel.kt`

- [ ] **Step 1: 先写 ViewModel 状态测试**

```kotlin
@Test
fun debugCloudUiStateContainsAiProviderAndLastVoiceDebugSnapshot() = runTest {
    val prefs = FakeUserPrefs(aiProvider = "deepseek", aiApiKey = "sk-test", voiceDebug = VoiceDebugSnapshot(asrText = "切环境"))
    val state = DebugCloudViewModel(prefs, FakeLedgerSyncRepository(), ServerEndpoint()).uiState.value
    assertThat(state.aiProvider).isEqualTo("deepseek")
    assertThat(state.lastVoiceDebug?.asrText).isEqualTo("切环境")
}
```

- [ ] **Step 2: 跑测试，确认 `DebugCloudUiState` 字段未实现**

Run:

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew testDebugUnitTest --tests com.renovation.ledger.voice.VoiceAssistantViewModelTest
```

Expected: FAIL。

- [ ] **Step 3: 扩展状态与保存 API**

```kotlin
data class DebugCloudUiState(
    val env: CloudEnv.Kind = CloudEnv.defaultKind(),
    val serverBaseUrl: String = CloudEnv.defaultUrl(),
    val devChannel: DebugDevChannel = DebugDevChannel.USB,
    val aiProvider: String = "deepseek",
    val aiApiKeyMasked: String = "",
    val lastVoiceDebug: VoiceDebugSnapshot? = null,
    val message: String? = null,
)
```

```kotlin
fun setAiProvider(value: String) {
    viewModelScope.launch {
        userPrefs.setAiProvider(value)
        message.value = "已切换 AI 模型"
    }
}

fun setAiApiKey(value: String) {
    viewModelScope.launch {
        userPrefs.setAiApiKey(value)
        message.value = "已保存 API Key"
    }
}
```

- [ ] **Step 4: 在 `DebugCloudScreen` 增加两张卡片**

```kotlin
Text(text = "AI 模型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
FilterChip(selected = uiState.aiProvider == "deepseek", onClick = { viewModel.setAiProvider("deepseek") }, label = { Text("DeepSeek") })
FilterChip(selected = uiState.aiProvider == "openai", onClick = { viewModel.setAiProvider("openai") }, label = { Text("OpenAI") })
ClearableOutlinedTextField(value = apiKeyDraft, onValueChange = { apiKeyDraft = it }, label = { Text("API Key") })
OutlinedButton(onClick = { viewModel.setAiApiKey(apiKeyDraft) }, modifier = Modifier.fillMaxWidth()) { Text("保存 API Key") }

Text(text = "语音调试", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
Text("ASR：${uiState.lastVoiceDebug?.asrText.orEmpty()}")
Text("置信度：${uiState.lastVoiceDebug?.asrConfidence ?: 0f}")
Text("Tool Calls：${uiState.lastVoiceDebug?.toolCallsText.orEmpty()}")
Text("执行结果：${uiState.lastVoiceDebug?.resultSummary.orEmpty()}")
```

- [ ] **Step 5: 编译 Debug 页面**

Run:

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL。

---

### Task 12: 联调、单测与整包验证

**Files:**
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/test/java/com/renovation/ledger/voice/DefaultIntentParserTest.kt`
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/test/java/com/renovation/ledger/voice/ToolOrchestratorTest.kt`
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/test/java/com/renovation/ledger/voice/AddLedgerEntryExecutorTest.kt`
- Modify: `/Users/beike/Projects/renovation-ledger/app/src/test/java/com/renovation/ledger/voice/VoiceAssistantViewModelTest.kt`

- [ ] **Step 1: 跑 voice 全量单测**

Run:

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew testDebugUnitTest --tests "com.renovation.ledger.voice.*"
```

Expected: PASS。

- [ ] **Step 2: 手工验收两条主链路**

1. 首页点麦克风，说“我想增加一笔新记账，大类是家电，名称是扫地机器人，价格是2950，定金1000，尾款还没付钱”
2. 预期：出现确认弹窗，字段为 `扫地机器人 / 家电 / 2950 / 定金1000已付 / 尾款1950未付`
3. 点击确认后，账本出现新 item 与两条 payment
4. 再说“切换环境到正式环境并且启用微信登录”
5. 预期：先 toast “已切换到正式环境”，再拉起微信登录

- [ ] **Step 3: 执行打包安装**

Run:

```bash
cd /Users/beike/Projects/renovation-ledger && sh oneClickSetup
```

Expected: `BUILD SUCCESSFUL`，APK 安装成功并拉起 App。若失败，记录是 Gradle、设备连接还是微信/权限问题。

- [ ] **Step 4: 记录收尾风险**

在实现说明中明确：

```text
1. System SpeechRecognizer 的 segments 仅整句级，真实逐段时间戳要等后续换引擎。
2. Provider API Key 仅 Debug 面板可配；正式包默认 key 需要本地环境注入。
3. 微信登录 executor 依赖当前 Activity；若从无 Activity 场景触发，需要回退为提示用户手动进入“我的”页。
```
