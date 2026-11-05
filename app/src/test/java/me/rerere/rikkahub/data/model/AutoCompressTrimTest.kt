package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * 自动压缩的截断点：只按消息条数保留，切点永远落在两条消息之间。
 */
class AutoCompressTrimTest {
    private fun conversation(vararg nodes: MessageNode) =
        Conversation(assistantId = Uuid.random(), messageNodes = nodes.toList())

    private fun checkpoint() =
        UIMessage.user("summary").copy(isContextCheckpoint = true).toMessageNode()

    // 400 个 ASCII 字符约 100 token，每条消息再加 4 的固定开销
    private fun reply(length: Int = 400) = UIMessage.assistant("a".repeat(length)).toMessageNode()

    @Test
    fun `keeps the configured message count`() {
        val conversation = conversation(checkpoint(), *Array(6) { reply() })

        // 7 个节点，保留 3 条 → 从下标 4 开始保留，前 4 条（含检查点之前的历史）进摘要
        assertEquals(
            ContextTrim(nodeIndex = 4),
            conversation.resolveAutoCompressTrim(keepMessages = 3),
        )
    }

    /**
     * 回归：循环压缩曾经写死「保留最后 1 条」，配置了保留 8 条也只留 1 条，
     * 刚压完的近期上下文被整个总结进摘要。
     */
    @Test
    fun `loop compression keeps the configured count instead of the last message`() {
        val conversation = conversation(checkpoint(), *Array(5) { reply() })

        assertEquals(
            ContextTrim(nodeIndex = 3),
            conversation.resolveAutoCompressTrim(keepMessages = 3),
        )
    }

    /** 回归：末尾那条是正在生成的回复，压进摘要会把这一轮的提问和输出一起丢掉 */
    @Test
    fun `never compresses the reply being generated`() {
        // 检查点之后只剩这一条：没有可压缩范围
        assertNull(conversation(checkpoint(), reply()).resolveAutoCompressTrim(keepMessages = 1))
        assertNull(conversation(checkpoint(), reply()).resolveAutoCompressTrim(keepMessages = 8))

        // 保留 1 条时恰好留下它
        assertEquals(
            ContextTrim(nodeIndex = 2),
            conversation(checkpoint(), reply(), reply()).resolveAutoCompressTrim(keepMessages = 1),
        )
    }

    /**
     * 保留条数配得比检查点之后的消息还多：一条都压不掉时按一半压。
     * 门限已经判定超限，把超限的请求原样发出去只会被拒。
     */
    @Test
    fun `falls back to half of the tail when the keep count covers every message`() {
        val conversation = conversation(checkpoint(), *Array(4) { reply() })

        assertEquals(
            ContextTrim(nodeIndex = 3),
            conversation.resolveAutoCompressTrim(keepMessages = 8),
        )
        // 没有检查点时一样按一半压
        assertEquals(
            ContextTrim(nodeIndex = 2),
            conversation(*Array(4) { reply() }).resolveAutoCompressTrim(keepMessages = 8),
        )
    }

    /** 保留区自己就超过预算时，从旧的一端整条丢；预算再小也保留最新的一条 */
    @Test
    fun `retained budget drops whole messages from the old end`() {
        val conversation = conversation(checkpoint(), *Array(4) { reply(800) })

        // 每条 204 token，保留 3 条是 612；预算 250 只装得下最后一条
        assertEquals(
            ContextTrim(nodeIndex = 4),
            conversation.resolveAutoCompressTrim(keepMessages = 3, retainedBudget = 250),
        )
        // 预算为 1 也至少留下最新的一条
        assertEquals(
            ContextTrim(nodeIndex = 4),
            conversation.resolveAutoCompressTrim(keepMessages = 3, retainedBudget = 1),
        )
        // 预算够时不额外切
        assertEquals(
            ContextTrim(nodeIndex = 2),
            conversation.resolveAutoCompressTrim(keepMessages = 3, retainedBudget = 612),
        )
    }

    @Test
    fun `no compressible range without room after the checkpoint`() {
        assertNull(conversation(checkpoint()).resolveAutoCompressTrim(keepMessages = 8))
        assertNull(conversation().resolveAutoCompressTrim(keepMessages = 8))
    }

    /**
     * 回归：循环压缩至少留下这一轮的提问和正在生成的回复。
     * 只留最后一条时，预算一紧就把用户刚问的问题压进摘要，模型只能对着摘要重开一轮。
     */
    @Test
    fun `loop compression keeps the question and the reply`() {
        val conversation = conversation(checkpoint(), *Array(4) { reply(800) })

        // 预算再小也不丢末尾两条
        assertEquals(
            ContextTrim(nodeIndex = 3),
            conversation.resolveAutoCompressTrim(
                keepMessages = 4,
                minTailNodes = 2,
                retainedBudget = 1,
            ),
        )
        // 检查点之后只剩这两条：没有可压缩范围
        assertNull(
            conversation(checkpoint(), reply(), reply())
                .resolveAutoCompressTrim(keepMessages = 4, minTailNodes = 2),
        )
    }

    /**
     * 截断点的上界（不含）：重新生成较早的消息时，检查点必须落在那个节点之前，
     * 否则它连同新回复一起被排除在请求之外。
     */
    @Test
    fun `trim stops before the regenerated node`() {
        val conversation = conversation(*Array(6) { reply() })

        assertEquals(
            ContextTrim(nodeIndex = 1),
            conversation.resolveAutoCompressTrim(keepMessages = 1, maxEndIndex = 1),
        )
        // 上界就是检查点之后的第一条：没有可压缩范围
        assertNull(
            conversation(checkpoint(), reply(), reply())
                .resolveAutoCompressTrim(keepMessages = 1, maxEndIndex = 1),
        )
    }
}
