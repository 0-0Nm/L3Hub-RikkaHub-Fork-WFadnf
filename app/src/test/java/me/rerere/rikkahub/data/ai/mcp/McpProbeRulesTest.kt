package me.rerere.rikkahub.data.ai.mcp

import me.rerere.rikkahub.data.ai.mcp.McpProbeRules.Hint
import me.rerere.rikkahub.data.ai.mcp.McpProbeRules.Phase
import org.junit.Assert.assertEquals
import org.junit.Test

class McpProbeRulesTest {
    @Test fun `network causes differ from protocol failure`() {
        assertEquals(Hint.TCP_REFUSED, McpProbeRules.assess(Phase.INITIALIZE,false,null,"REFUSED",false))
        assertEquals(Hint.TCP_TIMEOUT, McpProbeRules.assess(Phase.INITIALIZE,false,null,"TCP_TIMEOUT",true))
        assertEquals(Hint.DEADLINE, McpProbeRules.assess(Phase.INITIALIZE,false,null,null,true))
        assertEquals(Hint.MCP_INIT, McpProbeRules.assess(Phase.INITIALIZE,false,200,null,false))
        assertEquals(Hint.TOOLS_LIST, McpProbeRules.assess(Phase.TOOLS_LIST,false,200,null,false))
    }
    @Test fun `status observations give distinct actionable outcomes`() {
        for ((status, expected) in mapOf(401 to Hint.AUTH, 403 to Hint.AUTH, 404 to Hint.PATH,
            301 to Hint.REDIRECT, 500 to Hint.SERVER_HTTP)) {
            assertEquals(expected, McpProbeRules.assess(Phase.INITIALIZE,false,status,null,false))
        }
        assertEquals(Hint.OK, McpProbeRules.assess(Phase.TOOLS_LIST,true,200,null,false))
    }
}
