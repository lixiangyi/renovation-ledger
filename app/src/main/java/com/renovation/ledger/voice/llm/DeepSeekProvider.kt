package com.renovation.ledger.voice.llm

import com.google.gson.Gson
import com.renovation.ledger.voice.tool.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class DeepSeekProvider(
    private val client: OkHttpClient,
    private val apiKeyProvider: suspend () -> String,
    private val gson: Gson = com.renovation.ledger.dsl.gson,
) : LlmProvider {
    override val name: String = "deepseek"

    override suspend fun chat(prompt: String, tools: List<ToolSchema>): String {
        return chatCompletions(
            client = client,
            url = "https://api.deepseek.com/chat/completions",
            apiKey = apiKeyProvider(),
            model = "deepseek-chat",
            prompt = prompt,
            tools = tools,
            gson = gson,
        )
    }
}

class OpenAiProvider(
    private val client: OkHttpClient,
    private val apiKeyProvider: suspend () -> String,
    private val gson: Gson = com.renovation.ledger.dsl.gson,
) : LlmProvider {
    override val name: String = "openai"

    override suspend fun chat(prompt: String, tools: List<ToolSchema>): String {
        return chatCompletions(
            client = client,
            url = "https://api.openai.com/v1/chat/completions",
            apiKey = apiKeyProvider(),
            model = "gpt-4o-mini",
            prompt = prompt,
            tools = tools,
            gson = gson,
        )
    }
}

internal suspend fun chatCompletions(
    client: OkHttpClient,
    url: String,
    apiKey: String,
    model: String,
    prompt: String,
    tools: List<ToolSchema>,
    gson: Gson,
): String = withContext(Dispatchers.IO) {
    if (apiKey.isBlank()) {
        throw LlmHttpException(401, "missing api key")
    }
    val payload = mapOf(
        "model" to model,
        "messages" to listOf(
            mapOf("role" to "system", "content" to prompt),
        ),
        "tools" to tools.map { schema ->
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to schema.name,
                    "description" to schema.description,
                    "parameters" to gson.fromJson(schema.parametersJson, Map::class.java),
                ),
            )
        },
        "tool_choice" to "auto",
    )
    val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
    val request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $apiKey")
        .post(body)
        .build()
    client.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw LlmHttpException(response.code, text.take(300).ifBlank { response.message })
        }
        text
    }
}

class SwitchingLlmProvider(
    private val selectedProvider: suspend () -> String,
    private val deepSeek: LlmProvider,
    private val openAi: LlmProvider,
) : LlmProvider {
    override val name: String
        get() = "switching"

    override suspend fun chat(prompt: String, tools: List<ToolSchema>): String {
        return provideLlmProvider(selectedProvider(), deepSeek, openAi).chat(prompt, tools)
    }
}

fun provideLlmProvider(
    selected: String,
    deepSeek: LlmProvider,
    openAi: LlmProvider,
): LlmProvider = if (selected.equals("openai", ignoreCase = true)) openAi else deepSeek
