package com.renovation.ledger.voice

import android.speech.SpeechRecognizer
import com.renovation.ledger.voice.asr.AsrError
import com.renovation.ledger.voice.asr.mapSpeechRecognizerError
import org.junit.Assert.assertEquals
import org.junit.Test

class AsrErrorMapperTest {

    @Test
    fun clientAndServerErrorsAreRetryableNotUnsupported() {
        assertEquals(AsrError.UNKNOWN, mapSpeechRecognizerError(SpeechRecognizer.ERROR_CLIENT))
        assertEquals(AsrError.NETWORK_ERROR, mapSpeechRecognizerError(SpeechRecognizer.ERROR_SERVER))
    }
}
