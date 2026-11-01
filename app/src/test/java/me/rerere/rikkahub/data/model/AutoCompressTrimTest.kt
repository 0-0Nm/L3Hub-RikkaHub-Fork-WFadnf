package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class AutoCompressTrimTest {
    private fun text(char: Char, length: Int) = UIMessagePart.Text(char.toString().repeat(length))

    private fun conversation(vararg nodes: MessageNode) =
        Conversation(assistantId = Uuid.random(), messageNodes = nodes.toList())

    @Test
    fun `force compression refuses to swallow the reply being generated`() {
        val conversation = conversation(
            UIMessage.user("summary").copy(isContextCheckpoint = true).toMessageNode(),
            UIMessage.assistant("reply").toMessageNode(),
        )

        // 检查点之后只剩这条正在生成的回复：硬压会把它也总结进摘要，
        // 重试请求里连用户的问题都不剩，模型只能对着摘要重新开一条回复
        assertNull(
            conversation.resolveAutoCompressTrim(
                target = AutoCompressTarget.MESSAGES,
                targetValue = 8,
                keepTailNodes = 1,
                force = true,
            ),
        )
    }

    @Test
    fun `non-force compression refuses a range with no room`() {
        val conversation = conversation(
            UIMessage.user("summary").copy(isContextCheckpoint = true).toMessageNode(),
            UIMessage.assistant("reply").toMessageNode(),
        )

        assertNull(
            conversation.resolveAutoCompressTrim(
                target = AutoCompressTarget.MESSAGES,
                targetValue = 8,
                keepTailNodes = null,
                force = false,
            ),
        )
    }

    @Test
    fun `token target can split the first message after a checkpoint`() {
        val conversation = conversation(
            UIMessage.user("summary").copy(isContextCheckpoint = true).toMessageNode(),
            UIMessage.assistant("").copy(
                parts = listOf(text('b', 400), text('c', 400), text('d', 400)),
            ).toMessageNode(),
        )

        assertEquals(
            ContextTrim(nodeIndex = 1, partIndex = 1),
            conversation.resolveAutoCompressTrim(
                target = AutoCompressTarget.TOKENS,
                targetValue = 250,
                keepTailNodes = null,
                force = false,
            ),
        )
    }
}
