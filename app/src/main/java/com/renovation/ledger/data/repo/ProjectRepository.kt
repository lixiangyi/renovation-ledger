package com.renovation.ledger.data.repo

import androidx.room.withTransaction
import com.renovation.ledger.data.autosave.AutosaveSnapshot
import com.renovation.ledger.data.autosave.AutosaveSummary
import com.renovation.ledger.data.autosave.LedgerAutosave
import com.renovation.ledger.data.local.AppDatabase
import com.renovation.ledger.data.local.dao.BudgetItemDao
import com.renovation.ledger.data.local.dao.PaymentDao
import com.renovation.ledger.data.local.dao.ProjectDao
import com.renovation.ledger.data.local.entity.ProjectEntity
import com.renovation.ledger.data.local.mapper.toDomain
import com.renovation.ledger.data.local.mapper.toEntity
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.trash.TrashEntry
import com.renovation.ledger.data.trash.TrashStore
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import com.renovation.ledger.domain.model.Project
import com.renovation.ledger.domain.model.effectiveCost
import com.renovation.ledger.domain.template.DefaultBudgetTemplate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ProjectRepository @Inject constructor(
    private val db: AppDatabase,
    private val projectDao: ProjectDao,
    private val itemDao: BudgetItemDao,
    private val paymentDao: PaymentDao,
    private val userPrefs: UserPrefs,
    private val ledgerAutosave: LedgerAutosave,
    private val trashStore: TrashStore,
) {
    fun observeProjects(): Flow<List<Project>> =
        projectDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeProjectWithItems(): Flow<Pair<Project, List<BudgetItem>>> =
        resolveCurrentProjectEntity().flatMapLatest { projectEntity ->
            if (projectEntity == null) {
                emptyFlow()
            } else {
                itemsForProject(projectEntity.toDomain())
            }
        }

    private fun resolveCurrentProjectEntity(): Flow<ProjectEntity?> =
        combine(
            userPrefs.currentProjectId,
            projectDao.observeAll(),
        ) { preferredId, all ->
            when {
                all.isEmpty() -> null
                preferredId != null -> all.firstOrNull { it.id == preferredId } ?: all.first()
                else -> all.first()
            }
        }

    private fun itemsForProject(project: Project): Flow<Pair<Project, List<BudgetItem>>> =
        itemDao.observeByProject(project.id).flatMapLatest { itemEntities ->
            if (itemEntities.isEmpty()) {
                flowOf(project to emptyList())
            } else {
                paymentDao.observeByItems(itemEntities.map { it.id }).map { paymentEntities ->
                    val paymentsByItemId = paymentEntities.groupBy { it.budgetItemId }
                    val items = itemEntities.map { entity ->
                        entity.toDomain(
                            payments = paymentsByItemId[entity.id]
                                .orEmpty()
                                .map { it.toDomain() },
                        )
                    }
                    project to items
                }
            }
        }

    suspend fun ensureDefaultProject() {
        val existing = projectDao.getAll()
        if (existing.isNotEmpty()) {
            val preferred = userPrefs.currentProjectId.first()
            if (preferred == null || existing.none { it.id == preferred }) {
                userPrefs.setCurrentProjectId(existing.first().id)
            }
            return
        }

        val projectId = UUID.randomUUID().toString()
        val project = Project(
            id = projectId,
            name = "我家装修",
            memberNames = listOf("我"),
        )
        projectDao.upsert(project.toEntity())
        userPrefs.setCurrentProjectId(projectId)

        val summary = ledgerAutosave.probeSummary()
        val hasBackup = summary != null && summary.itemCount > 0
        if (hasBackup) return

        DefaultBudgetTemplate.items(projectId).forEach { item ->
            itemDao.upsert(item.toEntity())
        }
        autosaveNow()
    }

    suspend fun switchProject(projectId: String) {
        val entity = projectDao.getById(projectId) ?: return
        userPrefs.setCurrentProjectId(entity.id)
    }

    /** 新建空账本并切换过去；不种默认模板。 */
    suspend fun createProject(name: String, nickname: String = "我"): Project {
        val trimmed = name.trim().ifBlank { "新账本" }
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            memberNames = listOf(nickname.trim().ifBlank { "我" }),
        )
        projectDao.upsert(project.toEntity())
        userPrefs.setCurrentProjectId(project.id)
        return project
    }

    /** 导入前新建账本并切换。 */
    suspend fun createProjectForImport(nickname: String = "我"): Project {
        val stamp = SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date())
        return createProject(name = "导入账本 $stamp", nickname = nickname)
    }

    suspend fun renameProject(projectId: String, name: String) {
        val entity = projectDao.getById(projectId) ?: return
        val trimmed = name.trim().ifBlank { return }
        projectDao.upsert(entity.toDomain().copy(name = trimmed).toEntity())
        autosaveNow()
    }

    suspend fun renameCurrentProject(name: String) {
        val id = currentProjectEntity()?.id ?: return
        renameProject(id, name)
    }

    suspend fun upsertItem(item: BudgetItem) {
        db.withTransaction {
            itemDao.upsert(item.toEntity())
            item.payments.forEach { paymentDao.upsert(it.toEntity()) }
        }
        autosaveNow()
    }

    suspend fun upsertItems(items: List<BudgetItem>) {
        if (items.isEmpty()) return
        db.withTransaction {
            itemDao.upsertAll(items.map { it.toEntity() })
            items.forEach { item ->
                item.payments.forEach { paymentDao.upsert(it.toEntity()) }
            }
        }
        autosaveNow()
    }

    /** 导出用：直接读库快照，避免 Flow 瞬时陈旧。 */
    suspend fun snapshotCurrentProjectWithItems(): Pair<Project, List<BudgetItem>> {
        val preferredId = userPrefs.currentProjectId.first()
        val all = projectDao.getAll()
        val entity = when {
            all.isEmpty() -> error("当前没有账本可导出")
            preferredId != null -> all.firstOrNull { it.id == preferredId } ?: all.first()
            else -> all.first()
        }
        return snapshotProjectWithItems(entity.id)
    }

    suspend fun snapshotProjectWithItems(projectId: String): Pair<Project, List<BudgetItem>> {
        val entity = projectDao.getById(projectId) ?: error("账本不存在")
        val project = entity.toDomain()
        val itemEntities = itemDao.observeByProject(project.id).first()
        if (itemEntities.isEmpty()) return project to emptyList()
        val paymentEntities = paymentDao.observeByItems(itemEntities.map { it.id }).first()
        val paymentsByItemId = paymentEntities.groupBy { it.budgetItemId }
        val items = itemEntities.map { e ->
            e.toDomain(payments = paymentsByItemId[e.id].orEmpty().map { it.toDomain() })
        }
        return project to items
    }

    suspend fun listTrash(): List<TrashEntry> = trashStore.listEntries()

    /**
     * 导出完整 CSV → 写 trash 索引 → 硬删 project（CASCADE）。
     * 删当前本切到剩余；删尽则新建「新账本」。
     */
    suspend fun moveProjectToTrash(projectId: String): Result<Unit> = runCatching {
        val (project, items) = snapshotProjectWithItems(projectId)
        val payments = items.flatMap { it.payments }
        val itemsBare = items.map { it.copy(payments = emptyList()) }
        val csv = trashStore.encodeSnapshot(AutosaveSnapshot(project, itemsBare, payments))
        trashStore.writeTrash(
            projectId = project.id,
            name = project.name,
            itemCount = items.size,
            csvText = csv,
        )
        runCatching {
            projectDao.deleteById(projectId)
        }.onFailure { err ->
            runCatching { trashStore.removeEntry(projectId) }
            throw IllegalStateException("已备份但删除失败，请重试", err)
        }
        val remaining = projectDao.getAll()
        if (remaining.isNotEmpty()) {
            val preferred = userPrefs.currentProjectId.first()
            if (preferred == null || preferred == projectId || remaining.none { it.id == preferred }) {
                userPrefs.setCurrentProjectId(remaining.first().id)
            }
        } else {
            val nickname = userPrefs.userProfile.first().nickname
            createProject(name = "新账本", nickname = nickname)
        }
        runCatching { autosaveNow() }
    }

    suspend fun restoreFromTrash(entryId: String): Result<Unit> = runCatching {
        val csvText = trashStore.readCsvText(entryId)
            ?: error("垃圾箱备份文件不存在或已损坏")
        val snapshot = trashStore.decodeCsv(csvText)
            ?: error("垃圾箱备份无法解析")
        var project = snapshot.project
        var items = snapshot.items
        var payments = snapshot.payments
        if (projectDao.getById(project.id) != null) {
            val newId = UUID.randomUUID().toString()
            project = project.copy(id = newId)
            items = items.map { it.copy(projectId = newId) }
        }
        db.withTransaction {
            projectDao.upsert(project.toEntity())
            if (items.isNotEmpty()) {
                itemDao.upsertAll(items.map { it.toEntity() })
            }
            payments.forEach { paymentDao.upsert(it.toEntity()) }
        }
        trashStore.removeEntry(entryId)
        userPrefs.setCurrentProjectId(project.id)
        runCatching { autosaveNow() }
        Unit
    }

    suspend fun purgeTrashEntry(entryId: String): Result<Unit> = runCatching {
        trashStore.removeEntry(entryId)
    }

    suspend fun upsertPayment(payment: Payment) {
        paymentDao.upsert(payment.toEntity())
        autosaveNow()
    }

    suspend fun deletePayment(payment: Payment) {
        paymentDao.delete(payment.toEntity())
        autosaveNow()
    }

    suspend fun renameMember(oldName: String, newName: String) {
        val entity = currentProjectEntity() ?: return
        val project = entity.toDomain()
        val trimmed = newName.trim().ifBlank { return }
        val names = project.memberNames.toMutableList()
        val index = names.indexOfFirst { it == oldName }
        if (index >= 0) {
            names[index] = trimmed
        } else if (names.isEmpty()) {
            names.add(trimmed)
        } else {
            names[0] = trimmed
        }
        projectDao.upsert(
            project.copy(memberNames = names.distinct()).toEntity(),
        )
        autosaveNow()
    }

    suspend fun updateMemberNickname(index: Int, newName: String) {
        val entity = currentProjectEntity() ?: return
        val project = entity.toDomain()
        val trimmed = newName.trim().ifBlank { return }
        val names = project.memberNames.toMutableList()
        if (index !in names.indices) return
        names[index] = trimmed
        projectDao.upsert(project.copy(memberNames = names.distinct()).toEntity())
        autosaveNow()
    }

    suspend fun addMember(nickname: String) {
        val entity = currentProjectEntity() ?: return
        val project = entity.toDomain()
        val trimmed = nickname.trim().ifBlank { return }
        if (trimmed in project.memberNames) return
        projectDao.upsert(
            project.copy(memberNames = project.memberNames + trimmed).toEntity(),
        )
        autosaveNow()
    }

    suspend fun deleteItem(id: String) {
        val entity = itemDao.observeById(id).first() ?: return
        itemDao.delete(entity)
        autosaveNow()
    }

    fun observeItem(id: String): Flow<BudgetItem?> =
        combine(
            itemDao.observeById(id),
            paymentDao.observeByItem(id),
        ) { entity, payments ->
            entity?.toDomain(payments.map { it.toDomain() })
        }

    suspend fun settleItem(item: BudgetItem) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            item.payments
                .filter { it.status == PaymentStatus.UNPAID }
                .forEach { payment ->
                    paymentDao.upsert(
                        payment.copy(
                            status = PaymentStatus.PAID,
                            paidAtEpochMs = now,
                        ).toEntity(),
                    )
                }
            val paidSum = item.payments
                .filter { it.status == PaymentStatus.PAID }
                .sumOf { it.amount } +
                item.payments
                    .filter { it.status == PaymentStatus.UNPAID }
                    .sumOf { it.amount }
            val gap = item.effectiveCost() - paidSum
            if (gap > 0L) {
                val nickname = userPrefs.userProfile.first().nickname
                paymentDao.upsert(
                    Payment(
                        id = UUID.randomUUID().toString(),
                        budgetItemId = item.id,
                        type = PaymentType.OTHER,
                        amount = gap,
                        status = PaymentStatus.PAID,
                        paidAtEpochMs = now,
                        note = "结清补差",
                        createdBy = nickname,
                    ).toEntity(),
                )
            }
        }
        autosaveNow()
    }

    suspend fun restoreFromAutosave(): Result<AutosaveSummary> = runCatching {
        val snapshot = ledgerAutosave.loadPreferred()
            ?: error("没有可用的自动备份")
        if (snapshot.items.isEmpty()) error("自动备份里没有预算项")
        db.withTransaction {
            paymentDao.deleteAll()
            itemDao.deleteAll()
            projectDao.upsert(snapshot.project.toEntity())
            itemDao.upsertAll(snapshot.items.map { it.toEntity() })
            snapshot.payments.forEach { paymentDao.upsert(it.toEntity()) }
        }
        userPrefs.setCurrentProjectId(snapshot.project.id)
        ledgerAutosave.save(
            AutosaveSnapshot(
                snapshot.project,
                snapshot.items.map { it.copy(payments = emptyList()) },
                snapshot.payments,
            )
        )
        AutosaveSummary(snapshot.items.size, snapshot.payments.size)
    }

    private suspend fun currentProjectEntity(): ProjectEntity? {
        val preferred = userPrefs.currentProjectId.first()
        if (preferred != null) {
            projectDao.getById(preferred)?.let { return it }
        }
        return projectDao.getDefault()
    }

    private suspend fun autosaveNow() {
        val (project, items) = observeProjectWithItems().first()
        val payments = items.flatMap { it.payments }
        val itemsBare = items.map { it.copy(payments = emptyList()) }
        ledgerAutosave.save(AutosaveSnapshot(project, itemsBare, payments))
    }
}
