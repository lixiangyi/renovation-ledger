# Phone Login + LAN Dev Environment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace product「开发登录」with a dedicated login page (WeChat + phone SMS), default Debug API to LAN IP (no adb reverse), so the same phone number can sync ledgers across phones and miniprogram against a PC-hosted server.

**Architecture:** Server adds `/health`, `/auth/sms/send`, `/auth/sms/login` (test profile returns code in send response). Android + miniprogram get a Login route/page; Mine only navigates there. CloudEnv DEV defaults to `DEV_LAN_URL`. Existing ledger sync/invite paths stay unchanged.

**Tech Stack:** Spring Boot 3 + MockMvc；Android Compose + Hilt + Retrofit；微信小程序；现有 JWT / Room / store 同步。

**Spec:** `docs/superpowers/specs/2026-08-21-phone-login-lan-dev-design.md`

**Repos:**

| 仓库 | 路径 |
|------|------|
| Server | `/Users/beike/Projects/renovation-ledger-server` |
| Android | `/Users/beike/Projects/renovation-ledger` |
| 小程序 | `/Users/beike/Projects/renovation-ledger-miniprogram` |

**Note:** Workspace forbids agent git commits unless the user explicitly asks. **Skip all Commit steps** in this plan.

---

## File map

### Server (`renovation-ledger-server`)

| File | Responsibility |
|------|----------------|
| `.../health/HealthController.kt` | `GET /health` → `{ "ok": true }` |
| `.../auth/SmsCodeStore.kt` | In-memory phone→code+expiry |
| `.../auth/AuthDtos.kt` | Sms send/login request/response DTOs |
| `.../auth/AuthService.kt` | `sendSms` / `loginSms` |
| `.../auth/AuthController.kt` | Wire new routes |
| `.../config/SecurityConfig.kt` | permitAll health + sms |
| `application.yml` / `application-local.yml` / `src/test/resources/application.yml` | `app.sms.*` |
| `.../auth/SmsLoginTest.kt` | MockMvc coverage |

### Android (`renovation-ledger`)

| File | Responsibility |
|------|----------------|
| `data/remote/CloudEnv.kt` | DEV = LAN；去掉 127.0.0.1:18080 默认 |
| `data/remote/ApiErrorMessages.kt` | 无 adb reverse 文案 |
| `data/remote/ApiModels.kt` / `LedgerApi.kt` | SMS DTOs + health |
| `data/sync/LedgerSyncRepository.kt` | sendSms / smsLogin / logout / pingHealth |
| `ui/login/LoginScreen.kt` / `LoginViewModel.kt` | 登录页 |
| `ui/navigation/AppNav.kt` | `Route.Login` |
| `ui/mine/MineScreen.kt` / `MineViewModel.kt` | 去登录 / 退出；删开发登录 |
| `ui/debug/DebugCloud*.kt` | 删 USB；测通用 health |
| `data/auth/WeChatAppAuth.kt` | 文案不再提开发登录 |
| `voice/.../DevLoginExecutor.kt` + `VoiceModule.kt` | 移除产品工具 |

### 小程序 (`renovation-ledger-miniprogram`)

| File | Responsibility |
|------|----------------|
| `pages/login/*` | 登录页 |
| `pages/mine/*` | 去登录 / 退出 |
| `utils/sync.js` | sendSms / smsLogin / logout / pingHealth |
| `app.json` | 注册 login 页 |
| `pages/debug/*` | 测通与文案对齐（若有 USB/adb 文案则删） |

---

### Task 1: Server health + Security permitAll

**Files:**
- Create: `/Users/beike/Projects/renovation-ledger-server/src/main/kotlin/com/renovation/ledger/server/health/HealthController.kt`
- Modify: `/Users/beike/Projects/renovation-ledger-server/src/main/kotlin/com/renovation/ledger/server/config/SecurityConfig.kt`
- Test: `/Users/beike/Projects/renovation-ledger-server/src/test/kotlin/com/renovation/ledger/server/health/HealthControllerTest.kt`

- [ ] **Step 1: Write failing health test**

```kotlin
package com.renovation.ledger.server.health

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun healthOkWithoutAuth() {
        mockMvc.get("/health").andExpect {
            status { isOk() }
            jsonPath("$.ok") { value(true) }
        }
    }
}
```

- [ ] **Step 2: Run test — expect FAIL (404 or 401)**

```bash
cd /Users/beike/Projects/renovation-ledger-server
./gradlew test --tests com.renovation.ledger.server.health.HealthControllerTest
```

- [ ] **Step 3: Implement HealthController + Security**

```kotlin
// HealthController.kt
package com.renovation.ledger.server.health

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {
    @GetMapping("/health")
    fun health(): Map<String, Boolean> = mapOf("ok" to true)
}
```

In `SecurityConfig`, change matchers to:

```kotlin
it.requestMatchers(
    "/health",
    "/auth/wechat",
    "/auth/dev-login",
    "/auth/sms/send",
    "/auth/sms/login",
    "/error",
).permitAll()
    .anyRequest().authenticated()
```

(SMS paths permitted early so Task 2–3 do not re-touch Security.)

- [ ] **Step 4: Re-run health test — expect PASS**

```bash
./gradlew test --tests com.renovation.ledger.server.health.HealthControllerTest
```

---

### Task 2: Server SMS store + send/login (TDD)

**Files:**
- Create: `.../auth/SmsCodeStore.kt`
- Modify: `.../auth/AuthDtos.kt`, `AuthService.kt`, `AuthController.kt`
- Modify: `src/main/resources/application.yml`, `application-local.yml`, `src/test/resources/application.yml`
- Test: `.../auth/SmsLoginTest.kt`

- [ ] **Step 1: Add config keys**

`application.yml` under `app:`:

```yaml
  sms:
    return-code-in-response: false
    ttl-seconds: 300
```

`application-local.yml` and `src/test/resources/application.yml`:

```yaml
  sms:
    return-code-in-response: true
    ttl-seconds: 300
```

- [ ] **Step 2: Write failing SmsLoginTest**

```kotlin
package com.renovation.ledger.server.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class SmsLoginTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper

    @Test
    fun sendReturnsCodeThenLoginSameUserTwice() {
        val sendJson = mockMvc.post("/auth/sms/send") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"phone":"13800138000"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val send = mapper.readValue<SmsSendResponse>(sendJson)
        assertNotNull(send.code)

        val login1 = login("13800138000", send.code!!)
        val send2Json = mockMvc.post("/auth/sms/send") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"phone":"13800138000"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val send2 = mapper.readValue<SmsSendResponse>(send2Json)
        val login2 = login("13800138000", send2.code!!)
        assertEquals(login1.userId, login2.userId)
        assertEquals("13800138000", login2.phone)
    }

    @Test
    fun wrongCodeFails() {
        mockMvc.post("/auth/sms/send") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"phone":"13800138001"}"""
        }.andExpect { status { isOk() } }
        mockMvc.post("/auth/sms/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"phone":"13800138001","code":"000000"}"""
        }.andExpect { status { isBadRequest() } }
    }

    private fun login(phone: String, code: String): AuthResponse {
        val json = mockMvc.post("/auth/sms/login") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(SmsLoginRequest(phone = phone, code = code))
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return mapper.readValue(json)
    }
}
```

- [ ] **Step 3: Run — expect FAIL**

```bash
./gradlew test --tests com.renovation.ledger.server.auth.SmsLoginTest
```

- [ ] **Step 4: Implement DTOs + SmsCodeStore + AuthService methods**

Add to `AuthDtos.kt`:

```kotlin
data class SmsSendRequest(val phone: String)
data class SmsSendResponse(val expiresInSec: Long, val code: String? = null)
data class SmsLoginRequest(val phone: String, val code: String)
```

`SmsCodeStore.kt`:

```kotlin
package com.renovation.ledger.server.auth

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class SmsCodeStore {
    data class Entry(val code: String, val expiresAtMs: Long)

    private val map = ConcurrentHashMap<String, Entry>()

    fun put(phone: String, code: String, ttlSeconds: Long) {
        map[phone] = Entry(code, System.currentTimeMillis() + ttlSeconds * 1000)
    }

    fun consume(phone: String, code: String): Boolean {
        val entry = map[phone] ?: return false
        if (entry.expiresAtMs < System.currentTimeMillis()) {
            map.remove(phone)
            return false
        }
        if (entry.code != code) return false
        map.remove(phone)
        return true
    }
}
```

In `AuthService` inject `SmsCodeStore` and:

```kotlin
@Value("\${app.sms.return-code-in-response:false}") private val returnCodeInResponse: Boolean,
@Value("\${app.sms.ttl-seconds:300}") private val smsTtlSeconds: Long,
```

```kotlin
fun sendSms(request: SmsSendRequest): SmsSendResponse {
    val phone = normalizePhone(request.phone)
    if (!phone.matches(Regex("^1\\d{10}$"))) {
        throw ApiException(400, "BAD_REQUEST", "手机号格式不正确")
    }
    if (!returnCodeInResponse) {
        throw ApiException(501, "NOT_IMPLEMENTED", "正式环境短信未开通")
    }
    val code = (100000..999999).random().toString()
    smsCodeStore.put(phone, code, smsTtlSeconds)
    return SmsSendResponse(
        expiresInSec = smsTtlSeconds,
        code = code,
    )
}

@Transactional
fun loginSms(request: SmsLoginRequest): AuthResponse {
    val phone = normalizePhone(request.phone)
    if (!smsCodeStore.consume(phone, request.code.trim())) {
        throw ApiException(400, "BAD_REQUEST", "验证码错误或已过期")
    }
    val existing = users.findByPhone(phone)
    val user = existing ?: users.save(UserEntity(nickname = phone.takeLast(4), phone = phone))
    return toAuth(user)
}

private fun normalizePhone(raw: String): String = raw.trim()
```

Wire controller:

```kotlin
@PostMapping("/auth/sms/send")
fun smsSend(@RequestBody request: SmsSendRequest): SmsSendResponse = authService.sendSms(request)

@PostMapping("/auth/sms/login")
fun smsLogin(@RequestBody request: SmsLoginRequest): AuthResponse = authService.loginSms(request)
```

Ensure `ApiExceptionHandler` maps status from `ex.status` (existing) so 400/501 work.

- [ ] **Step 5: Run SmsLoginTest — expect PASS**

```bash
./gradlew test --tests com.renovation.ledger.server.auth.SmsLoginTest
```

---

### Task 3: Android CloudEnv + error copy + debug panel (no USB)

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/data/remote/CloudEnv.kt`
- Modify: `app/src/main/java/com/renovation/ledger/data/remote/ApiErrorMessages.kt`
- Modify: `app/src/main/java/com/renovation/ledger/ui/debug/DebugCloudViewModel.kt`
- Modify: `app/src/main/java/com/renovation/ledger/ui/debug/DebugCloudScreen.kt`
- Modify: `app/src/main/java/com/renovation/ledger/di/NetworkModule.kt` (Retrofit baseUrl uses `CloudEnv.defaultUrl()`)

- [ ] **Step 1: Change CloudEnv**

Replace `DEV_URL` constant usage so DEV resolves to LAN:

```kotlin
object CloudEnv {
    val DEV_URL: String
        get() = DEV_LAN_URL
    const val PROD_URL = "https://api.renovation-ledger.app/"
    val DEV_LAN_URL: String
        get() = BuildConfig.DEV_LAN_URL.let { if (it.endsWith("/")) it else "$it/" }
    // ... kindOf / urlOf unchanged: Kind.DEV -> DEV_URL
    fun isLegacyDebugDefault(url: String): Boolean {
        val bare = url.trim().trimEnd('/')
        return bare == "http://10.0.2.2:8080" ||
            bare == "http://127.0.0.1:8080" ||
            bare == "http://127.0.0.1:18080"
    }
}
```

When `UserPrefs.serverBaseUrl` hits legacy defaults, it already remaps via `isLegacyDebugDefault` → `defaultUrl()` (= LAN).

- [ ] **Step 2: Rewrite ApiErrorMessages connection strings**

```kotlin
is SocketTimeoutException ->
    "连接超时。请确认电脑服务已启动，且手机与电脑在同一局域网"
is ConnectException, is UnknownHostException ->
    "无法连接服务器。请确认服务器地址为电脑局域网 IP，且防火墙已放行端口"
// in fromBody 5xx branch: drop "adb reverse" suffix
// statusFallback:
408, 504 -> "连接超时。请确认电脑服务已启动，且地址为电脑局域网 IP"
502, 503 -> "服务器网关错误（$code）。请检查服务器地址与电脑服务是否运行"
```

- [ ] **Step 3: Debug UI**

- Delete `DebugDevChannel.USB` and `useUsbForward()`.
- `resolveDevChannel`: only LAN vs CUSTOM when env=DEV.
- `setEnv(DEV)` message: `"已切换到开发环境（电脑局域网）"`.
- `DebugCloudScreen`: remove USB chip and adb reverse text; show only LAN chip optional or just current URL.
- `ping()` will call `ledgerSync.pingHealth()` after Task 4; temporarily keep compiling by renaming in Task 4.

- [ ] **Step 4: NetworkModule Retrofit `.baseUrl(CloudEnv.defaultUrl())`**

Keep localhost proxy bypass (harmless if user pastes 127.0.0.1 manually).

- [ ] **Step 5: Compile check**

```bash
cd /Users/beike/Projects/renovation-ledger
./gradlew :app:compileDebugKotlin
```

Expected: SUCCESS (or only errors from still-missing pingHealth — fix in Task 4).

---

### Task 4: Android API + LedgerSyncRepository SMS / health / logout

**Files:**
- Modify: `ApiModels.kt`, `LedgerApi.kt`, `LedgerSyncRepository.kt`
- Modify: `data/auth/WeChatAppAuth.kt` message strings

- [ ] **Step 1: DTOs + API**

```kotlin
data class SmsSendRequestDto(val phone: String)
data class SmsSendResponseDto(val expiresInSec: Long, val code: String? = null)
data class SmsLoginRequestDto(val phone: String, val code: String)
data class HealthResponseDto(val ok: Boolean = false)
```

```kotlin
@GET("health")
suspend fun health(): HealthResponseDto

@POST("auth/sms/send")
suspend fun smsSend(@Body body: SmsSendRequestDto): SmsSendResponseDto

@POST("auth/sms/login")
suspend fun smsLogin(@Body body: SmsLoginRequestDto): AuthResponseDto
```

Keep `devLogin` on `LedgerApi` for now (unused by UI) or delete if no references after voice removal — prefer delete once Task 6 removes callers.

- [ ] **Step 2: Repository methods**

```kotlin
suspend fun pingHealth(): String {
    val res = apiCall { api.health() }
    if (!res.ok) error("服务异常")
    return "连通成功"
}

suspend fun sendSmsCode(phone: String): SmsSendResponseDto =
    apiCall { api.smsSend(SmsSendRequestDto(phone.trim())) }

suspend fun smsLogin(phone: String, code: String) {
    val res = apiCall { api.smsLogin(SmsLoginRequestDto(phone.trim(), code.trim())) }
    userPrefs.setJwt(res.token, res.userId, res.phone)
    userPrefs.setNickname(res.nickname)
}

suspend fun logout() {
    userPrefs.setJwt(null, null)
}

// Remove pingDevLogin or make it call pingHealth
// Remove public devLogin used by Mine / voice (delete method after Task 6)
```

`WeChatAppAuth.sendAuth` empty AppId message:

```kotlin
if (appId().isEmpty()) return "微信登录暂不可用，请使用手机号登录"
```

- [ ] **Step 3: Wire DebugCloudViewModel.ping → pingHealth**

- [ ] **Step 4: compileDebugKotlin — expect SUCCESS**

---

### Task 5: Android Login page + Mine + Nav

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/ui/login/LoginViewModel.kt`
- Create: `app/src/main/java/com/renovation/ledger/ui/login/LoginScreen.kt`
- Modify: `AppNav.kt`, `MineScreen.kt`, `MineViewModel.kt`

- [ ] **Step 1: LoginViewModel**

```kotlin
enum class LoginTab { WECHAT, PHONE }

data class LoginUiState(
    val tab: LoginTab = LoginTab.PHONE,
    val phone: String = "",
    val code: String = "",
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val ledgerSync: LedgerSyncRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(LoginUiState())
    val uiState = _ui.asStateFlow()

    fun selectTab(tab: LoginTab) { _ui.update { it.copy(tab = tab) } }
    fun setPhone(v: String) { _ui.update { it.copy(phone = v.filter { ch -> ch.isDigit() }.take(11) ) } }
    fun setCode(v: String) { _ui.update { it.copy(code = v.filter { ch -> ch.isDigit() }.take(6) ) } }
    fun clearMessage() { _ui.update { it.copy(message = null) } }

    fun sendCode() {
        val phone = _ui.value.phone
        if (phone.length != 11) {
            _ui.update { it.copy(message = "请输入11位手机号") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            runCatching { ledgerSync.sendSmsCode(phone) }
                .onSuccess { res ->
                    _ui.update {
                        it.copy(
                            busy = false,
                            code = res.code.orEmpty().ifBlank { it.code },
                            message = if (res.code.isNullOrBlank()) "验证码已发送" else "已填入验证码",
                        )
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(busy = false, message = ApiErrorMessages.fromThrowable(e)) }
                }
        }
    }

    fun loginPhone(onSuccess: () -> Unit) {
        val s = _ui.value
        if (s.phone.length != 11 || s.code.isBlank()) {
            _ui.update { it.copy(message = "请填写手机号与验证码") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            runCatching { ledgerSync.smsLogin(s.phone, s.code) }
                .onSuccess {
                    _ui.update { it.copy(busy = false) }
                    onSuccess()
                }
                .onFailure { e ->
                    _ui.update { it.copy(busy = false, message = ApiErrorMessages.fromThrowable(e)) }
                }
        }
    }

    fun wechatLogin(activity: Activity?) {
        if (activity == null) {
            _ui.update { it.copy(message = "微信登录暂不可用，请使用手机号登录") }
            return
        }
        val err = WeChatAppAuth.sendAuth(activity)
        if (err != null) _ui.update { it.copy(message = err) }
    }
}
```

- [ ] **Step 2: LoginScreen UI**

Compose screen with:

- Top bar title「登录」+ back
- Segmented buttons 微信 / 手机号
- Phone: `ClearableOutlinedTextField` phone + code,「获取验证码」,「登录」
- WeChat: single「微信登录」button
- Collect `message` → Toast

Match existing Mine styling (Material3, full-width buttons). Prefer default tab **PHONE** (WeChat AppId often empty).

- [ ] **Step 3: AppNav**

```kotlin
data object Login : Route("login")
```

Add `composable(Route.Login.path) { LoginScreen(onBack = { navController.popBackStack() }, onLoggedIn = { navController.popBackStack() }) }`  
Pass `navController` into `MineScreen` or use callbacks already used by other screens — follow existing pattern for `Settings` navigation from Mine.

- [ ] **Step 4: Mine changes**

- Remove Debug「开发登录」block and inline「微信登录」button.
- Unauthenticated: `Button("去登录") { navigate(Route.Login.path) }`
- Authenticated: add `OutlinedButton("退出登录") { viewModel.logout() }`
- Hide「绑定手机号」when `phone` non-blank (already).
- `MineViewModel.logout()` → `ledgerSync.logout()`; remove `devLogin()`; remove/adjust `wechatLogin` if unused.
- After WeChat success still handled in `WXEntryActivity` → user may open login page, authorize, then back; optional: if jwt becomes non-null while on Login, auto pop — nice-to-have, not required if user navigates back manually after toast.

- [ ] **Step 5: Install via oneClickSetup when implementing** (preference yes)

```bash
cd /Users/beike/Projects/renovation-ledger && sh oneClickSetup
```

---

### Task 6: Remove Android product「开发登录」+ voice tool

**Files:**
- Delete or gut: `voice/tool/executors/DevLoginExecutor.kt`
- Modify: `voice/di/VoiceModule.kt` (unregister)
- Modify: `voice` tests that list `dev_login` schema if any
- Remove `LedgerSyncRepository.devLogin` + `LedgerApi.devLogin` + `DevLoginRequestDto` if unused
- Grep `开发登录|devLogin|dev-login` under `app/` and clear product strings

- [ ] **Step 1: Grep and remove all product references**

```bash
rg -n "devLogin|开发登录|DevLogin" app/src
```

- [ ] **Step 2: Update voice tool registry / tests so suites pass**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.renovation.ledger.voice.*'
```

---

### Task 7: Miniprogram login page + mine + sync

**Files:**
- Create: `pages/login/login.js`, `login.json`, `login.wxml`, `login.wxss`
- Modify: `app.json`, `pages/mine/mine.js`, `mine.wxml`, `utils/sync.js`
- Modify debug page if it mentions 开发登录 / adb

- [ ] **Step 1: sync.js API helpers**

```javascript
async function sendSmsCode(phone) {
  return api.request('/auth/sms/send', {
    method: 'POST',
    data: { phone: String(phone || '').trim() },
    token: '',
  })
}

async function smsLogin(phone, code) {
  const store = require('./store')
  const res = await api.request('/auth/sms/login', {
    method: 'POST',
    data: { phone: String(phone || '').trim(), code: String(code || '').trim() },
    token: '',
  })
  store.setPrefs({ jwt: res.token, cloudUserId: res.userId, phone: res.phone || phone })
  if (res.nickname) store.setPrefs({ nickname: res.nickname })
  return res
}

async function logout() {
  const store = require('./store')
  store.setPrefs({ jwt: '', cloudUserId: '', phone: '' })
}

async function pingHealth() {
  const res = await api.request('/health', { method: 'GET', token: '' })
  if (!res || !res.ok) throw { message: '服务异常' }
  return '连通成功'
}

// Remove exports.devLogin usage from pages; can leave function deleted
```

Export the new functions; remove `devLogin` from exports once pages stop calling it.

- [ ] **Step 2: Register page in app.json** (non-tab)

```json
"pages/login/login"
```

Place after mine or near settings.

- [ ] **Step 3: login page**

- Data: `tab: 'phone'|'wechat'`, `phone`, `code`, busy flags
- Phone flow: `sendSmsCode` → if `res.code` set `code` automatically → `smsLogin` → `wx.navigateBack`
- WeChat: reuse existing `wechatLogin` from sync (move call into login page)
- UI: two tabs + forms; reuse mine button classes where possible

- [ ] **Step 4: mine.wxml / mine.js**

- Replace login buttons with `去登录` → `wx.navigateTo({ url: '/pages/login/login' })`
- Remove `devLogin` handler and develop-only button
- Add `退出登录` → `sync.logout()` + refresh state
- Need-login actions: if no jwt, navigate to login or toast

- [ ] **Step 5: Debug ping** use `pingHealth` if debug page had connectivity test via dev-login

---

### Task 8: Manual multi-device acceptance (checklist)

Do not automate; execute against PC server (`application-local`).

- [ ] **Step 1:** Start server on PC; note LAN IP; confirm phones/miniprogram baseUrl match (`http://<ip>:8080/`).

- [ ] **Step 2 — Scenario 1:** Phone A login `13800138000` → upload ledger → Phone B + MP same phone login → see ledger → edit item on A → reopen B/MP → see update.

- [ ] **Step 3 — Scenario 2:** Phone B logout → other phone login → join invite from A → edit → A/MP reopen → see update.

- [ ] **Step 4:** Confirm no「开发登录」on Mine; Debug has no USB/adb reverse; send code auto-fills on test server.

---

## Spec coverage self-check

| Spec item | Task |
|-----------|------|
| Dedicated login page WeChat/Phone | 5, 7 |
| Mine 去登录 / 退出 | 5, 7 |
| Optional login | 5 (no splash gate) |
| SMS send returns code (test) + auto-fill | 2, 5, 7 |
| Prod no code / 未开通 | 2 (`return-code-in-response: false`) |
| Same phone = same user | 2 test |
| DEV default LAN; no USB/adb | 3 |
| Remove 开发登录 product | 5, 6, 7 |
| `/health` ping | 1, 4, 7 |
| Sync/invite unchanged + acceptance | 8 |
| Dual platform | 5–7 |

## Placeholder scan

No TBD steps; commit steps omitted by policy.

---

**Plan complete and saved to** `docs/superpowers/plans/2026-08-21-phone-login-lan-dev.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — execute tasks in this session with checkpoints  

Which approach?
