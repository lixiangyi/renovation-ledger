package com.renovation.ledger.domain.model

data class Project(
    val id: String,
    val name: String,
    val memberNames: List<String>,
    val cloudLedgerId: String? = null,
    val cloudRevision: Long = 0,
    val pendingSync: Boolean = false,
    /** 本机首次绑定云端时间；排序兜底用。 */
    val cloudLinkedAtEpochMs: Long? = null,
)
