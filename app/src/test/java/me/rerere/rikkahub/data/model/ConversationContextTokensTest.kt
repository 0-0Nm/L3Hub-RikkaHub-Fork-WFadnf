package me.rerere.rikkahub.data.model

import kotlinx.datetime.LocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationContextTokensTest {
    private fun conversation(vararg messages: UIMessage) = Conversation(
        assistantId = Uuid.random(),
        messageNodes = messages.map { it.toMessageNode() },
    )

    private fun at(minute: Int) = LocalDateTime(2026, 1, 1, 0, minute)

    private fun assistantMessage(
        minute: Int,
        usage: TokenUsage? = null,
        lastUsage: TokenUsage? = null,
    ) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = emptyList(),
        createdAt = at(minute),
        usage = usage,
        lastUsage = lastUsage,
    )

    @Test
    fun `uses the last request of the last reply instead of accumulated usage`() {
        val conversation = conversation(
            UIMessage.user("hi"),
            assistantMessage(
                1,
                usage = TokenUsage(promptTokens = 15_000, completionTokens = 140),
                lastUsage = TokenUsage(promptTokens = 7_500, completionTokens = 100),
            ),
        )

        assertEquals(7_600, conversation.lastContextTokens())
    }

    @Test
    fun `falls back to usage for messages without last usage`() {
        val conversation = conversation(
            assistantMessage(1, usage = TokenUsage(promptTokens = 7_000, completionTokens = 100)),
        )

        assertEquals(7_100, conversation.lastContextTokens())
    }

    @Test
    fun `returns null when compressed after the last reply`() {
        val conversation = conversation(
            assistantMessage(1, lastUsage = TokenUsage(promptTokens = 7_500, completionTokens = 100)),
            UIMessage.user("summary").copy(createdAt = at(2), isContextCheckpoint = true),
        )

        assertNull(conversation.lastContextTokens())
    }

    @Test
    fun `usage of a reply kept from before the checkpoint is ignored`() {
        // 压缩把检查点插在保留的尾部消息之前：这些回复比检查点旧，usage 覆盖的是压缩前的上下文
        val conversation = conversation(
            UIMessage.user("old").copy(createdAt = at(0)),
            UIMessage.user("summary").copy(createdAt = at(3), isContextCheckpoint = true),
            assistantMessage(1, lastUsage = TokenUsage(promptTokens = 7_500, completionTokens = 100)),
            UIMessage.user("hi").copy(createdAt = at(4)),
        )

        assertNull(conversation.lastContextTokens())
    }

    @Test
    fun `reply continued after the checkpoint is used even if it started earlier`() {
        // 续写复用同一条助手消息，但每一步都会刷新 finishedAt
        val continued = assistantMessage(
            1,
            lastUsage = TokenUsage(promptTokens = 2_500, completionTokens = 100),
        ).copy(finishedAt = at(3))
        val conversation = conversation(
            UIMessage.user("summary").copy(createdAt = at(2), isContextCheckpoint = true),
            continued,
        )

        assertEquals(2_600, conversation.lastContextTokens())
    }

    @Test
    fun `reply after the checkpoint is used`() {
        val conversation = conversation(
            UIMessage.user("summary").copy(createdAt = at(1), isContextCheckpoint = true),
            UIMessage.user("hi").copy(createdAt = at(2)),
            assistantMessage(3, lastUsage = TokenUsage(promptTokens = 1_900, completionTokens = 100)),
        )

        assertEquals(2_000, conversation.lastContextTokens())
    }

    @Test
    fun `estimate includes reasoning and image parts`() {
        val conversation = conversation(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Reasoning(reasoning = "思".repeat(400)),
                    UIMessagePart.Image(url = "file:///tmp/image.png"),
                ),
            ),
        )

        assertEquals(404 + 768, conversation.estimateContextTokens())
    }

    /** 旧版本按 token 数保留时会把检查点切在一条消息中间，这些会话仍要按 part 下标裁剪请求 */
    @Test
    fun `estimate excludes the summarized prefix of a legacy split message`() {
        val split = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text("a".repeat(400)),
                UIMessagePart.Text("b".repeat(400)),
            ),
        )
        val source = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(
                UIMessage.user("hello").toMessageNode(),
                UIMessage.user("摘要").copy(
                    isContextCheckpoint = true,
                    contextStartMessageId = split.id,
                    contextStartPartIndex = 1,
                ).toMessageNode(),
                split.toMessageNode(),
            ),
        )

        // 检查点 4 + 2，切剩的后半段 4 + 100
        assertEquals(4 + 2 + 4 + 100, source.estimateContextTokens())
    }

    @Test
    fun `returns null without any reply`() {
        assertNull(conversation(UIMessage.user("hi")).lastContextTokens())
    }
}
