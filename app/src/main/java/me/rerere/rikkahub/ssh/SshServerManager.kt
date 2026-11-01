package me.rerere.rikkahub.ssh

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.MessageDigest

private const val TAG = "SshServerManager"
private const val HOST_ALL_INTERFACES = "0.0.0.0"
private const val HOST_LOOPBACK = "127.0.0.1"

/** 默认端口，和 Web 服务器的 8080 分开配置 */
const val SSH_DEFAULT_PORT = 2222

/**
 * 内置 SSH 服务端。
 *
 * 只做密码认证，密码就是 Web 服务器那张访问令牌（[SettingsStore] 里的 web server 访问密码），
 * 用户名随便填。默认只绑回环地址，开了「允许局域网访问」才绑 0.0.0.0。
 */
class SshServerManager(
    private val context: Context,
    private val appScope: AppScope,
    private val chatService: ChatService,
    private val conversationRepo: ConversationRepository,
    private val settingsStore: SettingsStore,
) {
    private var server: SshServer? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _port = MutableStateFlow(SSH_DEFAULT_PORT)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun start(port: Int = SSH_DEFAULT_PORT, token: String, allowLan: Boolean = false) {
        if (server != null) {
            Log.w(TAG, "SSH server already running")
            return
        }
        if (token.isBlank()) {
            Log.w(TAG, "Refusing to start: access token is empty")
            _isRunning.value = false
            _error.value = "access token is empty, set the web server access password first"
            return
        }

        appScope.launch(Dispatchers.IO) {
            val listenHost = if (allowLan) HOST_ALL_INTERFACES else HOST_LOOPBACK
            val listenPort = port
            try {
                if (server != null) return@launch
                if (!isPortAvailable(listenHost, listenPort)) {
                    Log.w(TAG, "Port $listenPort is already in use")
                    _error.value = "port $listenPort is already in use"
                    return@launch
                }

                val api = SshCommandApi(chatService, conversationRepo, settingsStore)
                val sshd = SshServer.setUpDefaultServer()
                sshd.setHost(listenHost)
                sshd.setPort(listenPort)
                sshd.setKeyPairProvider(
                    SimpleGeneratorHostKeyProvider(hostKeyFile().toPath()).apply {
                        // 只用 RSA：Android 的 JCE 没有 EdDSA，省得 sshd 去找 BouncyCastle
                        setAlgorithm("RSA")
                    }
                )
                sshd.setPasswordAuthenticator(
                    PasswordAuthenticator { username, password, _ ->
                        username.isNotBlank() &&
                            password != null &&
                            MessageDigest.isEqual(
                                password.toByteArray(Charsets.UTF_8),
                                token.toByteArray(Charsets.UTF_8),
                            )
                    }
                )
                sshd.setCommandFactory { _, command ->
                    SshShellCommand(api, execCommand = command.orEmpty())
                }
                sshd.setShellFactory { _ ->
                    SshShellCommand(api, execCommand = null)
                }

                sshd.start()
                server = sshd
                _port.value = sshd.getPort()
                _error.value = null
                _isRunning.value = true
                Log.i(TAG, "SSH server started on $listenHost:${sshd.getPort()}")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start SSH server", t)
                _isRunning.value = false
                _error.value = t.message ?: t.toString()
            }
        }
    }

    fun stop() {
        _isRunning.value = false
        appScope.launch(Dispatchers.IO) {
            val current = server ?: return@launch
            server = null
            try {
                current.stop(true)
                Log.i(TAG, "SSH server stopped")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to stop SSH server", t)
                _error.value = t.message ?: t.toString()
            }
        }
    }

    /** 主机密钥固定落在 filesDir/ssh/hostkey.ser，不存在时由 sshd 生成一次，之后一直复用 */
    private fun hostKeyFile(): File {
        val dir = File(context.filesDir, "ssh")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "hostkey.ser")
    }

    private fun isPortAvailable(host: String, port: Int): Boolean = try {
        ServerSocket().use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(host, port))
            true
        }
    } catch (e: Exception) {
        false
    }
}
