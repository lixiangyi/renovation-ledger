package com.renovation.ledger.ui.mine

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.export.CsvExporter
import com.renovation.ledger.data.export.ManualCsvExportStore
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.prefs.UserProfile
import com.renovation.ledger.data.profile.AvatarStorage
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.domain.importing.DcjzCsvImporter
import com.renovation.ledger.domain.importing.ImportDeduper
import com.renovation.ledger.domain.importing.ImportDraftStore
import com.renovation.ledger.domain.metrics.HealthColorResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MineUiState(
    val projectName: String = "",
    val memberNames: List<String> = emptyList(),
    val projects: List<com.renovation.ledger.domain.model.Project> = emptyList(),
    val profile: UserProfile = UserProfile(),
    val healthColorEnabled: Boolean = true,
    val mildOverMaxPercent: Int = HealthColorResolver.DEFAULT_MILD_OVER_MAX_PERCENT,
    val exportMessage: String? = null,
    val profileSavedMessage: String? = null,
    val actionMessage: String? = null,
)

sealed class CsvImportResult {
    data class Ready(val count: Int) : CsvImportResult()
    data class Failed(val message: String) : CsvImportResult()
}

@HiltViewModel
class MineViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val userPrefs: UserPrefs,
    private val csvExporter: CsvExporter,
    private val manualCsvExportStore: ManualCsvExportStore,
    private val importDraftStore: ImportDraftStore,
    private val avatarStorage: AvatarStorage,
) : ViewModel() {

    private val actionMessage = MutableStateFlow<String?>(null)

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
            memberNames = project.memberNames,
            projects = projects,
            profile = profile,
            healthColorEnabled = healthColorEnabled,
            mildOverMaxPercent = mildPercent,
        )
    }.combine(actionMessage) { state, message ->
        state.copy(actionMessage = message)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MineUiState(),
    )

    fun clearActionMessage() {
        actionMessage.value = null
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.moveProjectToTrash(projectId)
                .onSuccess { actionMessage.value = "已移入垃圾箱" }
                .onFailure { err -> actionMessage.value = err.message ?: "删除失败" }
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

    fun saveNickname(nickname: String) {
        viewModelScope.launch {
            val old = userPrefs.userProfile.first().nickname
            userPrefs.setNickname(nickname)
            projectRepository.renameMember(old, nickname.trim().ifBlank { "我" })
        }
    }

    fun updateAvatar(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val path = avatarStorage.saveFromUri(uri)
                userPrefs.setAvatarPath(path)
            }
        }
    }

    fun clearAvatar() {
        viewModelScope.launch {
            userPrefs.setAvatarPath(null)
        }
    }

    fun updateMemberNickname(index: Int, nickname: String) {
        viewModelScope.launch {
            val old = uiState.value.memberNames.getOrNull(index) ?: return@launch
            projectRepository.updateMemberNickname(index, nickname)
            // 若改的是当前登录角色昵称，同步资料
            if (old == uiState.value.profile.nickname) {
                userPrefs.setNickname(nickname)
            }
        }
    }

    fun addMember(nickname: String) {
        viewModelScope.launch {
            projectRepository.addMember(nickname)
        }
    }

    fun exportAndShare(context: Context) {
        viewModelScope.launch {
            val (project, items) = projectRepository.snapshotCurrentProjectWithItems()
            val csv = csvExporter.export(items)
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
