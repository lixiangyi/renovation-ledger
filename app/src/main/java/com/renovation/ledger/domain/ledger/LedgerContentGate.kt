package com.renovation.ledger.domain.ledger

object LedgerContentGate {
    /** 冷启动未读到本机账本前，不展示「暂无数据」空态，避免闪一下再填数。 */
    fun showEmptyCopy(contentReady: Boolean, isEmpty: Boolean): Boolean =
        contentReady && isEmpty
}
