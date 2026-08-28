package com.renovation.ledger.data.remote

import com.renovation.ledger.BuildConfig

object CloudEnv {
    /** 云上正式环境。 */
    const val PROD_URL = "http://111.229.202.28/"

    /** 云上测试环境（与正式分库、分进程）。 */
    const val TEST_URL = "http://111.229.202.28/test/"

    /** Debug 默认开发地址：云测试。电脑局域网仍可通过开发面板切换。 */
    val DEV_URL: String
        get() = TEST_URL

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

    fun isLegacyDebugDefault(url: String): Boolean = migrateStoredUrl(url) != null

    /**
     * 旧占位域名 / 本机默认地址迁到当前云环境。
     * 已是云地址或用户自定义地址则返回 null，保持原值。
     */
    fun migrateStoredUrl(raw: String): String? {
        val bare = raw.trim().trimEnd('/')
        return when (bare) {
            "https://api.renovation-ledger.app",
            "http://api.renovation-ledger.app",
            -> PROD_URL
            "http://10.0.2.2:8080",
            "http://127.0.0.1:8080",
            "http://127.0.0.1:18080",
            "http://10.35.86.169:8080",
            -> TEST_URL
            else -> null
        }
    }
}
