package com.renovation.ledger.voice

import com.renovation.ledger.data.remote.CloudEnv
import com.renovation.ledger.di.ServerEndpoint
import com.renovation.ledger.voice.llm.ToolCall
import com.renovation.ledger.voice.tool.OrchestratorEvent
import com.renovation.ledger.voice.tool.PreviewField
import com.renovation.ledger.voice.tool.RiskLevel
import com.renovation.ledger.voice.tool.ToolExecutor
import com.renovation.ledger.voice.tool.ToolOrchestrator
import com.renovation.ledger.voice.tool.ToolPreview
import com.renovation.ledger.voice.tool.ToolRegistry
import com.renovation.ledger.voice.tool.ToolResult
import com.renovation.ledger.voice.tool.ToolSchema
import com.renovation.ledger.voice.tool.executors.CloudEnvStore
import com.renovation.ledger.voice.tool.executors.SwitchEnvExecutor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolOrchestratorTest {

    @Test
    fun lowRiskRunsSequentiallyThenHighRiskWaitsConfirm() = runTest {
        val env = fakeExecutor("switch_env", RiskLevel.LOW, "已切到正式环境")
        val add = fakeExecutor("add_ledger_entry", RiskLevel.HIGH, "已保存")
        val session = ToolOrchestrator(ToolRegistry(listOf(env, add))).start(
            listOf(
                ToolCall("switch_env", mapOf("env" to "prod")),
                ToolCall("add_ledger_entry", mapOf("name" to "扫地机器人")),
            ),
        )

        assertTrue(session.next() is OrchestratorEvent.Executed)
        assertTrue(session.next() is OrchestratorEvent.NeedConfirm)
    }

    @Test
    fun confirmContinuesRemainingSteps() = runTest {
        val executed = mutableListOf<String>()
        val high = fakeExecutor("add_ledger_entry", RiskLevel.HIGH, "已保存", executed)
        val low = fakeExecutor("wechat_login", RiskLevel.LOW, "拉起微信", executed)
        val session = ToolOrchestrator(ToolRegistry(listOf(high, low))).start(
            listOf(
                ToolCall("add_ledger_entry", mapOf("name" to "扫地机器人")),
                ToolCall("wechat_login", emptyMap()),
            ),
        )

        assertTrue(session.next() is OrchestratorEvent.NeedConfirm)
        assertTrue(session.confirmCurrent() is OrchestratorEvent.Executed)
        assertTrue(session.next() is OrchestratorEvent.Executed)
        assertTrue(session.next() is OrchestratorEvent.AllDone)
        assertEquals(listOf("add_ledger_entry", "wechat_login"), executed)
    }

    @Test
    fun cancelSkipsHighRiskAndContinuesNextLowRisk() = runTest {
        val executed = mutableListOf<String>()
        val high = fakeExecutor("add_ledger_entry", RiskLevel.HIGH, "已保存", executed)
        val low = fakeExecutor("wechat_login", RiskLevel.LOW, "拉起微信", executed)
        val session = ToolOrchestrator(ToolRegistry(listOf(high, low))).start(
            listOf(
                ToolCall("add_ledger_entry", mapOf("name" to "扫地机器人")),
                ToolCall("wechat_login", emptyMap()),
            ),
        )

        assertTrue(session.next() is OrchestratorEvent.NeedConfirm)
        session.cancelCurrent()
        val afterCancel = session.next()
        assertTrue(afterCancel is OrchestratorEvent.Executed)
        assertEquals(listOf("wechat_login"), executed)
    }

    @Test
    fun switchEnvExecutorUpdatesPrefsAndEndpoint() = runTest {
        val endpoint = ServerEndpoint()
        val store = object : CloudEnvStore {
            override suspend fun apply(kind: CloudEnv.Kind, url: String) {
                endpoint.baseUrl = url
            }
        }
        val executor = SwitchEnvExecutor(store, endpoint)
        val result = executor.execute(mapOf("env" to "prod"))
        assertTrue(result.success)
        assertEquals(CloudEnv.PROD_URL, endpoint.baseUrl)
    }

    @Test
    fun unknownEnvReturnsFailure() = runTest {
        val endpoint = ServerEndpoint()
        val store = object : CloudEnvStore {
            override suspend fun apply(kind: CloudEnv.Kind, url: String) = Unit
        }
        val result = SwitchEnvExecutor(store, endpoint).execute(mapOf("env" to "oops"))
        assertFalse(result.success)
    }

    @Test
    fun switchEnvExecutor_blockedWhenDebugPanelDisabled() = runTest {
        val endpoint = ServerEndpoint()
        val store = object : CloudEnvStore {
            override suspend fun apply(kind: CloudEnv.Kind, url: String) {
                endpoint.baseUrl = url
            }
        }
        val result = SwitchEnvExecutor(store, endpoint, allowSwitch = false)
            .execute(mapOf("env" to "prod"))
        assertFalse(result.success)
        assertEquals(CloudEnv.defaultUrl(), endpoint.baseUrl)
    }
}

internal fun fakeExecutor(
    name: String,
    risk: RiskLevel,
    message: String,
    executed: MutableList<String>? = null,
    previewTitle: String = name,
): ToolExecutor = object : ToolExecutor {
    override val toolName: String = name
    override val risk: RiskLevel = risk
    override val schema: ToolSchema = ToolSchema(name, name, """{"type":"object"}""", risk)
    override fun preview(params: Map<String, Any?>): ToolPreview =
        ToolPreview(previewTitle, listOf(PreviewField("名称", params["name"]?.toString().orEmpty(), true, "name")))

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        executed?.add(name)
        return ToolResult(true, message)
    }
}
