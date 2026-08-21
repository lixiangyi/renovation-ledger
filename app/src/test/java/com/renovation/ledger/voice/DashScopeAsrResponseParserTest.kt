package com.renovation.ledger.voice

import com.renovation.ledger.voice.asr.parseDashScopeAsrText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashScopeAsrResponseParserTest {
    @Test
    fun parsesOpenAiCompatibleContentString() {
        val json = """
            {"choices":[{"message":{"role":"assistant","content":"增加一笔家电扫地机器人两千九"}}]}
        """.trimIndent()
        assertEquals("增加一笔家电扫地机器人两千九", parseDashScopeAsrText(json))
    }

    @Test
    fun blankContentReturnsNull() {
        val json = """{"choices":[{"message":{"content":"  "}}]}"""
        assertNull(parseDashScopeAsrText(json))
    }

    @Test
    fun dashScopeNativeTextArray() {
        val json = """
            {"output":{"choices":[{"message":{"content":[{"text":"尾款六千"}]}}]}}
        """.trimIndent()
        assertEquals("尾款六千", parseDashScopeAsrText(json))
    }
}
