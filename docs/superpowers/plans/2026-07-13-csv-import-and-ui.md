# CSV Import + UI Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打通旧 App CSV 导入（样例 66 行）、补齐 `recordedDate`/`remark`、清单可折叠分组，并按 Material 3 完成全页 TopAppBar、Card+图标、待花费双 Tab。

**Architecture:** `DcjzCsvImporter` + `ImportDeduper` → `ImportedLineDraft` → 批量确认 → `ProjectRepository.upsertItems`。OCR 多图后置。

**Tech Stack:** Kotlin Compose + Room + Hilt；手写 CSV；Material 3。

**Specs:** `docs/superpowers/specs/2026-07-13-screenshot-import-design.md`、主设计 §8.0–8.6  
**Fixture:** `docs/superpowers/fixtures/dcjz_export_sample.csv`（测试资源副本：`app/src/test/resources/`）

**Constraint:** AGP 保持 `8.6.0`，勿升级。

---

## File map

| Area | Paths |
|------|--------|
| Model + Room | `BudgetItem` / `BudgetItemEntity` / `Mappers` / `AppDatabase` v2 + `MIGRATION_1_2` |
| Import | `domain/importing/*` + `DcjzCsvImporterTest` |
| Batch UI | `ui/importbatch/BatchImportConfirmScreen.kt` + ViewModel + `ImportDraftStore` |
| List | `BudgetListScreen` collapsible stage groups |
| Overview/Pending | Cards+icons；展开双 Tab；明细页双 Tab + `tab` 路由参数 |
| Mine | 「从文件导入」OpenDocument → 解析 → 批量确认 |
| Nav | `AppNav` 新路由；导入/确认页隐藏底栏 |

---

### Task 1: recordedDate + remark + migration

- [x] Domain / entity / mappers / DB v2 / `upsertItems`（已落地）

### Task 2: DcjzCsvImporter + tests

- [x] Parser、去重、样例 66 行合计 `50634749` 分（已落地）

### Task 3: UI TopAppBar + Cards + pending dual tabs

- [x] 多数页面已有 TopAppBar / Card
- [ ] Overview → Pending 传递 `tab` 参数
- [ ] 确认各入口页标题栏齐全

### Task 4: Collapsible stage groups

- [ ] 组头可点折叠；默认折叠；显示项数 + 组合计

### Task 5: Batch confirm + Mine 从文件导入

- [ ] `ImportDraftStore` 暂存草稿
- [ ] `BatchImportConfirmScreen`：已选 N/M、按 stage 分组、勾选、确认导入
- [ ] Mine「从文件导入」+ 路由

### Task 6: Verify

- [ ] `./gradlew :app:assembleDebug :app:testDebugUnitTest`

---

## Spec coverage

| Spec | Status |
|------|--------|
| recordedDate/remark + migration | done |
| CSV 66 行 / 小数 / 空名称 | done |
| 备注「已交」不自动付款 | done |
| 批量确认 + 我的导入 | todo |
| 清单可折叠 | todo |
| TopAppBar / Card / 双 Tab | mostly done |
| OCR 多图 | out of scope |
