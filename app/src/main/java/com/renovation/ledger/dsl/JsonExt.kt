package com.renovation.ledger.dsl

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import org.json.JSONObject

val gson: Gson by lazy { Gson() }

fun JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    keys().forEach {
        map[it] = get(it)
    }
    return map
}

inline fun <reified T> JsonObject?.toObject(): T? {
    return this?.let { jsonObject ->
        gson.fromJson(jsonObject, object : TypeToken<T>() {}.type)
    }
}

inline fun <reified T> List<JsonObject>?.toList(): List<T>? {
    return this?.mapNotNull { it.toObject<T>() }
}

inline fun <reified T> JsonObject?.toObjectSafely(): Result<T?> {
    return runCatching { this.toObject<T>() }
}

inline fun <reified T> List<JsonObject>?.toListSafely(): Result<List<T>?> {
    return runCatching { this.toList<T>() }
}

fun JsonObject?.toMap(): Map<String, Any?>? {
    this ?: return null
    val map = mutableMapOf<String, Any?>()
    for ((key, value) in this.entrySet()) {
        map[key] = value
    }
    return map
}
