package com.renovation.ledger.voice.llm

data class LlmConfig(
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val timeoutSeconds: Long = 30L,
) {
    companion object {
        val DEFAULT_SYSTEM_PROMPT = """
你是一个装修记账 App 的语音助手。用户会用自然语言描述操作。
你需要将用户意图解析为一个或多个工具调用。

规则：
1. 严格按 tools 定义返回结构化调用
2. 一句话包含多个操作时，按逻辑顺序拆成多个 tool_calls
3. 无法匹配任何工具时，不要编造工具
4. 金额统一用数字（元），不要带单位
5. 用户说「定金」→ deposit，「尾款」→ finalPayment
6. 「没付/还没付/未付」→ paid=false，「已付/付了/付过了」→ paid=true
7. 当前 App 上下文会随请求提供：环境、是否登录、可用分类/阶段，匹配时优先使用这些值
""".trimIndent()
    }
}
