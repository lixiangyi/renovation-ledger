package com.renovation.ledger.voice

import com.renovation.ledger.voice.asr.AsrConfig
import com.renovation.ledger.voice.asr.AsrError
import com.renovation.ledger.voice.asr.AsrResult
import com.renovation.ledger.voice.asr.AsrSegment
import com.renovation.ledger.voice.asr.HoldSpeechAsr
import com.renovation.ledger.voice.llm.IntentError
import com.renovation.ledger.voice.llm.IntentRequest
import com.renovation.ledger.voice.llm.IntentResult
import com.renovation.ledger.voice.llm.LlmIntentParser
import com.renovation.ledger.voice.llm.ToolCall
import com.renovation.ledger.voice.tool.RiskLevel
import com.renovation.ledger.voice.tool.ToolRegistry
import com.renovation.ledger.voice.tool.executors.VoiceHostHolder
import com.renovation.ledger.voice.ui.AiCredentialProvider
import com.renovation.ledger.voice.ui.DashScopeCredentialProvider
import com.renovation.ledger.voice.ui.VoiceAppContextFactory
import com.renovation.ledger.voice.ui.VoiceAssistantMode
import com.renovation.ledger.voice.ui.VoiceAssistantViewModel
import com.renovation.ledger.voice.ui.VoiceDebugStore
import com.renovation.ledger.voice.ui.maskApiKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceAssistantViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun missingDashScopeKeyOpensTypedInput() = runTest(dispatcher) {
        val vm = buildViewModel(dashScopeKey = "")
        vm.openVoicePanel()
        advanceUntilIdle()
        assertEquals(VoiceAssistantMode.EDIT_TRANSCRIPT, vm.uiState.value.mode)
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("百炼"))
    }

    @Test
    fun highConfidenceParsesAndOpensConfirm() = runTest(dispatcher) {
        val vm = buildViewModel(
            asr = FakeHoldAsr(
                AsrResult(
                    finalText = "增加一笔新记账，家电，扫地机器人，2950，定金1000，尾款未付",
                    confidence = 0.92f,
                    segments = listOf(
                        AsrSegment("增加一笔新记账，家电，扫地机器人，2950，定金1000，尾款未付", 0, 1800, 0.92f),
                    ),
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

        vm.openVoicePanel()
        advanceUntilIdle()
        assertEquals(VoiceAssistantMode.HOLD_TO_TALK, vm.uiState.value.mode)
        vm.onHoldStart()
        vm.onHoldEnd()
        advanceUntilIdle()

        assertEquals(VoiceAssistantMode.NEED_CONFIRM, vm.uiState.value.mode)
        assertEquals("新增记账", vm.uiState.value.confirmPreview?.title)
    }

    @Test
    fun lowConfidenceOnlyEditsTranscript() = runTest(dispatcher) {
        val vm = buildViewModel(
            asr = FakeHoldAsr(
                AsrResult(
                    finalText = "切换到正式环境",
                    confidence = 0.55f,
                    segments = listOf(AsrSegment("切换到正式环境", 0, 900, 0.55f)),
                ),
            ),
        )

        vm.openVoicePanel()
        advanceUntilIdle()
        vm.onHoldStart()
        vm.onHoldEnd()
        advanceUntilIdle()

        assertEquals(VoiceAssistantMode.EDIT_TRANSCRIPT, vm.uiState.value.mode)
        assertEquals("切换到正式环境", vm.uiState.value.transcript)
    }

    @Test
    fun parserFailureShowsErrorMessage() = runTest(dispatcher) {
        val vm = buildViewModel(
            asr = FakeHoldAsr(
                AsrResult(
                    finalText = "切换环境",
                    confidence = 0.91f,
                    segments = listOf(AsrSegment("切换环境", 0, 600, 0.91f)),
                ),
            ),
            parser = FakeIntentParser(
                IntentResult(
                    toolCalls = emptyList(),
                    rawResponse = "timeout",
                    error = IntentError.NETWORK_ERROR,
                ),
            ),
        )

        vm.openVoicePanel()
        advanceUntilIdle()
        vm.onHoldStart()
        vm.onHoldEnd()
        advanceUntilIdle()

        assertEquals(VoiceAssistantMode.ERROR, vm.uiState.value.mode)
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("NETWORK_ERROR"))
    }

    @Test
    fun lowRiskActionsAutoExecuteAndClose() = runTest(dispatcher) {
        val vm = buildViewModel(
            asr = FakeHoldAsr(
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
        )

        vm.openVoicePanel()
        advanceUntilIdle()
        vm.onHoldStart()
        vm.onHoldEnd()
        advanceUntilIdle()

        assertEquals(VoiceAssistantMode.DONE, vm.uiState.value.mode)
        assertTrue(vm.uiState.value.snackMessage.orEmpty().contains("已切换到正式环境"))
    }

    @Test
    fun engineUnavailableFallsBackToTypedInput() = runTest(dispatcher) {
        val vm = buildViewModel(
            asr = FakeHoldAsr(
                AsrResult("", 0f, emptyList(), AsrError.ENGINE_UNAVAILABLE),
            ),
        )

        vm.openVoicePanel()
        advanceUntilIdle()
        vm.onHoldStart()
        vm.onHoldEnd()
        advanceUntilIdle()

        assertEquals(VoiceAssistantMode.EDIT_TRANSCRIPT, vm.uiState.value.mode)
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("直接输入"))
    }

    @Test
    fun maskApiKeyKeepsPrefixAndSuffix() {
        assertEquals("sk-t****test", maskApiKey("sk-test-secret-test"))
    }

    private fun buildViewModel(
        asr: HoldSpeechAsr = FakeHoldAsr(AsrResult("ok", 0.9f, emptyList())),
        parser: LlmIntentParser = FakeIntentParser(IntentResult(emptyList(), "")),
        dashScopeKey: String = "dash-test-key",
    ): VoiceAssistantViewModel {
        val registry = ToolRegistry(
            listOf(
                fakeExecutor("add_ledger_entry", RiskLevel.HIGH, "记账已保存", previewTitle = "新增记账"),
                fakeExecutor("switch_env", RiskLevel.LOW, "已切换到正式环境"),
                fakeExecutor("wechat_login", RiskLevel.LOW, "正在拉起微信登录"),
            ),
        )
        return VoiceAssistantViewModel(
            holdAsr = asr,
            parser = parser,
            registry = registry,
            asrConfig = AsrConfig(),
            contextFactory = VoiceAppContextFactory { demoContext() },
            debugStore = VoiceDebugStore(),
            credentialProvider = AiCredentialProvider { "test-key" },
            dashScopeCredentialProvider = DashScopeCredentialProvider { dashScopeKey },
            hostHolder = VoiceHostHolder(),
        )
    }
}

private class FakeHoldAsr(
    private val result: AsrResult,
) : HoldSpeechAsr {
    override fun beginHold(): Boolean = true
    override suspend fun endHoldAndRecognize(): AsrResult = result
    override fun cancel() = Unit
}

private class FakeIntentParser(
    private val result: IntentResult,
) : LlmIntentParser {
    override val providerName: String = "fake"
    override suspend fun parse(request: IntentRequest): IntentResult = result
}
