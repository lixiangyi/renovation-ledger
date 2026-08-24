package com.renovation.ledger

import com.renovation.ledger.domain.ledger.DeleteLedgerCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteLedgerCopyTest {
    @Test
    fun editorUnbindCopy() {
        val copy = DeleteLedgerCopy.forRole("EDITOR", "协作本", hasCloudId = true)
        assertEquals("解绑账本", copy.title)
        assertEquals("解绑", copy.confirm)
        assertTrue(copy.body.contains("退出该账本协作"))
        assertTrue(copy.body.contains("协作本"))
    }

    @Test
    fun ownerDeleteCopy() {
        val copy = DeleteLedgerCopy.forRole("OWNER", "我的本", hasCloudId = true)
        assertEquals("删除账本", copy.title)
        assertEquals("删除", copy.confirm)
        assertTrue(copy.body.contains("删除云端账本"))
    }

    @Test
    fun localOnlyDeleteCopy() {
        val copy = DeleteLedgerCopy.forRole(null, "本地本", hasCloudId = false)
        assertEquals("删除账本", copy.title)
        assertEquals("删除", copy.confirm)
        assertTrue(copy.body.contains("移入垃圾箱"))
        assertTrue(!copy.body.contains("云端"))
    }
}
