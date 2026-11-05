package me.rerere.rikkahub.data.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
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

    /**
     * 回归：以前是 max(真实 usage, 整段历史估算 × 1.1)。真实 usage 本来就已经包含系统提示词、
     * 工具定义和它之前的全部历史，两边各自算整段再取大，等于把上下文放大约一倍，
     * 中文会话在门限六成的时候就反复触发压缩。
     */
    @Test
    fun `real usage is the base and nothing is estimated twice`() {
        val messages = listOf(
            UIMessage.user("hi"),
            assistantWithUsage(promptTokens = 1_000, completionTokens = 100),
        )
        assertEquals(1_100, messages.resolveContextUsage())
    }

    /** usage 之后追加、还没发出去的消息仍然要算：那是 usage 唯一看不到的增长 */
    @Test
    fun `messages appended after the measured reply are estimated on top`() {
        val messages = listOf(
            UIMessage.user("hi"),
            assistantWithUsage(promptTokens = 1_000, completionTokens = 100),
            // 400 个 ASCII 字符 ≈ 100 token，加 4 的每条开销，再乘 1.1
            UIMessage.user("a".repeat(400)),
        )
        assertEquals(1_100 + 114, messages.resolveContextUsage())
    }

    /** 检查点之前的 usage 算的是压缩前的上下文，不能拿来判断 */
    @Test
    fun `a checkpoint hides the usage measured before it`() {
        val checkpoint = UIMessage.user("[Summary]").copy(isContextCheckpoint = true)
        val messages = listOf(
            UIMessage.user("hi"),
            assistantWithUsage(promptTokens = 90_000, completionTokens = 1_000),
            checkpoint,
            UIMessage.user("next"),
        )
        // 只剩 [Summary]（7）和 next（5）：12 × 1.1
        assertEquals(13, messages.resolveContextUsage())
    }

    /**
     * 压缩后尾部那条回复还在续写，它的 usage 是压缩前那次请求的。拿它判断，
     * 下一步就会立刻再压一遍——同一段历史付两次摘要钱。
     */
    @Test
    fun `usage recorded before the checkpoint is ignored even after it`() {
        val compressedAt = LocalDateTime(2025, 1, 1, 12, 0)
        val checkpoint = UIMessage.user("[Summary]")
            .copy(isContextCheckpoint = true, createdAt = compressedAt)
        val continued = assistantWithUsage(promptTokens = 90_000, completionTokens = 1_000).copy(
            createdAt = LocalDateTime(2025, 1, 1, 11, 0),
            finishedAt = LocalDateTime(2025, 1, 1, 11, 0),
        )
        // 退回估算：[Summary]（7）+ reply（6）= 13 × 1.1
        assertEquals(14, listOf(checkpoint, continued).resolveContextUsage())
    }

    /** 有的中转只回 completion、promptTokens 是 0：拿它当底等于门限永不触发 */
    @Test
    fun `usage without prompt tokens falls back to the estimate`() {
        val messages = listOf(
            UIMessage.user("hi"),
            assistantWithUsage(promptTokens = 0, completionTokens = 0),
        )
        // 估算：hi（5）+ reply（6）= 11 × 1.1
        assertEquals(12, messages.resolveContextUsage())
    }

    /**
     * 只回 completion 的中转：completionTokens 可能有几千，能骗过「至少是估算的一半」，
     * 但 prompt 侧根本没测过，拿它当上下文占用就永远判不到门限。
     */
    @Test
    fun `completion-only usage falls back to the estimate`() {
        val messages = listOf(
            UIMessage.user("hi"),
            assistantWithUsage(promptTokens = 0, completionTokens = 5_000),
        )
        assertEquals(12, messages.resolveContextUsage())
    }

    /** 完全没有 usage 时退回估算，这时连系统提示词一起估 */
    @Test
    fun `estimate includes the system prompt when no usage is available`() {
        val messages = listOf(UIMessage.user("hi"))
        assertEquals(115, messages.resolveContextUsage(fixedTokens = 100))
    }

    /**
     * 工具调用和它的输出是同一个 part，输出是请求发出之后原地写回去的。
     * 只看 usage 会完全漏掉这部分增长——循环里刚跑完一堆 shell 的那一步正是压力最大的时候。
     */
    @Test
    fun `tool output executed after the measured request is added on top`() {
        val message = messageWithToolOutput(executedAt = Instant.parse("2026-01-01T00:00:00Z"))
        // 只补输出正文 400 字符 = 100 token，×1.1；调用名和参数那次请求里已经计过
        assertEquals(1_000 + 110, listOf(message).resolveContextUsage())
    }

    /** 请求里已经带着的工具输出不能再算一遍，否则又变成按整段历史高估 */
    @Test
    fun `tool output the measured request already contained is not counted twice`() {
        val message = messageWithToolOutput(executedAt = Instant.parse("2024-01-01T00:00:00Z"))
        assertEquals(1_000, listOf(message).resolveContextUsage())
    }

    private fun messageWithToolOutput(executedAt: Instant) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(
            UIMessagePart.Tool(
                toolCallId = "call-1",
                toolName = "shell",
                input = "{}",
                output = listOf(UIMessagePart.Text("x".repeat(400))),
                startedAt = executedAt,
            ),
        ),
        createdAt = LocalDateTime(2025, 1, 1, 12, 0),
        finishedAt = LocalDateTime(2025, 1, 1, 12, 0),
        lastUsage = TokenUsage(promptTokens = 1_000, completionTokens = 0),
    )

    private fun assistantWithUsage(promptTokens: Int, completionTokens: Int) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text("reply")),
        lastUsage = TokenUsage(promptTokens = promptTokens, completionTokens = completionTokens),
    )
}
