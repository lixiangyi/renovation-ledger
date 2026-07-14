package com.renovation.ledger.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.BuildConfig
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.prefs.UserProfile
import com.renovation.ledger.data.profile.AvatarStorage
import com.renovation.ledger.data.repo.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val profile: UserProfile = UserProfile(),
    val versionName: String = BuildConfig.VERSION_NAME,
    val savedMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPrefs: UserPrefs,
    private val avatarStorage: AvatarStorage,
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val savedMessage = MutableStateFlow<String?>(null)

    val uiState = combine(
        userPrefs.userProfile,
        savedMessage,
    ) { profile, message ->
        SettingsUiState(
            profile = profile,
            versionName = BuildConfig.VERSION_NAME,
            savedMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun clearMessage() {
        savedMessage.value = null
    }

    fun saveNickname(nickname: String) {
        viewModelScope.launch {
            val old = userPrefs.userProfile.first().nickname
            userPrefs.setNickname(nickname)
            projectRepository.renameMember(old, nickname.trim().ifBlank { "我" })
            savedMessage.value = "昵称已保存"
        }
    }

    fun updateAvatar(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val path = avatarStorage.saveFromUri(uri)
                userPrefs.setAvatarPath(path)
                savedMessage.value = "头像已更新"
            }.onFailure {
                savedMessage.value = "头像更新失败"
            }
        }
    }

    fun clearAvatar() {
        viewModelScope.launch {
            userPrefs.setAvatarPath(null)
            savedMessage.value = "已清除头像"
        }
    }
}
