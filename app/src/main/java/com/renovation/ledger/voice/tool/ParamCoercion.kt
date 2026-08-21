package com.renovation.ledger.voice.tool

internal fun Any?.asDouble(): Double? = when (this) {
    is Number -> toDouble()
    is String -> trim().toDoubleOrNull()
    else -> null
}

internal fun Any?.asBoolean(default: Boolean = false): Boolean = when (this) {
    is Boolean -> this
    is Number -> toInt() != 0
    is String -> when (trim()) {
        "true", "1", "已付", "付了", "付过了" -> true
        "false", "0", "未付", "没付", "还没付" -> false
        else -> default
    }
    else -> default
}

internal fun Any?.asString(): String = when (this) {
    null -> ""
    is String -> trim()
    else -> toString().trim()
}

internal fun Any?.toYuanText(): String {
    val value = asDouble() ?: return asString()
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format("%.2f", value)
    }
}

internal fun Any?.toCurrencyWithPaidFlag(paidRaw: Any?): String {
    val amount = toYuanText()
    if (amount.isEmpty()) return ""
    val paid = paidRaw.asBoolean(default = false)
    return if (paid) "¥$amount（已付）" else "¥$amount（未付）"
}
