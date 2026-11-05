package me.rerere.rikkahub.data.model

import me.rerere.ai.provider.Model
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoCompressLimitTest {
    @Test
    fun `warn falls back to the max threshold when it is missing`() {
        assertEquals(
            64_000,
            Model(warnContextTokens = null, maxContextTokens = 64_000)
                .autoCompressLimit(AutoCompressThreshold.WARN),
        )
    }

    /**
     * 回归：两个门限都没配时曾经退回 [Model.contextLength] 原值，而它的默认值是 1M。
     * 真实窗口 128k / 200k 又没被识别出来的模型会拿到一个永远到不了的门限，自动压缩失效。
     */
    @Test
    fun `missing thresholds fall back to a fraction of the window`() {
        assertEquals(
            75_000,
            Model(warnContextTokens = null, maxContextTokens = null, contextLength = 100_000)
                .autoCompressLimit(AutoCompressThreshold.WARN),
        )
        assertEquals(
            90_000,
            Model(warnContextTokens = null, maxContextTokens = null, contextLength = 100_000)
                .autoCompressLimit(AutoCompressThreshold.MAX),
        )
    }

    @Test
    fun `thresholds never go above the window`() {
        assertEquals(
            48_000,
            Model(warnContextTokens = 100_000, contextLength = 64_000)
                .autoCompressLimit(AutoCompressThreshold.WARN),
        )
        assertEquals(
            90_000,
            Model(maxContextTokens = 1_000_000, contextLength = 100_000)
                .autoCompressLimit(AutoCompressThreshold.MAX),
        )
    }

    /**
     * 回归：两档都按 90% 封顶时，1M 窗口写下的旧预设夹到 128k 模型上会变成同一个数，
     * WARN 和 MAX 没有区别，预警那一档等于不存在。
     */
    @Test
    fun `auto thresholds stay ordered after clamping`() {
        val model = Model(
            warnContextTokens = 393_216,
            maxContextTokens = 524_288,
            contextLength = 128_000,
        )
        assertEquals(96_000, model.autoCompressLimit(AutoCompressThreshold.WARN))
        assertEquals(115_200, model.autoCompressLimit(AutoCompressThreshold.MAX))
    }

    /** 手填的门限是用户自己的选择：不按窗口封顶，否则旧的小 contextLength 会把每轮都压 */
    @Test
    fun `manually set thresholds are not clamped to the window`() {
        val model = Model(
            warnContextTokens = 100_000,
            contextLength = 64_000,
            contextThresholdsAuto = false,
        )
        assertEquals(100_000, model.autoCompressLimit(AutoCompressThreshold.WARN))
    }

    @Test
    fun `max keeps its own threshold`() {
        assertEquals(
            128_000,
            Model(warnContextTokens = 96_000, maxContextTokens = 128_000)
                .autoCompressLimit(AutoCompressThreshold.MAX),
        )
    }
}
