# 支付清单按空间分组与筛选 UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 支付清单增加「按空间」分组；状态 Tab 置顶；筛选区改为两行描边分段（与胶囊 Tab 区分）；Android + 小程序对等。

**Architecture:** 扩展现有 `PaymentListGroupBy` / prefs / Aggregator；只改清单页控件顺序与样式，不改金额口径与列表形态语义。

**Tech Stack:** Kotlin + Compose + JUnit；微信小程序 JS/WXML；收尾 `sh oneClickSetup`（本会话偏好=要）。

**Git：** 禁止自动 commit；仅用户明确要求时再提交。

**Spec：** `docs/superpowers/specs/2026-07-22-payment-list-space-and-filter-ui-design.md`

---

## File map

| 文件 | 改动 |
|------|------|
| `domain/list/PaymentListAggregator.kt` | `SPACE` + 空空间「未指定」 |
| `PaymentListAggregatorTest.kt` | 新增空间分组用例 |
| `data/prefs/UserPrefs.kt` | `space` ↔ `SPACE` |
| `ui/list/BudgetListViewModel.kt` | `TaxonomyKind.SPACE` |
| `ui/list/BudgetListScreen.kt` | Tab 上移；描边分段；按空间 |
| `utils/paymentList.js` | `SPACE` 对齐 |
| `pages/list/list.js\|wxml\|wxss` | 顺序 + 样式 + 按空间 |
| `store.js` | prefs 已通用字符串时确认接受 `space` |

---

### Task 1: Aggregator 支持 SPACE（TDD）

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/domain/list/PaymentListAggregator.kt`
- Test: `app/src/test/java/com/renovation/ledger/PaymentListAggregatorTest.kt`

- [ ] **Step 1: 写失败测试**

在 `PaymentListAggregatorTest.kt` 追加：

```kotlin
@Test
fun `group by space uses space key and blank becomes 未指定`() {
    val items = listOf(
        item("吊灯", stage = "软装", category = "灯具", space = "客厅", budget = 1_000_00),
        item("射灯", stage = "软装", category = "灯具", space = "客厅", budget = 500_00),
        item("未填空间项", stage = "软装", category = "软装", space = "", budget = 200_00),
    )
    // extend private item() helper to accept space: String = ""
    val groups = PaymentListAggregator.group(items, PaymentListGroupBy.SPACE)
    assertEquals(setOf("客厅", "未指定"), groups.map { it.key }.toSet())
    assertEquals(2, groups.first { it.key == "客厅" }.items.size)
    assertEquals(1, groups.first { it.key == "未指定" }.items.size)
}
```

若现有 `item()` 无 `space` 参数，增加 `space: String = ""` 并传入 `BudgetItem(..., space = space, ...)`。

- [ ] **Step 2: 跑测确认失败**

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.PaymentListAggregatorTest
```

Expected: FAIL（无 SPACE 或不完整 when）

- [ ] **Step 3: 实现**

```kotlin
enum class PaymentListGroupBy { STAGE, CATEGORY, SPACE }

fun group(...): List<PaymentListGroupMetrics> {
    return items.groupBy { item ->
        when (groupBy) {
            PaymentListGroupBy.STAGE -> item.stage.ifBlank { "未分类" }
            PaymentListGroupBy.CATEGORY ->
                item.category.ifBlank { item.stage }.ifBlank { "未分类" }
            PaymentListGroupBy.SPACE ->
                item.space.ifBlank { "未指定" }
        }
    }.map { (key, groupItems) -> metrics(key, groupItems) }
        .sortedBy { it.key }
}
```

注意：SPACE **不要**再套一层 `ifBlank { "未分类" }`，空空间固定「未指定」。

- [ ] **Step 4: 跑测 PASS**

同 Step 2，Expected: BUILD SUCCESSFUL，全部绿。

- [ ] **Step 5: Checkpoint** — 不自动 git commit。

---

### Task 2: UserPrefs 映射 space

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/data/prefs/UserPrefs.kt`

- [ ] **Step 1: 扩展 when**

```kotlin
val paymentListGroupBy: Flow<PaymentListGroupBy> =
    ctx.userPrefsDataStore.data.map { prefs ->
        when (prefs[paymentListGroupByKey]) {
            "category" -> PaymentListGroupBy.CATEGORY
            "space" -> PaymentListGroupBy.SPACE
            "stage" -> PaymentListGroupBy.STAGE
            else -> PaymentListGroupBy.STAGE
        }
    }

suspend fun setPaymentListGroupBy(value: PaymentListGroupBy) {
    ctx.userPrefsDataStore.edit { prefs ->
        prefs[paymentListGroupByKey] = when (value) {
            PaymentListGroupBy.STAGE -> "stage"
            PaymentListGroupBy.CATEGORY -> "category"
            PaymentListGroupBy.SPACE -> "space"
        }
    }
}
```

- [ ] **Step 2: 编译**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: SUCCESS（若 ViewModel exhaustive when 报错，在 Task 3 一并修）

---

### Task 3: ViewModel 图标 kind = SPACE

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/ui/list/BudgetListViewModel.kt`

- [ ] **Step 1: exhaustive when**

```kotlin
val taxonomyKind = when (groupBy) {
    PaymentListGroupBy.STAGE -> TaxonomyKind.STAGE
    PaymentListGroupBy.CATEGORY -> TaxonomyKind.CATEGORY
    PaymentListGroupBy.SPACE -> TaxonomyKind.SPACE
}
```

- [ ] **Step 2: compileDebugKotlin SUCCESS**

---

### Task 4: BudgetListScreen UI（顺序 + 描边分段 + 按空间）

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/ui/list/BudgetListScreen.kt`

- [ ] **Step 1: 调换 Column 内顺序**

在 `Scaffold` content 的 `Column` 中，先 `FilterRow(...)`，再 `ListControlsRow(...)`，再列表滚动区。

- [ ] **Step 2: 重写 ListControlsRow 为两行描边分段**

删除对状态 Tab 同款 `FilterChip`/`ToggleChip` 的依赖（筛选区专用）。示例：

```kotlin
@Composable
private fun ListControlsRow(
    groupBy: PaymentListGroupBy,
    layout: PaymentListLayout,
    onGroupBySelect: (PaymentListGroupBy) -> Unit,
    onLayoutSelect: (PaymentListLayout) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedSegmentedRow {
            SegmentedItem(
                text = "按阶段",
                selected = groupBy == PaymentListGroupBy.STAGE,
                onClick = { onGroupBySelect(PaymentListGroupBy.STAGE) },
                emphasis = true,
            )
            SegmentedItem(
                text = "按分类",
                selected = groupBy == PaymentListGroupBy.CATEGORY,
                onClick = { onGroupBySelect(PaymentListGroupBy.CATEGORY) },
                emphasis = true,
            )
            SegmentedItem(
                text = "按空间",
                selected = groupBy == PaymentListGroupBy.SPACE,
                onClick = { onGroupBySelect(PaymentListGroupBy.SPACE) },
                emphasis = true,
            )
        }
        OutlinedSegmentedRow(neutralBorder = true) {
            SegmentedItem(
                text = "二级列表",
                selected = layout == PaymentListLayout.NESTED,
                onClick = { onLayoutSelect(PaymentListLayout.NESTED) },
                emphasis = false,
            )
            SegmentedItem(
                text = "单列表",
                selected = layout == PaymentListLayout.FLAT,
                onClick = { onLayoutSelect(PaymentListLayout.FLAT) },
                emphasis = false,
            )
        }
    }
}
```

实现要点：
- 外框：`Modifier.border(1.5.dp, color, RoundedCornerShape(8.dp))` + `Row` + `clip`
- 选中段：`primary` 填充 + `onPrimary` 字（分组行）；列表形态行可用 `surfaceVariant`/`primaryContainer` 轻填充 + 中性描边
- 未选中：透明底 + `primary` 或 `onSurfaceVariant` 字
- **禁止**筛选区再用与 `FilterRow` 相同的圆角胶囊 `FilterChip` 样式

- [ ] **Step 3: 状态 Tab 保持现有 FilterChip 胶囊样式不变**

- [ ] **Step 4: compileDebugKotlin SUCCESS**

---

### Task 5: 小程序对等

**Files:**
- Modify: `/Users/beike/Projects/renovation-ledger-miniprogram/utils/paymentList.js`
- Modify: `pages/list/list.js` / `list.wxml` / `list.wxss`
- Confirm: `utils/store.js` prefs 写入任意 `paymentListGroupBy` 字符串即可（含 `space`）

- [ ] **Step 1: paymentList.js**

```javascript
const PaymentListGroupBy = { STAGE: 'stage', CATEGORY: 'category', SPACE: 'space' }

function group(items, groupBy) {
  const map = {}
  ;(items || []).forEach((item) => {
    let key
    if (groupBy === PaymentListGroupBy.CATEGORY) {
      key = (item.category || item.stage || '').trim() || '未分类'
    } else if (groupBy === PaymentListGroupBy.SPACE) {
      key = (item.space || '').trim() || '未指定'
    } else {
      key = (item.stage || '').trim() || '未分类'
    }
    // ... push into map[key], then metrics
  })
  // sort keys, return groups
}
```

- [ ] **Step 2: list.wxml 顺序**

先 `chip-row filters`（状态 Tab），再 `controls`（两行 seg）：

```xml
<view class="chip-row filters">...</view>
<view class="filter-panel">
  <view class="seg seg-primary">
    <view class="seg-item {{groupBy==='stage'?'active':''}}" data-value="stage" bindtap="setGroupBy">按阶段</view>
    <view class="seg-item {{groupBy==='category'?'active':''}}" data-value="category" bindtap="setGroupBy">按分类</view>
    <view class="seg-item {{groupBy==='space'?'active':''}}" data-value="space" bindtap="setGroupBy">按空间</view>
  </view>
  <view class="seg seg-neutral">
    <view class="seg-item {{layout==='nested'?'active':''}}" data-value="nested" bindtap="setLayout">二级列表</view>
    <view class="seg-item {{layout==='flat'?'active':''}}" data-value="flat" bindtap="setLayout">单列表</view>
  </view>
</view>
```

- [ ] **Step 3: list.js**

- `setGroupBy` 接受 `space`
- `refresh` 里 `groupBy === 'space'` → taxonomy kind `spaces`
- prefs 读写 `paymentListGroupBy: 'space'`

- [ ] **Step 4: list.wxss**

- `.filter-panel`：与 chip 区间隔开
- `.seg`：`border: 1.5px solid`；`.seg-primary` 用主题色描边；`.seg-neutral` 用灰色描边
- `.seg-item.active`：填充；**不要**做成与 `.chip.active` 相同的全圆角胶囊

- [ ] **Step 5: 语法检查**

```bash
node --check /Users/beike/Projects/renovation-ledger-miniprogram/utils/paymentList.js
node --check /Users/beike/Projects/renovation-ledger-miniprogram/pages/list/list.js
```

Expected: 无输出、exit 0

---

### Task 6: 收尾验证 + oneClickSetup

- [ ] **Step 1: Android 单测**

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.PaymentListAggregatorTest
```

Expected: PASS

- [ ] **Step 2: oneClickSetup（必须真实执行）**

```bash
cd /Users/beike/Projects/renovation-ledger && sh oneClickSetup
```

Expected: `BUILD SUCCESSFUL` + adb `Success` + 启动 MainActivity  
`block_until_ms` ≥ 600000；禁止仅用 `compileDebugKotlin` 代替。

- [ ] **Step 3: 冒烟清单**

1. 切「按空间」→ 同空间项同组；无空间 →「未指定」  
2. 顺序：状态胶囊 Tab 在上，描边筛选在下  
3. 筛选样式 ≠ Tab 胶囊  
4. 小程序开发者工具同样点验  

---

## Spec coverage

| Spec 项 | Task |
|---------|------|
| 按空间分组 + 未指定 | 1, 5 |
| prefs space | 2, 5 |
| SPACE 图标 | 3, 5 |
| Tab 在上、筛选在下 | 4, 5 |
| 描边分段 vs 胶囊 | 4, 5 |
| oneClickSetup | 6 |

## 非目标（勿做）

金额口径、搜索、首页、Sheet/下拉筛选方案。
