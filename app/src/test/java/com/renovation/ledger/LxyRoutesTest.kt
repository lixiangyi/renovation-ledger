package com.renovation.ledger

import com.renovation.ledger.ui.navigation.LxyRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LxyRoutesTest {
    @Test
    fun tabAndPageRoundTrip() {
        assertEquals("overview", LxyRoutes.parse(LxyRoutes.overview())!!.navRoute)
        assertTrue(LxyRoutes.parse("lxy://list")!!.tab)
        assertEquals("import/batch", LxyRoutes.parse(LxyRoutes.batchImport())!!.navRoute)
        assertFalse(LxyRoutes.parse(LxyRoutes.login())!!.tab)
    }

    @Test
    fun itemPendingAndEntry() {
        assertEquals("item/abc", LxyRoutes.parse(LxyRoutes.item("abc"))!!.navRoute)
        assertEquals("item/a%20b", LxyRoutes.parse(LxyRoutes.item("a b"))!!.navRoute)
        assertEquals(
            "pending?tab=tobuy",
            LxyRoutes.parse(LxyRoutes.pending("tobuy"))!!.navRoute,
        )
        assertEquals("pending?tab=unpaid", LxyRoutes.parse("LXY://pending")!!.navRoute)
        assertEquals(
            "paidgap?tab=surplus",
            LxyRoutes.parse(LxyRoutes.paidGap("surplus"))!!.navRoute,
        )
        assertEquals(
            "entry/manual?itemId=i1&editItemId=",
            LxyRoutes.parse(LxyRoutes.manualEntry(itemId = "i1"))!!.navRoute,
        )
        assertEquals(
            "entry/manual?itemId=&editItemId=",
            LxyRoutes.parse("LXY://entry/manual?fromVoice=1")!!.navRoute,
        )
        assertEquals(
            "entry/confirm?source=image&itemId=",
            LxyRoutes.parse(LxyRoutes.confirmEntry("image"))!!.navRoute,
        )
    }

    @Test
    fun rejectsUnknownAndEmptyItem() {
        assertNull(LxyRoutes.parse("LXY://debug/cloud"))
        assertNull(LxyRoutes.parse("LXY://item/"))
        assertNull(LxyRoutes.parse("https://item/1"))
        assertNull(LxyRoutes.parse("LXY://"))
    }
}
