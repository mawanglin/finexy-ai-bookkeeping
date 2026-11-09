package com.finexy.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentAiDataTest {
    private val summary = """{"requests":3,"failedRequests":1,"unmeteredRequests":0,"inputTokens":10,"outputTokens":20,"totalTokens":30,"cachedTokens":0,"reasoningTokens":0,"durationMs":900}"""

    @Test
    fun parsesUsageWithRecentCalls() {
        val raw = """{"success":true,"result":{"today":$summary,"month":$summary,"all":$summary,"updatedAt":1700000000,"timezone":"Asia/Shanghai",
            "recent":[{"time":1700000000,"provider":"openai","modelId":"deepseek-chat","success":true,"usageKnown":true,"totalTokens":30,"durationMs":400}]}}"""
        val usage = AgentAiParser.parseUsage(raw)
        assertEquals(30L, usage.today.totalTokens)
        assertEquals(1, usage.recent.size)
        assertEquals("deepseek-chat", usage.recent.single().modelId)
    }

    @Test
    fun rejectsUsageWithoutSummaryOrWithNegativeCounts() {
        assertThrows(IllegalArgumentException::class.java) { AgentAiParser.parseUsage("""{"success":true,"result":{"today":$summary}}""") }
        val negative = summary.replace("\"requests\":3", "\"requests\":-1")
        assertThrows(IllegalArgumentException::class.java) {
            AgentAiParser.parseUsage("""{"success":true,"result":{"today":$negative,"month":$summary,"all":$summary}}""")
        }
    }

    @Test
    fun parsesBalanceAndUnsupportedStatus() {
        val ok = AgentAiParser.parseBalance("""{"success":true,"result":{"status":"ok","available":true,"updatedAt":1,
            "balances":[{"currency":"CNY","totalBalance":"12.50","grantedBalance":"2.50","toppedUpBalance":"10.00"}]}}""")
        assertTrue(ok.available); assertEquals("12.50", ok.balances.single().total)
        val unsupported = AgentAiParser.parseBalance("""{"success":true,"result":{"status":"unsupported","available":false,"balances":[]}}""")
        assertEquals("unsupported", unsupported.status); assertTrue(unsupported.balances.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            AgentAiParser.parseBalance("""{"success":true,"result":{"balances":[{"currency":"","totalBalance":"1"}]}}""")
        }
    }

    @Test
    fun accessRequiresBothFlags() {
        val state = AgentAiParser.parseAccess("""{"success":true,"result":{"mcpEnabled":true,"mcpAvailable":false}}""")
        assertTrue(state.mcpEnabled); assertFalse(state.mcpAvailable)
        assertThrows(IllegalArgumentException::class.java) { AgentAiParser.parseAccess("""{"success":true,"result":{"mcpEnabled":true}}""") }
    }

    @Test
    fun keepsOnlyAgentTokensAndStringLedgerIds() {
        val tokens = AgentAiParser.parseTokens("""{"success":true,"result":[
            {"tokenId":"a","tokenType":1,"name":"browser"},
            {"tokenId":"b","tokenType":5,"name":"codex","ledgerId":"9007199254741003","scopes":["read","transactions:write"],"expiresAt":1800000000},
            {"tokenId":"c","tokenType":8,"expiresAt":1800000001}]}""")
        assertEquals(listOf("b", "c"), tokens.map { it.tokenId })
        assertTrue(tokens[0].isMcp); assertEquals(9007199254741003L, tokens[0].ledgerId)
        assertEquals(listOf("read"), tokens[1].scopes)
        assertFalse(tokens[1].isMcp)
    }

    @Test
    fun generatedTokenMustContainToken() {
        val ok = AgentAiParser.parseGenerated("""{"success":true,"result":{"token":"ebk-x","mcpUrl":"https://nas/mcp"}}""", mcp = true)
        assertEquals("https://nas/mcp", ok.endpoint)
        assertThrows(IllegalArgumentException::class.java) { AgentAiParser.parseGenerated("""{"success":true,"result":{"token":""}}""", mcp = false) }
    }

    @Test
    fun scopeLabelsFallBackToRawValue() {
        assertEquals("记账", AgentScopes.label("transactions:write"))
        assertEquals("custom:scope", AgentScopes.label("custom:scope"))
    }
}
