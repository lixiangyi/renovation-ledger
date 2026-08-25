package com.renovation.ledger.domain.ledger

import com.renovation.ledger.data.remote.LedgerSummaryDto
import com.renovation.ledger.domain.model.Project

data class VisibleLedger(
    val project: Project,
    val displayName: String,
    val isLocalUnbound: Boolean,
)

object LedgerVisibility {
    fun visible(
        projects: List<Project>,
        cloudSummaries: List<LedgerSummaryDto>,
        loggedIn: Boolean,
    ): List<VisibleLedger> {
        if (!loggedIn) {
            return projects.filter { it.cloudLedgerId.isNullOrBlank() }.map {
                VisibleLedger(it, it.name, isLocalUnbound = false)
            }
        }
        val cloudIds = cloudSummaries.map { it.id }.toSet()
        val createdAt = cloudSummaries.associate { it.id to it.createdAtEpochMs }
        val account = projects.filter { project ->
            val cid = project.cloudLedgerId
            !cid.isNullOrBlank() && cid in cloudIds
        }.sortedBy { project ->
            val cid = project.cloudLedgerId!!
            createdAt[cid] ?: project.cloudLinkedAtEpochMs ?: Long.MAX_VALUE
        }
        val unbound = projects.filter { it.cloudLedgerId.isNullOrBlank() }
        return account.map {
            VisibleLedger(it, it.name, isLocalUnbound = false)
        } + unbound.map {
            VisibleLedger(it, it.name + "（本地）", isLocalUnbound = true)
        }
    }

    fun firstAccountCloudId(summaries: List<LedgerSummaryDto>): String? =
        summaries.minByOrNull { it.createdAtEpochMs ?: Long.MAX_VALUE }?.id

    fun isAccessible(project: Project, cloudIds: Set<String>, loggedIn: Boolean): Boolean {
        if (!loggedIn) return project.cloudLedgerId.isNullOrBlank()
        val cid = project.cloudLedgerId
        if (cid.isNullOrBlank()) return true
        return cid in cloudIds
    }
}
