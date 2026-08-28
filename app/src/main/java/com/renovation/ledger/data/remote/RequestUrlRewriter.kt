package com.renovation.ledger.data.remote

import okhttp3.HttpUrl

object RequestUrlRewriter {
    fun rewrite(original: HttpUrl, base: HttpUrl): HttpUrl {
        val apiPath = stripTestPrefix(original.encodedPath)
        val prefix = base.encodedPath.trimEnd('/')
        val combined = if (prefix.isEmpty()) apiPath else prefix + apiPath
        return original.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .encodedPath(combined)
            .build()
    }

    private fun stripTestPrefix(path: String): String = when {
        path == "/test" -> "/"
        path.startsWith("/test/") -> path.removePrefix("/test")
        else -> path
    }
}
