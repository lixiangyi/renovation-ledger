package com.renovation.ledger.domain.metrics

import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.ItemStatus
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.deriveStatus
import com.renovation.ledger.domain.model.effectiveCost

import javax.inject.Inject

class MetricsCalculator @Inject constructor() {
    fun calculate(items: List<BudgetItem>): ProjectMetrics {
        val totalBudget = items.sumOf { it.budgetAmount }
        val paidActual = items.sumOf { item ->
            item.payments.filter { it.status == PaymentStatus.PAID }.sumOf { it.amount }
        }
        val unpaidFinal = items.sumOf { item ->
            if (item.deriveStatus() != ItemStatus.PAYING) 0L
            else item.payments.filter { it.status == PaymentStatus.UNPAID }.sumOf { it.amount }
        }
        val toBuyAmount = items
            .filter { it.deriveStatus() == ItemStatus.TO_BUY }
            .sumOf { it.effectiveCost() }
        val pendingSpend = unpaidFinal + toBuyAmount
        val projectedTotal = items.sumOf { it.effectiveCost() }
        return ProjectMetrics(
            totalBudget = totalBudget,
            paidActual = paidActual,
            unpaidFinal = unpaidFinal,
            toBuyAmount = toBuyAmount,
            pendingSpend = pendingSpend,
            currentOverspend = paidActual - totalBudget,
            projectedTotal = projectedTotal,
            projectedOverspend = projectedTotal - totalBudget,
        )
    }
}
