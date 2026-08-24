# 新用户默认昵称 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 服务端首次建号时把新用户昵称写成 `momo-` + 手机号或微信 openid 后 4 位；已有用户、游客、PATCH 空串、开发登录均不改。

**Architecture:** 抽出纯函数 `DefaultNickname.fromSuffix`；`AuthService.loginSms` / `loginWeChat` 仅在 `users.save` 新建时写入。Android / 小程序不改生成逻辑。

**Tech Stack:** Kotlin + Spring Boot + JUnit 5（`renovation-ledger-server`）。

**Spec:** `docs/superpowers/specs/2026-08-24-new-user-default-nickname-design.md`

**Git：** 本仓库禁止 agent 自动 commit；各 Task 末尾「Commit」步骤一律跳过。

---

## File map

| 路径 | 职责 |
|------|------|
| `renovation-ledger-server/src/main/kotlin/com/renovation/ledger/server/auth/DefaultNickname.kt` | `momo-` + trim 后后 4 位 |
| `renovation-ledger-server/src/test/kotlin/com/renovation/ledger/server/auth/DefaultNicknameTest.kt` | 纯函数单测 |
| `renovation-ledger-server/src/main/kotlin/com/renovation/ledger/server/auth/AuthService.kt` | 短信/微信建号用该函数 |
| `renovation-ledger-server/src/test/kotlin/com/renovation/ledger/server/auth/SmsLoginTest.kt` | 短信首次/再次登录昵称 |
| `renovation-ledger-server/src/test/kotlin/com/renovation/ledger/server/auth/MeProfileTest.kt` | 期望从 `"8999"` 改为 `"momo-8999"` |
| `renovation-ledger-server/src/test/kotlin/com/renovation/ledger/server/auth/AuthServiceTest.kt` | 微信首次/再次登录昵称 |

---

### Task 1: DefaultNickname 纯函数（TDD）

**Files:**
- Create: `/Users/beike/Projects/renovation-ledger-server/src/test/kotlin/com/renovation/ledger/server/auth/DefaultNicknameTest.kt`
- Create: `/Users/beike/Projects/renovation-ledger-server/src/main/kotlin/com/renovation/ledger/server/auth/DefaultNickname.kt`

- [x] **Step 1: 写失败单测**

```kotlin
package com.renovation.ledger.server.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultNicknameTest {
    @Test
    fun phoneLastFour() {
        assertEquals("momo-8000", DefaultNickname.fromSuffix("13800138000"))
        assertEquals("momo-8999", DefaultNickname.fromSuffix("13800138999"))
    }

    @Test
    fun openidLastFour() {
        assertEquals("momo-id_1", DefaultNickname.fromSuffix("mp_openid_1"))
        assertEquals("momo-PfL2", DefaultNickname.fromSuffix("oXXXXPfL2"))
    }

    @Test
    fun shorterThanFourUsesAll() {
        assertEquals("momo-ab", DefaultNickname.fromSuffix("ab"))
        assertEquals("momo-", DefaultNickname.fromSuffix("   "))
    }
}
```

- [x] **Step 2: 跑测确认失败**

Run: `cd /Users/beike/Projects/renovation-ledger-server && ./gradlew test --tests com.renovation.ledger.server.auth.DefaultNicknameTest`

Expected: FAIL（`DefaultNickname` 不存在）

- [x] **Step 3: 最小实现**

```kotlin
package com.renovation.ledger.server.auth

object DefaultNickname {
    fun fromSuffix(raw: String): String = "momo-" + raw.trim().takeLast(4)
}
```

- [x] **Step 4: 跑测确认通过**

Run: `cd /Users/beike/Projects/renovation-ledger-server && ./gradlew test --tests com.renovation.ledger.server.auth.DefaultNicknameTest`

Expected: PASS

- [x] **Step 5: Commit** — 跳过

---

### Task 2: 短信建号昵称 + MeProfileTest

**Files:**
- Modify: `/Users/beike/Projects/renovation-ledger-server/src/test/kotlin/com/renovation/ledger/server/auth/SmsLoginTest.kt`
- Modify: `/Users/beike/Projects/renovation-ledger-server/src/test/kotlin/com/renovation/ledger/server/auth/MeProfileTest.kt`
- Modify: `/Users/beike/Projects/renovation-ledger-server/src/main/kotlin/com/renovation/ledger/server/auth/AuthService.kt`（`loginSms` 建号处）

- [x] **Step 1: 写失败断言**

在 `SmsLoginTest.sendReturnsCodeThenLoginSameUserTwice` 里，`login1` 之后、`login2` 断言处增加：

```kotlin
        assertEquals("momo-8000", login1.nickname)
        assertEquals("momo-8000", login2.nickname)
        assertEquals(login1.nickname, login2.nickname)
```

`MeProfileTest.getAndPatchNickname` 把：

```kotlin
        assertEquals("8999", meBefore.nickname)
```

改为：

```kotlin
        assertEquals("momo-8999", meBefore.nickname)
```

`PATCH` 空串仍为 `"我"` 的断言不要改。

- [x] **Step 2: 跑测确认失败**

Run: `cd /Users/beike/Projects/renovation-ledger-server && ./gradlew test --tests com.renovation.ledger.server.auth.SmsLoginTest --tests com.renovation.ledger.server.auth.MeProfileTest`

Expected: FAIL（实际昵称仍是 `"8000"` / `"8999"`）

- [x] **Step 3: 最小实现**

`AuthService.loginSms` 建号从：

```kotlin
        val user = existing ?: users.save(
            UserEntity(
                nickname = phone.takeLast(4),
                phone = phone,
            ),
        )
```

改为：

```kotlin
        val user = existing ?: users.save(
            UserEntity(
                nickname = DefaultNickname.fromSuffix(phone),
                phone = phone,
            ),
        )
```

- [x] **Step 4: 跑测确认通过**

Run: `cd /Users/beike/Projects/renovation-ledger-server && ./gradlew test --tests com.renovation.ledger.server.auth.SmsLoginTest --tests com.renovation.ledger.server.auth.MeProfileTest`

Expected: PASS

- [x] **Step 5: Commit** — 跳过

---

### Task 3: 微信建号昵称

**Files:**
- Modify: `/Users/beike/Projects/renovation-ledger-server/src/test/kotlin/com/renovation/ledger/server/auth/AuthServiceTest.kt`
- Modify: `/Users/beike/Projects/renovation-ledger-server/src/main/kotlin/com/renovation/ledger/server/auth/AuthService.kt`（`loginWeChat` 新建 `UserEntity()` 处）

该类 stub 的 openid 固定为 `"mp_openid_1"`（后 4 位 `"id_1"`）。用**新的、该测试专用**的 login `code`，避免与同 JVM 里其它 `@SpringBootTest` 已创建的 wechat 用户撞车。若本类已有 `wechatLoginTwiceSameOpenidSameUser` 用 `c1`/`c2`，可在同一测试里直接断言昵称；若该用户可能已被先前跑过的测试建过且昵称是 `"我"`，则新增独立测试、用不同 stub openid。

推荐：给本测试类 stub 换成按 code 生成唯一 openid，并新增测试方法（保留原 twice-same-user 行为）。

- [x] **Step 1: 写失败测试**

把 `AuthServiceTest` 的 stub 和测试改成：

```kotlin
package com.renovation.ledger.server.auth

import com.renovation.ledger.server.wechat.StubWeChatClient
import com.renovation.ledger.server.wechat.WeChatClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@SpringBootTest
class AuthServiceTest {
    @TestConfiguration
    class WeChatStubConfig {
        @Bean
        @Primary
        fun weChatClient(): WeChatClient = StubWeChatClient(
            openidFor = { code -> "mp_openid_$code" },
            unionid = null,
        )
    }

    @Autowired lateinit var authService: AuthService

    @Test
    fun wechatLoginTwiceSameOpenidSameUser() {
        val first = authService.loginWeChat(WeChatLoginRequest(code = "nick_c1", client = "mp"))
        val second = authService.loginWeChat(WeChatLoginRequest(code = "nick_c1", client = "mp"))
        assertEquals(first.userId, second.userId)
        assertNotNull(first.token)
        assertEquals("momo-k_c1", first.nickname)
        assertEquals(first.nickname, second.nickname)
    }
}
```

说明：`mp_openid_nick_c1`.takeLast(4) == `"k_c1"`。`unionid = null`，避免和其它测试的 `union_1` 合并到旧用户（旧用户昵称可能是 `"我"`）。

- [x] **Step 2: 跑测确认失败**

Run: `cd /Users/beike/Projects/renovation-ledger-server && ./gradlew test --tests com.renovation.ledger.server.auth.AuthServiceTest`

Expected: FAIL（新建用户昵称仍是 `"我"`）

- [x] **Step 3: 最小实现**

`AuthService.loginWeChat` 的 `else` 分支从：

```kotlin
            val created = users.save(UserEntity())
```

改为：

```kotlin
            val created = users.save(UserEntity(nickname = DefaultNickname.fromSuffix(session.openid)))
```

不要改 `bindPhone`、`devLogin`、`updateMe`。

- [x] **Step 4: 跑测确认通过**

Run: `cd /Users/beike/Projects/renovation-ledger-server && ./gradlew test --tests com.renovation.ledger.server.auth.AuthServiceTest --tests com.renovation.ledger.server.auth.BindPhoneTest --tests com.renovation.ledger.server.auth.SmsLoginTest --tests com.renovation.ledger.server.auth.MeProfileTest --tests com.renovation.ledger.server.auth.DefaultNicknameTest`

Expected: 全部 PASS

- [x] **Step 5: Commit** — 跳过

---

## Spec coverage

| Spec 项 | Task |
|---------|------|
| 短信新用户 `momo-` + 后 4 位 | 1 + 2 |
| 微信新用户 `momo-` + openid 后 4 位 | 1 + 3 |
| 已有用户再登录不改昵称 | 2（sms 二次）、3（wechat 二次） |
| 绑手机不改昵称 | 不改 `bindPhone`；Task 3 跑 `BindPhoneTest` 回归 |
| 游客 / 退出仍 `"我"` | 客户端不改 |
| PATCH 空串仍 `"我"` | Task 2 保留 `MeProfileTest` 断言 |
| 开发登录仍用 label | 不改 `devLogin` |
| Android / 小程序不另生成 | 无客户端 Task |

## 客户端缺口

无。登录 / `GET /me` 已写入服务器 `nickname`。需部署/重启 `renovation-ledger-server` 后，新注册才生效。
