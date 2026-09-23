# 统一业务路由 LXY://

**日期：** 2026-09-23  
**状态：** 已确认，直接实现到打包  
**端：** Android App + 微信小程序  
**不改：** 官网、后端。调试页不进路由表。

## 1. 目标

同一业务页使用同一条路由。路由头只有一个：`LXY://`。

- 应用内跳转：Android 与小程序都走这条字符串。
- 外部唤起：只做 Android（通知、分享、剪贴板、其它 App）。小程序没有系统 scheme，不单独再设路由头。

## 2. 路由

写法：`LXY://<路径>?<查询>`。scheme 忽略大小写。Manifest 注册小写 `lxy`。路径用小写。

| 页面 | 路由 | Android 目的地 | 小程序页面 |
|---|---|---|---|
| 总览 / 清单 / 统计 / 我的 | `LXY://overview` `list` `stats` `mine` | 对应 Tab，回到该 Tab | `wx.switchTab` |
| 详情 | `LXY://item/{id}` | `item/{id}` | `/pages/detail/detail?id=` |
| 待付 | `LXY://pending?tab=` | `pending?tab=`，缺省 `unpaid` | `/pages/pending/pending` |
| 已付差额 | `LXY://paidgap?tab=` | `paidgap?tab=`，缺省 `overspend` | `/pages/paid-gap/paid-gap` |
| 手动记账 | `LXY://entry/manual` | `entry/manual`，可带 `itemId`、`editItemId` | `/pages/entry/entry`，查询原样带上（含 `fromVoice`） |
| 确认记账 | `LXY://entry/confirm?source=` | `entry/confirm`，可带 `itemId` | 同一记账页。小程序没有单独确认页 |
| 搜索、登录、资料、设置、分类、导入、回收站 | `LXY://search` `login` `profile` `settings` `taxonomy` `import/batch` `trash` | 同名目的地 | 现有页面路径 |

未知路由不跳转，提示「页面不存在」。详情缺少 `id` 同样视为未知。`navigateBack` 保持原样。摇一摇调试页仍走各端原来的入口。

## 3. Android

- `LxyRoutes` 负责生成和解析，不依赖 `android.net.Uri`，单元测试可直接跑。
- 页面跳转改为 `openLxy`。Tab（含导入完成后回清单）使用现有的 `popUpTo` 起点 + `saveState` / `restoreState` / `launchSingleTop`。
- `MainActivity` 为 `singleTask`。`VIEW` + `DEFAULT` + `BROWSABLE`，`android:scheme="lxy"`。冷启动读 `intent.data`，已在前台走 `onNewIntent`。同一条链接连开两次也要能再跳，用递增序号触发。
- 确认记账的 Compose 参数只有 `source` 与 `itemId`。`fromVoice` 只留给小程序记账页，不写进 Android 目的地。

## 4. 小程序

- `utils/router.js` 的 `open` 解析 `LXY://` 再调用 `wx.switchTab` 或 `wx.navigateTo`。
- 语音记账仍要 `success` 里的 `eventChannel`，`open` 把 `success` 传给 `wx.navigateTo`。
- 打开失败提示「页面打开失败」。

## 5. 校验与打包

- Android：`LxyRoutesTest`，再 `sh oneClickSetup`（clean、assembleDebug、安装、启动）。
- 小程序：用 Node 调用 `parse` 核对页面地址。无独立打包脚本，不上传微信后台。
