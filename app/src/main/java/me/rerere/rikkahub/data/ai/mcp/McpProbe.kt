package me.rerere.rikkahub.data.ai.mcp

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.security.NetworkSecurityPolicy
import android.system.ErrnoException
import android.system.OsConstants
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.BuildConfig
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.EventListener
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLException

internal object McpProbe {
    private const val BUDGET_MS = 15_000L

    data class Report(
        val timestamp: Long, val uid: Int, val server: String, val endpoint: String,
        val phase: McpProbeRules.Phase, val initialized: Boolean, val hint: McpProbeRules.Hint,
        val toolCount: Int?, val moreTools: Boolean, val elapsedMs: Long, val observations: List<String>,
    ) {
        fun summary(): String = buildString {
            append("time=").append(java.time.Instant.ofEpochMilli(timestamp))
            append(" app=").append(BuildConfig.APPLICATION_ID).append('/').append(BuildConfig.VERSION_CODE)
            append(" uid=").append(uid).append(" server=").append(server).append(" endpoint=").append(endpoint)
            append(" phase=").append(phase).append(" initialized=").append(initialized).append(" result=").append(hint)
            append(" elapsedMs=").append(elapsedMs)
            toolCount?.let { append(" firstPageTools=").append(it).append(" morePages=").append(moreTools) }
            observations.forEach { append('\n').append(it) }
        }
    }

    private class Trace : EventListener() {
        private val lock = Any()
        private val rows = ArrayDeque<String>()
        private var status: Int? = null
        private var network: String? = null
        private val start = SystemClock.elapsedRealtime()
        fun add(line: String) = synchronized(lock) {
            if (rows.size == 64) rows.removeFirst()
            rows.addLast("+${SystemClock.elapsedRealtime() - start}ms $line")
        }
        fun read(): List<String> = synchronized(lock) { rows.toList() }
        fun signals(): Pair<Int?, String?> = synchronized(lock) { status to network }
        fun clearSignals() = synchronized(lock) { status = null; network = null }
        override fun dnsStart(call: Call, domainName: String) = add("DNS START")
        override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) =
            add("DNS OK addresses=${inetAddressList.size}")
        override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) =
            add("TCP START route=${proxy.type().name} family=${McpProbeEvidence.addressFamily(inetSocketAddress.address)}")
        override fun secureConnectStart(call: Call) = add("TLS START")
        override fun secureConnectEnd(call: Call, handshake: okhttp3.Handshake?) = add("TLS OK")
        override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
            if (call.request().method == "POST") synchronized(lock) { network = null }
            add("CONNECTION OK")
        }
        override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) {
            val failure = networkCode(ioe, connecting = true) ?: "TCP_OTHER"
            if (call.request().method == "POST") synchronized(lock) { network = failure }
            add("CONNECTION FAILED $failure")
        }
        override fun responseHeadersEnd(call: Call, response: Response) {
            val method = call.request().method
            if (method == "POST") synchronized(lock) { status = response.code }
            add("HTTP $method ${response.code}")
        }
    }

    private fun networkCode(error: Throwable, connecting: Boolean = false): String? {
        val chain = generateSequence(error) { it.cause }.take(12).toList()
        return when {
            chain.any { it is UnknownHostException } -> "DNS"
            chain.any { it is SSLException } -> "TLS"
            chain.filterIsInstance<ErrnoException>().any { it.errno == OsConstants.ECONNREFUSED } ||
                chain.filterIsInstance<ConnectException>().any { it.message?.contains("ECONNREFUSED") == true ||
                    it.message?.contains("Connection refused", ignoreCase = true) == true } -> "REFUSED"
            chain.any { it.javaClass.simpleName == "ConnectTimeoutException" } ||
                (connecting && chain.any { it is SocketTimeoutException }) -> "TCP_TIMEOUT"
            chain.any { it is ConnectException } -> "TCP_OTHER"
            else -> null
        }
    }

    suspend fun run(config: McpServerConfig, base: OkHttpClient,
        transport: (McpServerConfig, HttpClient) -> AbstractTransport, context: Context): Report = withContext(Dispatchers.IO) {
        val start = SystemClock.elapsedRealtime()
        val now = System.currentTimeMillis()
        val target = config.serverUrl.toHttpUrlOrNull()
        val endpoint = McpDiagnostics.safeEndpoint(config.serverUrl)
        val trace = Trace()
        McpProbeEvidence.environment(context).forEach { trace.add(it) }
        var phase = McpProbeRules.Phase.PREFLIGHT
        var initialized = false
        var tools: Int? = null
        var moreTools = false
        fun report(hint: McpProbeRules.Hint) = Report(now, Process.myUid(), config.id.toString().take(8),
            endpoint, phase, initialized, hint, tools, moreTools, SystemClock.elapsedRealtime() - start, trace.read())
        if (target == null || target.username.isNotEmpty() || target.password.isNotEmpty() || target.fragment != null) {
            return@withContext report(McpProbeRules.Hint.CONFIG)
        }
        // Legacy SSE can advertise a separate POST URI. Do not send diagnostic credentials there.
        if (config !is McpServerConfig.StreamableHTTPServer) return@withContext report(McpProbeRules.Hint.SSE_MANUAL)
        if (!target.isHttps && !NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(target.host)) {
            return@withContext report(McpProbeRules.Hint.CLEARTEXT)
        }
        trace.add("PREFLIGHT STREAMABLE_HTTP redirects=off existing_credentials=only")
        if (endpoint.contains("://loopback:")) trace.add("LOOPBACK from this app instance")
        val streamOpened = AtomicBoolean(false)
        val finished = AtomicBoolean(false)
        val requests = AtomicInteger(0)
        val transmitted = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Call, Boolean>())
        val engineClient = base.newBuilder().dispatcher(Dispatcher()).connectionPool(ConnectionPool())
            .cookieJar(CookieJar.NO_COOKIES).cache(null)
            .authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
            .connectTimeout(4, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).writeTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS).retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false).eventListener(trace)
            .addInterceptor { chain ->
                val request = chain.request()
                if (finished.get() || SystemClock.elapsedRealtime() - start >= BUDGET_MS ||
                    request.url != target || requests.incrementAndGet() > 8 ||
                    (request.method != "POST" && request.method != "GET") ||
                    (request.method == "GET" && !streamOpened.compareAndSet(false, true))) {
                    throw IOException("Diagnostic request outside scope")
                }
                chain.proceed(request)
            }.addNetworkInterceptor { chain ->
                // Guard the actual network exchange too (e.g. an HTTP 503 follow-up).
                if (finished.get() || SystemClock.elapsedRealtime() - start >= BUDGET_MS ||
                    chain.request().url != target || transmitted.put(chain.call(), true) != null) {
                    throw IOException("Diagnostic network exchange outside scope")
                }
                chain.proceed(chain.request())
            }.build()
        val http = HttpClient(OkHttp) {
            followRedirects = false
            engine { preconfigured = engineClient }
            install(ContentNegotiation) { json(Json { isLenient = true }) }
            install(SSE)
        }
        val sdk = Client(clientInfo = Implementation(name = "L3Hub diagnostic", version = "1.0"))
        try {
            val completed = withTimeoutOrNull((BUDGET_MS - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(0L)) {
                phase = McpProbeRules.Phase.INITIALIZE
                trace.add("SDK CONNECT+INITIALIZE START")
                sdk.connect(transport(config, http))
                initialized = true
                trace.add("SDK CONNECT+INITIALIZE OK")
                trace.clearSignals()
                phase = McpProbeRules.Phase.TOOLS_LIST
                if (sdk.serverCapabilities?.tools == null) {
                    trace.add("TOOLS_LIST SKIPPED capability not advertised")
                    return@withTimeoutOrNull report(McpProbeRules.Hint.NO_TOOLS_CAPABILITY)
                }
                trace.add("TOOLS_LIST START")
                val firstPage = sdk.listTools()
                tools = firstPage.tools.size
                moreTools = !firstPage.nextCursor.isNullOrEmpty()
                trace.add("TOOLS_LIST OK firstPage=$tools morePages=$moreTools")
                report(McpProbeRules.Hint.OK)
            }
            val (status, network) = trace.signals()
            completed ?: report(McpProbeRules.assess(phase, false, status, network, expired = true))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val (status, network) = trace.signals()
            val failure = McpDiagnostics.classify(e)
            report(McpProbeRules.assess(phase, false, failure.http ?: status, networkCode(e) ?: network,
                failure.code == "TIMEOUT"))
        } finally {
            finished.set(true)
            // No live client or stored credentials/config are changed by diagnostics.
            withContext(NonCancellable) {
                withTimeoutOrNull(800L) { runCatching { sdk.close() } }
                http.close()
                engineClient.dispatcher.cancelAll()
                engineClient.connectionPool.evictAll()
                engineClient.dispatcher.executorService.shutdown()
            }
        }
    }
}
