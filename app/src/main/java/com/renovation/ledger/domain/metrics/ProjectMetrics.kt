package com.renovation.ledger.domain.metrics

data class ProjectMetrics(
    val totalBudget: Long,
    val paidActual: Long,
    val unpaidFinal: Long,
    val toBuyAmount: Long,
    val pendingSpend: Long,
    val currentOverspend: Long,
    val projectedTotal: Long,
    val projectedOverspend: Long,
)
