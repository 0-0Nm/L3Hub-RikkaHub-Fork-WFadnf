package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 自动压缩的预算口径：门限判断把摘要和 systemPrompt 算进用量，保留区的预算也必须扣掉它们，
 * 否则保留区一大，压完立刻又超限。
 */
class AutoCompressTest {
    @Test
    fun `budget is half of the current usage`() {
        assertEquals(816, autoCompressBudget(1_632, thresholdTokens = null))
    }

    /**
     * 回归：导入的长历史、换了窗口更小的模型时，占用可能是门限的好几倍。
     * 只按占用的一半压，压完仍然在门限之上，下一轮接着压，每轮都是一次整段摘要请求。
     */
    @Test
    fun `threshold caps the budget when usage is far beyond it`() {
        assertEquals(1_000, autoCompressBudget(8_000, thresholdTokens = 2_000))
    }

    @Test
    fun `threshold does not raise the budget when usage is below it`() {
        assertEquals(816, autoCompressBudget(1_632, thresholdTokens = 2_000))
    }

    @Test
    fun `retained budget subtracts the fixed overhead`() {
        assertEquals(500, autoCompressRetainedBudget(budgetTokens = 1_000, reservedTokens = 500))
    }

    @Test
    fun `retained budget keeps a quarter when the overhead eats it all`() {
        assertEquals(250, autoCompressRetainedBudget(budgetTokens = 1_000, reservedTokens = 5_000))
    }

    /** 回归：按 token 数保留的算法已经移除，旧配置不能把 32_000 当成消息条数 */
    @Test
    fun `removed token mode falls back to the default keep count`() {
        val legacy = Assistant(
            autoCompressTarget = AutoCompressTarget.TOKENS,
            autoCompressTargetValue = 32_000,
        )
        assertEquals(AUTO_COMPRESS_DEFAULT_KEEP_MESSAGES, legacy.autoCompressKeepMessages)

        val messages = legacy.copy(
            autoCompressTarget = AutoCompressTarget.MESSAGES,
            autoCompressTargetValue = 6,
        )
        assertEquals(6, messages.autoCompressKeepMessages)
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
