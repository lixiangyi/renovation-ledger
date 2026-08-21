# 云端录音转写 ASR（百炼 + DeepSeek）设计

> 日期：2026-08-21  
> 范围：Android App + 微信小程序  
> 前置：`2026-08-18-voice-intent-assistant-design.md`（系统 SpeechRecognizer 在国行机不可用）  
> 决策摘要：按住录音 → 阿里云百炼同步 ASR → DeepSeek 意图解析；失败则文字输入

## 1. 目标

在 OPPO 等无 Android 标准 `RecognitionService` 的设备上，以及小程序端，提供可用的语音记账入口：

1. 用户按住说话、松手结束；
2. 本地短音频上传到**阿里云百炼**同步语音识别，得到文字；
3. 文字交给现有** DeepSeek **意图解析（Function Calling / tool_calls）；
4. 无百炼 Key、转写失败、空结果 → **直接文字输入**，不空等系统 ASR。

非目标（第一期不做）：

- 实时流式识别 / WebSocket ASR
- 服务端代持 Key、代理转写
- 多模态大模型直接吃音频做意图
- 系统 SpeechRecognizer 作为默认路径（可保留代码，DI 默认切到云端引擎）
- 语音唤醒、历史录音列表

## 2. 整体链路

```
按住说话 → 本地录音文件（≤30s）
        → 松手
        → 百炼同步 ASR（qwen3-asr-flash，Base64）
        → finalText（无可靠置信度时固定 0.9）
        → DeepSeek Intent Parser（现有）
        → Tool Orchestrator / 记账确认
```

### 失败兜底

| 情况 | 行为 |
|---|---|
| 未配置百炼 Key | 不开始录音；进入文字输入，提示去配置 |
| 麦克风权限拒绝 | 错误态 + 可改文字输入 |
| 录音过短 / 空文件 | 文字输入，「没听清，可改打字」 |
| 网络 / 百炼 API 失败 | 文字输入 + 简短错误 |
| DeepSeek 意图失败 | 保持现有 ERROR；可编辑文字重试 |
| 用户取消（松手前上滑，可选） | 关闭面板，不上传 |

## 3. 密钥与配置

开发面板 / 小程序设置拆成**两个独立字段**（均只存本地，禁止入库、禁止写进 git）：

| 字段 | 用途 | 调用方 |
|---|---|---|
| DeepSeek API Key | 意图解析 `/chat/completions` | 现有 `DeepSeekProvider` |
| 百炼（DashScope）API Key | 语音转写 | 新建 `DashScopeAsrClient` |

说明：

- 原先「一套 AI Key + deepseek/openai 切换」中，**语音不再使用 OpenAI Whisper**。
- 意图第一期默认 DeepSeek；OpenAI chat 可保留为可选，与百炼无关。
- 用户曾在聊天中暴露过 DeepSeek Key：实现与文档均不落盘该值；提示用户在控制台轮换。

Android：`UserPrefs` 增加 `dashScopeApiKey`（或 `asrApiKey`）Flow；开发面板增加输入/保存/脱敏展示。  
小程序：`wx.setStorage` 对等字段；设置页或 debug 页可编辑。

## 4. ASR 中间件（云端实现）

### 4.1 接口（沿用现有）

继续实现 Android `AsrEngine`：

```kotlin
interface AsrEngine {
    val engineName: String
    suspend fun recognize(): AsrResult
    fun cancel()
    fun partialResults(): Flow<String> = emptyFlow()
}
```

第一期云端引擎**通常无 partial**（整段录完再转写）；UI 在录音阶段只显示「按住说话」，转写阶段显示「正在转写…」。

### 4.2 `CloudDashScopeAsrEngine`（Android）

职责拆分：

1. **`AudioRecorder`**：按住开始 / 松手停止；输出临时文件（优先 AAC/`m4a` 或 WAV 16k；与百炼支持格式对齐）。
2. **`DashScopeAsrClient`**：读文件 → Base64 Data URI → HTTP 调用百炼 → 解析文本。
3. **`CloudDashScopeAsrEngine`**：实现 `AsrEngine`；`recognize()` 等待「本次按住会话结束」的音频再转写。

因交互从「自动听」改为「按住说话」，`recognize()` 语义调整为：

- ViewModel / UI 驱动：`startHold()` → `stopHoldAndRecognize()`；或
- Engine 暴露 `beginCapture()` / `endCaptureAndRecognize()`，ViewModel 绑定按压事件。

推荐：**Engine 管录音+转写，ViewModel 只转发 press/release**，避免 UI 直接碰文件。

百炼同步调用约定（实现时以官方最新文档为准）：

- Endpoint：`POST https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation`
- Header：`Authorization: Bearer <DASHSCOPE_API_KEY>`
- Model：`qwen3-asr-flash`（短音频同步；按住 ≤30s 足够）
- 音频：`data:audio/<mime>;base64,...` 放入 messages content（字段名按官方 Qwen-ASR 文档：`audio` 或 `input_audio`）
- 语言提示：中文 `zh`（若 API 支持 `language` / `language_hints`）
- 超时：与现有 LLM OkHttp 类似，建议 connect/read 约 30–60s

错误映射到现有 `AsrError`：

| 情况 | AsrError |
|---|---|
| 无 Key | `ENGINE_UNAVAILABLE`（上层改文字输入） |
| HTTP 4xx/5xx / 业务码失败 | `NETWORK_ERROR` 或 `UNKNOWN` |
| 返回空文本 | `NO_SPEECH` |
| 无麦克风权限 | `NO_PERMISSION` |

Confidence：百炼若无分数，固定 `0.9`，走现有「≥ editThreshold 直接送 LLM」路径；用户仍可在确认页改字段。

### 4.3 DI

- 默认 `AsrEngine` → `CloudDashScopeAsrEngine`
- `SystemAsrEngine` 可留作 debug 开关，第一期 UI 不暴露

### 4.4 小程序对等模块

- `utils/dashScopeAsr.js`：Base64 + 请求百炼 + 解析 text
- `RecorderManager`：`start` / `stop`；`format` 选 `aac` 或 `mp3`（与百炼支持一致）
- 无 Key / 失败 → 同页展示 textarea 文字输入

## 5. UI / 交互

### 5.1 Android `VoiceAssistantSheet`

| 模式 | 展示 |
|---|---|
| `HOLD_TO_TALK`（新）或复用 LISTENING | 「按住说话…」；按住区域/按钮；可选「改用文字输入」 |
| 转写中 | 「正在转写…」 |
| `EDIT_TRANSCRIPT` | 无 Key / 失败 / 低置信度文案 + 输入框 |
| `ANALYZING` / 确认 / ERROR | 现有逻辑 |

手势：

- 按住开始录音，松手 `stop` + 转写
- 最长 30s 自动 stop + 转写
- 第一期可不做「上滑取消」；若做，取消不上传

### 5.2 小程序

- 总览增加语音入口（麦克风）
- 底部面板或半屏：按住按钮 + 状态文案，与 Android 同文案
- 转写成功后：
  - **第一期能力对齐**：将文字带入记账确认流（解析字段填入 `pages/entry`，或调用与 Android 同构的轻量意图请求若小程序已接 DeepSeek）
  - 若小程序尚无完整 Tool Orchestrator：最低标准是「转写 → 可编辑 → 进入手动记账页并预填名称/金额等（规则或 DeepSeek JSON）」
- 更新 `OPEN_QUESTIONS.md`：原「小程序不做语音」改为「做云端 ASR 语音记账」

## 6. 意图与双端边界

| 端 | 转写 | 意图 | 执行 |
|---|---|---|---|
| Android | 百炼 | DeepSeek（现有 parser + tools） | 现有 Orchestrator（记账确认、切环境等） |
| 小程序 | 百炼 | DeepSeek（新增精简 `chat/completions`，仅 `add_ledger_entry` 一类） | 填入 `pages/entry` 确认保存；不做切环境等 Debug tool |

小程序意图契约：输入转写文本 + 本地分类/阶段词表，要求模型返回与 Android `add_ledger_entry` 字段对齐的 JSON，用户在 entry 页确认后再写入 store。DeepSeek 失败时回退为「原文展示 + 手工填 entry」，不另做规则 NLP。

## 7. 错误处理与隐私

- 录音文件用完即删（Android cacheDir / 小程序临时路径）
- 音频只发往百炼；转写文本只发往 DeepSeek；不经过 renovation-ledger-server
- 日志只打长度/错误码，不打音频与 Key
- Key 脱敏展示（沿用 `maskApiKey`）

## 8. 测试

Android 单测：

- `DashScopeAsrClient`：成功 JSON → text；空 text → `NO_SPEECH`；401 → 映射错误（可用 MockWebServer）
- ViewModel：无百炼 Key → `EDIT_TRANSCRIPT`；转写成功 → 进入分析；失败 → 文字输入
- 现有意图 / Orchestrator 回归

小程序：关键解析函数单测或手工 checklist（真机按住说话）。

真机：OPPO 按住 → 转写 → 记账确认全流程。

## 9. 实现顺序建议

1. Prefs + 开发面板双 Key  
2. `DashScopeAsrClient` + 单测  
3. `AudioRecorder` + `CloudDashScopeAsrEngine` + DI 切换  
4. ViewModel / Sheet 按住交互 + 兜底  
5. `oneClickSetup` 真机验证  
6. 小程序 Recorder + 百炼 + 设置 Key + entry 预填 / 意图  
7. 更新小程序 OPEN_QUESTIONS 与本 spec 引用

## 10. 与旧设计关系

- 替换默认 ASR 实现路径；`AsrEngine` / confidence 阈值 / LLM tools **契约不变**
- 废弃「默认依赖 SystemAsrEngine」作为生产路径
- 用户决策：方案「录音 + 云端 STT」；STT = 百炼；意图 = DeepSeek；交互 = 按住说话；兜底 = 文字输入；双端一起做
