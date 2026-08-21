package com.renovation.ledger.voice.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.voice.asr.AsrConfig
import com.renovation.ledger.voice.asr.AsrError
import com.renovation.ledger.voice.asr.AsrResult
import com.renovation.ledger.voice.asr.HoldSpeechAsr
import com.renovation.ledger.voice.llm.AppContext
import com.renovation.ledger.voice.llm.IntentError
import com.renovation.ledger.voice.llm.IntentRequest
import com.renovation.ledger.voice.llm.LlmIntentParser
import com.renovation.ledger.voice.tool.OrchestratorEvent
import com.renovation.ledger.voice.tool.ToolExecutionSession
import com.renovation.ledger.voice.tool.ToolOrchestrator
import com.renovation.ledger.voice.tool.ToolPreview
import com.renovation.ledger.voice.tool.ToolRegistry
import com.renovation.ledger.voice.tool.executors.VoiceHostHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VoiceAssistantMode {
    IDLE,
    HOLD_TO_TALK,
    TRANSCRIBING,
    LISTENING,
    EDIT_TRANSCRIPT,
    ANALYZING,
    NEED_CONFIRM,
    EXECUTING,
    DONE,
    ERROR,
}

data class VoiceAssistantUiState(
    val visible: Boolean = false,
    val mode: VoiceAssistantMode = VoiceAssistantMode.IDLE,
    val transcript: String = "",
    val confidence: Float = 0f,
    val confirmPreview: ToolPreview? = null,
    val snackMessage: String? = null,
    val errorMessage: String? = null,
)

fun interface VoiceAppContextFactory {
    suspend fun create(): AppContext
}

fun interface AiCredentialProvider {
    suspend fun apiKey(): String
}

fun interface DashScopeCredentialProvider {
    suspend fun apiKey(): String
}

@HiltViewModel
class VoiceAssistantViewModel @Inject constructor(
    private val holdAsr: HoldSpeechAsr,
    private val parser: LlmIntentParser,
    private val registry: ToolRegistry,
    private val asrConfig: AsrConfig,
    private val contextFactory: VoiceAppContextFactory,
    private val debugStore: VoiceDebugStore,
    private val credentialProvider: AiCredentialProvider,
    private val dashScopeCredentialProvider: DashScopeCredentialProvider,
    private val hostHolder: VoiceHostHolder,
) : ViewModel() {

    private val orchestrator = ToolOrchestrator(registry)
    private val state = MutableStateFlow(VoiceAssistantUiState())
    val uiState: StateFlow<VoiceAssistantUiState> = state.asStateFlow()

    private var session: ToolExecutionSession? = null
    private var listenJob: Job? = null
    private var maxHoldJob: Job? = null
    private var latestDebug = VoiceDebugSnapshot()

    fun attachHost(activity: Activity?) {
        hostHolder.activity = activity
    }

    fun dismiss() {
        listenJob?.cancel()
        maxHoldJob?.cancel()
        holdAsr.cancel()
        session = null
        state.value = VoiceAssistantUiState()
    }

    fun clearSnack() {
        state.update { it.copy(snackMessage = null) }
    }

    fun updateTranscript(value: String) {
        state.update { it.copy(transcript = value) }
    }

    /** Entry from Overview after mic permission. */
    fun startVoice() = openVoicePanel()

    fun openVoicePanel() {
        listenJob?.cancel()
        maxHoldJob?.cancel()
        holdAsr.cancel()
        session = null
        listenJob = viewModelScope.launch {
            val key = dashScopeCredentialProvider.apiKey()
            if (key.isBlank()) {
                state.update {
                    it.copy(
                        visible = true,
                        mode = VoiceAssistantMode.EDIT_TRANSCRIPT,
                        transcript = "",
                        confidence = 0f,
                        confirmPreview = null,
                        snackMessage = null,
                        errorMessage = "请先在开发面板配置百炼 API Key，或直接输入",
                    )
                }
                return@launch
            }
            state.update {
                it.copy(
                    visible = true,
                    mode = VoiceAssistantMode.HOLD_TO_TALK,
                    transcript = "",
                    confidence = 0f,
                    confirmPreview = null,
                    errorMessage = null,
                    snackMessage = null,
                )
            }
        }
    }

    fun onHoldStart() {
        if (state.value.mode != VoiceAssistantMode.HOLD_TO_TALK) return
        if (!holdAsr.beginHold()) {
            state.update {
                it.copy(
                    mode = VoiceAssistantMode.EDIT_TRANSCRIPT,
                    errorMessage = "无法开始录音，请直接输入",
                )
            }
            return
        }
        maxHoldJob?.cancel()
        maxHoldJob = viewModelScope.launch {
            delay(30_000L)
            if (state.value.mode == VoiceAssistantMode.HOLD_TO_TALK) {
                onHoldEnd()
            }
        }
    }

    fun onHoldEnd() {
        if (state.value.mode != VoiceAssistantMode.HOLD_TO_TALK &&
            state.value.mode != VoiceAssistantMode.TRANSCRIBING
        ) {
            // Allow release after auto-timeout already moved to TRANSCRIBING only once
        }
        if (state.value.mode != VoiceAssistantMode.HOLD_TO_TALK) return
        maxHoldJob?.cancel()
        maxHoldJob = null
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            state.update { it.copy(mode = VoiceAssistantMode.TRANSCRIBING, errorMessage = null) }
            try {
                val asr = holdAsr.endHoldAndRecognize()
                handleAsrResult(asr)
            } catch (e: Throwable) {
                state.update {
                    it.copy(
                        mode = VoiceAssistantMode.EDIT_TRANSCRIPT,
                        errorMessage = e.message ?: e.javaClass.simpleName,
                    )
                }
            }
        }
    }

    fun useTypedInput() {
        listenJob?.cancel()
        maxHoldJob?.cancel()
        holdAsr.cancel()
        state.update {
            it.copy(
                visible = true,
                mode = VoiceAssistantMode.EDIT_TRANSCRIPT,
                errorMessage = it.errorMessage ?: "请直接输入要分析的内容",
            )
        }
    }

    fun submitEditedTranscript() {
        val text = state.value.transcript.trim()
        if (text.isEmpty()) {
            state.update { it.copy(mode = VoiceAssistantMode.ERROR, errorMessage = "请先输入要分析的内容") }
            return
        }
        viewModelScope.launch { submitTranscript(text) }
    }

    fun confirmCurrent(edits: Map<String, String> = emptyMap()) {
        viewModelScope.launch {
            val current = session ?: return@launch
            state.update { it.copy(mode = VoiceAssistantMode.EXECUTING) }
            val editedParams = edits.takeIf { it.isNotEmpty() }?.mapValues { it.value as Any? }
            when (val event = current.confirmCurrent(editedParams)) {
                is OrchestratorEvent.Executed -> {
                    appendDebugResult(event.result.message)
                    emitUntilPause(current)
                }
                is OrchestratorEvent.Failed -> {
                    state.update {
                        it.copy(mode = VoiceAssistantMode.ERROR, errorMessage = event.error)
                    }
                }
                else -> emitUntilPause(current)
            }
        }
    }

    fun cancelConfirm() {
        viewModelScope.launch {
            session?.cancelCurrent()
            val current = session ?: return@launch
            emitUntilPause(current)
        }
    }

    private suspend fun handleAsrResult(asr: AsrResult) {
        val transcript = asr.finalText.trim()
        latestDebug = VoiceDebugSnapshot(
            asrText = transcript,
            asrConfidence = asr.confidence,
            segments = asr.segments,
        )
        debugStore.update(latestDebug)
        when {
            asr.error == AsrError.NO_PERMISSION -> {
                state.update {
                    it.copy(
                        mode = VoiceAssistantMode.EDIT_TRANSCRIPT,
                        transcript = transcript,
                        errorMessage = "需要麦克风权限，可直接输入",
                    )
                }
            }
            asr.error == AsrError.ENGINE_UNAVAILABLE -> {
                state.update {
                    it.copy(
                        mode = VoiceAssistantMode.EDIT_TRANSCRIPT,
                        transcript = transcript,
                        errorMessage = "语音转写不可用，请检查百炼 Key 或直接输入",
                    )
                }
            }
            asr.error != null && transcript.isBlank() -> {
                state.update {
                    it.copy(
                        mode = VoiceAssistantMode.EDIT_TRANSCRIPT,
                        transcript = transcript,
                        errorMessage = asrErrorMessage(asr.error),
                    )
                }
            }
            asr.confidence < asrConfig.retryThreshold -> {
                state.update {
                    it.copy(
                        mode = VoiceAssistantMode.EDIT_TRANSCRIPT,
                        transcript = transcript,
                        confidence = asr.confidence,
                        errorMessage = "没听清，可改打字",
                    )
                }
            }
            asr.confidence < asrConfig.editThreshold -> {
                state.update {
                    it.copy(
                        mode = VoiceAssistantMode.EDIT_TRANSCRIPT,
                        transcript = transcript,
                        confidence = asr.confidence,
                        errorMessage = "识别结果请确认",
                    )
                }
            }
            else -> submitTranscript(transcript)
        }
    }

    private suspend fun submitTranscript(transcript: String) {
        state.update {
            it.copy(
                mode = VoiceAssistantMode.ANALYZING,
                transcript = transcript,
                errorMessage = null,
                confirmPreview = null,
            )
        }
        val apiKey = credentialProvider.apiKey()
        if (apiKey.isBlank() && parser.providerName != "fake") {
            state.update {
                it.copy(
                    mode = VoiceAssistantMode.ERROR,
                    errorMessage = "请先在开发面板配置 AI API Key",
                )
            }
            return
        }
        val request = IntentRequest(
            text = transcript,
            tools = registry.schemas(),
            context = contextFactory.create(),
        )
        val result = parser.parse(request)
        latestDebug = latestDebug.copy(
            asrText = transcript,
            rawLlm = result.rawResponse,
            toolCallsText = result.toolCalls.joinToString(" → ") { call ->
                call.tool + call.params.entries.joinToString(prefix = "(", postfix = ")") { "${it.key}=${it.value}" }
            },
        )
        debugStore.update(latestDebug)
        if (result.error != null) {
            state.update {
                it.copy(
                    mode = VoiceAssistantMode.ERROR,
                    errorMessage = intentErrorMessage(result.error),
                )
            }
            return
        }
        val started = orchestrator.start(result.toolCalls)
        session = started
        emitUntilPause(started)
    }

    private suspend fun emitUntilPause(current: ToolExecutionSession) {
        while (true) {
            when (val event = current.next()) {
                is OrchestratorEvent.Executed -> {
                    appendDebugResult(event.result.message)
                }
                is OrchestratorEvent.NeedConfirm -> {
                    state.update {
                        it.copy(
                            visible = false,
                            mode = VoiceAssistantMode.NEED_CONFIRM,
                            confirmPreview = event.preview,
                        )
                    }
                    return
                }
                is OrchestratorEvent.Failed -> {
                    state.update {
                        it.copy(
                            mode = VoiceAssistantMode.ERROR,
                            errorMessage = event.error,
                            confirmPreview = null,
                        )
                    }
                    return
                }
                is OrchestratorEvent.AllDone -> {
                    latestDebug = latestDebug.copy(resultSummary = event.summary)
                    debugStore.update(latestDebug)
                    state.update {
                        it.copy(
                            mode = VoiceAssistantMode.DONE,
                            snackMessage = event.summary.ifBlank { "已完成" },
                            confirmPreview = null,
                            visible = false,
                        )
                    }
                    return
                }
            }
        }
    }

    private fun appendDebugResult(message: String) {
        val merged = listOfNotNull(latestDebug.resultSummary.takeIf { it.isNotBlank() }, message)
            .joinToString("，")
        latestDebug = latestDebug.copy(resultSummary = merged)
        debugStore.update(latestDebug)
    }

    private fun asrErrorMessage(error: AsrError): String = when (error) {
        AsrError.NO_PERMISSION -> "需要麦克风权限"
        AsrError.NO_SPEECH -> "没听清，可改打字"
        AsrError.NETWORK_ERROR -> "转写需要网络，可改打字"
        AsrError.ENGINE_UNAVAILABLE -> "语音转写不可用，请直接输入"
        AsrError.UNKNOWN -> "语音识别失败，可改打字"
    }

    private fun intentErrorMessage(error: IntentError): String = when (error) {
        IntentError.NETWORK_ERROR -> "网络异常，无法连接大模型（NETWORK_ERROR）"
        IntentError.RATE_LIMITED -> "大模型请求过于频繁（RATE_LIMITED）"
        IntentError.PARSE_FAILED -> "无法解析大模型返回（PARSE_FAILED）"
        IntentError.NO_MATCH -> "没有匹配到可执行操作（NO_MATCH）"
    }
}
