package com.renovation.ledger.voice.asr

import android.speech.SpeechRecognizer

fun mapSpeechRecognizerError(error: Int): AsrError = when (error) {
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> AsrError.NO_PERMISSION
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> AsrError.NO_SPEECH
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    SpeechRecognizer.ERROR_SERVER,
    -> AsrError.NETWORK_ERROR
    else -> AsrError.UNKNOWN
}
