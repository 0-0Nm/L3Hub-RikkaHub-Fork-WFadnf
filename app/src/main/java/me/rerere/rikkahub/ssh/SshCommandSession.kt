package me.rerere.rikkahub.ssh

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ModelType
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import kotlin.uuid.Uuid

/** 终端输出。换行符、flush 这些和 SSH channel 相关的细节由实现负责，命令层只写文本。 */
interface SshTerminal {
    fun print(text: String)
    fun println(line: String)
    fun errorln(line: String)
}

/** 命令层直接调 ChatService / ConversationRepository / SettingsStore，不绕 HTTP。 */
class SshCommandSession(
    private val chatService: ChatService,
    private val conversationRepo: ConversationRepository,
    private val settingsStore: SettingsStore,
    private val terminal: SshTerminal,
    private val isAlive: () -> Boolean = { true },
) {
    /** 执行一行命令，返回 false 表示客户端要求关闭会话。 */
    suspend fun execute(result: SshParseResult): Boolean = when (result) {
        SshParseResult.Blank -> true
        is SshParseResult.Invalid -> {
            terminal.errorln(result.message)
            true
        }

        is SshParseResult.Valid -> executeCommand(result.command)
    }

    private suspend fun executeCommand(command: SshCommand): Boolean = when (command) {
        SshCommand.Help -> {
            HELP.forEach { terminal.println(it) }
            true
        }

        SshCommand.WhoAmI -> {
            whoAmI()
            true
        }

        SshCommand.Conversations -> {
            conversations()
            true
        }

        SshCommand.Models -> {
            models()
            true
        }

        SshCommand.Exit -> {
            terminal.println("bye")
            false
        }

        is SshCommand.Send -> {
            send(command)
            true
        }

        is SshCommand.Tail -> {
            tail(command)
            true
        }
    }

    private suspend fun whoAmI() {
        val settings = settingsStore.awaitLoaded()
        val assistant = settings.assistants.firstOrNull { it.id == settings.assistantId }
        terminal.println("l3hub")
        terminal.println("auth: password (the web server access token)")
        terminal.println("assistant: ${assistant?.name ?: settings.assistantId.toString()}")
    }

    private suspend fun conversations() {
        val settings = settingsStore.awaitLoaded()
        val conversations = conversationRepo.getConversationsOfAssistant(settings.assistantId).first()
        if (conversations.isEmpty()) {
            terminal.println("no conversations")
            return
        }
        terminal.println("ID                                    UPDATED   TITLE")
        conversations.forEach { conversation ->
            terminal.println(
                "${conversation.id}  ${relativeTime(conversation.updateAt.toEpochMilli())}  " +
                    oneLine(conversation.title, TITLE_WIDTH)
            )
        }
        terminal.println("${conversations.size} conversation(s)")
    }

    private suspend fun models() {
        val settings = settingsStore.awaitLoaded()
        val currentModelId = settings.chatModelId
        var count = 0
        terminal.println("  ID                                    NAME                 PROVIDER")
        settings.providers.filter { it.enabled }.forEach { provider ->
            provider.models.filter { it.type == ModelType.CHAT }.forEach { model ->
                val marker = if (model.id == currentModelId) "*" else " "
                terminal.println(
                    "$marker ${model.id}  ${oneLine(model.displayName, NAME_WIDTH).padEnd(NAME_WIDTH)} ${provider.name}"
                )
                count++
            }
        }
        if (count == 0) {
            terminal.println("no chat models configured")
        }
    }

    private suspend fun send(command: SshCommand.Send) {
        val conversationId = parseConversationId(command.conversationId) ?: return
        if (conversationRepo.getConversationById(conversationId) == null) {
            terminal.errorln("conversation not found: $conversationId")
            return
        }
        chatService.initializeConversation(conversationId)
        chatService.sendMessage(
            conversationId = conversationId,
            content = listOf(UIMessagePart.Text(command.text)),
            answer = true,
        )
        terminal.println("sent to $conversationId")
        terminal.println("follow the reply with: tail $conversationId")
    }

    private suspend fun tail(command: SshCommand.Tail) {
        val conversationId = parseConversationId(command.conversationId) ?: return
        if (conversationRepo.getConversationById(conversationId) == null) {
            terminal.errorln("conversation not found: $conversationId")
            return
        }
        chatService.initializeConversation(conversationId)

        var lastMessageId: Uuid? = null
        var printedChars = 0
        var lineOpen = false
        val seenErrors = mutableSetOf<Uuid>()

        // 只有会话内容变化时 StateFlow 才会发，生成状态单独一条；心跳用来在空闲时检查连接是否还在
        val heartbeat: Flow<Unit> = flow {
            while (true) {
                delay(TAIL_TICK_MILLIS)
                emit(Unit)
            }
        }

        try {
            combine(
                chatService.getConversationFlow(conversationId),
                chatService.getGenerationJobStateFlow(conversationId).map { it != null }.distinctUntilChanged(),
                heartbeat,
            ) { conversation, generating, _ -> conversation to generating }
                .collect { (conversation, generating) ->
                    if (!isAlive()) throw TailStopped()

                    chatService.errors.value.forEach { error ->
                        if (error.conversationId == conversationId && seenErrors.add(error.id)) {
                            if (lineOpen) terminal.println("")
                            lineOpen = false
                            terminal.errorln("error: ${error.error.message ?: error.error}")
                        }
                    }

                    val message = conversation.currentMessages.lastOrNull() ?: return@collect
                    val text = message.text()
                    when {
                        message.id != lastMessageId -> {
                            if (lineOpen) terminal.println("")
                            lastMessageId = message.id
                            printedChars = 0
                            lineOpen = false
                            if (text.isNotEmpty()) {
                                terminal.print(roleLabel(message.role) + text)
                                printedChars = text.length
                                lineOpen = true
                            }
                        }

                        text.length > printedChars -> {
                            terminal.print(text.substring(printedChars))
                            printedChars = text.length
                            lineOpen = true
                        }

                        // 重新生成 / 切换分支后内容会变短，这一条从头再打一遍
                        text.length < printedChars -> {
                            if (lineOpen) terminal.println("")
                            terminal.print(roleLabel(message.role) + text)
                            printedChars = text.length
                            lineOpen = true
                        }
                    }

                    if (!generating && lineOpen) {
                        terminal.println("")
                        lineOpen = false
                    }
                }
        } catch (_: TailStopped) {
            // 客户端断开
        }
        if (lineOpen) terminal.println("")
    }

    private fun parseConversationId(raw: String): Uuid? {
        val id = runCatching { Uuid.parse(raw) }.getOrNull()
        if (id == null) {
            terminal.errorln("invalid conversation id: $raw (expected a UUID)")
        }
        return id
    }

    private fun roleLabel(role: MessageRole): String = when (role) {
        MessageRole.USER -> "user: "
        MessageRole.ASSISTANT -> "assistant: "
        MessageRole.SYSTEM -> "system: "
        MessageRole.TOOL -> "tool: "
    }

    private fun UIMessage.text(): String = parts
        .filterIsInstance<UIMessagePart.Text>()
        .joinToString(separator = "") { it.text }

    private fun oneLine(text: String, maxLength: Int): String {
        val flat = text.replace('\n', ' ').replace('\r', ' ').trim()
        return if (flat.length <= maxLength) flat else flat.take(maxLength - 1) + "…"
    }

    private fun relativeTime(epochMillis: Long): String {
        val ageSeconds = ((System.currentTimeMillis() - epochMillis) / 1000).coerceAtLeast(0)
        val text = when {
            ageSeconds < 60 -> "${ageSeconds}s"
            ageSeconds < 3600 -> "${ageSeconds / 60}m"
            ageSeconds < 86_400 -> "${ageSeconds / 3600}h"
            else -> "${ageSeconds / 86_400}d"
        }
        return text.padStart(7)
    }

    private class TailStopped : Exception()

    companion object {
        private const val TAIL_TICK_MILLIS = 500L
        private const val TITLE_WIDTH = 60
        private const val NAME_WIDTH = 20

        private val HELP = listOf(
            "help                      show this list",
            "conversations             list conversations of the current assistant",
            "models                    list chat models",
            "send <conversationId> <text>   send a message, generation keeps running",
            "tail <conversationId>     stream a conversation until the connection closes",
            "whoami                    show the signed-in account",
            "exit                      close this session",
        )
    }
}
