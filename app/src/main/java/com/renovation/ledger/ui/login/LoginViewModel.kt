package com.renovation.ledger.ui.login

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.auth.WeChatAppAuth
import com.renovation.ledger.data.remote.ApiErrorMessages
import com.renovation.ledger.data.sync.LedgerSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoginTab { WECHAT, PHONE }

/** 11 位手机号展示为「前三 中四 后四」。 */
fun formatPhoneDisplay(digits: String): String {
    val d = digits.filter { it.isDigit() }.take(11)
    return when {
        d.length <= 3 -> d
        d.length <= 7 -> "${d.take(3)} ${d.drop(3)}"
        else -> "${d.take(3)} ${d.drop(3).take(4)} ${d.drop(7)}"
    }
}

fun digitsOnlyPhone(raw: String): String = raw.filter { it.isDigit() }.take(11)

data class LoginUiState(
    val tab: LoginTab = LoginTab.PHONE,
    /** 仅数字，最多 11 位。 */
    val phone: String = "",
    val code: String = "",
    val busy: Boolean = false,
    val message: String? = null,
) {
    val phoneDisplay: String get() = formatPhoneDisplay(phone)
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val ledgerSync: LedgerSyncRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(LoginUiState())
    val uiState = _ui.asStateFlow()

    fun selectTab(tab: LoginTab) {
        _ui.update { it.copy(tab = tab) }
    }

    fun setPhone(value: String) {
        _ui.update { it.copy(phone = digitsOnlyPhone(value)) }
    }

    fun setCode(value: String) {
        _ui.update { it.copy(code = value.filter { ch -> ch.isDigit() }.take(6)) }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun sendCode() {
        val phone = _ui.value.phone
        if (phone.length != 11) {
            _ui.update { it.copy(message = "请输入11位手机号") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            runCatching { ledgerSync.sendSmsCode(phone) }
                .onSuccess { res ->
                    _ui.update {
                        it.copy(
                            busy = false,
                            code = res.code.orEmpty().ifBlank { it.code },
                            message = if (res.code.isNullOrBlank()) "验证码已发送" else "已填入验证码",
                        )
                    }
                }
                .onFailure { e ->
                    _ui.update {
                        it.copy(busy = false, message = ApiErrorMessages.fromThrowable(e))
                    }
                }
        }
    }

    fun loginPhone(onSuccess: () -> Unit) {
        val state = _ui.value
        if (state.phone.length != 11 || state.code.isBlank()) {
            _ui.update { it.copy(message = "请填写手机号与验证码") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            runCatching { ledgerSync.smsLogin(state.phone, state.code) }
                .onSuccess {
                    _ui.update { it.copy(busy = false) }
                    onSuccess()
                }
                .onFailure { e ->
                    _ui.update {
                        it.copy(busy = false, message = ApiErrorMessages.fromThrowable(e))
                    }
                }
        }
    }

    fun wechatLogin(activity: Activity?) {
        if (activity == null) {
            _ui.update { it.copy(message = "微信登录暂不可用，请使用手机号登录") }
            return
        }
        val err = WeChatAppAuth.sendAuth(activity)
        if (err != null) _ui.update { it.copy(message = err) }
    }
}
