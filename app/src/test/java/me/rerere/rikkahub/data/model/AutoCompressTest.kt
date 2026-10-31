package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * 压缩预算的口径：门限判断把摘要和 systemPrompt 算进用量，截断目标也必须扣掉它们，
 * 否则保留区一大，压完立刻又超限。
 */
class AutoCompressTest {
    private fun conversation(vararg nodes: MessageNode) =
        Conversation(assistantId = Uuid.random(), messageNodes = nodes.toList())

    private fun checkpoint() =
        UIMessage.user("summary").copy(isContextCheckpoint = true).toMessageNode()

    // [length] 个 ASCII 字符约 length/4 token，每条消息再加 4 的固定开销
    private fun reply(length: Int = 400) = UIMessage.assistant("a".repeat(length)).toMessageNode()

    // 800 个 ASCII 字符约 200 token，每条消息再加 4 的固定开销
    private fun bigReply() = reply(800)

    @Test
    fun `reserved tokens shrink the token-mode target`() {
        val conversation = conversation(checkpoint(), reply(), reply(), reply(), reply())

        // 200 token 只装得下最后 2 条消息（每条 104），nodeIndex 停在 3
        assertEquals(
            ContextTrim(nodeIndex = 3),
            conversation.resolveAutoCompressTrim(
                target = AutoCompressTarget.TOKENS,
                targetValue = 200,
                keepTailNodes = null,
                force = false,
            ),
        )
        // 摘要占了 150，只剩 50 的预算：只能留最后一条
        assertEquals(
            ContextTrim(nodeIndex = 4),
            conversation.resolveAutoCompressTrim(
                target = AutoCompressTarget.TOKENS,
                targetValue = 200,
                keepTailNodes = null,
                force = false,
                reservedTokens = 150,
            ),
        )
    }

    @Test
    fun `token-mode target stops at a quarter of the threshold`() {
        // 每条 8 token 正文 + 4 固定开销；门限 100，固定开销 96 时预算只剩 4，
        // 没有下限会把消息全压掉（nodeIndex 等于消息数）
        val nodes = Array(5) { reply(32) }
        val conversation = conversation(checkpoint(), *nodes)

        assertEquals(
            ContextTrim(nodeIndex = 4),
            conversation.resolveAutoCompressTrim(
                target = AutoCompressTarget.TOKENS,
                targetValue = 100,
                keepTailNodes = null,
                force = false,
                reservedTokens = 96,
            ),
        )
        // 固定开销超过门限本身也不能把保留区压成空
        assertEquals(
            ContextTrim(nodeIndex = 4),
            conversation.resolveAutoCompressTrim(
                target = AutoCompressTarget.TOKENS,
                targetValue = 100,
                keepTailNodes = null,
                force = false,
                reservedTokens = 1_000,
            ),
        )
    }

    @Test
    fun `messages mode keeps the count when the estimate fits`() {
        val conversation = conversation(checkpoint(), bigReply(), bigReply(), bigReply(), bigReply(), bigReply())

        assertEquals(
            ContextTrim(nodeIndex = 3),
            conversation.resolveAutoCompressTrim(
                target = AutoCompressTarget.MESSAGES,
                targetValue = 3,
                keepTailNodes = null,
                force = false,
                limitTokens = 2_000,
            ),
        )
    }

    @Test
    fun `messages mode falls back to tokens when the retained region is too big`() {
        val conversation = conversation(checkpoint(), bigReply(), bigReply(), bigReply(), bigReply(), bigReply())

        // 按条数留 3 条是 612 token，门限只有 300：改用 token 预算再压一轮
        assertEquals(
            ContextTrim(nodeIndex = 5),
            conversation.resolveAutoCompressTrim(
                target = AutoCompressTarget.MESSAGES,
                targetValue = 3,
                keepTailNodes = null,
                force = false,
                limitTokens = 300,
            ),
        )
    }

    @Test
    fun `messages mode counts reserved tokens in the recheck`() {
        val conversation = conversation(checkpoint(), bigReply(), bigReply(), bigReply(), bigReply(), bigReply())

        // 保留区 612 没超门限 2000，但加上 1500 的固定开销就超了
        assertEquals(
            ContextTrim(nodeIndex = 4),
            conversation.resolveAutoCompressTrim(
                target = AutoCompressTarget.MESSAGES,
                targetValue = 3,
                keepTailNodes = null,
                force = false,
                reservedTokens = 1_500,
                limitTokens = 2_000,
            ),
        )
    }
}
