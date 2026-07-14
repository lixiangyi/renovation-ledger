package com.renovation.ledger.data.trash

import com.google.gson.reflect.TypeToken
import com.renovation.ledger.data.autosave.AutosaveCsvCodec
import com.renovation.ledger.data.autosave.AutosaveSnapshot
import com.renovation.ledger.dsl.catchException
import com.renovation.ledger.dsl.gson
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 沙盒垃圾箱：`filesDir/trash/index.json` + `{projectId}.csv`。
 * 由 [com.renovation.ledger.di.AppModule] 提供；单测直接 `TrashStore(tmpRoot, codec)`。
 */
class TrashStore(
    private val filesDir: File,
    private val codec: AutosaveCsvCodec,
) {
    private val mutex = Mutex()

    private fun trashDir(): File = File(filesDir, DIR_NAME).also { it.mkdirs() }

    private fun indexFile(): File = File(trashDir(), INDEX_NAME)

    private fun csvFile(projectId: String): File = File(trashDir(), "$projectId.csv")

    suspend fun listEntries(): List<TrashEntry> = withContext(Dispatchers.IO) {
        mutex.withLock { readIndexUnlocked().sortedByDescending { it.deletedAt } }
    }

    suspend fun writeTrash(
        projectId: String,
        name: String,
        itemCount: Int,
        csvText: String,
        deletedAt: Long = System.currentTimeMillis(),
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            trashDir()
            val csv = csvFile(projectId)
            val tmp = File(trashDir(), "$projectId.csv.tmp")
            tmp.writeText(csvText, Charsets.UTF_8)
            if (!tmp.renameTo(csv)) {
                csv.writeText(csvText, Charsets.UTF_8)
                tmp.delete()
            }
            val entry = TrashEntry(
                id = projectId,
                name = name,
                deletedAt = deletedAt,
                itemCount = itemCount,
                csvPath = "$projectId.csv",
            )
            val next = readIndexUnlocked().filterNot { it.id == projectId } + entry
            writeIndexUnlocked(next)
        }
    }

    suspend fun readCsvText(projectId: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = csvFile(projectId)
            if (!file.exists()) return@withLock null
            file.readText(Charsets.UTF_8)
        }
    }

    suspend fun removeEntry(projectId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            writeIndexUnlocked(readIndexUnlocked().filterNot { it.id == projectId })
            csvFile(projectId).delete()
        }
    }

    fun encodeSnapshot(snapshot: AutosaveSnapshot): String = codec.encode(snapshot)

    fun decodeCsv(csvText: String): AutosaveSnapshot? = codec.decode(csvText)

    private fun readIndexUnlocked(): List<TrashEntry> {
        val file = indexFile()
        if (!file.exists()) return emptyList()
        return catchException(isPrintStackTrace = false, onError = { emptyList() }) {
            val type = object : TypeToken<List<TrashEntry>>() {}.type
            gson.fromJson<List<TrashEntry>>(file.readText(Charsets.UTF_8), type) ?: emptyList()
        }
    }

    private fun writeIndexUnlocked(entries: List<TrashEntry>) {
        val sorted = entries.sortedByDescending { it.deletedAt }
        val json = gson.toJson(sorted)
        val tmp = File(trashDir(), "index.json.tmp")
        tmp.writeText(json, Charsets.UTF_8)
        val index = indexFile()
        if (!tmp.renameTo(index)) {
            index.writeText(json, Charsets.UTF_8)
            tmp.delete()
        }
    }

    companion object {
        const val DIR_NAME = "trash"
        const val INDEX_NAME = "index.json"
    }
}
