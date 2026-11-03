package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

        // 预算 = 门限的一半 = 1000；保留区 612 本身没超，加上 1500 的固定开销就超了，
        // 回退到 token 模式后固定开销已经吃满预算，只剩 25% 的下限可用
        assertEquals(
            ContextTrim(nodeIndex = 5),
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

    /** 回归：压缩目标贴着实测占用时，下一轮加一条消息就又过线，每发一条压一次 */
    @Test
    fun `messages mode compresses to half of the current usage`() {
        val conversation = conversation(checkpoint(), *Array(8) { bigReply() })

        // 当前占用 8×204 = 1632；按条数保留 8 条等于一条都不压，
        // 回退到 token 预算（用量的 50%）后至少要切掉一半
        assertEquals(
            ContextTrim(nodeIndex = 5),
            conversation.resolveAutoCompressTrim(
                target = AutoCompressTarget.MESSAGES,
                targetValue = 8,
                keepTailNodes = null,
                force = false,
                limitTokens = 1_632,
            ),
        )
    }

    /**
     * 回归：导入的长历史、换了窗口更小的模型时，占用可能是门限的好几倍。
     * 只按占用的一半压，压完仍然在门限之上，下一轮接着压，每轮都是一次整段摘要请求。
     */
    @Test
    fun `threshold caps the budget when usage is far beyond it`() {
        // checkpoint + 8 条 204 token 的回复
        val conversation = conversation(checkpoint(), *Array(8) { bigReply() })

        // 占用 1632（还没到门限）：预算取 min(816, 1000) = 816，封顶不生效
        assertEquals(
            ContextTrim(nodeIndex = 5),
            conversation.resolveAutoCompressTrim(
                target = AutoCompressTarget.TOKENS,
                targetValue = 1_000,
                keepTailNodes = null,
                force = false,
                limitTokens = 1_632,
                thresholdTokens = 2_000,
            ),
        )
        // 占用 8000、目标 8000：占用的一半（4000）才说了算，检查点之后的 1632 全都装得下，
        // 于是「没有可压缩范围」——门限判定说超限，实际一条都没压掉。
        // 目标填 1000 时预算还没被占用一半撑开，切到 (4, 0)，测不到这条路径
        assertNull(
            conversation.resolveAutoCompressTrim(
                target = AutoCompressTarget.TOKENS,
                targetValue = 8_000,
                keepTailNodes = null,
                force = false,
                limitTokens = 8_000,
            ),
        )
        // 封到门限的一半（1000）之后，一次切到第 4 条消息的正文处
        assertEquals(
            ContextTrim(nodeIndex = 4, partIndex = 0),
            conversation.resolveAutoCompressTrim(
                target = AutoCompressTarget.TOKENS,
                targetValue = 1_000,
                keepTailNodes = null,
                force = false,
                limitTokens = 8_000,
                thresholdTokens = 2_000,
            ),
        )
    }

    /** 回归：保留量填得比门限还大时，压缩曾经退化成「没有可压缩范围」，而 MAX 拦截已被放行 */
    @Test
    fun `token target larger than the current usage is clamped to the budget`() {
        val conversation = conversation(checkpoint(), reply(4_000), reply(4_000), reply(4_000), reply(4_000))

        // 设置页只校验下限（≥100），没有上限；夹到预算以内才压得动
        assertEquals(
            ContextTrim(nodeIndex = 3),
            conversation.resolveAutoCompressTrim(
                target = AutoCompressTarget.TOKENS,
                targetValue = 16_000,
                keepTailNodes = null,
                force = false,
                limitTokens = 8_000,
            ),
        )
    }

    /** 门限判断要同时看得见「真实 usage」和「刚追加的消息」，取两者的大值 */
    @Test
    fun `usage takes the larger of real usage and the visible estimate`() {
        // 刚粘贴一大段内容：usage 还没更新，估算更悲观
        assertEquals(
            2_200,
            resolveContextUsage(realTokens = 1_000, visibleTokens = 2_000, fixedTokens = 0),
        )
        // 工具定义只有 usage 算得到：估算偏小，用 usage
        assertEquals(
            5_000,
            resolveContextUsage(realTokens = 5_000, visibleTokens = 1_000, fixedTokens = 0),
        )
        // 压缩完 usage 还没落地，只剩估算
        assertEquals(
            1_100,
            resolveContextUsage(realTokens = null, visibleTokens = 1_000, fixedTokens = 0),
        )
    }
}
