package com.renovation.ledger.data.remote

import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ApiErrorMessages {
    fun fromThrowable(error: Throwable): String {
        return when (error) {
            is HttpException -> fromHttp(error)
            is SocketTimeoutException -> "连接超时。开发环境请确认电脑服务已启动，并执行 adb reverse tcp:18080 tcp:8080"
            is ConnectException, is UnknownHostException ->
                "无法连接服务器。开发环境请执行 adb reverse tcp:18080 tcp:8080，或改用电脑局域网 IP"
            is IOException -> "网络异常，请稍后重试"
            else -> {
                val raw = error.message?.trim().orEmpty()
                when {
                    raw.isEmpty() -> "请求失败"
                    looksLikeHtml(raw) -> "请求失败"
                    raw.length > 80 || raw.contains('\n') -> "请求失败"
                    else -> raw
                }
            }
        }
    }

    fun fromHttp(error: HttpException): String {
        val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull()
        return fromBody(body, error.code())
    }

    fun fromBody(raw: String?, code: Int): String {
        val body = raw?.trim().orEmpty()
        if (body.isNotEmpty() && !looksLikeHtml(body)) {
            runCatching {
                val obj = JSONObject(body)
                val message = obj.optString("message").trim()
                    .ifEmpty { obj.optString("error").trim() }
                if (message.isNotEmpty() && !looksLikeHtml(message) && message.length <= 80) {
                    return if (code in 500..599) {
                        "$message（请检查电脑服务与 adb reverse）"
                    } else {
                        message
                    }
                }
            }
        }
        return statusFallback(code)
    }

    private fun looksLikeHtml(value: String): Boolean {
        val lower = value.lowercase()
        return lower.contains("<html") ||
            lower.contains("<!doctype") ||
            lower.contains("<body") ||
            lower.contains("<head") ||
            (value.startsWith("<") && value.contains(">"))
    }

    private fun statusFallback(code: Int): String = when (code) {
        401 -> "请重新登录"
        403 -> "没有权限"
        404 -> "接口不存在，请检查服务器地址"
        408, 504 -> "连接超时。请确认电脑服务已启动，并执行 adb reverse tcp:18080 tcp:8080"
        409 -> "该条已被其他人更新，请查看后再改"
        410 -> "邀请已失效"
        415 -> "请求格式不正确"
        502, 503 -> "USB 转发失败（$code）。请在电脑执行：adb reverse tcp:18080 tcp:8080"
        500 -> "服务器异常。请确认电脑上的云同步服务仍在运行"
        else -> "请求失败（$code）"
    }
}
