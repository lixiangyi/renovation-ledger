package com.renovation.ledger.data.autosave

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class LedgerAutosave @Inject constructor(
    @ApplicationContext private val context: Context,
    private val codec: AutosaveCsvCodec,
) {
    private val mutex = Mutex()

    suspend fun save(snapshot: AutosaveSnapshot) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (snapshot.items.isEmpty()) {
                Log.i(TAG, "skip save: empty items (refuse to overwrite backup)")
                return@withLock
            }
            val csv = codec.encode(snapshot)
            writePrivateAtomic(csv)
            writeDownloadBestEffort(csv)
        }
    }

    suspend fun loadPreferred(): AutosaveSnapshot? = withContext(Dispatchers.IO) {
        mutex.withLock {
            readPrivate()?.let { text ->
                runCatching { codec.decode(text) }.getOrNull()
            } ?: readDownload()?.let { text ->
                runCatching { codec.decode(text) }.getOrNull()
            }
        }
    }


    suspend fun probeSummary(): AutosaveSummary? = withContext(Dispatchers.IO) {
        mutex.withLock {
            readPrivate()?.let { text ->
                runCatching { codec.summarize(text) }.getOrNull()
            } ?: readDownload()?.let { text ->
                runCatching { codec.summarize(text) }.getOrNull()
            }
        }
    }


    private fun privateFile(): File = File(context.filesDir, PRIVATE_NAME)

    private fun writePrivateAtomic(csv: String) {
        val target = privateFile()
        val tmp = File(context.filesDir, "$PRIVATE_NAME.tmp")
        tmp.writeText(csv, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun readPrivate(): String? {
        val f = privateFile()
        if (!f.exists() || f.length() == 0L) return null
        return f.readText(Charsets.UTF_8)
    }

    private fun writeDownloadBestEffort(csv: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val existing = resolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                    arrayOf(DOWNLOAD_NAME),
                    null,
                )
                val uri = existing?.use { c ->
                    if (c.moveToFirst()) {
                        ContentUris.withAppendedId(collection, c.getLong(0))
                    } else {
                        null
                    }
                }
                val outUri = uri ?: resolver.insert(
                    collection,
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, DOWNLOAD_NAME)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    },
                ) ?: return
                resolver.openOutputStream(outUri, "wt")?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val target = File(dir, DOWNLOAD_NAME)
                val tmp = File(dir, "$DOWNLOAD_NAME.tmp")
                tmp.writeText(csv, Charsets.UTF_8)
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }.onFailure { Log.e(TAG, "download write failed", it) }
    }

    private fun readDownload(): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(DOWNLOAD_NAME),
                null,
            )?.use { c ->
                if (!c.moveToFirst()) return@use null
                val uri = ContentUris.withAppendedId(collection, c.getLong(0))
                resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }
        } else {
            @Suppress("DEPRECATION")
            val f = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                DOWNLOAD_NAME,
            )
            if (f.exists()) f.readText(Charsets.UTF_8) else null
        }
    }.getOrNull()

    companion object {
        private const val TAG = "LedgerAutosave"
        const val PRIVATE_NAME = "ledger_autosave.csv"
        const val DOWNLOAD_NAME = "装修记账_自动备份.csv"
    }
}
