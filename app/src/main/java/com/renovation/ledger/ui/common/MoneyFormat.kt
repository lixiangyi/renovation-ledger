package com.renovation.ledger.ui.common

fun formatYuan(cents: Long): String {
    val yuan = cents / 100.0
    return "¥ " + String.format("%,.0f", yuan)
}
