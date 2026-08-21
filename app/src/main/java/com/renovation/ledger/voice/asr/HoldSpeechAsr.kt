package com.renovation.ledger.voice.asr

interface HoldSpeechAsr {
    fun beginHold(): Boolean
    suspend fun endHoldAndRecognize(): AsrResult
    fun cancel()
}
