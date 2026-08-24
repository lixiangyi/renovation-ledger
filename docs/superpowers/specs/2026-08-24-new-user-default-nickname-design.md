# 新用户默认昵称

**日期：** 2026-08-24  
**状态：** 已确认  
**端：** `renovation-ledger-server`（建号时写入）；Android / 小程序沿用登录返回的 `nickname`，不另生成  
**相关：** `2026-08-21-nickname-follows-account-design.md`

## 1. 问题

新用户云端昵称现状：

- 短信建号：`phone.takeLast(4)`（如 `8000`）
- 微信建号：`UserEntity()` 默认 `"我"`

希望新用户默认为 `momo-` 前缀，便于识别且不暴露完整手机号 / openid。

## 2. 已确认决策

| 项 | 选择 |
|----|------|
| 短信新用户 | `momo-` + 手机号后 4 位（`13800138000` → `momo-8000`） |
| 微信新用户 | `momo-` + **openid 后 4 位**（不是完整 openid） |
| 已有用户 | **不刷**（再登录、绑手机、GET/PATCH `/me` 都不改已有昵称） |
| 生成位置 | 服务端 **首次建号** |
| 游客 / 退出 | 本地仍默认 `"我"`（未登录态，不是云账号默认名） |
| `PATCH /me` 空串 | 仍存 `"我"`（主动清空） |
| 开发登录 | 仍用 `label` 作昵称，本期不动 |

## 3. 规则

首次 `users.save` 时写入 `nickname`：

1. **短信** `loginSms`：不存在该手机号的用户 → `nickname = "momo-" + phone.takeLast(4)`，并设 `phone`。
2. **微信** `loginWeChat`：无 identity / unionid 命中、新建用户 → `nickname = "momo-" + session.openid.takeLast(4)`。
3. 已存在用户：原样 `toAuth`，不改 `nickname`。
4. 绑手机：只写 `phone`，不改 `nickname`。

openid 不足 4 位时用全部可用字符（正常微信 openid 远长于 4）。

抽一个小函数（如 `defaultNickname(suffix: String)`）避免两处拼错；suffix 为 trim 后的尾号片段。

## 4. 非目标

- 迁移历史用户（含仍为 `"我"` 或纯 4 位尾号的账号）
- 改客户端游客默认 `"我"`
- 头像、开发登录昵称

## 5. 测试

- 短信首次登录：`nickname == "momo-" + 后4位`；第二次登录同一手机号：昵称不变。
- 微信首次登录：`nickname == "momo-" + openid 后4位`；第二次同一 openid：用户与昵称不变。
- `PATCH /me` 空串仍为 `"我"`（`MeProfileTest` 现有断言保持）。

## 6. 客户端

无需改生成逻辑。登录 / `GET /me` 已把服务器 `nickname` 写入 prefs。
