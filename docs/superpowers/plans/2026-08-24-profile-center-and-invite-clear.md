# 个人中心页与换号邀请码清理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建独立「个人中心」页承载资料编辑与云同步（含退出登录）；总览头像进入该页；设置去掉资料模块；「我的」去掉云同步；退出/换号时清空邀请码等会话 UI。双端对等。

**Architecture:** 抽出纯函数 `SessionCloudUi` 判定「账号身份变化则清会话 UI」。Android 新增 `ProfileScreen`/`ProfileViewModel` + `Route.Profile`；从 Mine/Settings 迁出云同步与资料。小程序新增 `pages/profile`，同步裁剪 mine/settings，总览加头像入口。

**Tech Stack:** Kotlin + Jetpack Compose + Hilt（Android）；微信小程序 Page + `utils/sync`/`store`；JUnit 单测纯函数。

**Spec:** `docs/superpowers/specs/2026-08-24-profile-center-and-invite-clear-design.md`

**Git：** 本仓库禁止 agent 自动 commit；各 Task 末尾「Commit」步骤一律跳过。

---

## File map

| 路径 | 职责 |
|------|------|
| `app/.../domain/ledger/SessionCloudUi.kt` | 纯函数：是否因 userId 变化清会话 UI |
| `app/.../ui/common/ProfileAvatar.kt` | 共用圆形头像 Composable |
| `app/.../ui/profile/ProfileViewModel.kt` | 个人中心状态：资料 + 云同步 + 会话清理 |
| `app/.../ui/profile/ProfileScreen.kt` | 个人中心 UI |
| `app/.../ui/navigation/AppNav.kt` | 注册 Profile 路由；Overview/Mine 回调 |
| `app/.../ui/overview/OverviewScreen.kt` | TopBar 头像入口 |
| `app/.../ui/mine/MineScreen.kt` / `MineViewModel.kt` | 去掉云同步；资料卡进个人中心 |
| `app/.../ui/settings/SettingsScreen.kt` / `SettingsViewModel.kt` | 去掉资料卡 |
| `app/src/test/.../SessionCloudUiTest.kt` | 单测 |
| `miniprogram/pages/profile/*` | 新页 |
| `miniprogram/app.json` | 注册 profile |
| `miniprogram/pages/overview/*` | 头像入口 |
| `miniprogram/pages/mine/*` | 去云同步 |
| `miniprogram/pages/settings/*` | 去资料 |

---

### Task 1: SessionCloudUi 纯函数（TDD）

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/domain/ledger/SessionCloudUi.kt`
- Create: `app/src/test/java/com/renovation/ledger/SessionCloudUiTest.kt`

- [ ] **Step 1: 写失败单测**

```kotlin
package com.renovation.ledger

import com.renovation.ledger.domain.ledger.SessionCloudUi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCloudUiTest {
    @Test
    fun clear_when_user_changes() {
        assertTrue(SessionCloudUi.shouldClearSessionUi("u1", "u2"))
        assertTrue(SessionCloudUi.shouldClearSessionUi("u1", null))
        assertTrue(SessionCloudUi.shouldClearSessionUi(null, "u2"))
    }

    @Test
    fun keep_when_same_or_both_null() {
        assertFalse(SessionCloudUi.shouldClearSessionUi("u1", "u1"))
        assertFalse(SessionCloudUi.shouldClearSessionUi(null, null))
    }
}
```

- [ ] **Step 2: 跑测确认失败**

Run: `cd /Users/beike/Projects/renovation-ledger && ./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.SessionCloudUiTest`

Expected: FAIL（类不存在）

- [ ] **Step 3: 最小实现**

```kotlin
package com.renovation.ledger.domain.ledger

object SessionCloudUi {
    /** 账号身份变化（含登出/登入）时清邀请码等会话 UI。 */
    fun shouldClearSessionUi(previousUserId: String?, nextUserId: String?): Boolean {
        val prev = previousUserId?.trim()?.takeIf { it.isNotEmpty() }
        val next = nextUserId?.trim()?.takeIf { it.isNotEmpty() }
        return prev != next
    }
}
```

- [ ] **Step 4: 跑测确认通过**

同 Step 2；Expected: BUILD SUCCESSFUL，tests PASS

- [ ] **Step 5: Commit** — 跳过

---

### Task 2: 共用 ProfileAvatar

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/ui/common/ProfileAvatar.kt`
- Modify: `MineScreen.kt` — 删除私有 `ProfileAvatar`，改用 common
- Modify: `SettingsScreen.kt` — 删除私有 `SettingsAvatar`（Task 5 删资料卡时一并清理亦可）

- [ ] **Step 1: 新增共用组件**

从 `MineScreen.kt` 底部私有 `ProfileAvatar` 抽出到：

```kotlin
package com.renovation.ledger.ui.common

// imports: BitmapFactory, Image, Icon, CircleShape, Person, asImageBitmap, ContentScale, Dp, dp, remember, ...

@Composable
fun ProfileAvatar(
    avatarPath: String?,
    size: Dp = 56.dp,
    contentDescription: String = "头像",
) {
    val bitmap = remember(avatarPath) {
        avatarPath
            ?.takeIf { it.isNotBlank() }
            ?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = "默认头像",
            modifier = Modifier.size(size * 0.45f),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
```

- [ ] **Step 2: MineScreen 改用 `com.renovation.ledger.ui.common.ProfileAvatar`，删除本地私有函数**

- [ ] **Step 3: Commit** — 跳过

---

### Task 3: Android ProfileViewModel

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/ui/profile/ProfileViewModel.kt`

- [ ] **Step 1: 实现 ViewModel**

合并现 `MineViewModel` 云同步相关字段/方法与 `SettingsViewModel` 资料方法，并加会话清理：

```kotlin
@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userPrefs: UserPrefs,
    private val avatarStorage: AvatarStorage,
    private val projectRepository: ProjectRepository,
    private val ledgerSync: LedgerSyncRepository,
) : ViewModel() {

    private val actionMessage = MutableStateFlow<String?>(null)
    private val cloudBusyLabel = MutableStateFlow<String?>(null)
    private val lastInviteCode = MutableStateFlow<String?>(null)
    private var lastObservedUserId: String? = null

    // uiState: combine userProfile, jwt, phone, cloudBusy, lastInviteCode,
    // currentUnbound from observeProjectWithItems, actionMessage
    // 结构对齐原 MineUiState 中云同步+资料所需字段

    init {
        viewModelScope.launch {
            userPrefs.cloudUserId.collect { userId ->
                if (SessionCloudUi.shouldClearSessionUi(lastObservedUserId, userId)) {
                    resetSessionUi()
                }
                lastObservedUserId = userId
            }
        }
    }

    fun resetSessionUi() {
        lastInviteCode.value = null
        cloudBusyLabel.value = null
        // inviteInput 若放在 Screen remember，由 Screen 在 jwt/userId 变化时清空（见 Task 4）
    }

    fun logout() {
        viewModelScope.launch {
            ledgerSync.logout()
            resetSessionUi()
            actionMessage.value = "已退出登录"
        }
    }

    // 从 MineViewModel 迁入：
    // uploadCurrentLedger, createInvite, copyInviteShare, joinInvite, bindPhone
    // 从 SettingsViewModel 迁入：
    // saveNickname, updateAvatar, clearAvatar
}
```

要点：
- `createInvite` 成功后仍 `lastInviteCode.value = code` 并 `copyInviteShare`
- `logout` 与 `cloudUserId` collect **都**调用 `resetSessionUi`
- 剪贴板逻辑原样从 `MineViewModel.copyInviteShare` 复制

- [ ] **Step 2: Commit** — 跳过

---

### Task 4: Android ProfileScreen + 路由

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/ui/profile/ProfileScreen.kt`
- Modify: `app/src/main/java/com/renovation/ledger/ui/navigation/AppNav.kt`

- [ ] **Step 1: ProfileScreen**

结构：
1. TopBar 标题「个人中心」+ Back
2. Card「资料」：头像可点选图（`GetContent`）、清除头像、昵称输入+保存（逻辑同原 SettingsScreen）
3. Card「云同步」：整块从原 MineScreen 云同步 Card 迁入（去登录 / 退出 / 绑手机 / 上传 / 邀请码 / 加入）

`inviteInput` 用 `remember { mutableStateOf("") }`；另加：

```kotlin
LaunchedEffect(uiState.jwt, uiState.cloudUserId) {
    inviteInput = ""
}
```

（ViewModel 的 `uiState` 需暴露 `cloudUserId`，来自 `userPrefs.cloudUserId`）

- [ ] **Step 2: AppNav**

```kotlin
data object Profile : Route("profile")
```

在 `NavHost` 中：

```kotlin
composable(Route.Profile.path) {
    ProfileScreen(
        onBack = { navController.popBackStack() },
        onOpenLogin = { navController.navigate(Route.Login.path) },
    )
}
```

Overview 调用处加 `onOpenProfile`（Task 6）；Mine 加 `onOpenProfile`（Task 5）。

- [ ] **Step 3: Commit** — 跳过

---

### Task 5: 裁剪 Mine + Settings（Android）

**Files:**
- Modify: `MineScreen.kt`, `MineViewModel.kt`
- Modify: `SettingsScreen.kt`, `SettingsViewModel.kt`
- Modify: `AppNav.kt`（Mine 的 `onOpenProfile`）

- [ ] **Step 1: MineViewModel**

删除：`lastInviteCode`、`cloudBusyLabel`、`logout`/`createInvite`/`copyInviteShare`/`joinInvite`/`uploadCurrentLedger`/`bindPhone`、以及 uiState 中 `jwt`/`phone`/`lastInviteCode`/`cloudBusy*`/`currentUnbound`（若仅云同步使用）。保留账本/健康色/导入导出。若 `ledgerSync` 仅服务于云同步，可从构造器移除。

- [ ] **Step 2: MineScreen**

- 删除整块「云同步」Card  
- 顶部资料卡：`clickable(onClick = onOpenProfile)`；副文案改为「点击进入个人中心」  
- 去掉 `onOpenLogin`（若不再需要）或保留但无用则删  
- 参数增加 `onOpenProfile: () -> Unit`

- [ ] **Step 3: Settings**

- `SettingsScreen`：删除「资料」Card（头像+昵称）；仅留「版本」  
- `SettingsViewModel`：删除 `avatarStorage`、`saveNickname`/`updateAvatar`/`clearAvatar`、`projectRepository`/`ledgerSync`（若只为昵称）；`uiState` 可只留 `versionName` + 可选空 message

- [ ] **Step 4: AppNav Mine**

```kotlin
onOpenProfile = { navController.navigate(Route.Profile.path) },
```

- [ ] **Step 5: Commit** — 跳过

---

### Task 6: 总览头像入口（Android）

**Files:**
- Modify: `OverviewScreen.kt`
- Modify: `AppNav.kt`（Overview 的 `onOpenProfile`）

- [ ] **Step 1: OverviewScreen 签名增加 `onOpenProfile: () -> Unit`**

- [ ] **Step 2: TopBar actions**（搜索左侧加头像）

```kotlin
actions = {
    val profile = uiState.profile // 确认 OverviewUiState 已有 profile；若无则从 ViewModel 暴露 userPrefs.userProfile
    IconButton(onClick = onOpenProfile) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            ProfileAvatar(avatarPath = profile.avatarPath, size = 32.dp)
        }
    }
    IconButton(onClick = onOpenSearch) {
        Icon(Icons.Outlined.Search, contentDescription = "搜索")
    }
}
```

若 `OverviewUiState` 尚无 `profile`：在 `OverviewViewModel` 组装处已有 `core.profile`，补进最终 `OverviewUiState` 字段即可。

- [ ] **Step 3: AppNav**

```kotlin
OverviewScreen(
    ...
    onOpenProfile = { navController.navigate(Route.Profile.path) },
)
```

- [ ] **Step 4: Commit** — 跳过

---

### Task 7: 小程序 pages/profile

**Files:**
- Create: `pages/profile/profile.js`, `profile.wxml`, `profile.wxss`, `profile.json`
- Modify: `app.json` — pages 列表加入 `"pages/profile/profile"`（建议紧挨 settings 前）

- [ ] **Step 1: profile.json**

```json
{
  "navigationBarTitleText": "个人中心"
}
```

- [ ] **Step 2: profile.wxml**

两块 Card：
1. 资料：头像点击 `chooseAvatar`、清除、昵称 input（从 settings 迁）
2. 云同步：从 mine.wxml 云同步块原样迁入（登录/退出/绑手机/上传/邀请码/加入）

- [ ] **Step 3: profile.js**

合并 settings 的头像/昵称方法与 mine 的云同步方法。关键：

```javascript
data: {
  nickname: '',
  avatarPath: '',
  jwt: '',
  phone: '',
  cloudUserId: '',
  lastInviteCode: '',
  inviteInput: '',
  currentUnbound: true,
  // theme...
},

_lastCloudUserId: undefined,

refresh() {
  const state = store.getState()
  const prefs = state.prefs || {}
  const cloudUserId = prefs.cloudUserId || ''
  if (this._lastCloudUserId !== undefined &&
      require('../../utils/sessionCloudUi').shouldClearSessionUi(this._lastCloudUserId, cloudUserId)) {
    this.setData({ lastInviteCode: '', inviteInput: '' })
  }
  this._lastCloudUserId = cloudUserId
  // ... setData 其它字段；注意不要无条件用旧 lastInviteCode 覆盖刚清空的值
},

logout() {
  require('../../utils/sync').logout()
  this.setData({ lastInviteCode: '', inviteInput: '' })
  this._lastCloudUserId = ''
  this.refresh()
  wx.showToast({ title: '已退出登录', icon: 'success' })
},
```

- [ ] **Step 4: 小程序纯函数文件**

Create: `utils/sessionCloudUi.js`

```javascript
function shouldClearSessionUi(previousUserId, nextUserId) {
  const prev = (previousUserId || '').trim() || null
  const next = (nextUserId || '').trim() || null
  return prev !== next
}

module.exports = { shouldClearSessionUi }
```

（与 Android `SessionCloudUi` 语义一致；无 Jest 时可用手工对照或跳过自动测）

- [ ] **Step 5: profile.wxss** — 复用 mine/settings 相关 class（avatar、invite-code、field 等），按需 copy

- [ ] **Step 6: Commit** — 跳过

---

### Task 8: 裁剪小程序 mine / settings + 总览入口

**Files:**
- Modify: `pages/mine/mine.wxml`, `mine.js`
- Modify: `pages/settings/settings.wxml`, `settings.js`
- Modify: `pages/overview/overview.wxml`, `overview.js`, `overview.wxss`

- [ ] **Step 1: mine**

- wxml：删除云同步 Card；profile-card `bindtap="openProfile"`；文案「点击进入个人中心」  
- js：删除 logout/createInvite/joinInvite/upload 等云同步方法；加

```javascript
openProfile() {
  wx.navigateTo({ url: '/pages/profile/profile' })
},
```

- [ ] **Step 2: settings**

- wxml：删除「资料」Card；保留版本 + AI Key  
- js：删除 chooseAvatar/clearAvatar/昵称相关（若仅服务资料）

- [ ] **Step 3: overview 头像**

wxml `header-right`：

```xml
<view class="header-right">
  <image wx:if="{{avatarPath}}" class="header-avatar" src="{{avatarPath}}" mode="aspectFill" bindtap="openProfile" />
  <view wx:else class="header-avatar placeholder" bindtap="openProfile">{{nickname[0] || '我'}}</view>
  <text class="search-icon" bindtap="goSearch">🔍</text>
</view>
```

js `refresh`/`onShow` 里带上 `avatarPath`、`nickname`；

```javascript
openProfile() {
  wx.navigateTo({ url: '/pages/profile/profile' })
},
```

wxss：

```css
.header-avatar {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: #C8E6C9;
  ...
}
```

- [ ] **Step 4: Commit** — 跳过

---

### Task 9: 双端验证

- [ ] **Step 1: Android 单元测试**

Run: `./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.SessionCloudUiTest`

Expected: PASS

- [ ] **Step 2: Android 安装**

Run: `cd /Users/beike/Projects/renovation-ledger && sh oneClickSetup`（`block_until_ms` ≥ 600000）

Expected: `BUILD SUCCESSFUL`，adb install 成功

- [ ] **Step 3: 手工验收清单**

1. A 生成邀请码 → 退出 → 登录 B → 个人中心无旧码、加入框空  
2. 总览点头像 → 个人中心；可改头像/昵称  
3. 设置无资料模块  
4. 「我的」无云同步/退出；账本管理可用  
5. 小程序同样 1–4  

- [ ] **Step 4: Commit** — 跳过

---

## Spec coverage check

| Spec 要求 | Task |
|-----------|------|
| 新建个人中心页 | 3–4, 7 |
| 资料+云同步在个人中心 | 3–4, 7 |
| 「我的」去云同步 | 5, 8 |
| 「设置」去资料 | 5, 8 |
| 总览头像入口 | 6, 8 |
| 退出/换号清会话 UI | 1, 3, 7 |
| 双端对等 | 7–9 |
| 邀请码不持久化 | 3, 7（仅内存） |

## Placeholder / 一致性

- `shouldClearSessionUi` 双端同名同语义  
- 路由名 `profile` / 页路径 `pages/profile/profile`  
- 无 TBD  
