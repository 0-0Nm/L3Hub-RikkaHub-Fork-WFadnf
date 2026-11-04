package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ServerToolStatus
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 压缩输入的分段：摘要请求必须装进模型窗口，否则整段历史原样再发一次只会被同样拒绝。
 */
class CompressionChunkTest {
    // 4 个 ASCII 字符约 1 token
    private fun text(tokens: Int) = UIMessagePart.Text("a".repeat(tokens * 4))

    private fun message(vararg parts: UIMessagePart) =
        UIMessage(role = MessageRole.ASSISTANT, parts = parts.toList())

    @Test
    fun `keeps short history in one chunk`() {
        val messages = listOf(message(text(100)), message(text(100)))

        assertEquals(1, chunkMessagesForCompression(messages, 1_000).size)
    }

    @Test
    fun `splits history at message boundaries`() {
        // 每条 400 正文 + 4 固定开销，预算 500 只装得下一条
        val messages = listOf(message(text(400)), message(text(400)), message(text(400)))

        val chunks = chunkMessagesForCompression(messages, 500)

        assertEquals(3, chunks.size)
        assertTrue(chunks.all { it.size == 1 })
    }

    @Test
    fun `splits one oversized message at part boundaries`() {
        val big = message(text(400), text(400), text(400))

        val chunks = chunkMessagesForCompression(listOf(big), 500)

        assertTrue(chunks.size > 1)
        // 原文一个字都不少，只是分到了不同段
        assertEquals(big.parts, chunks.flatMap { it.single().parts })
    }

    @Test
    fun `truncates a single part that cannot fit`() {
        val huge = message(text(10_000))

        val chunks = chunkMessagesForCompression(listOf(huge), 500)

        assertEquals(1, chunks.size)
        val trimmed = chunks.single().single().parts.single() as UIMessagePart.Text
        assertTrue(trimmed.text.endsWith("[truncated]"))
        assertTrue(estimateTokens(trimmed.text) < 1_000)
    }

    @Test
    fun `truncate never splits a surrogate pair`() {
        val truncated = "😀".repeat(100).truncateToTokens(20)

        assertTrue(truncated.endsWith("[truncated]"))
        val kept = truncated.removeSuffix("[truncated]")
        assertEquals(kept.count { it.isHighSurrogate() }, kept.count { it.isLowSurrogate() })
    }

    /** 回归：搜索/抓取的服务端工具结果经常比整段预算还大，不能原样发出去 */
    @Test
    fun `replaces an oversized server tool with text`() {
        val big = UIMessagePart.ServerTool(
            toolCallId = "call",
            toolName = "web_search",
            output = JsonPrimitive("a".repeat(40_000)),
            status = ServerToolStatus.COMPLETED,
        )

        val chunks = chunkMessagesForCompression(listOf(message(big)), 500)

        assertEquals(1, chunks.size)
        val part = chunks.single().single().parts.single()
        assertTrue(part is UIMessagePart.Text)
        assertTrue(estimateTokens((part as UIMessagePart.Text).text) <= 600)
    }

    @Test
    fun `blank text is returned unchanged`() {
        assertEquals("", "".truncateToTokens(10))
        assertEquals("hello", "hello".truncateToTokens(10))
    }
}
