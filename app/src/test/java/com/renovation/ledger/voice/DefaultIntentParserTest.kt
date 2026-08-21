package com.renovation.ledger.voice

import com.renovation.ledger.voice.llm.AppContext
import com.renovation.ledger.voice.llm.DefaultIntentParser
import com.renovation.ledger.voice.llm.IntentError
import com.renovation.ledger.voice.llm.LlmProvider
import com.renovation.ledger.voice.llm.provideLlmProvider
import com.renovation.ledger.voice.tool.RiskLevel
import com.renovation.ledger.voice.tool.ToolSchema
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultIntentParserTest {

    @Test
    fun parseReturnsToolCallsFromProviderJson() = runTest {
        val provider = FakeLlmProvider(
            raw = """
                {
                  "tool_calls": [
                    {"tool": "switch_env", "params": {"env": "prod"}},
                    {"tool": "wechat_login", "params": {}}
                  ]
                }
            """.trimIndent(),
        )
        val parser = DefaultIntentParser(provider)
        val result = parser.parse(
            text = "切换到正式环境并启用微信登录",
            tools = listOf(
                ToolSchema("switch_env", "切环境", """{"type":"object"}""", RiskLevel.LOW),
                ToolSchema("wechat_login", "微信登录", """{"type":"object"}""", RiskLevel.LOW),
            ),
            context = demoContext(),
        )

        assertNull(result.error)
        assertEquals(2, result.toolCalls.size)
        assertEquals("switch_env", result.toolCalls[0].tool)
        assertEquals("wechat_login", result.toolCalls[1].tool)
    }

    @Test
    fun parseReturnsNoMatchWhenToolCallsEmpty() = runTest {
        val parser = DefaultIntentParser(FakeLlmProvider("""{"tool_calls": []}"""))
        val result = parser.parse("今天天气怎么样", emptyList(), demoContext())
        assertEquals(IntentError.NO_MATCH, result.error)
    }

    @Test
    fun parseReturnsParseFailedWhenJsonBroken() = runTest {
        val parser = DefaultIntentParser(FakeLlmProvider("""{"tool_calls":["""))
        val result = parser.parse("切环境", emptyList(), demoContext())
        assertEquals(IntentError.PARSE_FAILED, result.error)
    }

    @Test
    fun parseReturnsNetworkErrorWhenProviderThrows() = runTest {
        val provider = object : LlmProvider {
            override val name = "fake"
            override suspend fun chat(prompt: String, tools: List<ToolSchema>): String = error("timeout")
        }
        val result = DefaultIntentParser(provider).parse("切环境", emptyList(), demoContext())
        assertEquals(IntentError.NETWORK_ERROR, result.error)
    }

    @Test
    fun voiceModuleUsesDeepSeekByDefault() {
        val provider = provideLlmProvider(
            selected = "deepseek",
            deepSeek = FakeLlmProvider("{}", name = "deepseek"),
            openAi = FakeLlmProvider("{}", name = "openai"),
        )
        assertEquals("deepseek", provider.name)
    }

    @Test
    fun voiceModuleCanSelectOpenAi() {
        val provider = provideLlmProvider(
            selected = "openai",
            deepSeek = FakeLlmProvider("{}", name = "deepseek"),
            openAi = FakeLlmProvider("{}", name = "openai"),
        )
        assertEquals("openai", provider.name)
    }
}

internal class FakeLlmProvider(
    private val raw: String,
    override val name: String = "fake",
) : LlmProvider {
    override suspend fun chat(prompt: String, tools: List<ToolSchema>): String = raw
}

internal fun demoContext() = AppContext(
    currentEnv = "dev",
    isLoggedIn = false,
    isDebugBuild = true,
    availableCategories = listOf("家电"),
    availableStages = listOf("主材"),
)
