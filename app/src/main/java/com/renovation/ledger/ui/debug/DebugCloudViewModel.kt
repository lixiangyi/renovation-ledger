package com.renovation.ledger.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.remote.ApiErrorMessages
import com.renovation.ledger.data.remote.CloudEnv
import com.renovation.ledger.data.sync.LedgerSyncRepository
import com.renovation.ledger.di.ServerEndpoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DebugDevChannel {
    USB,
    LAN,
    CUSTOM,
}

data class DebugCloudUiState(
    val env: CloudEnv.Kind = CloudEnv.defaultKind(),
    val serverBaseUrl: String = CloudEnv.defaultUrl(),
    val devChannel: DebugDevChannel = DebugDevChannel.USB,
    val message: String? = null,
)

private fun resolveDevChannel(env: CloudEnv.Kind, url: String): DebugDevChannel {
    if (env != CloudEnv.Kind.DEV) return DebugDevChannel.CUSTOM
    val bare = url.trim().trimEnd('/')
    return when (bare) {
        CloudEnv.DEV_URL.trimEnd('/') -> DebugDevChannel.USB
        CloudEnv.DEV_LAN_URL.trimEnd('/') -> DebugDevChannel.LAN
        else -> DebugDevChannel.CUSTOM
    }
}

@HiltViewModel
class DebugCloudViewModel @Inject constructor(
    private val userPrefs: UserPrefs,
    private val ledgerSync: LedgerSyncRepository,
    private val serverEndpoint: ServerEndpoint,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val uiState = combine(
        userPrefs.cloudEnv,
        userPrefs.serverBaseUrl,
        message,
    ) { env, url, msg ->
        serverEndpoint.baseUrl = url
        DebugCloudUiState(
            env = env,
            serverBaseUrl = url,
            devChannel = resolveDevChannel(env, url),
            message = msg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DebugCloudUiState(),
    )

    fun clearMessage() {
        message.value = null
    }

    fun ping() {
        viewModelScope.launch {
            runCatching { ledgerSync.pingDevLogin() }
                .onSuccess { message.value = it }
                .onFailure { message.value = ApiErrorMessages.fromThrowable(it) }
        }
    }

    fun useUsbForward() {
        viewModelScope.launch {
            userPrefs.setCloudEnv(CloudEnv.Kind.DEV, CloudEnv.DEV_URL)
            userPrefs.setJwt(null, null)
            serverEndpoint.baseUrl = CloudEnv.DEV_URL
            message.value = "已切到 USB 转发（需 adb reverse）"
        }
    }

    fun useLan() {
        viewModelScope.launch {
            userPrefs.setCloudEnv(CloudEnv.Kind.DEV, CloudEnv.DEV_LAN_URL)
            userPrefs.setJwt(null, null)
            serverEndpoint.baseUrl = CloudEnv.DEV_LAN_URL
            message.value = "已切到电脑局域网 ${CloudEnv.DEV_LAN_URL}"
        }
    }

    fun setEnv(kind: CloudEnv.Kind) {
        viewModelScope.launch {
            val url = when (kind) {
                CloudEnv.Kind.DEV -> CloudEnv.DEV_URL
                CloudEnv.Kind.PROD -> CloudEnv.PROD_URL
            }
            userPrefs.setCloudEnv(kind, url)
            userPrefs.setJwt(null, null)
            serverEndpoint.baseUrl = url
            message.value = if (kind == CloudEnv.Kind.DEV) {
                "已切换到开发环境（需 adb reverse tcp:18080 tcp:8080）"
            } else {
                "已切换到正式环境"
            }
        }
    }

    fun setServerBaseUrl(url: String) {
        viewModelScope.launch {
            userPrefs.setServerBaseUrl(url)
            serverEndpoint.baseUrl = userPrefs.serverBaseUrl.first()
            message.value = "已保存服务器地址"
        }
    }
}
