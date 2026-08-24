# 换账号后账本可见性与绑定提示

**日期：** 2026-08-24  
**状态：** 已确认，待实现  
**端：** Android App + 微信小程序（行为对等）；服务端需列表增加上传时间字段  
**前置：** `2026-08-14-cloud-login-and-auto-sync-design.md`、`2026-08-13-cloud-sync-server-design.md`

## 1. 问题

设备上已有本地账本（可能已绑旧账号云端）。退出登录后换新账号，再切账本会 `GET/PUT` 旧 `cloudLedgerId` → 403「没有这个账本的权限 / 没有权限」。用户期望：

- 未绑定任何账号的本地账本：登录时询问是否上传到当前账号；上传与否都**留在当前账本**
- 已绑定其他账号的本地账本：新账号登录后**看不见**（隐藏，数据保留）
- 当前账号云账本：按**上传时间**排序；需要默认本时用**最早上传**的那本

## 2. 已确认决策

| 项 | 选择 |
|----|------|
| 可见性依据 | 以 `GET /ledgers` 返回的云账本 ID 集合为准（方案 A） |
| 他人已绑本地本 | 登录态下从抽屉/「我的」列表**隐藏**；不删本地数据 |
| 未绑定本地本 | 名称展示为 `原名（本地）`；排在当前账号云账本**之后** |
| 云账本顺序 | 按上传/创建时间**升序**（最早 = 账号「第一本」） |
| 登录弹窗时机 | **仅当登录瞬间当前账本 `cloudLedgerId` 为空** |
| 弹窗选上传 | `importCurrent()`，绑定成功后**仍停在当前账本** |
| 弹窗不上传 | **仍停在当前账本**；仅本地编辑，不调该本同步 |
| 取消后本地编辑 | 允许；不做云同步 |
| 当前本属他人云本 | 自动切到账号第一本（上传时间最早）；若账号无云本则切第一本可见未绑定本地本（若有），否则新建空本 |
| 当前本已是自己云本 | **停留当前**，不强制切到第一本 |
| 未登录 | 列表展示全部本地账本；不加「（本地）」；不隐藏 |
| 双端 | Android + 小程序同步改 |

## 3. 账本分类

登录后对本地每一本 `Project`：

| 类型 | 判定 | 抽屉展示 |
|------|------|----------|
| 账号云账本 | `cloudLedgerId` 非空且 ∈ 当前用户 `GET /ledgers` | 显示真名；按上传时间升序 |
| 未绑定本地本 | `cloudLedgerId` 为空 / null | 显示 `name + "（本地）"`；排在云账本后，相对顺序保持本地原有顺序 |
| 他人云本 | `cloudLedgerId` 非空且 ∉ 账号列表 | **不展示** |

`refreshOnOpen` 仍把云端有、本地没有的摘要建成占位 Project（现有逻辑），这些一律算「账号云账本」。

## 4. 登录后流程

```
登录成功（sms / wechat）
  → refreshOnOpen：fetchMe + listLedgers + 补占位 + 若当前本可访问则 pull
  → 若当前本 cloudLedgerId 为空：
       弹窗：「使用「{name}」需要绑定到当前账号。是否上传？」
         确认 → importCurrent() → 停留当前本（已绑）
         取消 → 停留当前本（未绑，不同步）
  → 否则若当前本 cloudLedgerId 不在账号列表：
       switch 到账号第一本（见 §5）→ pull
  → 否则：
       停留当前本
```

打开 App / 回前台的 `refreshOnOpen`：**不再**因当前本是他人云本而 Toast「无权限」；应静默切到可访问第一本（与登录后「他人本」分支相同）。若当前本未绑定：不弹窗（弹窗只在登录成功瞬间），可继续本地编辑，`pullCurrent` 直接 no-op。

## 5. 「账号第一本」

1. 取 `GET /ledgers` 摘要，按 `createdAtEpochMs` **升序**，取第一本  
2. 若列表为空：取本地可见列表中第一本未绑定本；若也没有，`createProject("新账本")` 并（已登录时）`createCloudForCurrent`  
3. 切过去后对该本 `pull`（有 `cloudLedgerId` 时）

## 6. 切换账本

- 点账号云账本：`switch` + `pull`（现有）  
- 点「（本地）」：`switch`；**不** `pull` / `push`（无 cloud id）  
- 他人本：列表已隐藏，无入口  

对未绑定本的 `pushItem` / `pullCurrent`：无 `cloudLedgerId` 时保持现有 early-return，不报错。

对「本地仍存着他人 cloudLedgerId」的本：登录态下不可见；若代码路径误切过去，`pull` 遇 403 时：不 Toast「无权限」，改为切到账号第一本并可选短提示「已切换到当前账号的账本」。

## 7. 服务端：列表带上传时间

`GET /ledgers` 摘要扩展：

```json
{ "id", "name", "role", "revision", "createdAtEpochMs" }
```

- `createdAtEpochMs`：账本在服务端创建时间（`POST /ledgers` 或 `POST /ledgers/import` 写入时刻）  
- 客户端：`LedgerSummaryDto` / 小程序 summary 解析该字段；排序用它  
- **兜底**：若字段缺失，用客户端绑定时写入的本地 `cloudLinkedAtEpochMs`；再缺失则保持服务端数组顺序

本期客户端同时落本地字段 `cloudLinkedAtEpochMs`：在 `importCurrent` / `createCloudForCurrent` / `applySnapshot`（首次绑定）时写 `System.currentTimeMillis()`（或服务端返回的 createdAt）；仅用于排序兜底与「第一本」兜底。

## 8. 数据与 UI 落点

### Android

- `Project` / `ProjectEntity`：可选 `cloudLinkedAtEpochMs: Long?`（Room migration）  
- `LedgerSummaryDto.createdAtEpochMs: Long?`  
- 纯函数：`LedgerVisibility.visibleProjects(local, cloudSummaries, loggedIn) → List`（排序 + 展示名）  
- `OverviewViewModel` / 抽屉：用可见列表；登录成功后发一次性「绑定提示」事件  
- `LoginViewModel` / `WXEntryActivity` 登录成功后：`refreshOnOpen` + 触发绑定弹窗检查（或经共享 `LedgerSessionCoordinator`）  
- `LedgerSyncRepository.pullCurrent` / `refreshOnOpen`：403 时按 §6 处理，不裸 Toast「没有权限」

### 小程序

- `store` project：`cloudLinkedAtEpochMs`  
- `utils/ledgerVisibility.js`：与 Android 同规则  
- `pages/overview` 抽屉、`pages/mine` 账本列表用同一过滤排序  
- `utils/sync.js`：`refreshOnOpen` / `pull` 403 处理；登录页成功后弹窗逻辑与 Android 同文案

### 弹窗文案（双端一致）

- 标题：`绑定账本`  
- 正文：`「{账本名}」尚未绑定账号。上传后将同步到当前账号；取消则仅本机使用。`  
- 确认：`上传`  
- 取消：`暂不上传`

## 9. 非目标

- 不删他人云本的本地缓存（仅隐藏）  
- 不做账本「转让」给新账号以外的复杂权限迁移  
- 不改邀请加入流程（加入成功仍为账号云本）  
- 退出登录不清空本地账本

## 10. 验收

1. 本地两本均未绑 → 登录新账号 → 弹窗；选上传 → 当前本出现在云列表且仍打开；选暂不 → 仍打开，名带「（本地）」  
2. 本地一本已绑账号 A，换账号 B 登录 → 该本不在抽屉；展示 B 的云本（按上传时间）+ 未绑本地本在末尾  
3. 当前打开的是 A 的云本缓存 → 登 B 后自动切到 B 的第一本，无「无权限」Toast  
4. 当前已是 B 的某本云本 → 登录/刷新后不强制改到第一本  
5. 未登录：全部本地本可见，无「（本地）」后缀  
6. Android 与小程序行为一致  

## 11. 风险与注意

- 「我的」里账本删除列表须与抽屉同一可见性规则，避免删到隐藏本却误以为是当前账号本  
- `refreshOnOpen` 补占位后必须先算可见性再决定是否切第一本，避免用隐藏本当 current  
- 上传时间依赖服务端字段；上线前确认 API 已发版或客户端兜底可用  
