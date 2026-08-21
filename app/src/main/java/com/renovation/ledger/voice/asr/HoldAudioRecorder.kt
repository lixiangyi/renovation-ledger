package com.renovation.ledger.voice.asr

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class HoldAudioRecorder(
    private val appContext: Context,
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): Boolean {
        stopInternal(deleteFile = true)
        val file = File(appContext.cacheDir, "voice_asr_${System.currentTimeMillis()}.m4a")
        outputFile = file
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioSamplingRate(16000)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            true
        } catch (_: Exception) {
            mr.release()
            file.delete()
            outputFile = null
            false
        }
    }

    /** @return Pair(bytes, mime) or null */
    fun stop(): Pair<ByteArray, String>? {
        val file = outputFile
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
            recorder?.release()
        }
        recorder = null
        outputFile = null
        if (file == null || !file.exists() || file.length() < 256L) {
            file?.delete()
            return null
        }
        val bytes = file.readBytes()
        file.delete()
        return bytes to "audio/mp4"
    }

    fun cancel() {
        stopInternal(deleteFile = true)
    }

    private fun stopInternal(deleteFile: Boolean) {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
            recorder?.release()
        }
        recorder = null
        if (deleteFile) {
            outputFile?.delete()
        }
        outputFile = null
    }
}
