package com.renovation.ledger.voice.tool

enum class RiskLevel { HIGH, LOW }

data class ToolSchema(
    val name: String,
    val description: String,
    val parametersJson: String,
    val risk: RiskLevel,
)

data class ToolResult(
    val success: Boolean,
    val message: String,
    val error: String? = null,
)

data class ToolPreview(
    val title: String,
    val fields: List<PreviewField>,
)

data class PreviewField(
    val label: String,
    val value: String,
    val editable: Boolean,
    val key: String = "",
)
