# 头像上云（账号资料）

**日期：** 2026-08-24  
**状态：** 已实现（2026-08-24）  
**端：** `renovation-ledger-server` + Android + 微信小程序  
**关联：** 昵称跟账号 `2026-08-21-nickname-follows-account-design.md`

## 1. 问题

头像只存在本机（Android `filesDir/avatars/`、小程序 `USER_DATA_PATH/avatars/`），同账号多设备无法共享；服务器 `users.avatarUrl` 已有字段但未接入。

## 2. 目标与验收

1. 账号 A 在设备 1 换头像 → 上传成功 → 设备 2 同账号登录或 `GET /me` 后显示同一头像。
2. 未登录换头像仍只写本机；登录后以**云端头像**为准覆盖本地（游客头像不自动上传）。
3. 退出登录清除本地头像；再登另一账号不串图。
4. 已登录「清除头像」同步清空云端与本地。
5. Android 与小程序行为对等。

## 3. 非目标

- 成员列表展示他人头像。
- 对象存储（OSS/COS）。
- 票据 / 分类图标上云。

## 4. API

### 4.1 `GET /me` / 登录响应

增加可空 `avatarUrl`（相对路径，如 `/avatars/{uuid}.jpg`）。客户端展示时拼 `baseUrl + avatarUrl`。

### 4.2 `POST /me/avatar`

- 鉴权：Bearer JWT。
- `multipart/form-data`，字段名 `file`。
- 仅 jpeg/png；最大 2MB。
- 落盘到配置目录（默认 `./data/avatars`），文件名 UUID；更新 `users.avatarUrl`；删除旧文件（若存在）。
- 响应：同 `GET /me`。

### 4.3 `DELETE /me/avatar`

- 鉴权：Bearer JWT。
- 清空 `avatarUrl` 并删除文件；响应同 `GET /me`（`avatarUrl` 为 null）。

### 4.4 `GET /avatars/**`

- **公开读**（UUID 文件名，不暴露 userId）；SecurityConfig `permitAll`。

### 4.5 `PATCH /me`

仍只改昵称；不接收 `avatarUrl` 字符串。

## 5. 客户端行为

| 场景 | 行为 |
|------|------|
| 未登录换头像 | 只写本机 `avatarPath` |
| 已登录换头像 | 压缩 → `POST /me/avatar` → 成功后写本地（本机缓存文件或 URL 字符串） |
| 登录 / `fetchMe` | 用服务器 `avatarUrl` 覆盖本地展示路径 |
| 退出 | 清 `avatarPath` |
| 清除 | 已登录先 `DELETE`，再清本地；未登录只清本地 |

小程序：远程 URL 下载到 `USER_DATA_PATH/avatars/` 再展示，避免开发态 LAN 域名限制。

Android：`ProfileAvatar` 支持 `http(s)` URL（Coil）与本地文件路径。

## 6. 决议

- 账号规则：**A**（对齐昵称）。
- 存储：**本机服务器文件**，非 OSS。
