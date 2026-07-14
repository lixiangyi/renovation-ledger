# Renovation Ledger Android MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Android 国内环境下交付可安装调试的装修记账 App：本地优先（Room 离线），实现总览指标、待花费明细、预算清单/详情、手动录入、预算健康色、统计图表、模板与导出；随后接入国内可用的云同步与语音/识图。

**Architecture:** 单模块 Android App（Kotlin + Jetpack Compose）。领域层纯 Kotlin（状态机 + 指标计算，单元测试优先）。数据层 Room 为唯一本地真相源；同步层后续以「推送远端变更 / 拉取合并」挂在 Repository 外，不污染 UI。UI 四 Tab：总览 / 清单 / 统计 / 我的。

**Tech Stack:**
- Kotlin 2.x、Jetpack Compose、Material 3、Navigation Compose、Hilt、Room、DataStore
- 图表：Vico
- 导出：CSV 首版（Excel 兼容），后续可换 POI
- 同步（后期）：LeanCloud（国内可用，Realtime + 用户体系）——不使用 Firebase
- 语音（后期）：Android `SpeechRecognizer` 或讯飞 ASR
- 识图（后期）：百度 OCR / 腾讯云 OCR（国内）
- 最低 SDK：26；目标 SDK：35；无 Google Play Services 强依赖

**Spec：** `docs/superpowers/specs/2026-07-13-renovation-ledger-design.md`

**调试方式（给你）：** Android Studio 打开工程 → 选模拟器或真机 → Run。无需小程序开发者工具。

---

## File structure

```
renovation-ledger/
├── docs/superpowers/...
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/renovation/ledger/
│       │   │   ├── RenovationApp.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── domain/
│       │   │   │   ├── model/
│       │   │   │   │   ├── Project.kt
│       │   │   │   │   ├── BudgetItem.kt
│       │   │   │   │   ├── Payment.kt
│       │   │   │   │   ├── ItemStatus.kt
│       │   │   │   │   ├── PaymentType.kt
│       │   │   │   │   ├── PaymentStatus.kt
│       │   │   │   │   └── HealthLevel.kt
│       │   │   │   ├── metrics/
│       │   │   │   │   ├── ProjectMetrics.kt
│       │   │   │   │   ├── MetricsCalculator.kt
│       │   │   │   │   └── HealthColorResolver.kt
│       │   │   │   └── template/
│       │   │   │       └── DefaultBudgetTemplate.kt
│       │   │   ├── data/
│       │   │   │   ├── local/
│       │   │   │   │   ├── AppDatabase.kt
│       │   │   │   │   ├── entity/...
│       │   │   │   │   ├── dao/...
│       │   │   │   │   └── mapper/...
│       │   │   │   ├── prefs/UserPrefs.kt
│       │   │   │   └── repo/ProjectRepository.kt
│       │   │   ├── ui/
│       │   │   │   ├── theme/Theme.kt
│       │   │   │   ├── navigation/AppNav.kt
│       │   │   │   ├── overview/...
│       │   │   │   ├── pending/...
│       │   │   │   ├── list/...
│       │   │   │   ├── detail/...
│       │   │   │   ├── entry/...
│       │   │   │   ├── stats/...
│       │   │   │   └── mine/...
│       │   │   └── di/AppModule.kt
│       │   └── res/...
│       └── test/java/com/renovation/ledger/
│           ├── MetricsCalculatorTest.kt
│           └── HealthColorResolverTest.kt
```

---

### Task 1: Android 工程脚手架

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/renovation/ledger/RenovationApp.kt`, `app/src/main/java/com/renovation/ledger/MainActivity.kt`

- [ ] **Step 1: 用 Android Studio「Empty Activity (Compose)」创建应用，ApplicationId `com.renovation.ledger`，保存到本仓库根目录（与 `docs/` 并列）**

若已有空 git 仓库，在根目录生成 Gradle 工程，保证 `docs/` 不被删掉。

- [ ] **Step 2: 在 `app/build.gradle.kts` 加入依赖**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.renovation.ledger"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.renovation.ledger"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.patrykandpatrick.vico:compose-m3:2.0.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
```

- [ ] **Step 3: 写最小可运行入口**

```kotlin
// RenovationApp.kt
package com.renovation.ledger

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RenovationApp : Application()
```

```kotlin
// MainActivity.kt
package com.renovation.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("装修记账") }
    }
}
```

Manifest 中 `application android:name=".RenovationApp"`。

- [ ] **Step 4: 运行 App**

Run: Android Studio Run，或 `./gradlew :app:assembleDebug`

Expected: 真机/模拟器打开白屏，显示「装修记账」

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties app/ docs/
git commit -m "chore: scaffold Android Compose app for renovation ledger"
```

---

### Task 2: 领域模型

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/domain/model/*.kt`

- [ ] **Step 1: 创建枚举与数据类**

```kotlin
// ItemStatus.kt
package com.renovation.ledger.domain.model

enum class ItemStatus { TO_BUY, PAYING, SETTLED }

// PaymentType.kt
enum class PaymentType { DEPOSIT, FINAL, OTHER }

// PaymentStatus.kt
enum class PaymentStatus { PAID, UNPAID }

// HealthLevel.kt
enum class HealthLevel { WITHIN, MILD_OVER, SEVERE_OVER }

// Project.kt
data class Project(
    val id: String,
    val name: String,
    val memberNames: List<String>,
)

// BudgetItem.kt
data class BudgetItem(
    val id: String,
    val projectId: String,
    val name: String,
    val stage: String,
    val category: String = "",
    val space: String = "",
    val budgetAmount: Long,          // 分（¥1.00 = 100）
    val contractAmount: Long? = null,
    val merchant: String = "",
    val isNewAddition: Boolean = false,
    val payments: List<Payment> = emptyList(),
)

fun BudgetItem.effectiveCost(): Long = contractAmount ?: budgetAmount

fun BudgetItem.deriveStatus(): ItemStatus {
    if (payments.isEmpty()) return ItemStatus.TO_BUY
    val allPaid = payments.all { it.status == PaymentStatus.PAID }
    val paidSum = payments.filter { it.status == PaymentStatus.PAID }.sumOf { it.amount }
    val target = effectiveCost()
    return if (allPaid && paidSum >= target) ItemStatus.SETTLED else ItemStatus.PAYING
}

// Payment.kt
data class Payment(
    val id: String,
    val budgetItemId: String,
    val type: PaymentType,
    val amount: Long,
    val status: PaymentStatus,
    val paidAtEpochMs: Long? = null,
    val note: String = "",
    val receiptUri: String? = null,
    val createdBy: String = "",
)
```

金额一律用 **分（Long）**，避免浮点误差；UI 层再格式化为元。

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/renovation/ledger/domain/model/
git commit -m "feat: add domain models for budget items and payments"
```

---

### Task 3: MetricsCalculator（TDD）

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/domain/metrics/ProjectMetrics.kt`
- Create: `app/src/main/java/com/renovation/ledger/domain/metrics/MetricsCalculator.kt`
- Test: `app/src/test/java/com/renovation/ledger/MetricsCalculatorTest.kt`

- [ ] **Step 1: 写失败测试（按公式断言，金额单位：分，1 元 = 100）**

```kotlin
package com.renovation.ledger

import com.renovation.ledger.domain.metrics.MetricsCalculator
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import org.junit.Assert.assertEquals
import org.junit.Test

class MetricsCalculatorTest {
    private val calc = MetricsCalculator()
    private fun y(yuan: Long) = yuan * 100L

    @Test
    fun calculates_paid_unpaid_tobuy_and_projected() {
        val items = listOf(
            BudgetItem(
                id = "1", projectId = "p", name = "大项", stage = "泥木",
                budgetAmount = y(200_000),
                contractAmount = y(430_000),
                payments = listOf(
                    Payment("p1", "1", PaymentType.DEPOSIT, y(430_000), PaymentStatus.PAID)
                )
            ),
            BudgetItem(
                id = "2", projectId = "p", name = "尾款项", stage = "泥木",
                budgetAmount = y(100_000),
                contractAmount = y(50_000),
                payments = listOf(
                    Payment("p2", "2", PaymentType.FINAL, y(50_000), PaymentStatus.UNPAID)
                )
            ),
            BudgetItem(
                id = "3", projectId = "p", name = "待买", stage = "软装",
                budgetAmount = y(100_000)
            )
        )
        val m = calc.calculate(items)
        assertEquals(y(400_000), m.totalBudget)
        assertEquals(y(430_000), m.paidActual)
        assertEquals(y(50_000), m.unpaidFinal)
        assertEquals(y(100_000), m.toBuyAmount)
        assertEquals(y(150_000), m.pendingSpend)
        assertEquals(y(580_000), m.projectedTotal)
        assertEquals(y(30_000), m.currentOverspend)
        assertEquals(y(180_000), m.projectedOverspend)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.MetricsCalculatorTest`

Expected: FAIL（类不存在或方法不存在）

- [ ] **Step 3: 实现计算器**

```kotlin
// ProjectMetrics.kt
package com.renovation.ledger.domain.metrics

data class ProjectMetrics(
    val totalBudget: Long,
    val paidActual: Long,
    val unpaidFinal: Long,
    val toBuyAmount: Long,
    val pendingSpend: Long,
    val currentOverspend: Long,
    val projectedTotal: Long,
    val projectedOverspend: Long,
)

// MetricsCalculator.kt
package com.renovation.ledger.domain.metrics

import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.ItemStatus
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.deriveStatus
import com.renovation.ledger.domain.model.effectiveCost

class MetricsCalculator {
    fun calculate(items: List<BudgetItem>): ProjectMetrics {
        val totalBudget = items.sumOf { it.budgetAmount }
        val paidActual = items.sumOf { item ->
            item.payments.filter { it.status == PaymentStatus.PAID }.sumOf { it.amount }
        }
        val unpaidFinal = items.sumOf { item ->
            if (item.deriveStatus() != ItemStatus.PAYING) 0L
            else item.payments.filter { it.status == PaymentStatus.UNPAID }.sumOf { it.amount }
        }
        val toBuyAmount = items
            .filter { it.deriveStatus() == ItemStatus.TO_BUY }
            .sumOf { it.effectiveCost() }
        val pendingSpend = unpaidFinal + toBuyAmount
        val projectedTotal = items.sumOf { it.effectiveCost() }
        return ProjectMetrics(
            totalBudget = totalBudget,
            paidActual = paidActual,
            unpaidFinal = unpaidFinal,
            toBuyAmount = toBuyAmount,
            pendingSpend = pendingSpend,
            currentOverspend = paidActual - totalBudget,
            projectedTotal = projectedTotal,
            projectedOverspend = projectedTotal - totalBudget,
        )
    }
}
```

注意：`PAYING` 且存在「未付」付款时，该项虽有 contract，**不计入 toBuy**（状态已不是 TO_BUY）。`projectedTotal` 仍用各 item 的 `effectiveCost()` 求和（含已实付项的合同价）。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.MetricsCalculatorTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/renovation/ledger/domain/metrics/ app/src/test/
git commit -m "feat: add MetricsCalculator with unit tests"
```

---

### Task 4: 预算健康色解析（TDD）

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/domain/metrics/HealthColorResolver.kt`
- Test: `app/src/test/java/com/renovation/ledger/HealthColorResolverTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.renovation.ledger

import com.renovation.ledger.domain.metrics.HealthColorResolver
import com.renovation.ledger.domain.model.HealthLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthColorResolverTest {
    private val r = HealthColorResolver()

    @Test
    fun within_budget() {
        assertEquals(HealthLevel.WITHIN, r.resolve(overspend = -1, totalBudget = 100))
        assertEquals(HealthLevel.WITHIN, r.resolve(overspend = 0, totalBudget = 100))
    }

    @Test
    fun mild_and_severe() {
        // 10% of 100 = 10
        assertEquals(HealthLevel.MILD_OVER, r.resolve(overspend = 10, totalBudget = 100))
        assertEquals(HealthLevel.MILD_OVER, r.resolve(overspend = 15, totalBudget = 100))
        assertEquals(HealthLevel.SEVERE_OVER, r.resolve(overspend = 16, totalBudget = 100))
    }

    @Test
    fun zero_budget_neutral_as_within() {
        assertEquals(HealthLevel.WITHIN, r.resolve(overspend = 1, totalBudget = 0))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.HealthColorResolverTest`

Expected: FAIL

- [ ] **Step 3: 实现**

```kotlin
package com.renovation.ledger.domain.metrics

import com.renovation.ledger.domain.model.HealthLevel

class HealthColorResolver {
    fun resolve(overspend: Long, totalBudget: Long): HealthLevel {
        if (totalBudget <= 0L || overspend <= 0L) return HealthLevel.WITHIN
        val rate = overspend.toDouble() / totalBudget.toDouble()
        return if (rate <= 0.15) HealthLevel.MILD_OVER else HealthLevel.SEVERE_OVER
    }
}
```

- [ ] **Step 4: 跑测试确认通过 → Commit**

```bash
git add app/src/main/java/com/renovation/ledger/domain/metrics/HealthColorResolver.kt app/src/test/java/com/renovation/ledger/HealthColorResolverTest.kt
git commit -m "feat: add health color resolver (green/orange/red thresholds)"
```

---

### Task 5: Room 实体、DAO、Database

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/data/local/entity/ProjectEntity.kt`
- Create: `app/src/main/java/com/renovation/ledger/data/local/entity/BudgetItemEntity.kt`
- Create: `app/src/main/java/com/renovation/ledger/data/local/entity/PaymentEntity.kt`
- Create: `app/src/main/java/com/renovation/ledger/data/local/dao/*.kt`
- Create: `app/src/main/java/com/renovation/ledger/data/local/AppDatabase.kt`
- Create: `app/src/main/java/com/renovation/ledger/data/local/mapper/Mappers.kt`
- Create: `app/src/main/java/com/renovation/ledger/di/AppModule.kt`

- [ ] **Step 1: 定义 Entity（字段与 domain 对齐，payment 独立表）**

```kotlin
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val memberNamesCsv: String,
)

@Entity(
    tableName = "budget_items",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class BudgetItemEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val stage: String,
    val category: String,
    val space: String,
    val budgetAmount: Long,
    val contractAmount: Long?,
    val merchant: String,
    val isNewAddition: Boolean,
)

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(
        entity = BudgetItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["budgetItemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("budgetItemId")]
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    val budgetItemId: String,
    val type: String,
    val amount: Long,
    val status: String,
    val paidAtEpochMs: Long?,
    val note: String,
    val receiptUri: String?,
    val createdBy: String,
)
```

- [ ] **Step 2: DAO**

```kotlin
@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects LIMIT 1")
    fun observeDefault(): Flow<ProjectEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)
}

@Dao
interface BudgetItemDao {
    @Query("SELECT * FROM budget_items WHERE projectId = :projectId")
    fun observeByProject(projectId: String): Flow<List<BudgetItemEntity>>

    @Query("SELECT * FROM budget_items WHERE id = :id")
    fun observeById(id: String): Flow<BudgetItemEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: BudgetItemEntity)

    @Delete
    suspend fun delete(item: BudgetItemEntity)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE budgetItemId = :itemId")
    fun observeByItem(itemId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE budgetItemId IN (:itemIds)")
    fun observeByItems(itemIds: List<String>): Flow<List<PaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(payment: PaymentEntity)

    @Delete
    suspend fun delete(payment: PaymentEntity)
}
```

- [ ] **Step 3: Database + Hilt 提供**

```kotlin
@Database(
    entities = [ProjectEntity::class, BudgetItemEntity::class, PaymentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun budgetItemDao(): BudgetItemDao
    abstract fun paymentDao(): PaymentDao
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "renovation.db").build()

    @Provides fun projectDao(db: AppDatabase) = db.projectDao()
    @Provides fun budgetItemDao(db: AppDatabase) = db.budgetItemDao()
    @Provides fun paymentDao(db: AppDatabase) = db.paymentDao()
}
```

- [ ] **Step 4: Mapper domain ↔ entity（手写，枚举用 name）**

- [ ] **Step 5: Compile**

Run: `./gradlew :app:compileDebugKotlin`

Expected: SUCCESS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/renovation/ledger/data/ app/src/main/java/com/renovation/ledger/di/
git commit -m "feat: add Room schema for projects, items, payments"
```

---

### Task 6: ProjectRepository + 首次启动种子项目

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/data/repo/ProjectRepository.kt`
- Create: `app/src/main/java/com/renovation/ledger/domain/template/DefaultBudgetTemplate.kt`

- [ ] **Step 1: Repository 聚合 items + payments → domain BudgetItem**

```kotlin
@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val itemDao: BudgetItemDao,
    private val paymentDao: PaymentDao,
) {
    fun observeProjectWithItems(): Flow<Pair<Project, List<BudgetItem>>> = ...
    suspend fun ensureDefaultProject() { /* 若空则创建「我家装修」 */ }
    suspend fun upsertItem(item: BudgetItem) { ... }
    suspend fun upsertPayment(payment: Payment) { ... }
    suspend fun deleteItem(id: String) { ... }
}
```

用 `combine(projectFlow, itemsFlow, paymentsFlow)` 组装。

- [ ] **Step 2: 默认模板（若干阶段若干项，金额为示例预算）**

```kotlin
object DefaultBudgetTemplate {
    fun items(projectId: String): List<BudgetItem> = listOf(
        BudgetItem(id = UUID.randomUUID().toString(), projectId = projectId,
            name = "水电改造", stage = "水电", budgetAmount = 20_000_00L),
        BudgetItem(id = UUID.randomUUID().toString(), projectId = projectId,
            name = "瓷砖", stage = "泥木", budgetAmount = 15_000_00L),
        // … 再补 8～12 条常见项
    )
}
```

`ensureDefaultProject()` 创建项目后可询问是否导入模板（首版直接导入，便于调试）。

- [ ] **Step 3: 在 `RenovationApp` 或启动 ViewModel 调用 `ensureDefaultProject()`**

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/renovation/ledger/data/repo/ app/src/main/java/com/renovation/ledger/domain/template/
git commit -m "feat: add ProjectRepository and default budget template"
```

---

### Task 7: 导航壳 + 四 Tab

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/ui/navigation/AppNav.kt`
- Create: `app/src/main/java/com/renovation/ledger/ui/theme/Theme.kt`
- Modify: `MainActivity.kt`

- [ ] **Step 1: 定义路由**

```kotlin
sealed class Route(val path: String) {
    data object Overview : Route("overview")
    data object List : Route("list")
    data object Stats : Route("stats")
    data object Mine : Route("mine")
    data object PendingSpend : Route("pending")
    data class ItemDetail(val id: String) : Route("item/{id}") {
        companion object { const val pattern = "item/{id}" }
        fun create(id: String) = "item/$id"
    }
    data object ManualEntry : Route("entry/manual")
}
```

- [ ] **Step 2: Scaffold + NavigationBar 四 Tab，中间页用占位 Text**

- [ ] **Step 3: Run App，切换四个 Tab**

Expected: 可切换「总览/清单/统计/我的」

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/renovation/ledger/ui/
git commit -m "feat: add bottom navigation shell with four tabs"
```

---

### Task 8: 总览页（首页指标 + 待花费展开）

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/ui/overview/OverviewViewModel.kt`
- Create: `app/src/main/java/com/renovation/ledger/ui/overview/OverviewScreen.kt`
- Create: `app/src/main/java/com/renovation/ledger/ui/common/MoneyFormat.kt`
- Create: `app/src/main/java/com/renovation/ledger/ui/common/HealthColors.kt`

- [ ] **Step 1: ViewModel 暴露 `ProjectMetrics` + items + healthLevels + `healthColorEnabled`**

- [ ] **Step 2: UI 按 spec §8.1**
  - 总预算大字
  - 两列：已实付（含当前超支文案）、待花费（列内两行：待付尾款、待购买）
  - 点击待花费展开：两块列表各最多 5 条 + 「查看全部待花费明细」
  - 预计花费卡片 + 双进度条
  - 最近付款（从 payments 按时间倒序取 5 条）
  - FAB「＋记一笔」→ 暂跳手动录入

- [ ] **Step 3: `MoneyFormat.formatYuan(cents: Long): String`**

```kotlin
fun formatYuan(cents: Long): String {
    val yuan = cents / 100.0
    return "¥ " + String.format("%,.0f", yuan)
}
```

- [ ] **Step 4: 健康色：DataStore 读 `health_color_enabled`，关闭则中性色**

- [ ] **Step 5: Run，用模板数据核对数字**

Expected: 总览数字与 `MetricsCalculator` 一致

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/renovation/ledger/ui/overview/ app/src/main/java/com/renovation/ledger/ui/common/
git commit -m "feat: implement overview dashboard with expandable pending spend"
```

---

### Task 9: 待花费明细二级页

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/ui/pending/PendingSpendScreen.kt`

- [ ] **Step 1: 完整列出待付尾款项、待购买项（不截断），点击进详情**

- [ ] **Step 2: 从 Overview「查看全部」导航到此页**

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/renovation/ledger/ui/pending/
git commit -m "feat: add pending spend detail screen"
```

---

### Task 10: 预算清单页

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/ui/list/BudgetListScreen.kt`
- Create: `app/src/main/java/com/renovation/ledger/ui/list/BudgetListViewModel.kt`

- [ ] **Step 1: 按 stage 分组；Tab 过滤 TO_BUY / PAYING / SETTLED**

- [ ] **Step 2: 卡片展示预算→合同、付款摘要、状态色**

- [ ] **Step 3: ＋新增 → 手动录入（新建项模式）**

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/renovation/ledger/ui/list/
git commit -m "feat: add budget list grouped by stage with status filters"
```

---

### Task 11: 预算项详情 + 添加付款（手动）

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/ui/detail/ItemDetailScreen.kt`
- Create: `app/src/main/java/com/renovation/ledger/ui/detail/ItemDetailViewModel.kt`
- Create: `app/src/main/java/com/renovation/ledger/ui/entry/ManualEntryScreen.kt`
- Create: `app/src/main/java/com/renovation/ledger/ui/entry/ManualEntryViewModel.kt`

- [ ] **Step 1: 详情页展示状态、预算/合同、付款列表、标记已结清、编辑、删除**

- [ ] **Step 2: 「添加付款」打开 ManualEntry（付款模式），字段：类型/金额/已付未付/日期/备注；凭证 URI 可先可选**

- [ ] **Step 3: 首次已付付款后 `deriveStatus` 变为 PAYING；全部付清可点「标记已结清」（写入一笔补差或仅本地标记——首版用：**若未付清则提示先补付款；若已付合计 ≥ effectiveCost 则允许标记**，标记时把剩余未付改为已付或删除未付）

首版结清规则（写死避免歧义）：
1. 用户点「标记已结清」
2. 将所有 `UNPAID` 付款改为 `PAID`（金额不变），若已付合计仍 &lt; effectiveCost，自动补一笔 `OTHER/PAID` 差额

- [ ] **Step 4: 手动新建预算项表单（名称/阶段/预算金额）**

- [ ] **Step 5: 手动跑通：新建项 → 付定金 → 首页待花费变化**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/renovation/ledger/ui/detail/ app/src/main/java/com/renovation/ledger/ui/entry/
git commit -m "feat: add item detail and manual payment/budget entry"
```

---

### Task 12: 统计页（饼图 + 柱状图）

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/ui/stats/StatsScreen.kt`
- Create: `app/src/main/java/com/renovation/ledger/ui/stats/StatsViewModel.kt`
- Create: `app/src/main/java/com/renovation/ledger/domain/metrics/GroupAggregator.kt`
- Test: `app/src/test/java/com/renovation/ledger/GroupAggregatorTest.kt`

- [ ] **Step 1: `GroupAggregator.byStage(items)` 返回每组 budget/paid/projected**

- [ ] **Step 2: 单元测试至少 1 个分组断言**

- [ ] **Step 3: UI：维度切换 stage/category/space；饼图数据源切换 paid/projected/budget；Vico 柱状三列；列表 + TOP5 超支项**

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/renovation/ledger/ui/stats/ app/src/main/java/com/renovation/ledger/domain/metrics/GroupAggregator.kt app/src/test/java/com/renovation/ledger/GroupAggregatorTest.kt
git commit -m "feat: add stats screen with pie and bar charts"
```

---

### Task 13: 我的页 — 健康色开关 + CSV 导出

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/data/prefs/UserPrefs.kt`
- Create: `app/src/main/java/com/renovation/ledger/ui/mine/MineScreen.kt`
- Create: `app/src/main/java/com/renovation/ledger/data/export/CsvExporter.kt`

- [ ] **Step 1: DataStore boolean `health_color_enabled` 默认 true；Switch 绑定**

- [ ] **Step 2: 导出 CSV 到 `MediaStore` Downloads，分享 Intent**

CSV 列：项名称,阶段,分类,预算元,合同元,状态,付款类型,付款金额元,付款状态,日期,记账人

- [ ] **Step 3: 显示当前成员名（本地字符串，默认同机「我」；协作为后期）**

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/renovation/ledger/ui/mine/ app/src/main/java/com/renovation/ledger/data/prefs/ app/src/main/java/com/renovation/ledger/data/export/
git commit -m "feat: add health-color toggle and CSV export"
```

---

### Task 14: 录入入口三选一壳（语音/识图占位）

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/ui/entry/EntryChooserSheet.kt`
- Create: `app/src/main/java/com/renovation/ledger/ui/entry/ConfirmEntryScreen.kt`

- [ ] **Step 1: FAB 弹出：手动 / 语音 / 图片识别**

- [ ] **Step 2: 手动走现有 ManualEntry**

- [ ] **Step 3: 语音、图片 → 进入 `ConfirmEntryScreen` 前先 Toast「下一迭代接入」，或空实现解析后进确认页骨架**

本 Task **必须**落地确认页骨架（字段与 spec §8.7 一致），语音/OCR 可先手动把解析结果写成假数据验证确认流。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/renovation/ledger/ui/entry/
git commit -m "feat: add entry chooser and confirm-entry screen shell"
```

---

### Task 15: 国内云同步（LeanCloud）— 协作

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/data/sync/LeanCloudSync.kt`
- Create: `app/src/main/java/com/renovation/ledger/ui/mine/InviteScreen.kt`
- Modify: `ProjectRepository.kt`（同步钩子）

- [ ] **Step 1: 注册 LeanCloud 国内节点应用，把 AppId/Key 放 `local.properties`，勿提交密钥**

- [ ] **Step 2: 手机号或用户名密码登录；项目 `objectId`；成员关系表**

- [ ] **Step 3: 本地变更 upsert 后推送；Realtime 订阅拉回写入 Room（后写覆盖 + Snackbar「对方刚改过」）**

- [ ] **Step 4: 邀请码加入项目（最多 3 人）**

- [ ] **Step 5: 真机两台或双用户登录验证实时更新**

- [ ] **Step 6: Commit（不含密钥）**

```bash
git add app/src/main/java/com/renovation/ledger/data/sync/ app/src/main/java/com/renovation/ledger/ui/mine/InviteScreen.kt
git commit -m "feat: add LeanCloud sync and project invite for couple collab"
```

---

### Task 16: 语音录入

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/data/voice/SpeechParser.kt`
- Create: `app/src/main/java/com/renovation/ledger/ui/entry/VoiceEntryScreen.kt`
- Test: `app/src/test/java/com/renovation/ledger/SpeechParserTest.kt`

- [ ] **Step 1: `SpeechParser.parse(text): DraftEntry` 规则/简单关键词（定金/尾款/预算/已付/未付 + 数字）**

- [ ] **Step 2: 单测：「瓷砖定金三千五已付」→ amount、type、paid**

- [ ] **Step 3: UI 调 `SpeechRecognizer`，结果进 ConfirmEntryScreen**

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/renovation/ledger/data/voice/ app/src/main/java/com/renovation/ledger/ui/entry/VoiceEntryScreen.kt app/src/test/java/com/renovation/ledger/SpeechParserTest.kt
git commit -m "feat: add voice entry with confirm step"
```

---

### Task 17: 图片识别录入

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/data/ocr/OcrClient.kt`
- Create: `app/src/main/java/com/renovation/ledger/ui/entry/ImageEntryScreen.kt`

- [ ] **Step 1: 接入百度或腾讯 OCR（密钥 local.properties）；失败则仅保存图片为凭证并进确认页手填**

- [ ] **Step 2: 识别金额/日期预填 ConfirmEntry；原图作 receiptUri**

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/renovation/ledger/data/ocr/ app/src/main/java/com/renovation/ledger/ui/entry/ImageEntryScreen.kt
git commit -m "feat: add image OCR entry with confirm step"
```

---

## Spec coverage checklist

| Spec 项 | Task |
|---------|------|
| 指标公式 / 超支 / 待花费 | 3, 8, 9 |
| 状态机待购买/付款中/已结清 | 2, 11 |
| 首页两列 + 展开 ≤5 + 二级页 | 8, 9 |
| 清单 / 详情 / 手动录入 | 10, 11 |
| 健康色可关 | 4, 8, 13 |
| 统计饼图柱状图 | 12 |
| 模板 | 6 |
| 导出 | 13 |
| 协作实时同步 | 15 |
| 语音 / 识图 + 确认页 | 14, 16, 17 |
| Android 优先 / 无 Firebase | 全文 |
| 小程序 | 不在本计划 |

---

## 执行说明

**建议执行顺序：** Task 1→14 先做出可每日自用的本地版；15–17 再开协作与智能录入。

**Plan complete and saved to `docs/superpowers/plans/2026-07-13-renovation-ledger-android-mvp.md`.**

两种执行方式：

1. **Subagent-Driven（推荐）** — 每个 Task 派生子代理，我在中间做 review  
2. **Inline Execution** — 本会话按 `executing-plans` 连续做，设检查点  

你选哪种？若希望先只做「本地可跑通、暂不同步/语音/OCR」，也可以说 **只做 Task 1–14**。
