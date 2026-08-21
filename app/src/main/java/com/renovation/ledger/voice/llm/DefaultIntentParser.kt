package com.renovation.ledger.voice.llm

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.renovation.ledger.dsl.catchException
import com.renovation.ledger.dsl.jsonToMap
import com.renovation.ledger.voice.tool.ToolSchema

class DefaultIntentParser(
    private val provider: LlmProvider,
    private val config: LlmConfig = LlmConfig(),
) : LlmIntentParser {

    override val providerName: String get() = provider.name

    suspend fun parse(
        text: String,
        tools: List<ToolSchema>,
        context: AppContext,
    ): IntentResult = parse(IntentRequest(text = text, tools = tools, context = context))

    override suspend fun parse(request: IntentRequest): IntentResult {
        val prompt = buildPrompt(request)
        val raw = catchException(
            isPrintStackTrace = false,
            onError = { err ->
                val error = if (err is LlmHttpException && err.code == 429) {
                    IntentError.RATE_LIMITED
                } else {
                    IntentError.NETWORK_ERROR
                }
                return IntentResult(emptyList(), rawResponse = err.message.orEmpty(), error = error)
            },
        ) {
            provider.chat(prompt, request.tools)
        }
        val parsed = catchException(
            isPrintStackTrace = false,
            onError = {
                return IntentResult(emptyList(), raw, IntentError.PARSE_FAILED)
            },
        ) {
            parseToolCalls(raw)
        }
        return if (parsed.isEmpty()) {
            IntentResult(emptyList(), raw, IntentError.NO_MATCH)
        } else {
            IntentResult(parsed, rawResponse = raw)
        }
    }

    private fun buildPrompt(request: IntentRequest): String {
        val ctx = request.context
        return buildString {
            appendLine(config.systemPrompt)
            appendLine()
            appendLine("当前上下文：")
            appendLine("- 环境: ${ctx.currentEnv}")
            appendLine("- 已登录: ${ctx.isLoggedIn}")
            appendLine("- Debug 包: ${ctx.isDebugBuild}")
            appendLine("- 可用分类: ${ctx.availableCategories.joinToString("、")}")
            appendLine("- 可用阶段: ${ctx.availableStages.joinToString("、")}")
            appendLine()
            appendLine("用户说：")
            appendLine(request.text)
        }
    }

    private fun parseToolCalls(raw: String): List<ToolCall> {
        val element = JsonParser.parseString(raw)
        if (!element.isJsonObject) {
            error("not json object")
        }
        val obj = element.asJsonObject
        obj.getAsJsonArray("tool_calls")?.let { array ->
            if (array.size() == 0) return emptyList()
            val first = array[0]
            if (first.isJsonObject && first.asJsonObject.has("function")) {
                return parseOpenAiToolCalls(array)
            }
            return array.mapNotNull { parseSimplifiedCall(it) }
        }
        obj.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
            ?.getAsJsonObject("message")
            ?.let { message ->
                message.getAsJsonArray("tool_calls")?.let { array ->
                    return parseOpenAiToolCalls(array)
                }
                val content = message.get("content")
                if (content != null && content.isJsonPrimitive) {
                    val nested = content.asString
                    if (!nested.isNullOrBlank() && nested.trim().startsWith("{")) {
                        return parseToolCalls(nested)
                    }
                }
                return emptyList()
            }
        error("unrecognized llm payload")
    }

    private fun parseOpenAiToolCalls(array: com.google.gson.JsonArray): List<ToolCall> {
        return array.mapNotNull { item ->
            if (!item.isJsonObject) return@mapNotNull null
            val fn = item.asJsonObject.getAsJsonObject("function") ?: return@mapNotNull null
            val name = fn.get("name")?.asString?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val argsRaw = fn.get("arguments")?.asString ?: "{}"
            val params = argsRaw.jsonToMap<Any?>()
            ToolCall(name, params)
        }
    }

    private fun parseSimplifiedCall(element: JsonElement): ToolCall? {
        if (!element.isJsonObject) return null
        val obj: JsonObject = element.asJsonObject
        val name = (obj.get("tool") ?: obj.get("name"))?.asString?.trim().orEmpty()
        if (name.isEmpty()) return null
        val paramsEl = obj.get("params") ?: obj.get("arguments")
        val params = jsonElementToMap(paramsEl)
        return ToolCall(name, params)
    }

    private fun jsonElementToMap(element: JsonElement?): Map<String, Any?> {
        if (element == null || element.isJsonNull) return emptyMap()
        if (element.isJsonPrimitive) {
            val raw = element.asString
            return raw.jsonToMap<Any?>()
        }
        if (!element.isJsonObject) return emptyMap()
        return element.asJsonObject.entrySet().associate { (key, value) ->
            key to jsonValue(value)
        }
    }

    private fun jsonValue(value: JsonElement): Any? {
        if (value.isJsonNull) return null
        if (value.isJsonPrimitive) {
            val p = value.asJsonPrimitive
            return when {
                p.isBoolean -> p.asBoolean
                p.isNumber -> p.asDouble
                else -> p.asString
            }
        }
        if (value.isJsonObject) {
            return jsonElementToMap(value)
        }
        return value.toString()
    }
}
