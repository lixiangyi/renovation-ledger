package com.renovation.ledger.voice.asr

class CloudDashScopeAsrEngine(
    private val recorder: HoldAudioRecorder,
    private val client: DashScopeAsrClient,
) : AsrEngine, HoldSpeechAsr {
    override val engineName: String = "dashscope_qwen3_asr_flash"

    @Volatile
    private var holding: Boolean = false

    override fun beginHold(): Boolean {
        cancel()
        holding = recorder.start()
        return holding
    }

    override suspend fun endHoldAndRecognize(): AsrResult {
        if (!holding) {
            return AsrResult("", 0f, emptyList(), AsrError.NO_SPEECH)
        }
        holding = false
        val captured = recorder.stop()
            ?: return AsrResult("", 0f, emptyList(), AsrError.NO_SPEECH)
        return client.transcribe(captured.first, captured.second)
    }

    override suspend fun recognize(): AsrResult {
        return AsrResult("", 0f, emptyList(), AsrError.ENGINE_UNAVAILABLE)
    }

    override fun cancel() {
        holding = false
        recorder.cancel()
    }
}
