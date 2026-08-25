package com.renovation.ledger.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.prefs.UserProfile
import com.renovation.ledger.data.profile.AvatarStorage
import com.renovation.ledger.data.remote.ApiErrorMessages
import com.renovation.ledger.data.remote.InvitePreviewDto
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.data.sync.InviteShareText
import com.renovation.ledger.data.sync.LedgerSyncRepository
import com.renovation.ledger.domain.ledger.LedgerRoleGates
import com.renovation.ledger.domain.ledger.SessionCloudUi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: UserProfile = UserProfile(),
    val jwt: String? = null,
    val phone: String? = null,
    val cloudUserId: String? = null,
    val cloudLedgerId: String? = null,
    val currentUnbound: Boolean = false,
    val showCreateInvite: Boolean = false,
    val lastInviteCode: String? = null,
    val pendingJoin: InvitePreviewDto? = null,
    val cloudBusy: Boolean = false,
    val cloudBusyLabel: String? = null,
    val actionMessage: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userPrefs: UserPrefs,
    private val avatarStorage: AvatarStorage,
    private val projectRepository: ProjectRepository,
    private val ledgerSync: LedgerSyncRepository,
) : ViewModel() {

    private val actionMessage = MutableStateFlow<String?>(null)
    private val cloudBusyLabel = MutableStateFlow<String?>(null)
    private val lastInviteCode = MutableStateFlow<String?>(null)
    private val pendingJoin = MutableStateFlow<InvitePreviewDto?>(null)
    private var lastObservedUserId: String? = null
    private var userIdObservationStarted = false

    val uiState = combine(
        userPrefs.userProfile,
        userPrefs.jwt,
        userPrefs.phone,
        userPrefs.cloudUserId,
        projectRepository.observeProjectWithItems(),
    ) { profile, jwt, phone, cloudUserId, projectWithItems ->
        val (project, _) = projectWithItems
        val cloudId = project.cloudLedgerId
        ProfileUiState(
            profile = profile,
            jwt = jwt,
            phone = phone,
            cloudUserId = cloudUserId,
            cloudLedgerId = cloudId,
            currentUnbound = cloudId.isNullOrBlank(),
        )
    }.combine(ledgerSync.cloudSummaries) { state, summaries ->
        val role = LedgerRoleGates.roleOf(state.cloudLedgerId, summaries)
        val loggedIn = !state.jwt.isNullOrBlank()
        val hasCloud = !state.cloudLedgerId.isNullOrBlank()
        state.copy(
            showCreateInvite = LedgerRoleGates.showCreateInvite(role, loggedIn, hasCloud),
        )
    }.combine(actionMessage) { state, message ->
        state.copy(actionMessage = message)
    }.combine(cloudBusyLabel) { state, busyLabel ->
        state.copy(cloudBusy = busyLabel != null, cloudBusyLabel = busyLabel)
    }.combine(lastInviteCode) { state, code ->
        state.copy(lastInviteCode = code)
    }.combine(pendingJoin) { state, preview ->
        state.copy(pendingJoin = preview)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(),
    )

    init {
        viewModelScope.launch {
            userPrefs.cloudUserId.collect { userId ->
                if (userIdObservationStarted &&
                    SessionCloudUi.shouldClearSessionUi(lastObservedUserId, userId)
                ) {
                    resetSessionUi()
                }
                userIdObservationStarted = true
                lastObservedUserId = userId
            }
        }
    }

    fun clearActionMessage() {
        actionMessage.value = null
    }

    fun resetSessionUi() {
        lastInviteCode.value = null
        cloudBusyLabel.value = null
        pendingJoin.value = null
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

    fun logout() {
        viewModelScope.launch {
            ledgerSync.logout()
            resetSessionUi()
            actionMessage.value = "已退出登录"
        }
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

    fun requestJoinInvite(code: String) {
        viewModelScope.launch {
            val normalized = InviteShareText.extractCode(code)
            withCloudBusy("查询邀请…") { ledgerSync.previewInvite(normalized) }
                .onSuccess { pendingJoin.value = it }
                .onFailure { actionMessage.value = ApiErrorMessages.fromThrowable(it) }
        }
    }

    fun cancelJoinInvite() {
        pendingJoin.value = null
    }

    fun confirmJoinInvite() {
        val preview = pendingJoin.value ?: return
        viewModelScope.launch {
            pendingJoin.value = null
            withCloudBusy("加入中…") { ledgerSync.joinInvite(preview.code) }
                .onSuccess { actionMessage.value = "已加入账本" }
                .onFailure { actionMessage.value = ApiErrorMessages.fromThrowable(it) }
        }
    }

    fun saveNickname(nickname: String) {
        viewModelScope.launch {
            val old = userPrefs.userProfile.first().nickname
            runCatching {
                ledgerSync.updateNickname(nickname)
            }.onSuccess { saved ->
                projectRepository.renameMember(old, saved)
                actionMessage.value = "昵称已保存"
            }.onFailure { err ->
                actionMessage.value = err.message?.takeIf { it.isNotBlank() } ?: "昵称保存失败"
            }
        }
    }

    fun updateAvatar(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val path = avatarStorage.saveFromUri(uri)
                ledgerSync.uploadAvatarFile(java.io.File(path))
                actionMessage.value = "头像已更新"
            }.onFailure {
                actionMessage.value = it.message?.takeIf { m -> m.isNotBlank() } ?: "头像更新失败"
            }
        }
    }

    fun clearAvatar() {
        viewModelScope.launch {
            runCatching {
                ledgerSync.clearAvatarRemote()
                actionMessage.value = "已清除头像"
            }.onFailure {
                actionMessage.value = it.message?.takeIf { m -> m.isNotBlank() } ?: "清除头像失败"
            }
        }
    }
}
