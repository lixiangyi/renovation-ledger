package com.renovation.ledger

import com.renovation.ledger.data.remote.CloudEnv
import com.renovation.ledger.data.remote.RequestUrlRewriter
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestUrlRewriterTest {
    @Test
    fun testHostSwitchToProdKeepsApiPath() {
        val original = "https://test.zhuangxiujizhang.site/auth/sms/send".toHttpUrl()
        val rewritten = RequestUrlRewriter.rewrite(original, CloudEnv.PROD_URL.toHttpUrl())
        assertEquals("https://api.zhuangxiujizhang.site/auth/sms/send", rewritten.toString())
    }

    @Test
    fun prodHostSwitchToTestKeepsApiPath() {
        val original = "https://api.zhuangxiujizhang.site/auth/sms/send".toHttpUrl()
        val rewritten = RequestUrlRewriter.rewrite(original, CloudEnv.TEST_URL.toHttpUrl())
        assertEquals("https://test.zhuangxiujizhang.site/auth/sms/send", rewritten.toString())
    }

    @Test
    fun legacyIpTestPathSwitchToProdDropsPrefix() {
        val original = "http://111.229.202.28/test/auth/sms/send".toHttpUrl()
        val rewritten = RequestUrlRewriter.rewrite(original, CloudEnv.PROD_URL.toHttpUrl())
        assertEquals("https://api.zhuangxiujizhang.site/auth/sms/send", rewritten.toString())
    }

    @Test
    fun testPathSwitchToLanDropsPrefix() {
        val original = "https://test.zhuangxiujizhang.site/ledgers".toHttpUrl()
        val rewritten = RequestUrlRewriter.rewrite(original, "http://10.35.86.169:8080/".toHttpUrl())
        assertEquals("http://10.35.86.169:8080/ledgers", rewritten.toString())
    }

    @Test
    fun keepsQuery() {
        val original = "https://test.zhuangxiujizhang.site/health?x=1".toHttpUrl()
        val rewritten = RequestUrlRewriter.rewrite(original, CloudEnv.PROD_URL.toHttpUrl())
        assertEquals("https://api.zhuangxiujizhang.site/health?x=1", rewritten.toString())
    }
}
