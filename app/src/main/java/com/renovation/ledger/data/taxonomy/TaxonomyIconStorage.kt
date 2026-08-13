package com.renovation.ledger.data.taxonomy

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 标签自定义图标（相册图片）本地文件存储，落在 filesDir/taxonomy_icons/。 */
@Singleton
class TaxonomyIconStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun saveFromUri(uri: Uri): String {
        val dir = File(context.filesDir, "taxonomy_icons").apply { mkdirs() }
        val outFile = File(dir, "icon_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取所选图片")
        return outFile.absolutePath
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}
