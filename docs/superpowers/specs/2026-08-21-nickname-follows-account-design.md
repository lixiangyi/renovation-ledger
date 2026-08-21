# 昵称跟随账号（云端资料）

**日期：** 2026-08-21  
**状态：** 已实现（2026-08-21）  
**端：** `renovation-ledger-server` + Android + 微信小程序  
**关联：** 登录与 JWT 见 `2026-08-21-phone-login-lan-dev-design.md`；云同步见 `2026-08-14-cloud-login-and-auto-sync-design.md`

## 1. 问题

昵称目前存在本机 prefs（Android DataStore / 小程序 `store.prefs`）：

- 登录时会写入服务器返回的 `nickname`；
- **设置里改昵称只改本地，不写回服务器**；
- **退出登录不清昵称**，换账号会串号；
- 同账号多设备无法共享昵称。

服务器 `users.nickname` 已有，邀请成员列表已读该字段，缺的是「当前用户读/改资料」与客户端编排。

## 2. 目标与验收

1. 账号 A 在设备 1 改昵称为「小明」→ 设备 2 用同一账号登录或重新进入后显示「小明」。
2. 退出登录后本地昵称恢复默认「我」（或空态展示为「我」），不再残留上一账号昵称。
3. 未登录时改昵称：仍只写本地（游客态），登录后以**账号云端昵称**为准覆盖本地（不把游客昵称自动上传覆盖云端，除非用户登录后在设置里再次保存）。
4. Android 与小程序行为对等。

## 3. 非目标（本期不做）

- 头像上云 / 跟账号。
- 账本名称改名同步到服务器（服务器已有 `ledgers.name` 与 `rename`，客户端改名未接；另开任务）。
- 冲突弹窗、CRDT；昵称以服务器最后一次成功 PATCH 为准。

## 4. API

### 4.1 `GET /me`

- **鉴权：** Bearer JWT。
- **响应：** 与登录资料对齐的精简结构，例如：

```json
{
  "userId": "...",
  "nickname": "小明",
  "phone": "13800001111"
}
```

- `phone` 可空。

### 4.2 `PATCH /me`

- **鉴权：** Bearer JWT。
- **请求体：**

```json
{ "nickname": "小明" }
```

- `nickname` trim 后若空 → 存为 `"我"`（与客户端默认一致）。
- **响应：** 同 `GET /me`（更新后的资料）。
- 更新 `users.nickname`；账本成员列表里展示的昵称随之变化（已有读 `User.nickname` 的路径）。

### 4.3 安全

- 仅能改自己的资料；`userId` 从 JWT 解析，不信任 body 里的 id。
- SecurityConfig：`/me` 需认证（与其它业务接口一致）。

## 5. 客户端行为

### 5.1 登录 / 绑手机

保持现有：`AuthResponse.nickname` → 写入本地 prefs。

### 5.2 退出登录

清 JWT / userId / phone 的同时：

- 本地 `nickname` 重置为 `"我"`（Android + 小程序）。

不清理本地账本数据（与现网退出行为一致）。

### 5.3 保存昵称（设置页）

1. 本地先校验非空（空则按「我」）。
2. **已登录：** `PATCH /me` → 成功后再写本地，并按现有逻辑同步账本 `memberNames` 里对应旧昵称；失败 toast，不写本地（或回滚草稿）。
3. **未登录：** 只写本地 + 成员名同步（现状）。

### 5.4 拉取资料

已登录时在以下时机 `GET /me` 并覆盖本地 nickname / phone（有则写）：

- Android：`refreshOnOpen`（与拉账本列表同节奏）；
- 小程序：等价的进前台 / 进「我的」刷新路径（与现有 `refreshOnOpen` 对齐）。

网络失败：保持本地缓存，不强制登出。

### 5.5 UI

设置页、我的页展示逻辑不变；仅数据来源变为「账号资料缓存」。

## 6. 实现落点（概要）

| 层 | 改动 |
|----|------|
| Server | `MeController` / `AuthService` 或 `UserService`：`getMe` / `updateMe`；测试 |
| Android | `LedgerApi` + DTO；`LedgerSyncRepository`：`fetchMe` / `updateNickname` / `logout` 清昵称；`SettingsViewModel.saveNickname`；`refreshOnOpen` 调 `fetchMe` |
| 小程序 | `api.js` / `sync.js`：对等；`settings` 保存；`logout` 清昵称 |

## 7. 测试要点

- Server：未登录 401；PATCH 后 GET 一致；空串变「我」。
- 双端：登录 → 改昵称 → 另一端登录/刷新看到新昵称；退出后显示「我」再登另一账号不串号。

## 8. 决议记录

- 用户选择方案 **B / API 方案 1**：`GET/PATCH /me` 云端昵称。
- 账本名称：服务器有，客户端改名未同步——**本期不做**。
