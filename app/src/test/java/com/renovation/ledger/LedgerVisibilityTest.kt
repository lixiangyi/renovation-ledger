package com.renovation.ledger

import com.renovation.ledger.data.remote.LedgerSummaryDto
import com.renovation.ledger.domain.ledger.LedgerVisibility
import com.renovation.ledger.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerVisibilityTest {
    private fun p(
        id: String,
        name: String,
        cloudId: String? = null,
        linkedAt: Long? = null,
    ) = Project(
        id = id,
        name = name,
        memberNames = listOf("我"),
        cloudLedgerId = cloudId,
        cloudLinkedAtEpochMs = linkedAt,
    )

    @Test
    fun loggedOut_showsAllWithoutLocalSuffix() {
        val local = listOf(p("a", "A", "cloud-a"), p("b", "B", null))
        val out = LedgerVisibility.visible(
            projects = local,
            cloudSummaries = emptyList(),
            loggedIn = false,
        )
        assertEquals(2, out.size)
        assertEquals("A", out[0].displayName)
        assertEquals("B", out[1].displayName)
    }

    @Test
    fun loggedIn_hidesForeignCloud_appendsUnboundWithSuffix_sortedByUploadTime() {
        val local = listOf(
            p("foreign", "他人", "cloud-foreign", linkedAt = 1L),
            p("mine2", "我的晚", "cloud-late", linkedAt = 200L),
            p("local", "本地本", null),
            p("mine1", "我的早", "cloud-early", linkedAt = 100L),
        )
        val summaries = listOf(
            LedgerSummaryDto("cloud-late", "我的晚", "OWNER", 0, createdAtEpochMs = 200L),
            LedgerSummaryDto("cloud-early", "我的早", "OWNER", 0, createdAtEpochMs = 100L),
        )
        val out = LedgerVisibility.visible(local, summaries, loggedIn = true)
        assertEquals(listOf("我的早", "我的晚", "本地本（本地）"), out.map { it.displayName })
        assertTrue(out.none { it.project.id == "foreign" })
    }

    @Test
    fun firstAccountLedger_prefersEarliestCreatedAt() {
        val summaries = listOf(
            LedgerSummaryDto("b", "B", "OWNER", 0, 200L),
            LedgerSummaryDto("a", "A", "OWNER", 0, 100L),
        )
        assertEquals("a", LedgerVisibility.firstAccountCloudId(summaries))
    }
}
