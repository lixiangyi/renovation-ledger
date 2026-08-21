package com.renovation.ledger.voice.tool

interface ToolExecutor {
    val toolName: String
    val risk: RiskLevel
    val schema: ToolSchema
    suspend fun execute(params: Map<String, Any?>): ToolResult
    fun preview(params: Map<String, Any?>): ToolPreview
}
