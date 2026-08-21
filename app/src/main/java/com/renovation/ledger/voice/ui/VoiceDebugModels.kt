package com.renovation.ledger.voice.ui

import com.renovation.ledger.voice.asr.AsrSegment

data class VoiceDebugSnapshot(
    val asrText: String = "",
    val asrConfidence: Float = 0f,
    val segments: List<AsrSegment> = emptyList(),
    val rawLlm: String = "",
    val toolCallsText: String = "",
    val resultSummary: String = "",
)

fun maskApiKey(raw: String): String {
    val value = raw.trim()
    if (value.isEmpty()) return ""
    if (value.length <= 8) return "****"
    return value.take(4) + "****" + value.takeLast(4)
}
