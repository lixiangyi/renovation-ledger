package com.renovation.ledger.voice.llm

interface LlmIntentParser {
    val providerName: String
    suspend fun parse(request: IntentRequest): IntentResult
}
