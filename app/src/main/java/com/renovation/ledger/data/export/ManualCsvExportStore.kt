package com.renovation.ledger.data.export

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
import kotlinx.coroutines.withContext

/**
 * 手动导出 CSV：文件名=账本名_时间；同账本旧导出会被新文件覆盖（删除旧再写新）。
 */
@Singleton
class ManualCsvExportStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun writeShareFile(ledgerName: String, csv: String): File = withContext(Dispatchers.IO) {
        val name = CsvExportFileNames.fileName(ledgerName)
        removeOldDownloads(ledgerName)
        writeDownloadBestEffort(name, csv)
        clearOldCacheExports(ledgerName)
        val shareFile = File(context.cacheDir, name)
        shareFile.writeText(csv, Charsets.UTF_8)
        shareFile
    }

    private fun clearOldCacheExports(ledgerName: String) {
        context.cacheDir.listFiles()?.forEach { file ->
            if (CsvExportFileNames.isSameLedgerExport(file.name, ledgerName)) {
                file.delete()
            }
        }
    }

    private fun removeOldDownloads(ledgerName: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val prefix = "${CsvExportFileNames.sanitize(ledgerName)}_"
                resolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                    arrayOf("$prefix%"),
                    null,
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val displayName = cursor.getString(nameCol) ?: continue
                        if (!CsvExportFileNames.isSameLedgerExport(displayName, ledgerName)) continue
                        val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                        resolver.delete(uri, null, null)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.listFiles()?.forEach { file ->
                    if (CsvExportFileNames.isSameLedgerExport(file.name, ledgerName)) {
                        file.delete()
                    }
                }
            }
        }.onFailure { Log.e(TAG, "remove old exports failed", it) }
    }

    private fun writeDownloadBestEffort(displayName: String, csv: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(
                    collection,
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    },
                ) ?: return
                resolver.openOutputStream(uri, "wt")?.use {
                    it.write(csv.toByteArray(Charsets.UTF_8))
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                File(dir, displayName).writeText(csv, Charsets.UTF_8)
            }
        }.onFailure { Log.e(TAG, "download write failed", it) }
    }

    companion object {
        private const val TAG = "ManualCsvExport"
    }
}
