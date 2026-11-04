package me.rerere.rikkahub.data.ai

import me.rerere.ai.provider.Model
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 压缩请求自己的预算：文本装不下时会出现 prompt + max_tokens 一起超过窗口，
 * 摘要请求被 provider 拒绝，自动压缩在真正需要它的时候失效。
 */
class CompressionBudgetTest {
    private fun model(contextLength: Int) = Model(
        modelId = "test-model",
        displayName = "Test",
        contextLength = contextLength,
    )

    @Test
    fun `input budget leaves room for the output and fixed prompt`() {
        assertEquals(128_000 - 8_192 - 12_288, model(128_000).compressionInputBudget())
    }

    @Test
    fun `input budget never shrinks below the chunk floor`() {
        assertEquals(2_048, model(8_000).compressionInputBudget())
    }

    @Test
    fun `max tokens use the output cap when there is room`() {
        assertEquals(8_192, model(200_000).compressionMaxTokens(100_000))
    }

    @Test
    fun `max tokens shrink when the input nearly fills the window`() {
        assertEquals(200_000 - 180_000 - 12_288, model(200_000).compressionMaxTokens(180_000))
    }

    @Test
    fun `max tokens never drop below the floor`() {
        assertEquals(1_024, model(8_000).compressionMaxTokens(20_000))
    }
}
