package com.renovation.ledger.voice.llm

import com.renovation.ledger.voice.tool.ToolSchema

data class AppContext(
    val currentEnv: String,
    val isLoggedIn: Boolean,
    val isDebugBuild: Boolean,
    val availableCategories: List<String>,
    val availableStages: List<String>,
)

data class ToolCall(
    val tool: String,
    val params: Map<String, Any?>,
)

data class IntentRequest(
    val text: String,
    val tools: List<ToolSchema>,
    val context: AppContext,
)

data class IntentResult(
    val toolCalls: List<ToolCall>,
    val rawResponse: String,
    val error: IntentError? = null,
)

enum class IntentError {
    NETWORK_ERROR,
    RATE_LIMITED,
    PARSE_FAILED,
    NO_MATCH,
}

class LlmHttpException(
    val code: Int,
    override val message: String,
) : RuntimeException(message)
