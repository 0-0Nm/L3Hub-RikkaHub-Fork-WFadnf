package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.limitContext
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * 检查点对请求上下文的影响。
 *
 * 新写入的检查点只落在消息边界上；这里守的是旧数据——按 token 数保留的那套算法会把检查点
 * 切在一条消息中间，这些会话升级之后仍然要能正确裁剪请求、原文不能丢。
 */
class ContextTrimTest {
    private fun text(char: Char, length: Int) = UIMessagePart.Text(char.toString().repeat(length))

    private fun message(vararg parts: UIMessagePart) =
        UIMessage(role = MessageRole.ASSISTANT, parts = parts.toList())

    private fun legacyCheckpoint(split: UIMessage, keepPartsFrom: Int) =
        UIMessage.user("summary").copy(
            isContextCheckpoint = true,
            contextStartMessageId = split.id,
            contextStartPartIndex = keepPartsFrom,
        ).toMessageNode()

    @Test
    fun `reasoning parts count toward context tokens`() {
        val reasoning = UIMessagePart.Reasoning(reasoning = "思".repeat(400))
        val tokens = estimateMessageTokens(message(reasoning))

        assertEquals(400 + 4, tokens)
    }

    @Test
    fun `legacy checkpoint split keeps every stored part while trimming the request`() {
        val parts = listOf(text('a', 8), text('b', 8), text('c', 8))
        val alternate = text('x', 8)
        val splitNode = MessageNode(
            messages = listOf(message(*parts.toTypedArray()), message(alternate)),
            selectIndex = 0,
        )
        val nodes = listOf(
            UIMessage.user("hello").toMessageNode(),
            legacyCheckpoint(splitNode.currentMessage, keepPartsFrom = 1),
            splitNode,
        )
        val conversation = Conversation(assistantId = Uuid.random(), messageNodes = nodes)

        // 原文（含其它分支）一个字都不少
        assertEquals(parts, conversation.messageNodes[2].currentMessage.parts)
        assertEquals(parts, conversation.messageNodes[2].messages[0].parts)
        assertEquals(alternate, conversation.messageNodes[2].messages[1].parts.single())
        // 只有发给模型的内容从截断点开始
        assertEquals(parts.drop(1), conversation.currentMessages.limitContext(0)[1].parts)
    }

    @Test
    fun `legacy checkpoint keeps the whole message when the part index is out of range`() {
        val whole = text('a', 8)
        val split = message(whole)
        val nodes = listOf(
            UIMessage.user("hello").toMessageNode(),
            legacyCheckpoint(split, keepPartsFrom = 1),
            split.toMessageNode(),
        )
        val conversation = Conversation(assistantId = Uuid.random(), messageNodes = nodes)

        // 下标越界时整条发出去，不能凭空丢掉没进摘要的内容
        assertEquals(listOf(whole), conversation.currentMessages.limitContext(0)[1].parts)
    }

    @Test
    fun `checkpoint keeps the history before it out of the request`() {
        val nodes = listOf(
            UIMessage.user("summarized").toMessageNode(),
            UIMessage.user("summary").copy(isContextCheckpoint = true).toMessageNode(),
            UIMessage.user("kept").toMessageNode(),
        )
        val conversation = Conversation(assistantId = Uuid.random(), messageNodes = nodes)

        assertEquals(
            listOf(conversation.messageNodes[1].currentMessage, conversation.messageNodes[2].currentMessage),
            conversation.currentMessages.limitContext(0),
        )
    }
}
