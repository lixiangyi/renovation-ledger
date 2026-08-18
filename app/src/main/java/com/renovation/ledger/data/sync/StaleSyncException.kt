package com.renovation.ledger.data.sync

class StaleSyncException : RuntimeException("该条已被其他人更新，请查看后再改")
