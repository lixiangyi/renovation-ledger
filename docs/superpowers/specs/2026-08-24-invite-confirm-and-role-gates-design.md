# 邀请确认、成员列表与角色门控

**日期：** 2026-08-24  
**状态：** 已实现（待真机验收）  
**端：** Android App + 微信小程序 + 服务端（行为对等）  
**前置：** `2026-08-13-cloud-sync-server-design.md`、`2026-08-21-nickname-follows-account-design.md`、`2026-08-24-account-ledger-visibility-design.md`

## 1. 问题

1. 输入邀请码后直接 `join`，用户无法确认「是谁的哪本账本」。
2. 项目成员来自本地 `memberNames`，协助者加入后列表里往往看不到自己；且成员行可改任意人昵称。
3. 协助者（EDITOR）仍可改健康色、生成邀请码、添加成员；这些应仅拥有者（OWNER）。
4. 删除账本弹窗对 OWNER / EDITOR 文案相同，未区分「删除云端」与「解绑退出」。

## 2. 已确认决策

| 项 | 选择 |
|----|------|
| 邀请确认信息来源 | 服务端预览接口（方案 A） |
| 成员列表来源 | `GET /ledgers/{id}/members`（云端为准） |
| 「＋添加成员」 | 双端去掉；加人只靠邀请码 |
| 改昵称 | 项目成员内去掉；仅设置页改自己账号昵称 |
| 健康色（EDITOR） | 当前账本为 EDITOR 时整块隐藏 |
| 删除弹窗 | OWNER「删除账本」/ EDITOR「解绑账本」分两套文案 |
| 实现路径 | 服务端加预览 + 客户端用现有 role/members 做门控 |

## 3. 邀请码加入确认

### 3.1 服务端：预览接口

新增（需登录）：

```
GET /invites/{code}/preview
```

响应：

```json
{
  "code": "ABC123",
  "ledgerId": "...",
  "ledgerName": "我家装修",
  "ownerNickname": "小明",
  "alreadyMember": false
}
```

规则：

- 校验邀请码存在、未作废、未过期；否则 **410**「邀请已失效」（与 join 一致）。
- **不**写入 `ledger_members`，不改 revision。
- `ownerNickname`：该账本 `role=OWNER` 成员对应用户的 `users.nickname`；缺失时用 `"账本拥有者"`。
- `alreadyMember`：当前用户是否已在成员表中（便于文案微调，可选）。
- 任意已登录用户可预览有效码（与 join 前置一致）；不要求已是成员。

### 3.2 客户端流程

1. 用户输入邀请码 → 点「加入账本」。
2. `GET /invites/{code}/preview`；失败 → toast，不弹窗。
3. 弹窗文案：  
   `是否加入「{ownerNickname}」的「{ledgerName}」账本？`  
   按钮：**确认** / **取消**。
4. 确认 → 现有 `POST /invites/join`；取消 → 不调用 join。
5. 已是成员：预览仍返回信息；确认后 join 幂等（现有行为）。

双端：Android `AlertDialog`；小程序 `wx.showModal`。

## 4. 项目成员列表与昵称

### 4.1 数据源

| 场景 | 列表来源 |
|------|----------|
| 已登录且当前账本有 `cloudLedgerId` | `GET /ledgers/{id}/members` → `{ userId, nickname, role }` |
| 未登录 / 未绑云 | 本地 `memberNames` **只读**展示 |

- 列表**必须包含当前用户自己**（云端成员表已有 EDITOR 行；根因是客户端未拉 members）。
- 展示：昵称；可选角色角标「拥有者 / 协助者」。
- 打开「我的」、切换账本、join 成功、pull 成功后刷新 members。
- 可选兜底：刷新成功后把昵称列表回写本地 `memberNames`（仅展示缓存，不当作加人入口）。

### 4.2 去掉的能力

- 去掉「＋添加成员」及对应本地 `addMember` UI（双端）。
- 去掉项目成员行的「改昵称」；昵称只在「设置」经 `PATCH /me` 改自己。
- 本地 `updateMemberNickname` / `addMember` 可保留仓库实现供迁移/离线，但 UI 不再暴露。

## 5. 角色门控（当前账本）

角色来源：`GET /ledgers` 摘要中当前 `cloudLedgerId` 对应项的 `role`（`OWNER` | `EDITOR`）。未绑云 / 未登录：视为本机所有者能力（可改健康色；无邀请码生成）。

| 能力 | OWNER | EDITOR | 未绑云本地 |
|------|-------|--------|------------|
| 生成邀请码 | ✓ | ✗ 隐藏 | ✗（无 cloud） |
| 「＋添加成员」 | 已删除 | 已删除 | 已删除 |
| 预算健康色整块 | ✓ | ✗ **隐藏** | ✓ |
| 输入邀请码加入其他账本 | ✓ | ✓ | 需先登录 |
| 删除/解绑账本 | 删除文案 | 解绑文案 | 仅本地移入垃圾箱文案 |

服务端已有：`POST /ledgers/{id}/invites` 仅 OWNER；客户端须 UI 隐藏，避免无意义 403。

健康色仍是本机 prefs；EDITOR 仅隐藏编辑入口，已有偏好继续用于总览主题色。切到自己 OWNER 账本后可再改。

## 6. 删除 / 解绑弹窗

按 `CloudUnbindDecision.actionForRole(role)`（已有）分支文案；本地仍一律：导出 CSV → 云端 unbind（若有）→ 硬删本地 → 进垃圾箱。

### OWNER（或未绑云但用户点的是「删除」入口）

- 标题：`删除账本`
- 正文：将「{name}」移入垃圾箱，并**删除云端账本**（协作成员将无法再访问）。会先导出备份，之后可从垃圾箱恢复；永久删除前仍可找回。
- 确认按钮：`删除`

未绑云（无 cloudLedgerId）：标题仍可用「删除账本」，正文去掉云端句，只说明移入垃圾箱与备份。

### EDITOR

- 标题：`解绑账本`
- 正文：将「{name}」移入垃圾箱，并**退出该账本协作**（云端账本仍保留，拥有者不受影响）。会先导出备份……
- 确认按钮：`解绑`

双端：「我的」账本管理 + 总览抽屉删除入口文案一致。

## 7. 数据与落点

### 服务端（`renovation-ledger-server`）

- `InviteController` + `InviteService.preview(code)`
- DTO：`InvitePreviewDto`
- 测试：有效码返回 owner/ledger；过期/作废 410；preview 后 members 数量不变

### Android

- `LedgerApi.previewInvite` / `listMembers`；DTO
- `LedgerSyncRepository`：preview、fetchMembers；join 前由 UI 编排确认
- `MineViewModel` / `MineScreen`：确认弹窗、members 列表、`isOwner` 门控、删除文案
- `OverviewScreen` 删除弹窗同步分角色文案
- 单元测试：门控展示条件、删除文案选择（可纯函数）

### 小程序

- `api.js` / `sync.js`：preview、listMembers
- `pages/mine`：确认 `showModal`、成员列表、OWNER 才显示生成邀请码、EDITOR 隐藏健康色、删除/解绑文案
- overview 删除入口对齐

## 8. 非目标

- 踢人、转让 OWNER、作废邀请码的新 UI
- 健康色改为账本级云同步字段
- 重构废弃 Room `memberNames` 列（可继续作离线缓存）
- 邀请码短链 / 扫码

## 9. 验收

1. 输入有效邀请码 → 弹「是否加入「甲」的「乙」账本？」→ 取消不加入；确认后成为 EDITOR 且列表含自己。
2. 失效码 → toast，无确认弹窗。
3. 项目成员无「改昵称」「＋添加成员」；设置里仍可改自己昵称并在成员列表刷新后可见。
4. EDITOR：无生成邀请码、无健康色设置块；OWNER：两者都有。
5. OWNER 删账本弹「删除…」；EDITOR 弹「解绑…」；行为仍分别走 soft-delete / leave。

## 10. 自检

- 无 TBD；预览路径与 join 错误码对齐 410。
- 成员源与「去掉本地加人」一致，避免假成员。
- 健康色隐藏仅 EDITOR + 当前本有云角色；未登录本地本可改。
- 范围单计划可落地：服务端一小接口 + 双端「我的/删除」门控。
