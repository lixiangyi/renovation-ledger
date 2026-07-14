# 账本自动 CSV 备份与确认恢复

**日期：** 2026-07-13  
**状态：** 已实现（2026-07-13）  
**范围：** 装修记账 App 本地数据防丢失（单份可还原 CSV + 空库确认恢复）

---

## 1. 背景与目标

主库（Room / SQLite）曾出现运行中被清空的情况；多份 `.db` 文件快照与静默自愈未能可靠覆盖「进程已打开后变空」的场景，且用旧账本 CSV 恢复会丢掉结清/付款。

**目标**

- 每次账本写操作成功后，生成**一份**可完整还原的自动备份 CSV，并**覆盖**写入两处（私有 + Download），不保留历史多版本。
- 发现预算项为空且备份有数据时，**弹窗确认**后再恢复，不静默改库。
- 退役现有多份 `.db` 快照与静默 heal / 预打开拷库，避免两套逻辑并存。

**非目标**

- 云同步、多人实时协作备份
- 多版本历史备份
- 头像文件、收据图片二进制备份（路径字符串可写入 CSV）

---

## 2. 决策摘要

| 项 | 选择 |
|----|------|
| 方案 | A：单 CSV 双写 + 确认恢复 |
| 空库恢复 | 弹窗确认后再回灌 |
| 存放 | 私有 `files/ledger_autosave.csv` + Download `装修记账_自动备份.csv`（内容相同，各只留最新一份） |
| 与现有导出 | 自动备份用专用 v1 格式；手动「导出 CSV」分享格式保持独立 |

---

## 3. CSV 格式（autosave v1）

**识别**

- 首行魔数：`#renovation_ledger_autosave_v1`
- 编码：UTF-8（带 BOM，便于 Excel）

**路径**

- 私有：`context.filesDir/ledger_autosave.csv`
- 公开：系统 Download 目录下 `装修记账_自动备份.csv`（覆盖写）

**表头（稳定英文键，单行）**

```
record_type,project_id,project_name,member_names,item_id,item_name,stage,category,space,budget_fen,contract_fen,merchant,recorded_date,remark,is_new_addition,payment_id,payment_type,payment_amount_fen,payment_status,paid_at_epoch_ms,payment_note,created_by
```

**行类型 `record_type`**

| 类型 | 数量 | 说明 |
|------|------|------|
| `project` | 1 | `project_id` / `project_name` / `member_names`（成员用 `\|` 分隔） |
| `item` | 每项 1 行 | 无付款也写；付款相关列留空 |
| `payment` | 每笔 1 行 | 必须带 `item_id`；挂到对应预算项 |

**写入顺序：** `project` → 全部 `item` → 全部 `payment`。

**金额与时间**

- 金额一律用分（`*_fen`），避免元小数往返误差。
- 付款时间用 `paid_at_epoch_ms`；结清补差通过 `payment_note` + `payment_status=PAID` 完整保留。

**枚举落盘**

- `payment_type`：`DEPOSIT` / `FINAL` / `OTHER`
- `payment_status`：`PAID` / `UNPAID`
- `is_new_addition`：`0` / `1`

---

## 4. 写时机与覆盖规则

**触发点（Room 提交成功之后）**

经 `ProjectRepository`：`upsertItem`、`upsertItems`、`upsertPayment`、`settleItem`、`deleteItem`、项目/成员变更、批量导入完成等所有账本写路径。

**步骤**

1. 读出当前项目 + 全部预算项 + 付款  
2. 编码为 autosave v1 CSV  
3. 若当前 `budget_items` 数量为 0：**不覆盖**已有非空备份（防止空库冲掉唯一备份）  
4. 否则：先写私有（临时文件 → rename 覆盖），再写 Download（同样原子覆盖）；Download 失败只记日志，不回滚已成功的数据库写入  
5. 使用 Mutex，与写库串行，避免并发覆盖

**退役**

- 删除 / 停用 `LocalDbBackup` 多份 `renovation_*.db`、`latest.db`、预打开 `restoreFileIfNeeded`、静默 `healIfEmpty`。

---

## 5. 空库检测与确认恢复

**检测时机**

`MainActivity` 启动协程：在 `ensureDefaultProject()` 之后检查 `budget_items` 计数。

**弹出条件**

- 计数为 0，且  
- 私有或 Download 任一侧 CSV 可解析且 `item` 行数 ≥ 1  

**弹窗**

- 标题：发现账本数据为空  
- 正文：检测到自动备份（约 N 项 / M 笔付款）。是否恢复？恢复将写入当前账本。  
- 操作：
  - **恢复**：整库替换（先清 `payments`、`budget_items`，再按 CSV 写入 `projects` / items / payments，**保留 CSV 中的 id**）；成功 Toast；失败 Alert 且不改库（或事务回滚保持空）  
  - **暂不**：本进程不再提示；下次冷启动若仍为空再问  

**选源顺序：** 优先私有文件；损坏或不存在再试 Download。

---

## 6. 模块划分

| 模块 | 职责 |
|------|------|
| `AutosaveCsvCodec` | v1 编解码；与分享导出、旧 `DcjzCsvImporter` 分离 |
| `LedgerAutosave` | 双写覆盖、空库不覆盖、读取/选源 |
| `ProjectRepository` | 写成功后 `save`；提供 `restoreFromAutosave()` |
| 启动侧 VM / 状态 | 空库检测 →「待确认恢复」 |
| `RestoreAutosaveDialog` | 确认/取消 UI（挂在 App Scaffold 或等价根层） |

手动「导出 CSV」仍用现有面向分享的格式，不与 autosave v1 混用。

---

## 7. 错误处理

| 场景 | 行为 |
|------|------|
| 私有目录写入失败 | 记日志；可选非阻断提示；不回滚库 |
| Download 写入失败（权限等） | 记日志；私有成功则仍可用于恢复 |
| CSV 解析失败 | 弹窗恢复时 Alert；不改库 |
| 恢复中途失败 | 事务回滚，库保持检测时的空状态 |
| 备份为空或无 item | 不弹恢复窗 |

---

## 8. 测试要点

- 结清多项后，CSV 含对应 `payment`；模拟空库 → 确认恢复 → 状态仍为已结清、金额一致  
- 空库时执行写路径不得把非空 CSV 覆盖成空  
- Download 无权限时，仅私有备份仍可弹出并恢复  
- 旧账本导入 CSV、手动分享导出不受 autosave 格式影响  
- Mutex 下连续结清/记账不产生截断半截文件（rename 原子覆盖）

---

## 9. 实现顺序（供后续 plan）

1. `AutosaveCsvCodec` + 单测（往返含结清付款）  
2. `LedgerAutosave` 双写与空库不覆盖  
3. 接入 `ProjectRepository` 写路径；移除 `LocalDbBackup`  
4. 启动检测 + 确认弹窗 + `restoreFromAutosave`  
5. `oneClickSetup` 实机验证空库确认恢复

---

## 10. 与主产品 spec 的关系

本文件为**数据耐久性专项**；主产品说明见 `2026-07-13-renovation-ledger-design.md`。落地后可在主 spec「数据 / 导出」相关节增加一句：自动备份为 autosave v1 单文件双写，空库需用户确认恢复。
