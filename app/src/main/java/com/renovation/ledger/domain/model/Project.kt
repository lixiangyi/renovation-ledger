package com.renovation.ledger.domain.model

data class Project(
    val id: String,
    val name: String,
    val memberNames: List<String>,
    val cloudLedgerId: String? = null,
    val cloudRevision: Long = 0,
    val pendingSync: Boolean = false,
)
