package com.renovation.ledger.voice.asr

data class AsrConfig(
    val retryThreshold: Float = 0.4f,
    val editThreshold: Float = 0.7f,
    val readyTimeoutMs: Long = 3_000,
    val listenTimeoutMs: Long = 12_000,
)
