package com.renovation.ledger

import com.renovation.ledger.data.autosave.AutosaveCsvCodec
import com.renovation.ledger.data.autosave.AutosaveSnapshot
import com.renovation.ledger.data.trash.TrashStore
import com.renovation.ledger.domain.model.Project
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrashStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: TrashStore
    private val codec = AutosaveCsvCodec()

    @Before
    fun setUp() {
        store = TrashStore(tmp.root, codec)
    }

    @Test
    fun writeAndList_emptyProjectCsv_andIndex() = runTest {
        val project = Project("p1", "测试账本", listOf("我"))
        val csv = codec.encode(AutosaveSnapshot(project, emptyList(), emptyList()))
        store.writeTrash(
            projectId = "p1",
            name = "测试账本",
            itemCount = 0,
            csvText = csv,
            deletedAt = 100L,
        )
        val list = store.listEntries()
        assertEquals(1, list.size)
        assertEquals("p1", list[0].id)
        assertEquals("测试账本", list[0].name)
        assertEquals(0, list[0].itemCount)
        assertTrue(File(tmp.root, "trash/p1.csv").exists())
    }

    @Test
    fun list_sortedByDeletedAtDesc() = runTest {
        store.writeTrash(
            "a",
            "A",
            0,
            codec.encode(AutosaveSnapshot(Project("a", "A", listOf("我")), emptyList(), emptyList())),
            100L,
        )
        store.writeTrash(
            "b",
            "B",
            0,
            codec.encode(AutosaveSnapshot(Project("b", "B", listOf("我")), emptyList(), emptyList())),
            200L,
        )
        assertEquals(listOf("b", "a"), store.listEntries().map { it.id })
    }

    @Test
    fun removeEntry_deletesCsvAndIndex() = runTest {
        store.writeTrash(
            "p1",
            "X",
            0,
            codec.encode(AutosaveSnapshot(Project("p1", "X", listOf("我")), emptyList(), emptyList())),
            1L,
        )
        store.removeEntry("p1")
        assertTrue(store.listEntries().isEmpty())
        assertFalse(File(tmp.root, "trash/p1.csv").exists())
    }
}
