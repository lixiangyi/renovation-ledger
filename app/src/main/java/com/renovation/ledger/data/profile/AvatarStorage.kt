package com.renovation.ledger.data.profile

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvatarStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun saveFromUri(uri: Uri): String {
        val dir = File(context.filesDir, "avatars").apply { mkdirs() }
        val outFile = File(dir, "self_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取所选图片")
        // 清理旧头像，只保留当前文件
        dir.listFiles()
            ?.filter { it.isFile && it.absolutePath != outFile.absolutePath }
            ?.forEach { it.delete() }
        return outFile.absolutePath
    }
}
