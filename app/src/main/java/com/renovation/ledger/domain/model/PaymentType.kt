package com.renovation.ledger.domain.model

enum class PaymentType {
    DEPOSIT,
    FULL,
    FINAL,
    OTHER,
}

fun PaymentType.label(): String = when (this) {
    PaymentType.DEPOSIT -> "定金"
    PaymentType.FULL -> "全款"
    PaymentType.FINAL -> "尾款"
    PaymentType.OTHER -> "其他"
}
