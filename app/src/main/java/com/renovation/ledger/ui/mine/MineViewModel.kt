package com.renovation.ledger.ui.mine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.autosave.AutosaveCsvCodec
import com.renovation.ledger.data.autosave.AutosaveSnapshot
import com.renovation.ledger.data.export.ManualCsvExportStore
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.prefs.UserProfile
import com.renovation.ledger.data.profile.AvatarStorage
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.domain.importing.DcjzCsvImporter
import com.renovation.ledger.domain.importing.ImportDeduper
import com.renovation.ledger.domain.importing.ImportDraftStore
import com.renovation.ledger.domain.metrics.HealthColorResolver
import com.renovation.ledger.data.sync.InviteShareText
import com.renovation.ledger.data.sync.LedgerSyncRepository
import com.renovation.ledger.data.remote.ApiErrorMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val cloudBusy: Boolean = false,
    val cloudBusyLabel: String? = null,
    val jwt: String? = null,
    val phone: String? = null,
    val currentUnbound: Boolean = false,
    val lastInviteCode: String? = null,
)

sealed class CsvImportResult {
    data class Ready(val count: Int) : CsvImportResult()
    data class Failed(val message: String) : CsvImportResult()
}

@HiltViewModel
class MineViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val projectRepository: ProjectRepository,
    private val userPrefs: UserPrefs,
    private val autosaveCsvCodec: AutosaveCsvCodec,
    private val manualCsvExportStore: ManualCsvExportStore,
    private val importDraftStore: ImportDraftStore,
    private val avatarStorage: AvatarStorage,
    private val ledgerSync: LedgerSyncRepository,
) : ViewModel() {

    private val actionMessage = MutableStateFlow<String?>(null)
    private val cloudBusyLabel = MutableStateFlow<String?>(null)
    private val lastInviteCode = MutableStateFlow<String?>(null)

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
            currentUnbound = project.cloudLedgerId.isNullOrBlank(),
        )
    }.combine(actionMessage) { state, message ->
        state.copy(actionMessage = message)
    }.combine(cloudBusyLabel) { state, busyLabel ->
        state.copy(cloudBusy = busyLabel != null, cloudBusyLabel = busyLabel)
    }.combine(userPrefs.jwt) { state, jwt ->
        state.copy(jwt = jwt)
    }.combine(userPrefs.phone) { state, phone ->
        state.copy(phone = phone)
    }.combine(lastInviteCode) { state, code ->
        state.copy(lastInviteCode = code)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MineUiState(),
    )

    fun clearActionMessage() {
        actionMessage.value = null
    }

    private suspend fun <T> withCloudBusy(label: String, block: suspend () -> T): Result<T> {
        if (cloudBusyLabel.value != null) {
            return Result.failure(IllegalStateException("请等待当前操作完成"))
        }
        cloudBusyLabel.value = label
        return try {
            Result.success(block())
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            cloudBusyLabel.value = null
        }
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

    fun devLogin() {
        viewModelScope.launch {
            runCatching { ledgerSync.devLogin() }
                .onSuccess { actionMessage.value = "已登录" }
                .onFailure { actionMessage.value = it.message ?: "登录失败" }
        }
    }

    fun wechatLogin(activity: android.app.Activity?) {
        if (activity == null) {
            actionMessage.value = "尚未配置微信 AppId，开发包请用开发登录"
            return
        }
        val err = com.renovation.ledger.data.auth.WeChatAppAuth.sendAuth(activity)
        if (err != null) actionMessage.value = err
    }

    fun bindPhone() {
        actionMessage.value = "请在微信小程序中绑定手机号"
    }

    fun uploadCurrentLedger() {
        viewModelScope.launch {
            withCloudBusy("上传中…") { ledgerSync.importCurrent() }
                .onSuccess { actionMessage.value = "已上传到云端" }
                .onFailure { actionMessage.value = ApiErrorMessages.fromThrowable(it) }
        }
    }

    fun createInvite() {
        viewModelScope.launch {
            withCloudBusy("生成邀请码…") { ledgerSync.createInviteCode() }
                .onSuccess { code ->
                    lastInviteCode.value = code
                    copyInviteShare(code, toastOnSuccess = true)
                }
                .onFailure { actionMessage.value = ApiErrorMessages.fromThrowable(it) }
        }
    }

    fun copyInviteShare(code: String? = lastInviteCode.value, toastOnSuccess: Boolean = true) {
        val trimmed = code?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            actionMessage.value = "暂无邀请码"
            return
        }
        val text = InviteShareText.message(trimmed)
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("invite", text))
        if (toastOnSuccess) {
            actionMessage.value = "邀请信息已复制"
        }
    }

    fun joinInvite(code: String) {
        viewModelScope.launch {
            val normalized = InviteShareText.extractCode(code)
            withCloudBusy("加入中…") { ledgerSync.joinInvite(normalized) }
                .onSuccess { actionMessage.value = "已加入账本" }
                .onFailure { actionMessage.value = ApiErrorMessages.fromThrowable(it) }
        }
    }
}
