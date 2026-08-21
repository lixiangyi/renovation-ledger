package com.renovation.ledger.voice.asr

enum class AsrRecognizerChoice {
    SYSTEM_SERVICE,
    ON_DEVICE,
    UNAVAILABLE,
}

fun chooseAsrRecognizer(
    hasRecognitionService: Boolean,
    onDeviceAvailable: Boolean,
): AsrRecognizerChoice = when {
    hasRecognitionService -> AsrRecognizerChoice.SYSTEM_SERVICE
    onDeviceAvailable -> AsrRecognizerChoice.ON_DEVICE
    else -> AsrRecognizerChoice.UNAVAILABLE
}
