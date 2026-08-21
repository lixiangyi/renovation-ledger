package com.renovation.ledger.voice.asr

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface AsrEngine {
    val engineName: String
    suspend fun recognize(): AsrResult
    fun cancel()
    fun partialResults(): Flow<String> = emptyFlow()
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

enum class AsrError {
    NO_PERMISSION,
    NO_SPEECH,
    NETWORK_ERROR,
    ENGINE_UNAVAILABLE,
    UNKNOWN,
}
