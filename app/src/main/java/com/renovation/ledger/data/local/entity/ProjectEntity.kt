package com.renovation.ledger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val memberNamesCsv: String,
    val cloudLedgerId: String? = null,
    val cloudRevision: Long = 0,
    val pendingSync: Boolean = false,
    val cloudLinkedAtEpochMs: Long? = null,
)
