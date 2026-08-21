# Cloud ASR（百炼 + DeepSeek）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace broken system SpeechRecognizer with hold-to-talk recording + Alibaba DashScope (`qwen3-asr-flash`) transcription, then existing DeepSeek intent flow — on Android and WeChat miniprogram.

**Architecture:** `AudioRecorder` captures short audio on press/release; `DashScopeAsrClient` POSTs Base64 audio to DashScope OpenAI-compatible chat completions; `CloudDashScopeAsrEngine` implements `AsrEngine`. ViewModel binds hold gestures and maps missing-key / ASR failure to `EDIT_TRANSCRIPT`. Miniprogram mirrors with `RecorderManager` + same HTTP shape, then DeepSeek JSON into `pages/entry`.

**Tech Stack:** Kotlin, Hilt, OkHttp, MediaRecorder, DataStore; 微信小程序 RecorderManager + `wx.request`; DashScope `qwen3-asr-flash`; DeepSeek chat (existing).

**Spec:** `docs/superpowers/specs/2026-08-21-cloud-asr-dashscope-design.md`

**Git:** Workspace forbids git unless the user explicitly asks — skip all commit steps.

---

## File map

| File | Responsibility |
|---|---|
| `app/.../data/prefs/UserPrefs.kt` | Persist `dashScopeApiKey` |
| `app/.../ui/debug/DebugCloud*.kt` | UI to edit/mask DashScope key |
| `app/.../voice/asr/DashScopeAsrModels.kt` | Request/response parse helpers (NoProguard if Gson beans) |
| `app/.../voice/asr/DashScopeAsrClient.kt` | HTTP transcription |
| `app/.../voice/asr/HoldAudioRecorder.kt` | MediaRecorder start/stop → temp file |
| `app/.../voice/asr/CloudDashScopeAsrEngine.kt` | `AsrEngine` + hold capture API |
| `app/.../voice/di/VoiceModule.kt` | Provide client + default engine |
| `app/.../voice/ui/VoiceAssistantViewModel.kt` | Hold / release / no-key fallback |
| `app/.../voice/ui/VoiceAssistantSheet.kt` | Hold button UI |
| `app/.../ui/overview/OverviewScreen.kt` | Wire sheet callbacks |
| Tests under `app/src/test/.../voice/` | Client + ViewModel |
| Miniprogram `utils/dashScopeAsr.js`, `utils/aiKeys.js`, `utils/voiceIntent.js` | ASR + keys + DeepSeek JSON |
| Miniprogram `pages/settings/*`, `pages/overview/*`, `pages/entry/*` | Key UI + hold voice + prefill |
| `OPEN_QUESTIONS.md` | Flip「不做语音」 |

---

### Task 1: UserPrefs DashScope key

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/data/prefs/UserPrefs.kt`
- Test: `app/src/test/java/com/renovation/ledger/voice/DashScopePrefsContractTest.kt` (lightweight assertion on key name constant if extracted; otherwise skip unit test and verify via compile + manual — prefer add Flow + setter only)

- [ ] **Step 1: Add prefs key + Flow + setter**

In `UserPrefs.kt`, next to `aiApiKeyKey`:

```kotlin
private val dashScopeApiKeyKey = stringPreferencesKey("dashscope_api_key")

val dashScopeApiKey: Flow<String> =
    ctx.userPrefsDataStore.data.map { prefs ->
        prefs[dashScopeApiKeyKey]?.trim().orEmpty()
    }

suspend fun setDashScopeApiKey(value: String) {
    ctx.userPrefsDataStore.edit { prefs ->
        val cleaned = value.trim()
        if (cleaned.isEmpty()) {
            prefs.remove(dashScopeApiKeyKey)
        } else {
            prefs[dashScopeApiKeyKey] = cleaned
        }
    }
}
```

Mirror `setAiApiKey` style exactly.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit** — SKIP (no-git)

---

### Task 2: Debug panel DashScope key UI

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/ui/debug/DebugCloudViewModel.kt`
- Modify: `app/src/main/java/com/renovation/ledger/ui/debug/DebugCloudScreen.kt`

- [ ] **Step 1: Extend ui state**

```kotlin
data class DebugCloudUiState(
    // ...existing...
    val dashScopeApiKeyMasked: String = "",
)

// In combine sources, include userPrefs.dashScopeApiKey
dashScopeApiKeyMasked = maskApiKey(dashScopeApiKey),
```

```kotlin
fun setDashScopeApiKey(value: String) {
    viewModelScope.launch {
        userPrefs.setDashScopeApiKey(value)
        message.value = "已保存百炼 API Key"
    }
}
```

- [ ] **Step 2: Add UI block under AI Key section**

Label: `百炼（DashScope）API Key`  
Show masked current key; `OutlinedTextField` +「保存百炼 Key」calling `viewModel.setDashScopeApiKey`.  
Hint text: `用于语音转写（qwen3-asr-flash），与 DeepSeek 意图 Key 分开`

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`  
Expected: SUCCESS

- [ ] **Step 4: Commit** — SKIP

---

### Task 3: DashScopeAsrClient (TDD)

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/voice/asr/DashScopeAsrClient.kt`
- Create: `app/src/main/java/com/renovation/ledger/voice/asr/DashScopeAsrResponseParser.kt`
- Test: `app/src/test/java/com/renovation/ledger/voice/DashScopeAsrResponseParserTest.kt`

- [ ] **Step 1: Write failing parser tests**

```kotlin
package com.renovation.ledger.voice

import com.renovation.ledger.voice.asr.parseDashScopeAsrText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashScopeAsrResponseParserTest {
    @Test
    fun parsesOpenAiCompatibleContentString() {
        val json = """
            {"choices":[{"message":{"role":"assistant","content":"增加一笔家电扫地机器人两千九"}}]}
        """.trimIndent()
        assertEquals("增加一笔家电扫地机器人两千九", parseDashScopeAsrText(json))
    }

    @Test
    fun blankContentReturnsNull() {
        val json = """{"choices":[{"message":{"content":"  "}}]}"""
        assertNull(parseDashScopeAsrText(json))
    }

    @Test
    fun dashScopeNativeTextArray() {
        val json = """
            {"output":{"choices":[{"message":{"content":[{"text":"尾款六千"}]}}]}}
        """.trimIndent()
        assertEquals("尾款六千", parseDashScopeAsrText(json))
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL (unresolved reference)**

Run: `./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.voice.DashScopeAsrResponseParserTest`

- [ ] **Step 3: Implement parser**

```kotlin
package com.renovation.ledger.voice.asr

import com.google.gson.JsonParser

fun parseDashScopeAsrText(body: String): String? {
    val root = JsonParser.parseString(body).asJsonObject
    root.getAsJsonArray("choices")
        ?.firstOrNull()?.asJsonObject
        ?.getAsJsonObject("message")
        ?.get("content")
        ?.let { content ->
            when {
                content.isJsonPrimitive -> content.asString.trim().ifBlank { null }
                content.isJsonArray -> content.asJsonArray
                    .mapNotNull { el ->
                        el.asJsonObject.get("text")?.asString?.trim()?.takeIf { it.isNotEmpty() }
                    }
                    .joinToString("")
                    .ifBlank { null }
                else -> null
            }
        }?.let { return it }

    root.getAsJsonObject("output")
        ?.getAsJsonArray("choices")
        ?.firstOrNull()?.asJsonObject
        ?.getAsJsonObject("message")
        ?.get("content")
        ?.let { content ->
            if (content.isJsonArray) {
                return content.asJsonArray
                    .mapNotNull { it.asJsonObject.get("text")?.asString?.trim()?.takeIf { t -> t.isNotEmpty() } }
                    .joinToString("")
                    .ifBlank { null }
            }
        }
    return null
}
```

- [ ] **Step 4: Implement client**

```kotlin
package com.renovation.ledger.voice.asr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.renovation.ledger.dsl.gson

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
        val b64 = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)
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
        } catch (_: Exception) {
            AsrResult("", 0f, emptyList(), AsrError.NETWORK_ERROR)
        }
    }
}
```

Note: Prefer `java.util.Base64` in unit-testable code if `android.util.Base64` is awkward in JVM tests; client can use `java.util.Base64.getEncoder().encodeToString`.

- [ ] **Step 5: Re-run parser tests**

Expected: PASS

- [ ] **Step 6: Commit** — SKIP

---

### Task 4: HoldAudioRecorder

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/voice/asr/HoldAudioRecorder.kt`

- [ ] **Step 1: Implement recorder**

```kotlin
package com.renovation.ledger.voice.asr

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class HoldAudioRecorder(
    private val appContext: Context,
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): Boolean {
        stopInternal(deleteFile = true)
        val file = File(appContext.cacheDir, "voice_asr_${System.currentTimeMillis()}.m4a")
        outputFile = file
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioSamplingRate(16000)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            true
        } catch (_: Exception) {
            mr.release()
            file.delete()
            outputFile = null
            false
        }
    }

    /** @return Pair(bytes, mime) or null */
    fun stop(): Pair<ByteArray, String>? {
        val file = outputFile
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
            recorder?.release()
        }
        recorder = null
        outputFile = null
        if (file == null || !file.exists() || file.length() < 256) {
            file?.delete()
            return null
        }
        val bytes = file.readBytes()
        file.delete()
        return bytes to "audio/mp4"
    }

    fun cancel() {
        stopInternal(deleteFile = true)
    }

    private fun stopInternal(deleteFile: Boolean) {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
            recorder?.release()
        }
        recorder = null
        if (deleteFile) {
            outputFile?.delete()
        }
        outputFile = null
    }
}
```

- [ ] **Step 2: Compile**

Expected: SUCCESS

- [ ] **Step 3: Commit** — SKIP

---

### Task 5: CloudDashScopeAsrEngine + DI

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/voice/asr/CloudDashScopeAsrEngine.kt`
- Modify: `app/src/main/java/com/renovation/ledger/voice/di/VoiceModule.kt`
- Modify: `app/src/main/java/com/renovation/ledger/voice/asr/AsrEngine.kt` (optional hold methods — prefer engine-specific API, not interface pollution)

- [ ] **Step 1: Engine API**

```kotlin
class CloudDashScopeAsrEngine(
    private val recorder: HoldAudioRecorder,
    private val client: DashScopeAsrClient,
) : AsrEngine {
    override val engineName: String = "dashscope_qwen3_asr_flash"

    @Volatile private var holding = false

    fun beginHold(): Boolean {
        cancel()
        holding = recorder.start()
        return holding
    }

    suspend fun endHoldAndRecognize(): AsrResult {
        if (!holding) {
            return AsrResult("", 0f, emptyList(), AsrError.NO_SPEECH)
        }
        holding = false
        val captured = recorder.stop()
            ?: return AsrResult("", 0f, emptyList(), AsrError.NO_SPEECH)
        return client.transcribe(captured.first, captured.second)
    }

    override suspend fun recognize(): AsrResult {
        // Not used for hold UX; return unavailable if called accidentally
        return AsrResult("", 0f, emptyList(), AsrError.ENGINE_UNAVAILABLE)
    }

    override fun cancel() {
        holding = false
        recorder.cancel()
    }
}
```

- [ ] **Step 2: Wire VoiceModule**

```kotlin
@Provides @Singleton
fun dashScopeAsrClient(
    @LlmHttp client: OkHttpClient,
    userPrefs: UserPrefs,
): DashScopeAsrClient = DashScopeAsrClient(
    client = client,
    apiKeyProvider = { userPrefs.dashScopeApiKey.first() },
)

@Provides @Singleton
fun holdAudioRecorder(@ApplicationContext context: Context): HoldAudioRecorder =
    HoldAudioRecorder(context)

@Provides @Singleton
fun asrEngine(
    recorder: HoldAudioRecorder,
    client: DashScopeAsrClient,
): AsrEngine = CloudDashScopeAsrEngine(recorder, client)
```

Remove / stop providing `SystemAsrEngine` as default (keep class file for optional later).

Inject `CloudDashScopeAsrEngine` into ViewModel **or** cast/`@Named` — cleanest: provide both `AsrEngine` bind to cloud engine **and** inject concrete `CloudDashScopeAsrEngine` into ViewModel for hold methods.

```kotlin
@Provides @Singleton
fun cloudDashScopeAsrEngine(...): CloudDashScopeAsrEngine = CloudDashScopeAsrEngine(...)

@Provides @Singleton
fun asrEngine(engine: CloudDashScopeAsrEngine): AsrEngine = engine
```

- [ ] **Step 3: Compile**

Expected: SUCCESS

- [ ] **Step 4: Commit** — SKIP

---

### Task 6: ViewModel hold UX + tests

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/voice/ui/VoiceAssistantViewModel.kt`
- Modify: `app/src/main/java/com/renovation/ledger/voice/ui/VoiceAssistantMode` (add `HOLD_TO_TALK`, `TRANSCRIBING` if needed)
- Test: `app/src/test/java/com/renovation/ledger/voice/VoiceAssistantViewModelTest.kt`

- [ ] **Step 1: Failing tests**

```kotlin
@Test
fun missingDashScopeKeyOpensTypedInput() = runTest(dispatcher) {
    val vm = buildViewModel(
        asr = FakeHoldAsrEngine(available = false), // or inject key provider blank
        dashScopeKey = "",
    )
    vm.openVoicePanel()
    advanceUntilIdle()
    assertEquals(VoiceAssistantMode.EDIT_TRANSCRIPT, vm.uiState.value.mode)
    assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("百炼"))
}

@Test
fun releaseHoldTranscribesThenAnalyzes() = runTest(dispatcher) {
    val vm = buildViewModel(
        asr = FakeHoldAsrEngine(
            result = AsrResult("增加一笔家电扫地机器人两千九", 0.9f, emptyList()),
        ),
        dashScopeKey = "sk-test",
        parser = FakeIntentParser(/* high risk add_ledger_entry */),
    )
    vm.openVoicePanel()
    vm.onHoldStart()
    vm.onHoldEnd()
    advanceUntilIdle()
    assertEquals(VoiceAssistantMode.NEED_CONFIRM, vm.uiState.value.mode)
}
```

Refactor `buildViewModel` to accept `dashScopeKeyProvider` and `CloudDashScopeAsrEngine`-like fake with `beginHold`/`endHoldAndRecognize`. Simplest path: change ViewModel to depend on `CloudDashScopeAsrEngine` + `suspend () -> String` for dash key.

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement ViewModel API**

```kotlin
fun openVoicePanel() {
    viewModelScope.launch {
        val key = dashScopeKeyProvider()
        if (key.isBlank()) {
            state.update {
                it.copy(
                    visible = true,
                    mode = VoiceAssistantMode.EDIT_TRANSCRIPT,
                    transcript = "",
                    errorMessage = "请先在开发面板配置百炼 API Key，或直接输入",
                )
            }
            return@launch
        }
        state.update {
            it.copy(
                visible = true,
                mode = VoiceAssistantMode.HOLD_TO_TALK,
                transcript = "",
                errorMessage = null,
                confirmPreview = null,
            )
        }
    }
}

fun onHoldStart() {
    if (state.value.mode != VoiceAssistantMode.HOLD_TO_TALK) return
    if (!cloudAsr.beginHold()) {
        state.update {
            it.copy(mode = VoiceAssistantMode.EDIT_TRANSCRIPT, errorMessage = "无法开始录音")
        }
    }
}

fun onHoldEnd() {
    listenJob?.cancel()
    listenJob = viewModelScope.launch {
        state.update { it.copy(mode = VoiceAssistantMode.TRANSCRIBING) }
        val asr = cloudAsr.endHoldAndRecognize()
        // same branching as former startVoice() after recognize():
        // ENGINE_UNAVAILABLE / errors with blank → EDIT_TRANSCRIPT
        // else confidence / submitTranscript
    }
}

// Replace startVoice() callers with openVoicePanel(); keep startVoice as alias → openVoicePanel for Overview
```

Map `AsrError.ENGINE_UNAVAILABLE` and network errors to `EDIT_TRANSCRIPT` with message, not endless retry-only ERROR.

Auto-max duration: in `onHoldStart`, launch job `delay(30_000); onHoldEnd()` cancellable on release.

- [ ] **Step 4: Tests PASS**

- [ ] **Step 5: Commit** — SKIP

---

### Task 7: Sheet UI + Overview wiring

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/voice/ui/VoiceAssistantSheet.kt`
- Modify: `app/src/main/java/com/renovation/ledger/ui/overview/OverviewScreen.kt`

- [ ] **Step 1: Sheet modes**

- `HOLD_TO_TALK`: Text「按住说话…」+ `Modifier.pointerInput` / `detectTapGestures(onPress=...)` on a large button; on release call `onHoldEnd`; also「改用文字输入」
- `TRANSCRIBING`: 「正在转写…」
- Keep EDIT / ERROR / ANALYZING

```kotlin
// onPress example
Button(
    onClick = {},
    modifier = Modifier
        .fillMaxWidth()
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    onHoldStart()
                    tryAwaitRelease()
                    onHoldEnd()
                },
            )
        },
) { Text("按住 说话") }
```

- [ ] **Step 2: Overview**

After mic permission granted → `voiceViewModel.openVoicePanel()`  
Pass `onHoldStart` / `onHoldEnd` into sheet.

- [ ] **Step 3: oneClickSetup**

Run: `cd /Users/beike/Projects/renovation-ledger && sh oneClickSetup`  
`block_until_ms` ≥ 600000  
Expected: BUILD SUCCESSFUL + adb Success  
Manual: 开发面板填百炼 Key → 总览语音 → 按住说话 → 转写 → 确认记账

- [ ] **Step 4: Commit** — SKIP

---

### Task 8: Miniprogram keys + DashScope ASR util

**Files:**
- Create: `/Users/beike/Projects/renovation-ledger-miniprogram/utils/aiKeys.js`
- Create: `/Users/beike/Projects/renovation-ledger-miniprogram/utils/dashScopeAsr.js`
- Modify: `pages/settings/settings.js` + `.wxml`
- Modify: `OPEN_QUESTIONS.md` row 4 → 做云端 ASR 语音记账

- [ ] **Step 1: aiKeys.js**

```javascript
const DS_KEY = 'dashscope_api_key'
const DEEPSEEK_KEY = 'deepseek_api_key'

function getDashScopeKey() {
  return (wx.getStorageSync(DS_KEY) || '').trim()
}
function setDashScopeKey(v) {
  const t = (v || '').trim()
  if (!t) wx.removeStorageSync(DS_KEY)
  else wx.setStorageSync(DS_KEY, t)
}
// same for deepseek
module.exports = { getDashScopeKey, setDashScopeKey, getDeepSeekKey, setDeepSeekKey }
```

- [ ] **Step 2: dashScopeAsr.js**

```javascript
function parseDashScopeAsrText(body) {
  // mirror Android parser logic in JS
}

function transcribeFile(filePath) {
  const key = require('./aiKeys').getDashScopeKey()
  if (!key) return Promise.reject(new Error('missing_dashscope_key'))
  const fs = wx.getFileSystemManager()
  const b64 = fs.readFileSync(filePath, 'base64')
  const dataUri = 'data:audio/mp3;base64,' + b64
  return new Promise((resolve, reject) => {
    wx.request({
      url: 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions',
      method: 'POST',
      header: {
        Authorization: 'Bearer ' + key,
        'Content-Type': 'application/json',
      },
      data: {
        model: 'qwen3-asr-flash',
        messages: [{
          role: 'user',
          content: [{ type: 'input_audio', input_audio: { data: dataUri } }],
        }],
        asr_options: { language: 'zh', enable_itn: false },
      },
      success(res) {
        const text = parseDashScopeAsrText(res.data) || parseDashScopeAsrText(JSON.stringify(res.data))
        if (!text) reject(new Error('empty_asr'))
        else resolve(text)
      },
      fail: reject,
    })
  })
}
module.exports = { transcribeFile, parseDashScopeAsrText }
```

Note: `wx.request` `data` if object is auto-JSON; for parse, if `res.data` already object, adapt parser to accept object.

- [ ] **Step 3: Settings UI** — two inputs for DeepSeek + 百炼 keys, save buttons

- [ ] **Step 4: Update OPEN_QUESTIONS.md**

- [ ] **Step 5: Commit** — SKIP

---

### Task 9: Miniprogram hold-to-talk + entry prefill

**Files:**
- Create: `utils/voiceIntent.js` (DeepSeek chat → JSON fields for entry)
- Modify: `pages/overview/overview.js` + `.wxml`
- Modify: `pages/entry/entry.js` (read `prefill` from storage / eventChannel)

- [ ] **Step 1: voiceIntent.js**

Call `https://api.deepseek.com/chat/completions` with system prompt asking for JSON:
`{name, category, amountYuan, depositYuan, depositPaid, finalPaymentYuan, finalPaid}`  
Parse content; on failure return `{ rawText }` only.

- [ ] **Step 2: Overview voice panel**

- Button `bindtouchstart` / `bindtouchend` on hold control
- `RecorderManager`: format `mp3`, sampleRate 16000
- touchstart → `recorder.start()`; touchend → `stop` → `transcribeFile` → `voiceIntent.parse` → `wx.navigateTo({ url: '/pages/entry/entry', success: ... eventChannel emit prefill })`
- No key → show textarea modal / navigate entry with empty + toast

- [ ] **Step 3: entry.js** accept prefill and set form fields

- [ ] **Step 4: Manual test on WeChat devtools / phone**

- [ ] **Step 5: Commit** — SKIP

---

### Task 10: Regression + polish note

- [ ] **Step 1:** `./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.voice.*`
- [ ] **Step 2:** `sh oneClickSetup` again after final Android polish
- [ ] **Step 3:** Confirm OPPO: no hang on「正在听你说」; hold → 转写 → 确认
- [ ] **Step 4:** Remind user: rotate any DeepSeek key that appeared in chat; never commit keys

---

## Spec coverage checklist

| Spec item | Task |
|---|---|
| 百炼同步 ASR Base64 | 3, 5 |
| DeepSeek 意图不变 | 6 (Android), 9 (MP) |
| 双 Key | 1–2, 8 |
| 按住说话 ≤30s | 6–7, 9 |
| 无 Key / 失败 → 文字 | 6–7, 9 |
| 替换默认 SystemAsr | 5 |
| 小程序入口 + OPEN_QUESTIONS | 8–9 |
| 隐私删临时文件 | 4 (`HoldAudioRecorder.stop`) |
| 测试 | 3, 6, 10 |

## Placeholder / consistency self-check

- Endpoint + model names consistent: `compatible-mode/v1/chat/completions` + `qwen3-asr-flash`
- Modes: `HOLD_TO_TALK`, `TRANSCRIBING`, `EDIT_TRANSCRIPT`
- No OpenAI Whisper in this plan
- Git commits skipped per workspace rule
