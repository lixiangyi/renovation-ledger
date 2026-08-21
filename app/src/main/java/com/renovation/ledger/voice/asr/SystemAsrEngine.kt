package com.renovation.ledger.voice.asr

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.renovation.ledger.dsl.logD
import com.renovation.ledger.voice.tool.executors.VoiceHostHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class SystemAsrEngine(
    private val appContext: Context,
    private val hostHolder: VoiceHostHolder,
    private val config: AsrConfig = AsrConfig(),
    private val locale: Locale = Locale.SIMPLIFIED_CHINESE,
) : AsrEngine {
    override val engineName: String = "android_speech_recognizer"

    private var speechRecognizer: SpeechRecognizer? = null
    private val partials = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun partialResults(): Flow<String> = partials

    override suspend fun recognize(): AsrResult = withContext(Dispatchers.Main) {
        val ctx = hostHolder.activity ?: appContext
        val component = findRecognitionService(ctx)
        val onDevice = isOnDeviceAvailable(ctx)
        val choice = chooseAsrRecognizer(
            hasRecognitionService = component != null,
            onDeviceAvailable = onDevice,
        )
        logD("VoiceAsr") {
            "choice=$choice service=${component?.flattenToShortString()} onDevice=$onDevice ctx=${ctx.javaClass.simpleName}"
        }
        val recognizer = createRecognizer(ctx, choice, component)
            ?: return@withContext AsrResult("", 0f, emptyList(), AsrError.ENGINE_UNAVAILABLE)
        val timed = withTimeoutOrNull(config.listenTimeoutMs) {
            awaitRecognition(recognizer)
        }
        if (timed == null) {
            logD("VoiceAsr") { "listen timeout ${config.listenTimeoutMs}ms" }
            destroyRecognizer()
            AsrResult("", 0f, emptyList(), AsrError.ENGINE_UNAVAILABLE)
        } else {
            timed
        }
    }

    override fun cancel() {
        destroyRecognizer()
    }

    private suspend fun awaitRecognition(recognizer: SpeechRecognizer): AsrResult =
        suspendCancellableCoroutine { cont ->
            speechRecognizer = recognizer
            val startedAt = System.currentTimeMillis()
            val handler = Handler(Looper.getMainLooper())
            var ready = false
            val readyTimeout = Runnable {
                if (!ready && cont.isActive) {
                    logD("VoiceAsr") { "no onReadyForSpeech within ${config.readyTimeoutMs}ms" }
                    cont.resume(AsrResult("", 0f, emptyList(), AsrError.ENGINE_UNAVAILABLE))
                    destroyRecognizer()
                }
            }
            handler.postDelayed(readyTimeout, config.readyTimeoutMs)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onError(error: Int) {
                    handler.removeCallbacks(readyTimeout)
                    logD("VoiceAsr") { "onError code=$error" }
                    if (cont.isActive) {
                        cont.resume(AsrResult("", 0f, emptyList(), mapSpeechRecognizerError(error)))
                    }
                    destroyRecognizer()
                }

                override fun onResults(results: Bundle) {
                    handler.removeCallbacks(readyTimeout)
                    val texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val scores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    val text = texts.firstOrNull().orEmpty()
                    val confidence = scores?.firstOrNull() ?: if (text.isBlank()) 0f else 0.75f
                    logD("VoiceAsr") { "onResults textLen=${text.length} confidence=$confidence" }
                    if (cont.isActive) {
                        cont.resume(
                            AsrResult(
                                finalText = text,
                                confidence = confidence,
                                segments = listOf(
                                    AsrSegment(
                                        text = text,
                                        startMs = 0,
                                        endMs = System.currentTimeMillis() - startedAt,
                                        confidence = confidence,
                                    ),
                                ),
                                error = if (text.isBlank()) AsrError.NO_SPEECH else null,
                            ),
                        )
                    }
                    destroyRecognizer()
                }

                override fun onReadyForSpeech(params: Bundle?) {
                    ready = true
                    handler.removeCallbacks(readyTimeout)
                    logD("VoiceAsr") { "onReadyForSpeech" }
                }

                override fun onBeginningOfSpeech() {
                    logD("VoiceAsr") { "onBeginningOfSpeech" }
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    logD("VoiceAsr") { "onEndOfSpeech" }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isNotBlank()) {
                        partials.tryEmit(text)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            }
            logD("VoiceAsr") { "start listening engine=$engineName" }
            recognizer.startListening(intent)
            cont.invokeOnCancellation {
                handler.removeCallbacks(readyTimeout)
                destroyRecognizer()
            }
        }

    private fun createRecognizer(
        ctx: Context,
        choice: AsrRecognizerChoice,
        component: ComponentName?,
    ): SpeechRecognizer? = try {
        when (choice) {
            AsrRecognizerChoice.SYSTEM_SERVICE -> {
                if (component == null) {
                    null
                } else {
                    logD("VoiceAsr") { "using recognition service ${component.flattenToShortString()}" }
                    SpeechRecognizer.createSpeechRecognizer(ctx, component)
                }
            }
            AsrRecognizerChoice.ON_DEVICE -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    null
                } else {
                    logD("VoiceAsr") { "using on-device speech recognizer" }
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
                }
            }
            AsrRecognizerChoice.UNAVAILABLE -> null
        }
    } catch (e: Exception) {
        logD("VoiceAsr") { "createRecognizer failed: ${e.message}" }
        null
    }

    private fun isOnDeviceAvailable(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)
        } catch (_: Exception) {
            false
        }
    }

    private fun findRecognitionService(ctx: Context): ComponentName? {
        val matches = ctx.packageManager.queryIntentServices(
            Intent(RecognitionService.SERVICE_INTERFACE),
            0,
        )
        val info = matches.firstOrNull()?.serviceInfo ?: return null
        return ComponentName(info.packageName, info.name)
    }

    private fun destroyRecognizer() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
