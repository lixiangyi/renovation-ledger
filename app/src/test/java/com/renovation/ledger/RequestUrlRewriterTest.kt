package com.renovation.ledger

import com.renovation.ledger.data.remote.CloudEnv
import com.renovation.ledger.data.remote.RequestUrlRewriter
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestUrlRewriterTest {
    @Test
    fun debugTestPathSwitchToProdDropsPrefix() {
        val original = "http://111.229.202.28/test/auth/sms/send".toHttpUrl()
        val rewritten = RequestUrlRewriter.rewrite(original, CloudEnv.PROD_URL.toHttpUrl())
        assertEquals("http://111.229.202.28/auth/sms/send", rewritten.toString())
    }

    @Test
    fun prodPathSwitchToTestAddsPrefix() {
        val original = "http://111.229.202.28/auth/sms/send".toHttpUrl()
        val rewritten = RequestUrlRewriter.rewrite(original, CloudEnv.TEST_URL.toHttpUrl())
        assertEquals("http://111.229.202.28/test/auth/sms/send", rewritten.toString())
    }

    @Test
    fun testPathSwitchToLanDropsPrefix() {
        val original = "http://111.229.202.28/test/ledgers".toHttpUrl()
        val rewritten = RequestUrlRewriter.rewrite(original, "http://10.35.86.169:8080/".toHttpUrl())
        assertEquals("http://10.35.86.169:8080/ledgers", rewritten.toString())
    }

    @Test
    fun keepsQuery() {
        val original = "http://111.229.202.28/test/health?x=1".toHttpUrl()
        val rewritten = RequestUrlRewriter.rewrite(original, CloudEnv.PROD_URL.toHttpUrl())
        assertEquals("http://111.229.202.28/health?x=1", rewritten.toString())
    }
}
