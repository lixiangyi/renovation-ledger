package com.renovation.ledger.ui.debug.netrecord

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

object NetRecordStore {
    private const val MAX_RECORDS = 50

    private val queue = ArrayDeque<NetRecordBean>()
    private val _records = MutableStateFlow<List<NetRecordBean>>(emptyList())
    val records: StateFlow<List<NetRecordBean>> = _records.asStateFlow()

    @Synchronized
    fun add(record: NetRecordBean) {
        if (queue.size >= MAX_RECORDS) {
            queue.removeFirst()
        }
        queue.addLast(record)
        _records.value = queue.toList()
    }

    @Synchronized
    fun clear() {
        queue.clear()
        _records.value = emptyList()
    }

    @Synchronized
    fun snapshot(): List<NetRecordBean> = queue.toList()
}
