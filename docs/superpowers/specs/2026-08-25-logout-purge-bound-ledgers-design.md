# 退出登录：硬删已绑定账本本机副本

**日期：** 2026-08-25  
**状态：** 已确认，直接实现  
**端：** Android App + 微信小程序（行为对等）  
**修订：** 覆盖 `2026-08-24-account-ledger-visibility-design.md` 中「未登录展示全部本地账本 / 退出登录不清空本地账本」条款。

## 1. 目标

退出登录后：

- **已绑定账号**的账本（`cloudLedgerId` 非空）：从本机**硬删**（条目 CASCADE；**不进垃圾箱**；**不调**云端 unbind / delete）
- **未绑定**账本：保留
- 若删后本机无账本：自动新建「新账本」并切过去
- 云端数据不变；再登录同一账号靠 `refreshOnOpen` 拉回

## 2. 行为细则

| 项 | 选择 |
|----|------|
| 已绑定本处理 | 硬删本机副本 |
| 垃圾箱 | 退出路径不写 trash；产品上只有未绑定本走「移入垃圾箱」 |
| 云端 | 不解绑、不软删 |
| 删尽后 | `createProject("新账本")` |
| 未登录列表 | 仅展示未绑定本（与数据一致，防漏网） |
| 双端 | Android + 小程序同步 |

## 3. 落点

- Android：`ProjectRepository.purgeBoundLocalLedgersOnLogout` ← `LedgerSyncRepository.logout`；`LedgerVisibility` 未登录过滤
- 小程序：`store.purgeBoundLocalLedgersOnLogout` ← `sync.logout`；`ledgerVisibility` 未登录过滤

## 4. 验收

1. 本地一本已绑 + 一本未绑 → 退出 → 只剩未绑本  
2. 仅已绑本 → 退出 → 出现新建「新账本」  
3. 再登录原账号 → 云本重新出现（占位/拉取）  
4. 垃圾箱内无因本次退出新增的条目  
5. Android 与小程序一致  
