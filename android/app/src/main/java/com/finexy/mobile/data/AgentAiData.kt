package com.finexy.mobile.data

import org.json.JSONArray
import org.json.JSONObject

/** Contracts follow pkg/models/ai_usage.go, agent_access.go and token_record.go. */
data class AiUsageSummary(
    val requests: Long, val failedRequests: Long, val unmeteredRequests: Long,
    val inputTokens: Long, val outputTokens: Long, val totalTokens: Long,
    val cachedTokens: Long, val reasoningTokens: Long, val durationMs: Long
)

data class AiUsageRecord(
    val time: Long, val provider: String, val modelId: String, val success: Boolean,
    val usageKnown: Boolean, val totalTokens: Long, val durationMs: Long
)

data class AiUsageSnapshot(
    val today: AiUsageSummary, val month: AiUsageSummary, val all: AiUsageSummary,
    val recent: List<AiUsageRecord>, val updatedAt: Long, val timezone: String
)

data class AiBalance(val currency: String, val total: String, val granted: String, val toppedUp: String)

/** status is one of the strings produced by pkg/api/ai_usage.go. */
data class AiBalanceSnapshot(val status: String, val available: Boolean, val balances: List<AiBalance>, val updatedAt: Long)

data class AgentAccessState(val mcpEnabled: Boolean, val mcpAvailable: Boolean)

data class AgentToken(
    val tokenId: String, val name: String, val isMcp: Boolean, val scopes: List<String>,
    val ledgerId: Long, val expiresAt: Long
)

data class GeneratedAgentToken(val token: String, val endpoint: String)

object AgentScopes {
    const val READ = "read"
    val all = listOf(
        READ to "只读查询：读取账户、余额、分类和流水（基础权限）",
        "transactions:write" to "记账：新增、修改和删除普通流水",
        "accounts:manage" to "账户与分类管理：新增、修改、停用和删除",
        "bills:import" to "账单导入：预览、映射并确认支付宝和微信账单"
    )

    fun label(scope: String): String = all.firstOrNull { it.first == scope }?.second?.substringBefore('：') ?: scope
}

object AgentAiParser {
    private fun result(raw: String): JSONObject {
        val root = JSONObject(raw)
        require(root.optBoolean("success", true)) { root.optString("errorMessage", "服务器拒绝了请求") }
        return root.optJSONObject("result") ?: error("响应缺少数据")
    }

    private fun summary(item: JSONObject?): AiUsageSummary {
        requireNotNull(item) { "用量响应缺少汇总" }
        fun n(key: String): Long = item.optLong(key, 0).also { require(it >= 0) { "用量数据无效" } }
        return AiUsageSummary(n("requests"), n("failedRequests"), n("unmeteredRequests"), n("inputTokens"),
            n("outputTokens"), n("totalTokens"), n("cachedTokens"), n("reasoningTokens"), n("durationMs"))
    }

    fun parseUsage(raw: String): AiUsageSnapshot {
        val data = result(raw)
        val recent = data.optJSONArray("recent") ?: JSONArray()
        return AiUsageSnapshot(
            summary(data.optJSONObject("today")), summary(data.optJSONObject("month")), summary(data.optJSONObject("all")),
            (0 until recent.length()).map { index ->
                val item = recent.getJSONObject(index)
                AiUsageRecord(item.optLong("time"), item.optString("provider"), item.optString("modelId"),
                    item.optBoolean("success"), item.optBoolean("usageKnown"), item.optLong("totalTokens"), item.optLong("durationMs"))
            },
            data.optLong("updatedAt"), data.optString("timezone")
        )
    }

    fun parseBalance(raw: String): AiBalanceSnapshot {
        val data = result(raw)
        val items = data.optJSONArray("balances") ?: JSONArray()
        val balances = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            AiBalance(item.optString("currency"), item.optString("totalBalance"), item.optString("grantedBalance"), item.optString("toppedUpBalance"))
        }
        require(balances.none { it.currency.isBlank() }) { "余额币种无效" }
        return AiBalanceSnapshot(data.optString("status"), data.optBoolean("available"), balances, data.optLong("updatedAt"))
    }

    fun parseAccess(raw: String): AgentAccessState {
        val data = result(raw)
        require(data.has("mcpEnabled") && data.has("mcpAvailable")) { "MCP 状态响应不完整" }
        return AgentAccessState(data.getBoolean("mcpEnabled"), data.getBoolean("mcpAvailable"))
    }

    /** Only API (8) and MCP (5) tokens are agent tokens; login sessions are filtered out. */
    fun parseTokens(raw: String): List<AgentToken> {
        val root = JSONObject(raw)
        require(root.optBoolean("success", true)) { root.optString("errorMessage", "服务器拒绝了请求") }
        val items = root.optJSONArray("result") ?: error("令牌列表响应缺少数据")
        return (0 until items.length()).map { items.getJSONObject(it) }
            .filter { it.optInt("tokenType") == TOKEN_TYPE_MCP || it.optInt("tokenType") == TOKEN_TYPE_API }
            .map { item ->
                val scopes = item.optJSONArray("scopes")
                AgentToken(
                    item.getString("tokenId"), item.optString("name"), item.optInt("tokenType") == TOKEN_TYPE_MCP,
                    if (scopes == null) listOf(AgentScopes.READ) else (0 until scopes.length()).map { scopes.getString(it) },
                    item.optString("ledgerId", "0").toLongOrNull() ?: 0L, item.optLong("expiresAt")
                )
            }
    }

    fun parseGenerated(raw: String, mcp: Boolean): GeneratedAgentToken {
        val data = result(raw)
        val token = data.optString("token")
        require(token.isNotBlank()) { "响应缺少令牌" }
        return GeneratedAgentToken(token, data.optString(if (mcp) "mcpUrl" else "apiBaseUrl"))
    }

    const val TOKEN_TYPE_MCP = 5
    const val TOKEN_TYPE_API = 8
}

class AgentAiApi(private val api: FinexyApi) {
    suspend fun usage(): AiUsageSnapshot = AgentAiParser.parseUsage(api.request("v1/llm/usage/get.json"))
    suspend fun balance(): AiBalanceSnapshot = AgentAiParser.parseBalance(api.request("v1/llm/balance/get.json"))
    suspend fun access(): AgentAccessState = AgentAiParser.parseAccess(api.request("v1/agent/access/get.json"))

    suspend fun setMcpEnabled(enabled: Boolean): AgentAccessState = AgentAiParser.parseAccess(
        api.request("v1/agent/access/update.json", JSONObject().put("mcpEnabled", enabled).toString())
    )

    suspend fun tokens(): List<AgentToken> = AgentAiParser.parseTokens(api.request("v1/tokens/list.json"))

    suspend fun generate(
        mcp: Boolean, name: String, scopes: Set<String>, expiresInSeconds: Long, ledgerId: Long, password: String
    ): GeneratedAgentToken {
        require(name.isNotBlank() && name.length <= 64) { "令牌名称不能为空且最多 64 个字符" }
        require(password.length in 6..128) { "请输入 Finexy 登录密码" }
        require(expiresInSeconds in 3600..2_592_000) { "有效期无效" }
        require(ledgerId >= 0) { "账本无效" }
        val payload = JSONObject().put("name", name.trim()).put("ledgerId", ledgerId.toString()).put("password", password)
            .put("expiresInSeconds", expiresInSeconds)
            .put("scopes", JSONArray((scopes + AgentScopes.READ).toList().sorted()))
        return AgentAiParser.parseGenerated(
            api.request(if (mcp) "v1/tokens/generate/mcp.json" else "v1/tokens/generate/api.json", payload.toString()), mcp
        )
    }

    suspend fun revoke(tokenId: String) {
        require(tokenId.isNotBlank()) { "令牌无效" }
        check(JSONObject(api.request("v1/tokens/revoke.json", JSONObject().put("tokenId", tokenId).toString())).optBoolean("success", true)) { "撤销失败" }
    }
}
