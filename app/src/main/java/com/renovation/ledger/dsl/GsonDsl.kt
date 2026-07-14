package com.renovation.ledger.dsl

import com.google.gson.JsonArray
import com.google.gson.JsonObject

@JsonElementMarker
class JsonObjectBuilder {
    private val json = JsonObject()

    infix fun String.with(contentValue: String?): JsonObjectBuilder {
        json.addProperty(this, contentValue)
        return this@JsonObjectBuilder
    }

    inline infix fun String.withString(block: Supplier<String?>): JsonObjectBuilder = with(block())

    infix fun String.with(contentValue: Number?): JsonObjectBuilder {
        json.addProperty(this, contentValue)
        return this@JsonObjectBuilder
    }

    inline infix fun String.withNumber(block: Supplier<Number?>): JsonObjectBuilder = with(block())

    infix fun String.with(contentValue: Boolean?): JsonObjectBuilder {
        json.addProperty(this, contentValue)
        return this@JsonObjectBuilder
    }

    inline infix fun String.withBoolean(block: Supplier<Boolean?>): JsonObjectBuilder = with(block())

    infix fun String.with(contentValue: JsonObject?): JsonObjectBuilder {
        json.add(this, contentValue)
        return this@JsonObjectBuilder
    }

    inline infix fun String.gson(block: VoidCallbackWithReceiver<JsonObjectBuilder>): JsonObjectBuilder =
        with(JsonObjectBuilder().apply(block).build())

    infix fun String.with(contentValue: JsonArray?): JsonObjectBuilder {
        json.add(this, contentValue)
        return this@JsonObjectBuilder
    }

    inline infix fun String.gsonArray(block: VoidCallbackWithReceiver<JsonArrayBuilder>): JsonObjectBuilder =
        with(JsonArrayBuilder().apply(block).build())

    fun build(): JsonObject = json
}

inline fun gson(block: VoidCallbackWithReceiver<JsonObjectBuilder>): JsonObject {
    return JsonObjectBuilder().apply(block).build()
}

@JsonElementMarker
class JsonArrayBuilder {
    private val jsonArray = JsonArray()

    fun with(contentValue: String?): JsonArrayBuilder {
        jsonArray.add(contentValue)
        return this
    }

    inline fun withString(block: Supplier<String?>): JsonArrayBuilder = with(block())

    fun with(contentValue: Number?): JsonArrayBuilder {
        jsonArray.add(contentValue)
        return this
    }

    inline fun withNumber(block: Supplier<Number?>): JsonArrayBuilder = with(block())

    fun with(contentValue: Boolean?): JsonArrayBuilder {
        jsonArray.add(contentValue)
        return this
    }

    inline fun withBoolean(block: Supplier<Boolean?>): JsonArrayBuilder = with(block())

    fun with(contentValue: JsonObject?): JsonArrayBuilder {
        jsonArray.add(contentValue)
        return this
    }

    inline fun gson(block: VoidCallbackWithReceiver<JsonObjectBuilder>): JsonArrayBuilder =
        with(JsonObjectBuilder().apply(block).build())

    fun with(contentValue: JsonArray?): JsonArrayBuilder {
        jsonArray.add(contentValue)
        return this
    }

    inline fun gsonArray(block: VoidCallbackWithReceiver<JsonArrayBuilder>): JsonArrayBuilder =
        with(JsonArrayBuilder().apply(block).build())

    fun build(): JsonArray = jsonArray
}

inline fun gsonArray(block: VoidCallbackWithReceiver<JsonArrayBuilder>): JsonArray {
    return JsonArrayBuilder().apply(block).build()
}

@DslMarker
@Retention(AnnotationRetention.SOURCE)
internal annotation class JsonElementMarker
