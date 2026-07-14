package com.renovation.ledger.domain.model

data class Project(
    val id: String,
    val name: String,
    val memberNames: List<String>,
)
