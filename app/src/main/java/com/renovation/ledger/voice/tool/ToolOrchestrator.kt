package com.renovation.ledger.voice.tool

import com.renovation.ledger.voice.llm.ToolCall

sealed class OrchestratorEvent {
    data class Executed(val tool: String, val result: ToolResult) : OrchestratorEvent()
    data class NeedConfirm(val tool: String, val preview: ToolPreview) : OrchestratorEvent()
    data class Failed(val tool: String, val error: String) : OrchestratorEvent()
    data class AllDone(val summary: String) : OrchestratorEvent()
}

class ToolOrchestrator(private val registry: ToolRegistry) {
    fun start(toolCalls: List<ToolCall>): ToolExecutionSession =
        ToolExecutionSession(toolCalls, registry)
}

class ToolExecutionSession(
    private val toolCalls: List<ToolCall>,
    private val registry: ToolRegistry,
) {
    private var index: Int = 0
    private var pendingHighRisk: Pair<ToolCall, ToolExecutor>? = null
    private val summaries = mutableListOf<String>()

    suspend fun next(): OrchestratorEvent {
        pendingHighRisk?.let { (call, executor) ->
            return OrchestratorEvent.NeedConfirm(call.tool, executor.preview(call.params))
        }
        while (index < toolCalls.size) {
            val call = toolCalls[index]
            val executor = registry.find(call.tool)
                ?: return OrchestratorEvent.Failed(call.tool, "未找到工具 ${call.tool}")
            if (executor.risk == RiskLevel.HIGH) {
                pendingHighRisk = call to executor
                return OrchestratorEvent.NeedConfirm(call.tool, executor.preview(call.params))
            }
            val result = executor.execute(call.params)
            if (!result.success) {
                return OrchestratorEvent.Failed(call.tool, result.error ?: result.message)
            }
            summaries += result.message
            index += 1
            return OrchestratorEvent.Executed(call.tool, result)
        }
        return OrchestratorEvent.AllDone(summaries.joinToString("，"))
    }

    suspend fun confirmCurrent(editedParams: Map<String, Any?>? = null): OrchestratorEvent {
        val (call, executor) = pendingHighRisk ?: return next()
        val result = executor.execute(editedParams ?: call.params)
        if (!result.success) {
            pendingHighRisk = null
            return OrchestratorEvent.Failed(call.tool, result.error ?: result.message)
        }
        summaries += result.message
        pendingHighRisk = null
        index += 1
        return OrchestratorEvent.Executed(call.tool, result)
    }

    fun cancelCurrent() {
        pendingHighRisk = null
        index += 1
    }
}
