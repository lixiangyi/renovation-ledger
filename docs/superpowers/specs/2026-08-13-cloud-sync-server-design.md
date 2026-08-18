# 装修记账云同步后台设计

**日期：** 2026-08-13  
**状态：** 待实现  
**端：** 新仓库 Spring Boot 服务 + Android App + 微信小程序（一期双端都接）  
**栈：** Spring Boot + Kotlin + PostgreSQL  
**会话偏好：** Android 改码收尾执行 `sh oneClickSetup`

## 1. 目标

把装修记账从「本机账本」做成可公开的产品：用户用微信登录（手机号可选绑定），小程序与 Android 打开/下拉时与云端互通；一个账本可通过邀请码/链接让家人共同编辑（所有者 + 编辑者）；登录后可将本地账本一键上传为云账本。

账号与同步协议与客户端无关，为以后 Google Play、iOS、Google/Apple 登录留扩展位。第一期不实现这些登录方式。

## 2. 已确认决策

| 项 | 选择 |
|----|------|
| 产品定位 | 多用户产品（非仅自家一台服务） |
| 登录 | 微信即可用；手机号可选绑定 |
| 邀请 | 邀请码 / 链接 |
| 一期客户端 | 小程序 + Android 一起接 |
| 同步节奏 | 打开页面或下拉刷新，不追求真实时 |
| 权限 | 一个账本一个所有者 + 若干编辑者 |
| 本地数据 | 登录后一键上传为云账本 |
| 后台 | 自建 API（非微信云开发） |
| 实现栈 | Spring Boot + Kotlin + Postgres |

## 3. 第一期明确不做

- 真实时协作（对方正在输入立刻可见）
- 微信云开发 / BaaS 作主存储
- 短信验证码作为主登录；手机号不是使用前提
- 只读成员；按手机号搜索邀请
- iOS 客户端、Google/Apple 登录（表结构预留 `provider` 即可）
- 两个已有账号的手动合并
- 自动合并同步冲突（冲突时拒绝并让用户刷新）

## 4. 仓库与职责

| 仓库 | 职责 |
|------|------|
| 新建 `renovation-ledger-server` | API：认证、账本、同步、邀请、成员 |
| `renovation-ledger`（Android） | Room 继续作本地缓存；登录后调 API |
| `renovation-ledger-miniprogram` | `wx.setStorageSync` 继续作本地缓存；同样调 API |

请求路径：

```
小程序 / Android
  → HTTPS + JWT
  → Spring Boot
  → PostgreSQL
```

## 5. 现状（实现前约束）

两端今天都是本机、无登录、无云同步。跨设备只能 CSV。

Android：`renovation.db` Room，表 `projects`、`budget_items`、`payments`；标签与偏好在 DataStore，**整机一份**，不跟账本走。

小程序：`renovation_ledger_v1` 一份 JSON；付款嵌在 item 上。

金额单位：**分（整数）**。项状态由付款推导，没有独立 status 列。`Project.memberNames` / `Payment.createdBy` 是本机昵称，不是账号。

云端表结构对齐 Room 的账本 → 项 → 付款，并补用户、成员、邀请、账本级标签。

## 6. 账号与登录

### 6.1 用户

一条 `User` 表示一个人。登录方式挂在用户上：

| provider | 第一期 | 以后 |
|----------|--------|------|
| 微信 | 要（小程序 code + Android 开放平台） | — |
| 手机号 | 可选绑定 | 仍可选 |
| Google / Apple | 不实现，枚举预留 | 出海时加 |

小程序与 App 的微信 `openid` 可能不同：能拿到 `unionid` 则按 unionid 认同一人；否则分栏存小程序/App openid，**绑定同一手机号后视为同一 User**。

### 6.2 微信登录

1. 客户端取得微信 `code`
2. `POST /auth/wechat`
3. 服务端向微信换 openid（及 unionid，若有）
4. 已有用户签发 JWT；否则创建 User 再签发

JWT 有效期 **30 天**。过期后重新走微信登录。第一期不做独立 refresh token 体系。

### 6.3 手机号（可选）

- 不绑定也可建账本、邀请、同步
- 一个手机号只能绑定一个 User
- 用途：换微信时找回；把小程序身份与 App 身份合成一人
- 绑定冲突（号已绑别人）：拒绝，提示「该手机号已被使用」
- 小程序优先用微信手机号组件；Android 短信验证码可后置，但绑定接口语义与小程序相同

### 6.4 合并规则（第一期）

- 同一 openid/unionid → 进已有账号
- 绑定手机号时号已被占用 → 拒绝
- **不做**两个已有账号的手动合并

### 6.5 资料

可用微信昵称/头像作默认，用户可改。付款 `createdBy` 改为 **userId + 当时昵称快照**，多人账本里能看出是谁记的。

## 7. 账本、成员、邀请

### 7.1 账本

与本地 Project 对应：名称、预算项、付款。云端另有成员列表。标签（阶段/分类/空间及图标）**属于账本**，不再整机一份；同步后本地按当前云账本缓存。

### 7.2 角色

| 角色 | 能力 |
|------|------|
| 所有者 | 改数据；生成/作废邀请；踢人；转让所有者；删除账本 |
| 编辑者 | 改数据（项、付款、标签等）；不能管成员/邀请/删账本 |

一个账本 **恰好一个所有者**。创建者或一键上传者即为所有者。邀请加入者为编辑者。

### 7.3 邀请

- 所有者生成短码（如 6 位）+ 链接
- 有效期 **7 天**，可随时作废再生成
- 对方须先登录，再打开链接或输入短码 → 成为编辑者
- 已是成员：提示已在账本中，不重复加入
- 第一期不限制成员人数

### 7.4 踢人、退出、转让、删除

- 所有者可移除编辑者；被踢后本地缓存仍可能可见，再同步返回无权限
- 编辑者可自行退出
- 所有者转让给某编辑者后，自己变为编辑者
- 删除账本需所有者确认；与现有本地回收站对齐：**软删除**，所有者可在回收站恢复（保留 30 天后再硬删）

### 7.5 一键上传本地账本

- 每个本地账本上传一次 → 云账本，当前用户为所有者
- 成功后本地记录 `cloudLedgerId`，之后只走同步，禁止再当成新账本导入
- 已绑定 `cloudLedgerId` 的不得重复 `import`

## 8. 同步协议

### 8.1 时机

打开账本、从后台回到前台、用户下拉刷新。无长连接推送。

### 8.2 本地优先展示

Android 继续 Room，小程序继续本地 storage。云端为权威副本。

1. 打开/下拉 → `GET` 该账本完整数据，写入本地
2. 用户修改 → 先写本地 → 再上传
3. 失败（断网、401、409）→ 本地标待同步，提示用户，下次打开再试

未登录或离线：行为与今天一致，仅本地记账。登录联网后：未绑定云的走导入；已绑定的走同步。

### 8.3 粒度与版本

- **拉**：整本（账本信息 + 全部项 + 付款 + 标签）。装修数据量通常几十到几百条，第一期不做分页增量。
- **写**：按一条预算项（含其付款列表）提交，或单独提交账本设置/标签。避免两人改不同项时互相覆盖整本。
- 账本有整数 **`revision`**：每次成功写入 +1。项带 `updatedAt`（或项级版本）供冲突判断。

### 8.4 冲突（乐观锁）

提交携带客户端基于的 revision / 项版本。

- 服务端发现已被他人更新 → **HTTP 409**，返回最新数据
- 客户端用服务器数据覆盖该条本地，提示「该条已被其他人更新，请查看后再改」
- **不自动合并**金额等字段
- 两人改 **不同项**：均可成功
- 删除：重复删除视为成功；若版本过期则 409，刷新后再决定

### 8.5 权限校验

除登录外所有账本接口需 JWT。读/写须为该账本成员；邀请、踢人、转让、删账本仅所有者。

## 9. HTTP 接口轮廓

除 `/auth/wechat` 外均需 `Authorization: Bearer <jwt>`。金额字段为分（整数）。路径实现时可微调，语义不变。

| 用途 | 方法与路径 |
|------|------------|
| 微信登录 | `POST /auth/wechat` |
| 绑定手机号 | `POST /auth/bind-phone` |
| 账本列表 | `GET /ledgers` |
| 上传本地账本 | `POST /ledgers/import` |
| 拉一整本 | `GET /ledgers/{id}` |
| 改账本（名等） | `PATCH /ledgers/{id}` |
| 软删除账本 | `DELETE /ledgers/{id}` |
| 回收站恢复 | `POST /ledgers/{id}/restore` |
| 写入/更新一项（含付款） | `PUT /ledgers/{id}/items/{itemId}` |
| 删除一项 | `DELETE /ledgers/{id}/items/{itemId}` |
| 生成邀请 | `POST /ledgers/{id}/invites` |
| 作废邀请 | `DELETE /ledgers/{id}/invites/{inviteId}` |
| 加入 | `POST /invites/join` |
| 成员列表 | `GET /ledgers/{id}/members` |
| 踢人 | `DELETE /ledgers/{id}/members/{userId}` |
| 转让所有者 | `POST /ledgers/{id}/transfer` |
| 退出账本 | `POST /ledgers/{id}/leave` |

### 9.1 错误与文案

| 情况 | HTTP | 用户感知 |
|------|------|----------|
| 未登录 / JWT 过期 | 401 | 重新微信登录 |
| 非成员 / 被踢 | 403 | 没有这个账本的权限 |
| 同步冲突 | 409 | 已被其他人更新，请刷新 |
| 邀请过期或已作废 | 410 | 邀请已失效 |
| 手机号已被占用 | 409 | 该手机号已绑定其他账号 |
| 离线 | 客户端 | 离线已保存，联网后同步 |

响应不暴露微信密钥或内部堆栈。

## 10. 数据模型（服务端）

与 Room 对齐并扩展（名称实现时可按 Kotlin 习惯微调）：

- `users`：id、昵称、头像、phone（可空、唯一）
- `user_identities`：userId、provider（wechat_mp / wechat_app / google / apple）、openid、unionid
- `ledgers`：id、name、revision、ownerUserId、deletedAt
- `ledger_members`：ledgerId、userId、role（OWNER / EDITOR）
- `ledger_taxonomy`：账本级阶段/分类/空间及图标
- `budget_items`：对齐 `BudgetItemEntity`，含 project/ledger 外键
- `payments`：对齐 `PaymentEntity`，`createdByUserId` + `createdByName`
- `invites`：ledgerId、code、expiresAt、revokedAt、createdBy

客户端本地增加：`cloudLedgerId`、`pendingSync`、项/账本版本缓存。

## 11. 测试

- **服务端：** 登录发 token（微信 HTTP 用测试替身）、权限矩阵、邀请过期、409 冲突、import 幂等（已绑定不重复创建）。Spring MockMvc / WebTestClient，不依赖真微信。
- **Android：** Room ↔ JSON 映射、409 时刷新本地。单元测试为主。
- **小程序：** store 合并与待同步标记；微信登录走真机手工验收。

## 12. 部署（第一期）

一台云主机：Docker 运行 Postgres + Spring Boot，对外 HTTPS。微信公众平台 / 开放平台配置服务器域名。具体云厂商与域名实现计划里再定，不阻塞本 spec。

## 13. 分期建议（实现计划可再拆任务）

| 期 | 范围 | 验收 |
|----|------|------|
| **S0** | 服务端工程 + User/微信登录 + JWT | 小程序/Android 能拿到 token |
| **S1** | 账本 CRUD + import + 整本 GET/项 PUT + revision | 单用户两端看到同一本账 |
| **S2** | 邀请码、成员、所有者权限 | 第二人加入后可改；被踢后 403 |
| **S3** | 手机号绑定、冲突 409 UI、待同步/离线提示 | 绑号冲突有文案；两人改同一项有刷新提示 |

S0–S2 构成「能用的多人账本」；S3 为完整度。Google/iOS 登录不在上述期内。
