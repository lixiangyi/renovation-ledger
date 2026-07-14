package com.renovation.ledger.dsl

/** Run [block] only when the receiver is `true`. */
inline operator fun Boolean.invoke(block: () -> Unit) {
    if (this) {
        block.invoke()
    }
}

/** Run [block] when [condition] is false (guard-style). */
inline fun require(condition: Boolean, block: () -> Unit) {
    if (!condition) {
        block.invoke()
    }
}

/** Run [block] when at least one of [params] is non-null. */
inline fun requireNotNull(vararg params: Any?, block: () -> Unit) {
    params.filterNotNull().isNotEmpty().invoke(block)
}
