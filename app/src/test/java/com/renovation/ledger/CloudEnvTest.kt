package com.renovation.ledger

import com.renovation.ledger.data.remote.CloudEnv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudEnvTest {

    @Test
    fun cloudDefaultsPointAtShanghaiServer() {
        assertEquals("http://111.229.202.28/", CloudEnv.PROD_URL)
        assertEquals("http://111.229.202.28/test/", CloudEnv.TEST_URL)
        assertEquals(CloudEnv.TEST_URL, CloudEnv.urlOf(CloudEnv.Kind.DEV))
        assertEquals(CloudEnv.PROD_URL, CloudEnv.urlOf(CloudEnv.Kind.PROD))
    }

    @Test
    fun migrateStoredUrl_rewritesOldPlaceholderAndLanDefaults() {
        assertEquals(
            CloudEnv.PROD_URL,
            CloudEnv.migrateStoredUrl("https://api.renovation-ledger.app"),
        )
        assertEquals(
            CloudEnv.PROD_URL,
            CloudEnv.migrateStoredUrl("https://api.renovation-ledger.app/"),
        )
        assertEquals(
            CloudEnv.TEST_URL,
            CloudEnv.migrateStoredUrl("http://10.35.86.169:8080"),
        )
        assertEquals(
            CloudEnv.TEST_URL,
            CloudEnv.migrateStoredUrl("http://127.0.0.1:8080/"),
        )
        assertEquals(
            CloudEnv.TEST_URL,
            CloudEnv.migrateStoredUrl("http://10.0.2.2:8080"),
        )
    }

    @Test
    fun migrateStoredUrl_keepsCustomAndNewCloudUrls() {
        assertNull(CloudEnv.migrateStoredUrl("http://111.229.202.28/"))
        assertNull(CloudEnv.migrateStoredUrl("http://111.229.202.28/test/"))
        assertNull(CloudEnv.migrateStoredUrl("http://192.168.1.8:8080/"))
    }
}
