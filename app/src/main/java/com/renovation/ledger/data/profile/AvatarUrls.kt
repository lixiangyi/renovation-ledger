package com.renovation.ledger.data.profile

object AvatarUrls {
    fun isRemoteRef(path: String?): Boolean {
        val p = path?.trim().orEmpty()
        return p.startsWith("http://") ||
            p.startsWith("https://") ||
            p.startsWith("/avatars/")
    }

    fun absoluteUrl(path: String?, baseUrl: String): String? {
        val p = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            p.startsWith("http://") || p.startsWith("https://") -> p
            p.startsWith("/avatars/") -> baseUrl.trimEnd('/') + p
            else -> null
        }
    }
}
