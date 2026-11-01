package me.rerere.rikkahub.ssh

/**
 * 终端里能敲的命令。
 *
 * 解析和执行分开：[SshCommandParser] 是纯函数，不碰 Android 也不碰 ChatService，
 * 执行在 [SshCommandSession] 里。
 */
sealed interface SshCommand {
    data object Help : SshCommand
    data object Conversations : SshCommand
    data object Models : SshCommand
    data class Send(val conversationId: String, val text: String) : SshCommand
    data class Tail(val conversationId: String) : SshCommand
    data object WhoAmI : SshCommand
    data object Exit : SshCommand
}

sealed interface SshParseResult {
    /** 空行：不回显、不报错 */
    data object Blank : SshParseResult

    data class Valid(val command: SshCommand) : SshParseResult

    /** 参数缺失、未知命令等，内容直接写 stderr */
    data class Invalid(val message: String) : SshParseResult
}

object SshCommandParser {
    const val USAGE_SEND = "usage: send <conversationId> <text>"
    const val USAGE_TAIL = "usage: tail <conversationId>"

    fun parse(line: String): SshParseResult {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return SshParseResult.Blank

        val token = trimmed.substringBefore(' ')
        // 只按第一个空格切，后面的整段都是参数，消息里的空格原样保留
        val rest = trimmed.substring(token.length).trimStart()

        return when (token.lowercase()) {
            "help" -> SshParseResult.Valid(SshCommand.Help)
            "conversations" -> SshParseResult.Valid(SshCommand.Conversations)
            "models" -> SshParseResult.Valid(SshCommand.Models)
            "whoami" -> SshParseResult.Valid(SshCommand.WhoAmI)
            "exit" -> SshParseResult.Valid(SshCommand.Exit)

            "send" -> {
                val conversationId = rest.substringBefore(' ')
                val text = rest.substring(conversationId.length).trim()
                if (conversationId.isEmpty() || text.isEmpty()) {
                    SshParseResult.Invalid(USAGE_SEND)
                } else {
                    SshParseResult.Valid(SshCommand.Send(conversationId, text))
                }
            }

            "tail" -> {
                val conversationId = rest.substringBefore(' ').trim()
                if (conversationId.isEmpty()) {
                    SshParseResult.Invalid(USAGE_TAIL)
                } else {
                    SshParseResult.Valid(SshCommand.Tail(conversationId))
                }
            }

            else -> SshParseResult.Invalid("unknown command: $token (type 'help' for the command list)")
        }
    }
}
