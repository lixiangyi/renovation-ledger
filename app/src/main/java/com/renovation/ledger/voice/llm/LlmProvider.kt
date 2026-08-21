package com.renovation.ledger.voice.llm

import com.renovation.ledger.voice.tool.ToolSchema

interface LlmProvider {
    val name: String
    suspend fun chat(prompt: String, tools: List<ToolSchema>): String
}
