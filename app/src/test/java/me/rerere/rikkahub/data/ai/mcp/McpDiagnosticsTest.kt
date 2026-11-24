package me.rerere.rikkahub.data.ai.mcp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException

class McpDiagnosticsTest {
    @Test fun `loopback keeps port but does not echo credentials or path`() {
        assertEquals("http://loopback:18766", McpDiagnostics.safeEndpoint(
            "http://user:secret@127.0.0.1:18766/private-token?key=secret#private"))
    }
    @Test fun `remote hostname is not disclosed and malformed endpoints are rejected`() {
        assertEquals("https://remote:443", McpDiagnostics.safeEndpoint("https://secret.example.org/path"))
        assertEquals("invalid", McpDiagnostics.safeEndpoint("file:///private/token"))
        assertEquals("invalid", McpDiagnostics.safeEndpoint("http://localhost:70000/"))
    }
    @Test fun `wrapped failures yield stable codes without exception messages`() {
        assertEquals(McpDiagnostics.Failure("CONNECT_ERROR"),
            McpDiagnostics.classify(IllegalStateException("secret", ConnectException("private endpoint"))))
        assertEquals(McpDiagnostics.Failure("TIMEOUT"), McpDiagnostics.classify(SocketTimeoutException("secret")))
        assertEquals(McpDiagnostics.Failure("MCP_ERROR"), McpDiagnostics.classify(IllegalStateException("secret")))
    }
}
