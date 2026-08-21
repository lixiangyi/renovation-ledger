package com.renovation.ledger.voice.asr

import com.renovation.ledger.dsl.gson
import com.renovation.ledger.dsl.logD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

class DashScopeAsrClient(
    private val client: OkHttpClient,
    private val apiKeyProvider: suspend () -> String,
    private val endpoint: String = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
    private val model: String = "qwen3-asr-flash",
) {
    suspend fun transcribe(
        audioBytes: ByteArray,
        mimeType: String = "audio/mp4",
    ): AsrResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank()) {
            return@withContext AsrResult("", 0f, emptyList(), AsrError.ENGINE_UNAVAILABLE)
        }
        if (audioBytes.isEmpty()) {
            return@withContext AsrResult("", 0f, emptyList(), AsrError.NO_SPEECH)
        }
        val b64 = Base64.getEncoder().encodeToString(audioBytes)
        val dataUri = "data:$mimeType;base64,$b64"
        val payload = mapOf(
            "model" to model,
            "messages" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to listOf(
                        mapOf(
                            "type" to "input_audio",
                            "input_audio" to mapOf("data" to dataUri),
                        ),
                    ),
                ),
            ),
            "asr_options" to mapOf(
                "language" to "zh",
                "enable_itn" to false,
            ),
        )
        val body = gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                logD("VoiceAsr") { "dashscope status=${resp.code} bodyLen=${raw.length}" }
                if (!resp.isSuccessful) {
                    val err = when (resp.code) {
                        401, 403 -> AsrError.ENGINE_UNAVAILABLE
                        else -> AsrError.NETWORK_ERROR
                    }
                    return@withContext AsrResult("", 0f, emptyList(), err)
                }
                val text = parseDashScopeAsrText(raw).orEmpty()
                if (text.isBlank()) {
                    AsrResult("", 0f, emptyList(), AsrError.NO_SPEECH)
                } else {
                    AsrResult(
                        finalText = text,
                        confidence = 0.9f,
                        segments = listOf(AsrSegment(text, 0, 0, 0.9f)),
                    )
                }
            }
        } catch (e: Exception) {
            logD("VoiceAsr") { "dashscope failed: ${e.message}" }
            AsrResult("", 0f, emptyList(), AsrError.NETWORK_ERROR)
        }
    }
}
