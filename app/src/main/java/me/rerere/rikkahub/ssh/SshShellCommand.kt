package me.rerere.rikkahub.ssh

import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream
import kotlin.concurrent.thread

/** 命令层需要的那几个单例，连一次 SSH 会话共用一份。 */
class SshCommandApi(
    val chatService: ChatService,
    val conversationRepo: ConversationRepository,
    val settingsStore: SettingsStore,
)

/**
 * 按行处理的命令通道。
 *
 * 不申请 PTY：客户端发一行、我们执行、把结果写回去。
 * - `ssh -p 2222 l3hub@127.0.0.1` 拿到的是交互循环（shell 请求，参数为 null）
 * - `ssh -p 2222 l3hub@127.0.0.1 conversations` 只跑一条就退出（exec 请求）
 *
 * 行尾：交互式用 CRLF，客户端把终端切到 raw 模式后 `\n` 不会回到行首；
 * exec 用 LF，方便被脚本 / 管道直接消费。
 */
internal class SshShellCommand(
    private val api: SshCommandApi,
    private val execCommand: String?,
) : Command {
    private val lineSeparator = if (execCommand == null) "\r\n" else "\n"

    @Volatile
    private var running = false

    private var worker: Thread? = null
    private var inputStream: InputStream? = null
    private var outputStream: PrintStream? = null
    private var errorStream: PrintStream? = null
    private var exitCallback: ExitCallback? = null

    override fun setInputStream(inputStream: InputStream) {
        this.inputStream = inputStream
    }

    override fun setOutputStream(outputStream: OutputStream) {
        this.outputStream = PrintStream(outputStream, true, Charsets.UTF_8.name())
    }

    override fun setErrorStream(errorStream: OutputStream) {
        this.errorStream = PrintStream(errorStream, true, Charsets.UTF_8.name())
    }

    override fun setExitCallback(callback: ExitCallback) {
        this.exitCallback = callback
    }

    override fun start(channel: ChannelSession, env: Environment) {
        running = true
        worker = thread(start = true, isDaemon = true, name = "l3hub-ssh") { run() }
    }

    override fun destroy(channel: ChannelSession) {
        running = false
        worker?.interrupt()
        worker = null
    }

    private fun run() {
        val out = outputStream ?: return
        val err = errorStream ?: return
        val terminal = StreamTerminal(out, err, lineSeparator)
        val session = SshCommandSession(
            chatService = api.chatService,
            conversationRepo = api.conversationRepo,
            settingsStore = api.settingsStore,
            terminal = terminal,
            isAlive = { running },
        )

        try {
            if (execCommand != null) {
                runBlocking { session.execute(SshCommandParser.parse(execCommand)) }
            } else {
                terminal.println("L3Hub SSH — type 'help' for commands, 'exit' to close")
                val reader = inputStream?.bufferedReader(Charsets.UTF_8) ?: return
                while (running) {
                    terminal.print("> ")
                    val line = reader.readLine() ?: break
                    // 客户端发来的 Ctrl+C 是一个裸 0x03，不当成命令内容
                    val cleaned = line.replace("\u0003", "")
                    val keepGoing = runBlocking { session.execute(SshCommandParser.parse(cleaned)) }
                    if (!keepGoing) break
                }
            }
        } catch (_: Exception) {
            // 客户端断开、线程被打断都会走到这里，不算错误
        } finally {
            running = false
            exitCallback?.onExit(0)
        }
    }

    private class StreamTerminal(
        private val out: PrintStream,
        private val err: PrintStream,
        private val lineSeparator: String,
    ) : SshTerminal {
        override fun print(text: String) {
            out.print(text)
            out.flush()
        }

        override fun println(line: String) {
            out.print(line)
            out.print(lineSeparator)
            out.flush()
        }

        override fun errorln(line: String) {
            err.print(line)
            err.print(lineSeparator)
            err.flush()
        }
    }
}
