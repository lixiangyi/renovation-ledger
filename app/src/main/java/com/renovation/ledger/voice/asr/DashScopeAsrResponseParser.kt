package com.renovation.ledger.voice.asr

import com.google.gson.JsonParser

fun parseDashScopeAsrText(body: String): String? {
    val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return null

    root.getAsJsonArray("choices")
        ?.firstOrNull()?.asJsonObject
        ?.getAsJsonObject("message")
        ?.get("content")
        ?.let { content -> extractContentText(content) }
        ?.let { return it }

    root.getAsJsonObject("output")
        ?.getAsJsonArray("choices")
        ?.firstOrNull()?.asJsonObject
        ?.getAsJsonObject("message")
        ?.get("content")
        ?.let { content -> extractContentText(content) }
        ?.let { return it }

    return null
}

private fun extractContentText(content: com.google.gson.JsonElement): String? = when {
    content.isJsonPrimitive -> content.asString.trim().ifBlank { null }
    content.isJsonArray -> content.asJsonArray
        .mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            el.asJsonObject.get("text")?.asString?.trim()?.takeIf { it.isNotEmpty() }
        }
        .joinToString("")
        .ifBlank { null }
    else -> null
}
