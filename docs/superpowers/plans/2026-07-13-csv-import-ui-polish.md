# Screenshot/CSV Import + UI Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打通旧 App CSV 导入（66 行样例）、补齐 `recordedDate`/`remark`、清单可折叠分组，并按 Material 3 原质感完成全页标题栏、Card+图标、待花费双 Tab。

**Architecture:** 纯 Kotlin 解析器（`DcjzCsvImporter` + `ImportDeduper`）产出 `ImportedLineDraft`；批量确认页勾选后 `ProjectRepository.upsertItems`；UI 层 Compose Material3 Card/TopAppBar/TabRow。OCR 多图后置，本计划不接云 OCR。

**Tech Stack:** 现有 Kotlin Compose + Room + Hilt；CSV 手写解析；Material Icons Extended（若体积可接受）或默认 Icons。

**Specs:** `docs/superpowers/specs/2026-07-13-screenshot-import-design.md`、主设计 §8.0–8.2  
**Fixture:** `docs/superpowers/fixtures/dcjz_export_sample.csv`

---

## File structure

```
domain/model/BudgetItem.kt          (+recordedDate, remark)
domain/import/ImportedLineDraft.kt
domain/import/ImportDeduper.kt
domain/import/DcjzCsvImporter.kt
data/local/entity/BudgetItemEntity.kt (+fields)
data/local/mapper/Mappers.kt
data/local/AppDatabase.kt           (version 2 + Migration)
data/repo/ProjectRepository.kt      (+upsertItems)
ui/list/...                         (collapsible + Card)
ui/overview/...                     (Card+icons+dual tab expand)
ui/pending/...                      (dual Tab)
ui/importbatch/...                  (BatchConfirmScreen)
ui/mine/...                         (file picker import)
ui/theme/Theme.kt                   (M3 color scheme polish)
ui/navigation/AppNav.kt             (new routes)
```

---

### Task 1: BudgetItem + Room migration

**Files:**
- Modify: `domain/model/BudgetItem.kt`
- Modify: `data/local/entity/BudgetItemEntity.kt`
- Modify: `data/local/mapper/Mappers.kt`
- Modify: `data/local/AppDatabase.kt`
- Modify: all BudgetItem constructors (template, entry VMs)

- [ ] Add `recordedDate: String? = null`, `remark: String = ""` to domain + entity
- [ ] Bump DB to v2 with `ALTER TABLE budget_items ADD COLUMN recordedDate TEXT` and `remark TEXT NOT NULL DEFAULT ''`
- [ ] Update mappers; fix compile of all call sites
- [ ] Run `./gradlew :app:compileDebugKotlin`

### Task 2: DcjzCsvImporter + Deduper (TDD)

**Files:**
- Create: `domain/import/ImportedLineDraft.kt`
- Create: `domain/import/DcjzCsvImporter.kt`
- Create: `domain/import/ImportDeduper.kt`
- Test: `DcjzCsvImporterTest.kt`, `ImportDeduperTest.kt`
- Fixture: copy or read `docs/superpowers/fixtures/dcjz_export_sample.csv` in test via `javaClass.getResource` — put copy under `app/src/test/resources/dcjz_export_sample.csv`

- [ ] Write failing tests: 66 lines, sum cents 50634749, empty name → stage, remark kept
- [ ] Implement importer + deduper
- [ ] Tests pass

### Task 3: Repository batch upsert

- [ ] `suspend fun upsertItems(items: List<BudgetItem>)`
- [ ] Helper `fun ImportedLineDraft.toBudgetItem(projectId: String): BudgetItem`

### Task 4: UI — theme + TopAppBar + Cards + dual tabs

- [ ] Polish `Theme.kt` (seed colors optional)
- [ ] Overview: TopAppBar「总览」, metric Cards with icons, expand area = TabRow 待付尾款|待购买 (max 5)
- [ ] PendingSpend: TabRow dual tabs full lists
- [ ] Ensure List/Stats/Mine/Detail/Entry have TopAppBar
- [ ] Add material-icons-extended dependency if needed

### Task 5: Collapsible budget list

- [ ] Stage headers toggle expand/collapse; show group budget sum; default collapsed
- [ ] Cards for items

### Task 6: Batch confirm + Mine file import

- [ ] `BatchImportConfirmScreen` + ViewModel
- [ ] Route from Mine file picker (OpenDocument) → parse → confirm → upsertItems
- [ ] Wire AppNav; hide bottom bar on import routes

### Task 7: Verify

- [ ] `./gradlew :app:assembleDebug :app:testDebugUnitTest`
- [ ] Manual: import fixture CSV → list shows 卫浴/全屋定制等 groups

---

## Spec coverage

| Spec | Task |
|------|------|
| recordedDate/remark | 1 |
| Dcjz CSV + decimals + empty name | 2 |
| No auto payment from 已交 | 2–3 |
| Batch confirm | 6 |
| Collapsible stages | 5 |
| TopAppBar / Card / dual tab | 4 |
| File import entry | 6 |
| OCR multi-image | **out of scope** |
