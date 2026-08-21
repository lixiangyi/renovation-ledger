package com.renovation.ledger.voice

import com.renovation.ledger.voice.asr.AsrRecognizerChoice
import com.renovation.ledger.voice.asr.chooseAsrRecognizer
import org.junit.Assert.assertEquals
import org.junit.Test

class AsrRecognizerChoiceTest {

    @Test
    fun prefersInstalledRecognitionServiceOverOnDevice() {
        assertEquals(
            AsrRecognizerChoice.SYSTEM_SERVICE,
            chooseAsrRecognizer(hasRecognitionService = true, onDeviceAvailable = true),
        )
    }

    @Test
    fun usesOnDeviceOnlyWhenNoSystemService() {
        assertEquals(
            AsrRecognizerChoice.ON_DEVICE,
            chooseAsrRecognizer(hasRecognitionService = false, onDeviceAvailable = true),
        )
    }

    @Test
    fun unavailableWhenNoServiceAndNoOnDevice() {
        assertEquals(
            AsrRecognizerChoice.UNAVAILABLE,
            chooseAsrRecognizer(hasRecognitionService = false, onDeviceAvailable = false),
        )
    }
}
