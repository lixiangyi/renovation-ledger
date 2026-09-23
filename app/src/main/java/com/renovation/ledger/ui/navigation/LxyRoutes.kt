package com.renovation.ledger.ui.navigation

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 业务路由。两端共用 `LXY://`，scheme 忽略大小写。
 * 解析结果 [Target.navRoute] 是 Compose Navigation 的目的地。
 */
object LxyRoutes {
    const val SCHEME = "lxy"

    data class Target(
        val navRoute: String,
        val tab: Boolean,
    )

    fun overview() = link("overview")

    fun list() = link("list")

    fun stats() = link("stats")

    fun mine() = link("mine")

    fun login() = link("login")

    fun search() = link("search")

    fun profile() = link("profile")

    fun settings() = link("settings")

    fun taxonomy() = link("taxonomy")

    fun batchImport() = link("import/batch")

    fun trash() = link("trash")

    fun item(id: String) = "LXY://item/${encode(id)}"

    fun pending(tab: String) = link("pending", mapOf("tab" to tab))

    fun paidGap(tab: String) = link("paidgap", mapOf("tab" to tab))

    fun manualEntry(
        itemId: String = "",
        editItemId: String = "",
        fromVoice: String = "",
    ) = link(
        "entry/manual",
        mapOf(
            "itemId" to itemId,
            "editItemId" to editItemId,
            "fromVoice" to fromVoice,
        ),
    )

    fun confirmEntry(source: String, itemId: String = "") =
        link("entry/confirm", mapOf("source" to source, "itemId" to itemId))

    fun parse(raw: String): Target? {
        val text = raw.trim()
        val schemeEnd = text.indexOf("://")
        if (schemeEnd <= 0) return null
        if (!text.substring(0, schemeEnd).equals(SCHEME, ignoreCase = true)) return null
        val rest = text.substring(schemeEnd + 3)
        val noHash = rest.substringBefore('#')
        val pathRaw = noHash.substringBefore('?').trim('/')
        if (pathRaw.isEmpty()) return null
        val query = parseQuery(noHash.substringAfter('?', ""))
        val segments = pathRaw.split('/').map { segment ->
            decode(segment) ?: return null
        }
        val path = segments.joinToString("/")
        if (path in TAB_PATHS) {
            return Target(navRoute = path, tab = true)
        }
        if (path in PAGE_PATHS) {
            return Target(navRoute = path, tab = false)
        }
        return when (path) {
            "pending" -> Target(
                navRoute = "pending?tab=${encode(query["tab"] ?: "unpaid")}",
                tab = false,
            )
            "paidgap" -> Target(
                navRoute = "paidgap?tab=${encode(query["tab"] ?: "overspend")}",
                tab = false,
            )
            "entry/manual" -> Target(
                navRoute = "entry/manual?itemId=${encode(query["itemId"].orEmpty())}" +
                    "&editItemId=${encode(query["editItemId"].orEmpty())}",
                tab = false,
            )
            "entry/confirm" -> Target(
                navRoute = "entry/confirm?source=${encode(query["source"] ?: "manual")}" +
                    "&itemId=${encode(query["itemId"].orEmpty())}",
                tab = false,
            )
            else -> parseItem(segments)
        }
    }

    private fun parseItem(segments: List<String>): Target? {
        if (segments.size != 2 || segments[0] != "item") return null
        val id = segments[1]
        if (id.isEmpty()) return null
        return Target(navRoute = "item/${encode(id)}", tab = false)
    }

    private fun link(path: String, query: Map<String, String> = emptyMap()): String {
        val parts = query.entries
            .filter { it.value.isNotEmpty() }
            .joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        return if (parts.isEmpty()) "LXY://$path" else "LXY://$path?$parts"
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isEmpty()) return emptyMap()
        val values = linkedMapOf<String, String>()
        raw.split('&').forEach { part ->
            if (part.isEmpty()) return@forEach
            val eq = part.indexOf('=')
            val key = decode(if (eq < 0) part else part.substring(0, eq)) ?: return@forEach
            val value = decode(if (eq < 0) "" else part.substring(eq + 1)) ?: return@forEach
            values[key] = value
        }
        return values
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun decode(value: String): String? = try {
        URLDecoder.decode(value, Charsets.UTF_8.name())
    } catch (_: IllegalArgumentException) {
        null
    }

    private val TAB_PATHS = setOf("overview", "list", "stats", "mine")

    private val PAGE_PATHS = setOf(
        "login",
        "search",
        "profile",
        "settings",
        "taxonomy",
        "trash",
        "import/batch",
    )
}
