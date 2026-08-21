package com.renovation.ledger.voice.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceDebugStore @Inject constructor() {
    private val lastState = MutableStateFlow<VoiceDebugSnapshot?>(null)
    val last: StateFlow<VoiceDebugSnapshot?> = lastState.asStateFlow()

    fun update(snapshot: VoiceDebugSnapshot) {
        lastState.value = snapshot
    }
}
