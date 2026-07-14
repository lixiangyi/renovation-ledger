package com.renovation.ledger.dsl

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

fun String?.logI(tag: String) {
    this ?: return
    logI(tag) { this }
}

fun String?.logE(tag: String) {
    this ?: return
    logE(tag) { this }
}

fun String?.logV(tag: String) {
    this ?: return
    logV(tag) { this }
}

fun String?.logD(tag: String) {
    this ?: return
    logD(tag) { this }
}

fun String?.logW(tag: String) {
    this ?: return
    logW(tag) { this }
}

fun String.maxLength(maxLength: Int, ellipse: String = "…"): String =
    if (length > maxLength) "${substring(0, maxLength)}$ellipse" else this

operator fun String.times(n: Int) = repeat(n)

fun String.limitLength(length: Int): String =
    if (this.length <= length) this else substring(0, length)

fun String.isJson(): Boolean = catchException(onError = { false }) {
    JSONObject(this)
    true
}

val String.urlDecodeValue: String
    get() = catchException(onError = { this }) { URLDecoder.decode(this, "UTF-8") }

val String.urlEncodeValue: String
    get() = catchException(onError = { this }) { URLEncoder.encode(this, "UTF-8") }

fun String.throwIllegalArgumentException(): Nothing = throw IllegalArgumentException(this)

fun String.throwIllegalStateException(): Nothing = throw IllegalStateException(this)

fun String.throwUnsupportedOperationException(): Nothing = throw UnsupportedOperationException(this)

fun String.throwNoSuchElementException(): Nothing = throw NoSuchElementException(this)

fun String.throwNoSuchMethodException(): Nothing = throw NoSuchMethodException(this)

fun String.throwRuntimeException(): Nothing = throw RuntimeException(this)

fun String.centerPad(maxLength: Int, padChar: Char = ' '): String {
    if (length >= maxLength) return this
    val left = (maxLength - length + 1) / 2
    val right = maxLength - length - left
    return padStart(length + left, padChar).padEnd(maxLength, padChar)
}

fun String.addUrlParams(vararg params: Pair<String, String>): String {
    var url = this
    for ((key, value) in params) {
        val sep = if (url.contains("?")) "&" else "?"
        url = "$url$sep${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
    }
    return url
}

fun queryStringToJson(
    queryString: String,
    pairDelimiter: String = "&",
    keyValueDelimiter: String = "=",
): String {
    val params = queryString.split(pairDelimiter)
    val jsonObject = JSONObject()
    for (param in params) {
        val pair = param.split(keyValueDelimiter, limit = 2)
        if (pair.size == 2) {
            val key = pair[0]
            val value = pair[1]
            when {
                "cookie".trim().equals(key, ignoreCase = true) -> {
                    val jObj = buildJSONObject(parseKeyValues(param, ";"))
                    jsonObject.put(key, jObj.getJSONObject(key))
                }
                "extension".equals(key, ignoreCase = true) -> {
                    val jObj = buildJSONObject(parseKeyValues(param, "&"))
                    jsonObject.put(key, jObj.getJSONObject(key))
                }
                else -> jsonObject.put(key, value)
            }
        }
    }
    return jsonObject.toString(4)
}

fun parseKeyValues(
    input: String,
    delimiter: String,
): Pair<String, List<Pair<String, String>>> {
    val (header, content) = input.split(":", limit = 2)
    val contentValues = content.trim().split(delimiter).map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { part ->
            val (key, value) = part.split("=", limit = 2).let {
                it[0].trim() to if (it.size > 1) it[1].trim() else ""
            }
            key to value
        }
    return header.trim() to contentValues
}

fun buildJSONObject(pair: Pair<String, List<Pair<String, String>>>): JSONObject {
    val res = JSONObject()
    val jObj = JSONObject()
    res.put(pair.first, jObj)
    pair.second.forEach {
        jObj.put(it.first, it.second)
    }
    return res
}

inline fun <reified V> String?.jsonToMap(tag: String = "String.jsonToMap"): Map<String, V> {
    if (this.isNullOrBlank()) return emptyMap()
    val json = this
    return catchException(
        isPrintStackTrace = false,
        onError = { e ->
            logE(tag, e) { "jsonToMap failed, origin=$json" }
            emptyMap()
        },
    ) {
        val type = object : TypeToken<Map<String, V>>() {}.type
        Gson().fromJson<Map<String, V>>(json, type) ?: emptyMap()
    }
}
