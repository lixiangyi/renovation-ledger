package com.renovation.ledger.data.sync

sealed class LoginLedgerAction {
    data object None : LoginLedgerAction()
    data class OfferBind(val projectId: String, val projectName: String) : LoginLedgerAction()
    data object SwitchedAway : LoginLedgerAction()
}
