package com.renovation.ledger.dsl

/**
 * Exception-catching DSL from shared_uilib (stripped of Beike-specific monitors).
 */
inline fun <reified T> catchException(
    isPrintStackTrace: Boolean = true,
    onError: (Throwable) -> T,
    block: () -> T,
): T {
    return try {
        block.invoke()
    } catch (e: Throwable) {
        if (isPrintStackTrace) e.printStackTrace()
        onError(e)
    }
}

inline fun <reified T> catchExceptionWithReturnValueNullable(
    onError: (Throwable) -> T? = { null },
    isPrintStackTrace: Boolean = true,
    block: () -> T?,
): T? {
    return try {
        block.invoke()
    } catch (e: Throwable) {
        if (isPrintStackTrace) e.printStackTrace()
        onError(e)
    }
}

inline fun catchExceptionWithoutReturnValue(
    onError: Consumer<Throwable> = {},
    isPrintStackTrace: Boolean = true,
    block: VoidCallback,
) {
    try {
        block.invoke()
    } catch (e: Throwable) {
        if (isPrintStackTrace) e.printStackTrace()
        onError(e)
    }
}
