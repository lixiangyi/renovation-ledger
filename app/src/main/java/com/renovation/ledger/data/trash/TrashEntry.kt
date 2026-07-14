package com.renovation.ledger.data.trash

/** 垃圾箱索引条目（`filesDir/trash/index.json`）。 */
data class TrashEntry(
    val id: String,
    val name: String,
    val deletedAt: Long,
    val itemCount: Int,
    val csvPath: String,
)
