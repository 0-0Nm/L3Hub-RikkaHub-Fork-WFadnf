package me.rerere.rikkahub.data.ai.mcp

import android.os.Process
import android.os.SystemClock
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.rerere.rikkahub.BuildConfig
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Separate bounded, process-local diagnostics. Never retain raw configuration or exceptions. */
internal object McpDiagnostics {
    private const val MAX_ENTRIES = 120
    private val lock = Any()
    private var nextAttempt = 0L
    private val recent = MutableStateFlow<List<Entry>>(emptyList())
    val entries = recent.asStateFlow()

    enum class Stage { CONNECT_INITIALIZE, TOOLS_LIST }
    enum class Outcome { START, OK, FAILED, CANCELLED }
    data class Failure(val code: String, val http: Int? = null)
    data class Attempt(val id: Long, val server: String, val transport: String, val endpoint: String)
    data class Entry(
        val timestamp: Long,
        val attempt: Attempt,
        val uid: Int,
        val stage: Stage,
        val outcome: Outcome,
        val elapsedMillis: Long?,
        val failure: Failure?,
        val toolCount: Int?,
    ) {
        fun summary(): String = buildString {
            append("time=").append(java.time.Instant.ofEpochMilli(timestamp))
            append(" app=").append(BuildConfig.APPLICATION_ID).append('/').append(BuildConfig.VERSION_CODE)
            append(" uid=").append(uid)
            append(" attempt=").append(attempt.id).append(" server=").append(attempt.server)
            append(" transport=").append(attempt.transport).append(" endpoint=").append(attempt.endpoint)
            append(" phase=").append(stage.name).append(" result=").append(outcome.name)
            elapsedMillis?.let { append(" elapsedMs=").append(it) }
            failure?.let { append(" error=").append(it.code); it.http?.let { status -> append(" http=").append(status) } }
            toolCount?.let { append(" tools=").append(it) }
        }
    }

    fun clear() = synchronized(lock) { recent.value = emptyList() }

    fun begin(config: McpServerConfig): Attempt = synchronized(lock) {
        Attempt(
            id = ++nextAttempt,
            server = config.id.toString().take(8),
            transport = when (config) {
                is McpServerConfig.SseTransportServer -> "SSE"
                is McpServerConfig.StreamableHTTPServer -> "STREAMABLE_HTTP"
            },
            endpoint = safeEndpoint(config.serverUrl),
        )
    }

    suspend fun <T> measure(
        attempt: Attempt,
        stage: Stage,
        toolCount: (T) -> Int? = { null },
        operation: suspend () -> T,
    ): T {
        val start = SystemClock.elapsedRealtime()
        record(attempt, stage, Outcome.START)
        try {
            val result = operation()
            record(attempt, stage, Outcome.OK, start, toolCount = toolCount(result))
            return result
        } catch (error: CancellationException) {
            record(attempt, stage, Outcome.CANCELLED, start, classify(error))
            throw error
        } catch (error: Exception) {
            record(attempt, stage, Outcome.FAILED, start, classify(error))
            throw error
        }
    }

    private fun record(
        attempt: Attempt, stage: Stage, outcome: Outcome, started: Long? = null,
        failure: Failure? = null, toolCount: Int? = null,
    ) = synchronized(lock) {
        val item = Entry(System.currentTimeMillis(), attempt, Process.myUid(), stage, outcome,
            started?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) }, failure, toolCount)
        recent.value = (listOf(item) + recent.value).take(MAX_ENTRIES)
    }

    // Echo only a fixed scheme, a fixed host category, and a numeric port.
    internal fun safeEndpoint(raw: String): String {
        val uri = try { URI(raw) } catch (_: Exception) { return "invalid" }
        val scheme = uri.scheme?.lowercase().takeIf { it == "http" || it == "https" } ?: return "invalid"
        val host = uri.host?.lowercase() ?: return "invalid"
        val local = host in setOf("localhost", "127.0.0.1", "[::1]", "::1")
        val port = if (uri.port == -1) (if (scheme == "https") 443 else 80) else uri.port
        if (port !in 1..65535) return "invalid"
        return "$scheme://${if (local) "loopback" else "remote"}:$port"
    }

    internal fun classify(error: Throwable): Failure {
        val chain = generateSequence(error) { it.cause }.take(12).toList()
        val http = chain.filterIsInstance<ResponseException>().firstOrNull()?.response?.status?.value
        if (http != null) return Failure("HTTP_ERROR", http)
        val code = when {
            chain.any { it is SocketTimeoutException || it is TimeoutCancellationException ||
                it.javaClass.simpleName in setOf("ConnectTimeoutException", "HttpRequestTimeoutException", "SocketTimeoutException") } -> "TIMEOUT"
            chain.any { it is CancellationException } -> "CANCELLED"
            chain.any { it is UnknownHostException } -> "DNS"
            chain.any { it is SSLException } -> "TLS"
            chain.any { it is SecurityException } -> "PERMISSION"
            chain.any { it is ConnectException } -> "CONNECT_ERROR"
            else -> "MCP_ERROR"
        }
        return Failure(code)
    }
}
