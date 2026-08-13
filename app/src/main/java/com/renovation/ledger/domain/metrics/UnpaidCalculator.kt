package com.renovation.ledger.domain.metrics

object UnpaidCalculator {
    fun displayUnpaid(contract: Long?, paid: Long, unpaidRowsSum: Long = 0L): Long {
        if (contract != null) return (contract - paid).coerceAtLeast(0L)
        return unpaidRowsSum.coerceAtLeast(0L)
    }

    fun suggestUnpaidAmount(contract: Long?, paid: Long): Long {
        if (contract == null) return 0L
        return (contract - paid).coerceAtLeast(0L)
    }
}
