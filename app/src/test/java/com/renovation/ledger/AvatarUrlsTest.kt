package com.renovation.ledger.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarUrlsTest {
    @Test
    fun absoluteUrl_resolvesRelativeAndPassthrough() {
        assertEquals(
            "http://10.0.0.1:8080/avatars/a.jpg",
            AvatarUrls.absoluteUrl("/avatars/a.jpg", "http://10.0.0.1:8080/"),
        )
        assertEquals(
            "https://cdn.example/x.png",
            AvatarUrls.absoluteUrl("https://cdn.example/x.png", "http://ignored/"),
        )
        assertNull(AvatarUrls.absoluteUrl("/data/local.jpg", "http://10.0.0.1:8080/"))
        assertNull(AvatarUrls.absoluteUrl(null, "http://10.0.0.1:8080/"))
    }

    @Test
    fun isRemoteRef() {
        assertTrue(AvatarUrls.isRemoteRef("/avatars/x.jpg"))
        assertTrue(AvatarUrls.isRemoteRef("http://a/b"))
        assertFalse(AvatarUrls.isRemoteRef("/data/avatars/x.jpg"))
        assertFalse(AvatarUrls.isRemoteRef(null))
    }
}
