package com.renovation.ledger.voice.tool

class ToolRegistry(executors: List<ToolExecutor>) {
    private val byName = executors.associateBy { it.toolName }

    fun find(toolName: String): ToolExecutor? = byName[toolName]

    fun schemas(): List<ToolSchema> = byName.values.map { it.schema }
}
