package com.renovation.ledger.data.autosave

import android.content.ContentUris
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
            removeDownloadBackupBestEffort()
        }
    }

    suspend fun loadPreferred(): AutosaveSnapshot? = withContext(Dispatchers.IO) {
        mutex.withLock {
            readPrivate()?.let { text ->
                runCatching { codec.decode(text) }.getOrNull()
            }
        }
    }


    suspend fun probeSummary(): AutosaveSummary? = withContext(Dispatchers.IO) {
        mutex.withLock {
            readPrivate()?.let { text ->
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

    private fun removeDownloadBackupBestEffort() {
        runCatching {
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
                    while (c.moveToNext()) {
                        val uri = ContentUris.withAppendedId(collection, c.getLong(0))
                        resolver.delete(uri, null, null)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(dir, DOWNLOAD_NAME).delete()
                File(dir, "$DOWNLOAD_NAME.tmp").delete()
            }
        }.onFailure { Log.e(TAG, "remove download backup failed", it) }
    }

    companion object {
        private const val TAG = "LedgerAutosave"
        const val PRIVATE_NAME = "ledger_autosave.csv"
        const val DOWNLOAD_NAME = "装修记账_自动备份.csv"
    }
}
