package com.renovation.ledger.dsl

/** Portable functional aliases (from shared_uilib). */
typealias Consumer<T> = (element: T) -> Unit
typealias Supplier<T> = () -> T
typealias Mapper<T, R> = (element: T) -> R
typealias VoidCallback = () -> Unit
typealias VoidCallbackWithReceiver<T> = T.() -> Unit
