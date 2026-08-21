package com.renovation.ledger.voice

import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.voice.tool.executors.AddLedgerEntryExecutor
import com.renovation.ledger.voice.tool.executors.VoiceLedgerStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AddLedgerEntryExecutorTest {

    @Test
    fun executeCreatesTwoPaymentsFromDepositAndFinalFields() = runTest {
        val store = FakeVoiceLedgerStore()
        val executor = AddLedgerEntryExecutor(store)

        executor.execute(
            mapOf(
                "name" to "扫地机器人",
                "category" to "家电",
                "stage" to "主材",
                "amount" to 2950,
                "deposit" to 1000,
                "depositPaid" to true,
                "finalPayment" to 1950,
                "finalPaid" to false,
            ),
        )

        assertEquals("扫地机器人", store.upsertedItems.single().name)
        assertEquals(2, store.upsertedPayments.size)
        assertEquals("PAID", store.upsertedPayments[0].status.name)
        assertEquals("UNPAID", store.upsertedPayments[1].status.name)
    }
}

private class FakeVoiceLedgerStore : VoiceLedgerStore {
    val upsertedItems = mutableListOf<BudgetItem>()
    val upsertedPayments = mutableListOf<Payment>()

    override suspend fun currentProjectId(): String = "proj_1"
    override suspend fun nickname(): String = "开发者"
    override suspend fun upsertItem(item: BudgetItem) {
        upsertedItems += item
    }

    override suspend fun upsertPayment(payment: Payment) {
        upsertedPayments += payment
    }
}
