package com.renovation.ledger.data.remote

import com.renovation.ledger.BuildConfig

object CloudEnv {
    /** Debug 开发环境默认：电脑局域网（打包写入 DEV_LAN_URL）。 */
    val DEV_URL: String
        get() = DEV_LAN_URL

    const val PROD_URL = "https://api.renovation-ledger.app/"

    /** 打包时写入的电脑局域网地址。 */
    val DEV_LAN_URL: String
        get() = BuildConfig.DEV_LAN_URL.let { if (it.endsWith("/")) it else "$it/" }

    enum class Kind { DEV, PROD }

    fun defaultKind(): Kind = if (BuildConfig.DEBUG) Kind.DEV else Kind.PROD

    fun urlOf(kind: Kind): String = when (kind) {
        Kind.DEV -> DEV_URL
        Kind.PROD -> PROD_URL
    }

    fun defaultUrl(): String = urlOf(defaultKind())

    fun kindOf(raw: String?): Kind = when (raw) {
        "prod" -> Kind.PROD
        "dev" -> Kind.DEV
        else -> defaultKind()
    }

    fun storageValue(kind: Kind): String = when (kind) {
        Kind.DEV -> "dev"
        Kind.PROD -> "prod"
    }

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return defaultUrl()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    fun isLegacyDebugDefault(url: String): Boolean {
        val bare = url.trim().trimEnd('/')
        return bare == "http://10.0.2.2:8080" ||
            bare == "http://127.0.0.1:8080" ||
            bare == "http://127.0.0.1:18080"
    }
}
