package com.renovation.ledger.ui.mine

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.autosave.AutosaveCsvCodec
import com.renovation.ledger.data.autosave.AutosaveSnapshot
import com.renovation.ledger.data.export.ManualCsvExportStore
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.prefs.UserProfile
import com.renovation.ledger.data.remote.MemberDto
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.data.sync.LedgerSyncRepository
import com.renovation.ledger.domain.importing.DcjzCsvImporter
import com.renovation.ledger.domain.importing.ImportDeduper
import com.renovation.ledger.domain.importing.ImportDraftStore
import com.renovation.ledger.domain.ledger.DeleteLedgerCopy
import com.renovation.ledger.domain.ledger.DeleteLedgerDialogCopy
import com.renovation.ledger.domain.ledger.LedgerRoleGates
import com.renovation.ledger.domain.ledger.LedgerVisibility
import com.renovation.ledger.domain.metrics.HealthColorResolver
import com.renovation.ledger.domain.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MineMemberRow(
    val nickname: String,
    val role: String? = null,
    val isSelf: Boolean = false,
)

data class MineUiState(
    val projectName: String = "",
    val cloudLedgerId: String? = null,
    val members: List<MineMemberRow> = emptyList(),
    val visibleLedgers: List<com.renovation.ledger.domain.ledger.VisibleLedger> = emptyList(),
    val profile: UserProfile = UserProfile(),
    val healthColorEnabled: Boolean = true,
    val mildOverMaxPercent: Int = HealthColorResolver.DEFAULT_MILD_OVER_MAX_PERCENT,
    val showHealthColorSettings: Boolean = true,
    val actionMessage: String? = null,
    val jwt: String? = null,
    val cloudUserId: String? = null,
)

sealed class CsvImportResult {
    data class Ready(val count: Int) : CsvImportResult()
    data class Failed(val message: String) : CsvImportResult()
}

@HiltViewModel
class MineViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val userPrefs: UserPrefs,
    private val autosaveCsvCodec: AutosaveCsvCodec,
    private val manualCsvExportStore: ManualCsvExportStore,
    private val importDraftStore: ImportDraftStore,
    private val ledgerSync: LedgerSyncRepository,
) : ViewModel() {

    private val actionMessage = MutableStateFlow<String?>(null)
    private val cloudMembers = MutableStateFlow<List<MemberDto>>(emptyList())

    val uiState = combine(
        projectRepository.observeProjectWithItems(),
        projectRepository.observeProjects(),
        userPrefs.healthColorEnabled,
        userPrefs.mildOverMaxPercent,
        userPrefs.userProfile,
    ) { projectWithItems, projects, healthColorEnabled, mildPercent, profile ->
        val (project, _) = projectWithItems
        MineUiState(
            projectName = project.name,
            cloudLedgerId = project.cloudLedgerId,
            members = project.memberNames.map { MineMemberRow(nickname = it) },
            profile = profile,
            healthColorEnabled = healthColorEnabled,
            mildOverMaxPercent = mildPercent,
        ) to projects
    }.combine(actionMessage) { pair, message ->
        pair.first.copy(actionMessage = message) to pair.second
    }.combine(userPrefs.jwt) { pair, jwt ->
        pair.first.copy(jwt = jwt) to pair.second
    }.combine(userPrefs.cloudUserId) { pair, cloudUserId ->
        pair.first.copy(cloudUserId = cloudUserId) to pair.second
    }.combine(ledgerSync.cloudSummaries) { pair, summaries ->
        val state = pair.first
        val projects = pair.second
        val role = LedgerRoleGates.roleOf(state.cloudLedgerId, summaries)
        val loggedIn = !state.jwt.isNullOrBlank()
        val hasCloud = !state.cloudLedgerId.isNullOrBlank()
        state.copy(
            visibleLedgers = LedgerVisibility.visible(
                projects = projects,
                cloudSummaries = summaries,
                loggedIn = loggedIn,
            ),
            showHealthColorSettings = LedgerRoleGates.canManageInviteAndHealth(
                role,
                loggedIn,
                hasCloud,
            ),
        ) to summaries
    }.combine(cloudMembers) { pair, remoteMembers ->
        val state = pair.first
        val members = if (!state.cloudLedgerId.isNullOrBlank() && remoteMembers.isNotEmpty()) {
            remoteMembers.map { m ->
                MineMemberRow(
                    nickname = m.nickname.ifBlank { "我" },
                    role = m.role,
                    isSelf = m.userId == state.cloudUserId,
                )
            }
        } else {
            state.members.map { row ->
                row.copy(isSelf = row.nickname == state.profile.nickname)
            }
        }
        state.copy(members = members)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MineUiState(),
    )

    init {
        viewModelScope.launch {
            projectRepository.observeProjectWithItems().collect { (project, _) ->
                refreshCloudMembers(project.cloudLedgerId)
            }
        }
    }

    private suspend fun refreshCloudMembers(cloudLedgerId: String?) {
        val cloudId = cloudLedgerId?.trim().orEmpty()
        if (cloudId.isEmpty() || userPrefs.jwt.first().isNullOrBlank()) {
            cloudMembers.value = emptyList()
            return
        }
        runCatching { ledgerSync.listMembers(cloudId) }
            .onSuccess { list ->
                cloudMembers.value = list
                val names = com.renovation.ledger.domain.ledger.LedgerOwnerDisplay
                    .namesOwnerFirst(list)
                if (names.isNotEmpty()) {
                    projectRepository.replaceMemberNames(names)
                }
            }
            .onFailure { cloudMembers.value = emptyList() }
    }

    fun clearActionMessage() {
        actionMessage.value = null
    }

    fun deleteDialogCopy(project: Project): DeleteLedgerDialogCopy {
        val summaries = ledgerSync.cloudSummaries.value
        val role = LedgerRoleGates.roleOf(project.cloudLedgerId, summaries)
        return DeleteLedgerCopy.forRole(
            role = role,
            ledgerName = project.name,
            hasCloudId = !project.cloudLedgerId.isNullOrBlank(),
        )
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.moveProjectToTrash(projectId)
                .onSuccess { actionMessage.value = "已移入垃圾箱" }
                .onFailure { err ->
                    actionMessage.value = err.message ?: "删除失败"
                }
        }
    }

    fun setHealthColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPrefs.setHealthColorEnabled(enabled)
        }
    }

    fun setMildOverMaxPercent(percent: Int) {
        viewModelScope.launch {
            userPrefs.setMildOverMaxPercent(percent)
        }
    }

    fun exportAndShare(context: Context) {
        viewModelScope.launch {
            val (project, items) = projectRepository.snapshotCurrentProjectWithItems()
            val csv = autosaveCsvCodec.encode(
                AutosaveSnapshot(
                    project = project,
                    items = items.map { it.copy(payments = emptyList()) },
                    payments = items.flatMap { it.payments },
                ),
            )
            val file = manualCsvExportStore.writeShareFile(project.name, csv)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "导出 CSV"))
        }
    }

    fun prepareImportFromCsv(csvText: String, sourceLabel: String): CsvImportResult {
        return try {
            val parsed = DcjzCsvImporter.parse(csvText)
            if (parsed.isEmpty()) {
                CsvImportResult.Failed("未能解析到有效行，请检查 CSV 是否为本 App 或旧版导出")
            } else {
                val drafts = ImportDeduper.dedupe(parsed)
                importDraftStore.set(drafts, sourceLabel)
                CsvImportResult.Ready(drafts.size)
            }
        } catch (e: Exception) {
            CsvImportResult.Failed(
                e.message ?: "无法解析 CSV，请使用本 App「导出 CSV」或旧装修记账导出",
            )
        }
    }
}
