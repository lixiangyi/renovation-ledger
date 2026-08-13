# 支付清单与首页优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按 spec `docs/superpowers/specs/2026-07-22-payment-list-and-home-optimize-design.md` 分三期落地：P0 支付清单口径/分组/数量/未付/抽屉，P1 首页合并模块，P2 搜索与标签图标；Android + 小程序对等。

**Architecture:** 清单聚合与角标/未付/超支百分比抽到纯 Kotlin / JS 工具（便于单测）；UI 只消费聚合结果。分组维度与列表形态进 DataStore / 小程序 prefs。搜索与标签图标为独立页面与 prefs 扩展，不改动账本核心 schema（自定义图标存本地路径）。

**Tech Stack:** Android Kotlin + Compose + Hilt + Room/DataStore + JUnit；微信小程序 JS/WXML；收尾 `sh oneClickSetup`（本会话偏好=要）。

**Git：** 工作区禁止自动 git。计划中 **不** 执行 commit；仅当用户明确要求时再提交。

---

## File map

### 新建（Android）

| 文件 | 职责 |
|------|------|
| `app/src/main/java/com/renovation/ledger/domain/list/PaymentListAggregator.kt` | 分组、大类指标、Tab 计数、新增角标判定 |
| `app/src/main/java/com/renovation/ledger/domain/metrics/UnpaidCalculator.kt` | 未付合计 = max(0, 合同−已付) |
| `app/src/main/java/com/renovation/ledger/domain/metrics/ProjectedSpendPercent.kt` | 预计超支/节省 % |
| `app/src/main/java/com/renovation/ledger/domain/search/ItemNameSearch.kt` | 名称模糊匹配 + 历史去重裁剪 |
| `app/src/main/java/com/renovation/ledger/ui/search/SearchGuideScreen.kt` | 搜索引导页 UI |
| `app/src/main/java/com/renovation/ledger/ui/search/SearchGuideViewModel.kt` | 搜索 VM |
| `app/src/test/java/com/renovation/ledger/PaymentListAggregatorTest.kt` | P0 聚合单测 |
| `app/src/test/java/com/renovation/ledger/UnpaidCalculatorTest.kt` | 未付单测 |
| `app/src/test/java/com/renovation/ledger/ProjectedSpendPercentTest.kt` | P1 百分比单测 |
| `app/src/test/java/com/renovation/ledger/ItemNameSearchTest.kt` | P2 搜索单测 |

### 修改（Android）

| 文件 | 改动 |
|------|------|
| `data/prefs/UserPrefs.kt` | `paymentListGroupBy` / `paymentListLayout` / `searchHistory` |
| `ui/list/BudgetListViewModel.kt` | 接 prefs + Aggregator |
| `ui/list/BudgetListScreen.kt` | 改名、控件、大类/Tab/平铺 UI |
| `ui/navigation/AppNav.kt` | 路由 search；Tab 文案可保持「清单」或改为「支付」——**标题栏用「支付清单」** |
| `ui/overview/OverviewScreen.kt` | 抽屉 3/4；P1 合并卡；P2 搜索入口 |
| `ui/detail/ItemDetailViewModel.kt` + `ItemDetailScreen.kt` | 未付主口径；加付款回填 |
| `ui/entry/ManualEntryScreen.kt` (+ VM 若需要) | 未付回填 |
| `data/prefs/TaxonomyPrefs.kt` + taxonomy UI | 图标字段 |
| `domain/taxonomy/*` | 选项模型带 icon |

### 小程序

| 文件 | 改动 |
|------|------|
| `utils/paymentList.js`（新建） | 与 Aggregator 对齐的聚合 |
| `utils/unpaid.js` / `utils/search.js`（新建或并入 model） | 未付、搜索 |
| `pages/list/list.js|wxml|wxss` | P0 UI |
| `pages/overview/*` | 抽屉 75vw；P1 模块；P2 入口 |
| `pages/detail/*` / `pages/entry/*` | 未付 |
| `pages/search/*`（新建）+ `app.json` | 搜索页 |
| `pages/taxonomy/*` + `utils/taxonomy.js` | 图标 |
| `utils/store.js` prefs | 偏好键 |

---

## P0

### Task 1: PaymentListAggregator（纯逻辑 + 测试）

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/domain/list/PaymentListAggregator.kt`
- Test: `app/src/test/java/com/renovation/ledger/PaymentListAggregatorTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.renovation.ledger

import com.renovation.ledger.domain.list.PaymentListAggregator
import com.renovation.ledger.domain.list.PaymentListGroupBy
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentListAggregatorTest {

    @Test
    fun `group by category puts 灯具 under its own group`() {
        val items = listOf(
            item("客厅主灯", stage = "软装", category = "灯具", budget = 0,
                payments = listOf(pay(3_200_00, PaymentStatus.PAID))),
            item("窗帘", stage = "软装", category = "软装", budget = 5_000_00),
        )
        val groups = PaymentListAggregator.group(items, PaymentListGroupBy.CATEGORY)
        assertEquals(setOf("灯具", "软装"), groups.map { it.key }.toSet())
        val lights = groups.first { it.key == "灯具" }
        assertEquals(3_200_00L, lights.paidSum)
        assertEquals(0L, lights.budgetSum)
        assertEquals(0L, lights.projectedSum) // effectiveCost = budget 0 when no contract
    }

    @Test
    fun `paidSum is not effectiveCost when contract differs`() {
        val items = listOf(
            item("橱柜", stage = "主材", category = "全屋定制", budget = 10_000_00,
                contract = 12_000_00,
                payments = listOf(pay(5_000_00, PaymentStatus.PAID), pay(7_000_00, PaymentStatus.UNPAID))),
        )
        val g = PaymentListAggregator.group(items, PaymentListGroupBy.STAGE).single()
        assertEquals(5_000_00L, g.paidSum)
        assertEquals(10_000_00L, g.budgetSum)
        assertEquals(12_000_00L, g.projectedSum)
        assertEquals(1, g.paidItemCount)
        assertEquals(1, g.pendingItemCount)
        assertEquals(7_000_00L, g.pendingAmountSum) // unpaid payment only (item is PAYING)
    }

    @Test
    fun `new badge when budget zero and paid positive`() {
        val item = item("临时灯", stage = "软装", category = "灯具", budget = 0,
            payments = listOf(pay(100_00, PaymentStatus.PAID)), isNew = false)
        assertTrue(PaymentListAggregator.showNewBadge(item))
    }

    @Test
    fun `new badge when manual flag even if budget positive`() {
        val item = item("中途加", stage = "软装", category = "软装", budget = 1_000_00, isNew = true)
        assertTrue(PaymentListAggregator.showNewBadge(item))
        assertFalse(
            PaymentListAggregator.showNewBadge(
                item("普通", stage = "软装", category = "软装", budget = 1_000_00, isNew = false),
            ),
        )
    }

    @Test
    fun `filter tab counts use item count and effectiveCost sum`() {
        val items = listOf(
            item("a", stage = "软装", category = "软装", budget = 1_000_00), // TO_BUY
            item("b", stage = "软装", category = "软装", budget = 2_000_00,
                payments = listOf(pay(500_00, PaymentStatus.PAID), pay(1_500_00, PaymentStatus.UNPAID))),
        )
        val tabs = PaymentListAggregator.tabStats(items)
        assertEquals(2, tabs.all.count)
        assertEquals(1, tabs.toBuy.count)
        assertEquals(1_000_00L, tabs.toBuy.amountSum)
        assertEquals(1, tabs.paying.count)
        assertEquals(2_000_00L, tabs.paying.amountSum)
    }

    private fun item(
        name: String,
        stage: String,
        category: String,
        budget: Long,
        contract: Long? = null,
        payments: List<Payment> = emptyList(),
        isNew: Boolean = false,
    ) = BudgetItem(
        id = name, projectId = "p", name = name, stage = stage, category = category,
        budgetAmount = budget, contractAmount = contract, isNewAddition = isNew, payments = payments,
    )

    private fun pay(amount: Long, status: PaymentStatus) = Payment(
        id = amount.toString(), itemId = "x", type = PaymentType.OTHER,
        amount = amount, status = status,
    )
}
```

- [ ] **Step 2: 跑测确认失败**

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.PaymentListAggregatorTest
```

Expected: FAIL（类不存在）

- [ ] **Step 3: 实现 Aggregator**

```kotlin
package com.renovation.ledger.domain.list

import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.ItemStatus
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.deriveStatus
import com.renovation.ledger.domain.model.effectiveCost

enum class PaymentListGroupBy { STAGE, CATEGORY }

enum class PaymentListLayout { NESTED, FLAT }

data class PaymentListGroupMetrics(
    val key: String,
    val items: List<BudgetItem>,
    val paidSum: Long,
    val budgetSum: Long,
    val projectedSum: Long,
    val paidItemCount: Int,
    val pendingItemCount: Int,
    val pendingAmountSum: Long,
)

data class FilterTabStat(val count: Int, val amountSum: Long)

data class FilterTabStats(
    val all: FilterTabStat,
    val toBuy: FilterTabStat,
    val paying: FilterTabStat,
    val settled: FilterTabStat,
)

object PaymentListAggregator {

    fun showNewBadge(item: BudgetItem): Boolean {
        if (item.isNewAddition) return true
        val paid = item.payments.filter { it.status == PaymentStatus.PAID }.sumOf { it.amount }
        return item.budgetAmount == 0L && paid > 0L
    }

    fun group(items: List<BudgetItem>, groupBy: PaymentListGroupBy): List<PaymentListGroupMetrics> {
        return items.groupBy { item ->
            val raw = when (groupBy) {
                PaymentListGroupBy.STAGE -> item.stage
                PaymentListGroupBy.CATEGORY -> item.category.ifBlank { item.stage }
            }
            raw.ifBlank { "未分类" }
        }.map { (key, groupItems) -> metrics(key, groupItems) }
            .sortedBy { it.key }
    }

    fun tabStats(items: List<BudgetItem>): FilterTabStats {
        fun stat(pred: (BudgetItem) -> Boolean): FilterTabStat {
            val subset = items.filter(pred)
            return FilterTabStat(
                count = subset.size,
                amountSum = subset.sumOf { it.effectiveCost() },
            )
        }
        return FilterTabStats(
            all = FilterTabStat(items.size, items.sumOf { it.effectiveCost() }),
            toBuy = stat { it.deriveStatus() == ItemStatus.TO_BUY },
            paying = stat { it.deriveStatus() == ItemStatus.PAYING },
            settled = stat { it.deriveStatus() == ItemStatus.SETTLED },
        )
    }

    private fun metrics(key: String, items: List<BudgetItem>): PaymentListGroupMetrics {
        var paidSum = 0L
        var budgetSum = 0L
        var projectedSum = 0L
        var paidItemCount = 0
        var pendingItemCount = 0
        var pendingAmountSum = 0L
        items.forEach { item ->
            val paid = item.payments.filter { it.status == PaymentStatus.PAID }.sumOf { it.amount }
            val unpaid = item.payments.filter { it.status == PaymentStatus.UNPAID }.sumOf { it.amount }
            paidSum += paid
            budgetSum += item.budgetAmount
            projectedSum += item.effectiveCost()
            if (paid > 0L) paidItemCount++
            val status = item.deriveStatus()
            val isPending = status == ItemStatus.TO_BUY || unpaid > 0L
            if (isPending) {
                pendingItemCount++
                pendingAmountSum += when (status) {
                    ItemStatus.TO_BUY -> item.effectiveCost()
                    else -> unpaid
                }
            }
        }
        return PaymentListGroupMetrics(
            key = key,
            items = items,
            paidSum = paidSum,
            budgetSum = budgetSum,
            projectedSum = projectedSum,
            paidItemCount = paidItemCount,
            pendingItemCount = pendingItemCount,
            pendingAmountSum = pendingAmountSum,
        )
    }
}
```

- [ ] **Step 4: 跑测通过**

同 Step 2，Expected: PASS

---

### Task 2: UnpaidCalculator

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/domain/metrics/UnpaidCalculator.kt`
- Test: `app/src/test/java/com/renovation/ledger/UnpaidCalculatorTest.kt`

- [ ] **Step 1: 失败测试**

```kotlin
@Test
fun `display unpaid uses contract minus paid when contract set`() {
    assertEquals(7_000_00L, UnpaidCalculator.displayUnpaid(contract = 12_000_00, paid = 5_000_00))
    assertEquals(0L, UnpaidCalculator.displayUnpaid(contract = 5_000_00, paid = 5_000_00))
    assertEquals(0L, UnpaidCalculator.displayUnpaid(contract = 3_000_00, paid = 5_000_00))
}

@Test
fun `without contract fall back to unpaid payment rows sum`() {
    assertEquals(2_000_00L, UnpaidCalculator.displayUnpaid(contract = null, paid = 1_000_00, unpaidRowsSum = 2_000_00))
}

@Test
fun `suggest unpaid remainder for new unpaid row`() {
    assertEquals(7_000_00L, UnpaidCalculator.suggestUnpaidAmount(contract = 12_000_00, paid = 5_000_00))
    assertEquals(0L, UnpaidCalculator.suggestUnpaidAmount(contract = null, paid = 5_000_00))
}
```

- [ ] **Step 2: 跑测失败 → Step 3 实现**

```kotlin
object UnpaidCalculator {
    fun displayUnpaid(contract: Long?, paid: Long, unpaidRowsSum: Long = 0L): Long {
        if (contract != null) return (contract - paid).coerceAtLeast(0L)
        return unpaidRowsSum.coerceAtLeast(0L)
    }

    fun suggestUnpaidAmount(contract: Long?, paid: Long): Long {
        if (contract == null) return 0L
        return (contract - paid).coerceAtLeast(0L)
    }
}
```

- [ ] **Step 4: 跑测 PASS**

---

### Task 3: UserPrefs 清单偏好

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/data/prefs/UserPrefs.kt`

- [ ] **Step 1: 增加键与 API**

```kotlin
enum class /* reuse domain */ // 在 UserPrefs 内存储字符串：
// payment_list_group_by: "stage" | "category"  default stage
// payment_list_layout: "nested" | "flat"      default nested

val paymentListGroupBy: Flow<PaymentListGroupBy> = ...
val paymentListLayout: Flow<PaymentListLayout> = ...
suspend fun setPaymentListGroupBy(value: PaymentListGroupBy)
suspend fun setPaymentListLayout(value: PaymentListLayout)
```

映射：`stage`↔`STAGE`，`category`↔`CATEGORY`，`nested`↔`NESTED`，`flat`↔`FLAT`。

- [ ] **Step 2: 编译**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: SUCCESS

---

### Task 4: BudgetListViewModel 接入聚合

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/ui/list/BudgetListViewModel.kt`

- [ ] **Step 1: 重写 uiState combine**

- 注入 `UserPrefs`
- `combine(projectItems, filter, expanded, mild, groupBy, layout)`
- 过滤后用 `PaymentListAggregator.group` / `tabStats`
- `BudgetListStageGroup` 字段改为：`paidSum`、`budgetSum`、`projectedSum`、`paidItemCount`、`pendingItemCount`、`pendingAmountSum`；删除把 effectiveCost 叫作 `actualSum` 的用法（可保留 `projectedSum` 命名）
- `BudgetListItemUi` 增加 `showNewBadge: Boolean = PaymentListAggregator.showNewBadge(item)`
- 暴露 `setGroupBy` / `setLayout`

- [ ] **Step 2: 编译通过**

---

### Task 5: BudgetListScreen UI（P0 清单）

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/ui/list/BudgetListScreen.kt`

- [ ] **Step 1: 标题「支付清单」；空态「暂无支付项」**

- [ ] **Step 2: Top 增加两个菜单/Chip**

- 「按阶段 / 按分类」
- 「二级列表 / 单列表」

- [ ] **Step 3: FilterRow 展示条数·金额**

例：`Text("${label}\n${stat.count} · ${formatYuan(stat.amountSum)}")`（Chip 内两行或 `label + " ${count}·金额"`）

- [ ] **Step 4: 大类头文案**

```
实际支付 {paid} · 预算 {budget} · 预计要支付 {projected}
已支付 {paidItemCount}项 · {paidSum文案}
待支付 {pendingItemCount}项 · {pendingAmount文案}
```

健康色：用 `projectedSum - budgetSum` 相对预算（与旧 overspend 一致，但基于预计要支付）。

- [ ] **Step 5: 项行「新增」用 `showNewBadge`**

- [ ] **Step 6: `layout == FLAT`**

不渲染折叠，按 `groups` 顺序输出：可选 sticky 分隔条（大类名）+ 全部 items。

- [ ] **Step 7: 编译**

---

### Task 6: 详情 + 录入未付自动算（Android）

**Files:**
- Modify: `ItemDetailViewModel.kt` — `unpaidSum = UnpaidCalculator.displayUnpaid(contract, paid, unpaidRowsSum)`
- Modify: `ItemDetailScreen.kt` — 新增/编辑付款对话框：当用户选「未付」且金额为空时，预填 `fenToYuanString(UnpaidCalculator.suggestUnpaidAmount(...))`；用户可改
- Modify: `ManualEntryScreen.kt` / `ManualEntryViewModel.kt` — 同样：有合同价时，添加未付行默认金额 = suggest

- [ ] **Step 1: ViewModel 改口径 + 编译**
- [ ] **Step 2: Dialog 预填逻辑 + 编译**

---

### Task 7: 抽屉宽度 3/4（Android）

**Files:**
- Modify: `OverviewScreen.kt` `LedgerDrawerContent` / `ModalDrawerSheet`

- [ ] **Step 1:** `Modifier = Modifier.fillMaxWidth(3f / 4f)`（现状 `2f/3f`）
- [ ] **Step 2: 编译**

---

### Task 8: 小程序 P0 对等

**Files:**
- Create: `renovation-ledger-miniprogram/utils/paymentList.js`（逻辑对齐 Aggregator）
- Create: `renovation-ledger-miniprogram/utils/unpaid.js`
- Modify: `utils/store.js` prefs：`paymentListGroupBy`、`paymentListLayout`
- Modify: `pages/list/list.js|wxml|wxss` — 标题在 `list.json` navigationBarTitleText「支付清单」；分组切换；指标；Tab 数量；新增角标；flat 模式
- Modify: `pages/detail/detail.js|wxml`、`pages/entry/entry.js` — 未付
- Modify: `pages/overview/overview.wxss` — `.drawer { width: 75vw; max-width: 75vw; }`

- [ ] **Step 1: 实现 `paymentList.js` 并在 list 页调用**
- [ ] **Step 2: UI 文案与指标对齐 Android**
- [ ] **Step 3: 未付 + 抽屉宽度**
- [ ] **Step 4: 开发者工具手动点验：按分类出现灯具大类**

---

### Task 9: P0 收尾验证

- [ ] **Step 1: Android 单测**

```bash
./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.PaymentListAggregatorTest --tests com.renovation.ledger.UnpaidCalculatorTest
```

Expected: PASS

- [ ] **Step 2: `sh oneClickSetup`**（仓库根，`block_until_ms` ≥ 600000）

Expected: `BUILD SUCCESSFUL` + adb 安装成功

- [ ] **Step 3: 真机冒烟**

1. 标签管理加分类「灯具」，改一项分类为灯具 → 支付清单切「按分类」见灯具大类  
2. 大类「实际支付」≠ 有合同时的预计要支付  
3. 抽屉约 3/4 屏  

---

## P1

### Task 10: ProjectedSpendPercent

**Files:**
- Create: `domain/metrics/ProjectedSpendPercent.kt`
- Test: `ProjectedSpendPercentTest.kt`

```kotlin
data class ProjectedSpendPercentResult(
    val percent: Int?,       // null if budget==0
    val gap: Long,           // projected - budget
    val label: String,       // 预计超支 4% / 预计节省 4% / 持平 / —
)

fun compute(projectedTotal: Long, totalBudget: Long): ProjectedSpendPercentResult
```

- [ ] 测试：预算 42000000 分、预计 43800000 → percent=4、label 含「预计超支」
- [ ] 预算 0 → percent null、label「—」
- [ ] 实现并 PASS

---

### Task 11: 首页合并模块（双端）

**Files:**
- Modify: `OverviewScreen.kt` — 删除 `BudgetProgressSection` 调用；将原总预算 Card + `ProjectedSpendCard` 合并为单卡（总预算大号 + 预计花费 + percent label + 差额）
- Modify: `pages/overview/overview.wxml|js|wxss` — 删除两行 progress；合并展示

- [ ] Android UI + 编译  
- [ ] 小程序对等  
- [ ] `sh oneClickSetup`  

---

## P2

### Task 12: ItemNameSearch + SearchHistory prefs

**Files:**
- Create: `domain/search/ItemNameSearch.kt`
- Test: `ItemNameSearchTest.kt`
- Modify: `UserPrefs.kt` — `searchHistory: Flow<List<String>>`；`addSearchHistory(q)`（去重置顶、最多 20）；`clearSearchHistory()`

```kotlin
fun matchByName(items: List<BudgetItem>, query: String): List<BudgetItem> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return items.filter { it.name.contains(q, ignoreCase = true) }
}

fun pushHistory(existing: List<String>, query: String, max: Int = 20): List<String>
```

- [ ] 测试 PASS → prefs 接入

---

### Task 13: 搜索引导页（双端）

**Files:**
- Create: `ui/search/SearchGuideScreen.kt` + `SearchGuideViewModel.kt`
- Modify: `AppNav.kt` — `Route.Search`；Overview TopBar 搜索 Icon → navigate
- Create: `pages/search/search.js|wxml|wxss|json`；`app.json` 注册（非 tab）
- Modify: overview 入口

行为：无输入显示历史；输入即时 `matchByName`；点结果进详情；点历史填入查询。

- [ ] Android  
- [ ] 小程序  

---

### Task 14: 标签图标（双端）

**Files:**
- Modify: `TaxonomyPrefs` — 选项从纯 `List<String>` 升级为可解析 JSON 列表元素 `{ "value":"灯具","iconKey":"light"|"iconPath":"..." }`；**读路径兼容旧纯字符串列表**
- Modify: `TaxonomyManageScreen` — 编辑弹窗增加「选预置 / 相册」；预置约 12～20 个 Material Icon 或 emoji key
- 自定义图：复制到 `filesDir/taxonomy_icons/`，存相对路径
- 支付清单大类头：若当前分组 key 在 catalog 中有 icon则显示
- 小程序：`taxonomy.js` 同结构；`chooseImage` + 本地路径；list 大类头展示

- [ ] 迁移兼容旧 prefs  
- [ ] UI 添加/修改图标  
- [ ] 清单大类展示  
- [ ] `sh oneClickSetup` 最终验收  

---

## Spec coverage check

| Spec 项 | Task |
|---------|------|
| 改名支付清单 | 5, 8 |
| 分组 stage/category | 1, 4, 5, 8 |
| 三金额口径 | 1, 4, 5, 8 |
| Tab 条数+金额 | 1, 4, 5, 8 |
| 大类已付/待付数量 | 1, 4, 5, 8 |
| 新增角标规则 | 1, 4, 5, 8 |
| 二级/单列表 | 3, 4, 5, 8 |
| 未付自动算 | 2, 6, 8 |
| 抽屉 3/4 | 7, 8 |
| 首页合并 + 删进度条 | 10, 11 |
| 搜索 | 12, 13 |
| 标签图标 | 14 |
| 主题统一 | 沿用现有 Theme（无单独任务） |
| oneClickSetup | 9, 11, 14 |

**非目标已排除：** 按空间分组、跨字段搜索、主题重构。

---

## 执行说明

优先顺序：**Task 1 → 9（P0）→ 10–11（P1）→ 12–14（P2）**。每完成一期做一次 `oneClickSetup` 与双端冒烟后再开下一期。
