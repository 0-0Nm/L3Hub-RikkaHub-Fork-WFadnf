package me.rerere.rikkahub.data.ai.mcp

/** Only bounded, typed observations enter this classification. */
internal object McpProbeRules {
    enum class Phase { PREFLIGHT, INITIALIZE, TOOLS_LIST }
    enum class Hint {
        OK, NO_TOOLS_CAPABILITY, CONFIG, SSE_MANUAL, CLEARTEXT, AUTH, PATH, REDIRECT, SERVER_HTTP,
        DNS, TLS, TCP_REFUSED, TCP_TIMEOUT, TCP_OTHER, DEADLINE, MCP_INIT, TOOLS_LIST
    }

    fun assess(phase: Phase, ok: Boolean, status: Int?, network: String?, expired: Boolean): Hint {
        if (ok) return Hint.OK
        when (status ?: 0) {
            401, 403 -> return Hint.AUTH
            404, 405 -> return Hint.PATH
            in 300..399 -> return Hint.REDIRECT
            in 400..599 -> return Hint.SERVER_HTTP
        }
        return when (network) {
            "DNS" -> Hint.DNS
            "TLS" -> Hint.TLS
            "REFUSED" -> Hint.TCP_REFUSED
            "TCP_TIMEOUT" -> Hint.TCP_TIMEOUT
            "TCP_OTHER" -> Hint.TCP_OTHER
            else -> if (expired) Hint.DEADLINE else if (phase == Phase.TOOLS_LIST) Hint.TOOLS_LIST else Hint.MCP_INIT
        }
    }
}
