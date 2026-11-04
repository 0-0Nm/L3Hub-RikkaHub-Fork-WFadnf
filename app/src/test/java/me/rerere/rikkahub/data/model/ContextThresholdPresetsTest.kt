package me.rerere.rikkahub.data.model

import me.rerere.ai.provider.Model
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextThresholdPresetsTest {
    private fun preset(id: String) = ContextThresholdPresets.resolve(id)

    @Test
    fun `every model shares the same preset`() {
        listOf("kimi-k3", "deepseek-v4.1", "deepseek-v4", "glm-5-flash", "gpt-5.2", "claude-opus-5")
            .forEach { id ->
                assertEquals(393_216 to 524_288, preset(id).pair())
            }
    }

    @Test
    fun `falls back to the same preset for unknown models`() {
        assertEquals(393_216 to 524_288, preset("llama-3-70b").pair())
        assertEquals(393_216 to 524_288, preset("deepseek-chat").pair())
    }

    @Test
    fun `keeps user thresholds untouched`() {
        val model = Model(
            modelId = "custom-model",
            displayName = "Custom",
            warnContextTokens = 1_000,
            maxContextTokens = 2_000,
        ).withContextPresets()
        assertEquals(1_000, model.warnContextTokens)
        assertEquals(2_000, model.maxContextTokens)
    }

    @Test
    fun `fills preset when auto`() {
        val model = Model(
            modelId = "unknown-model",
            displayName = "Unknown",
        ).withContextPresets()
        assertEquals(393_216, model.warnContextTokens)
        assertEquals(524_288, model.maxContextTokens)
    }

    /** 回归：预设比模型窗口还大时门限等于没有，MAX 拦截和自动压缩都不会触发 */
    @Test
    fun `preset leaves headroom inside the model window`() {
        val model = Model(
            modelId = "small-model",
            displayName = "Small",
            contextLength = 128_000,
        ).withContextPresets()
        assertEquals(96_000, model.warnContextTokens)
        assertEquals(115_200, model.maxContextTokens)
    }

    @Test
    fun `preset keeps the family values when the window is large`() {
        val model = Model(
            modelId = "big-model",
            displayName = "Big",
            contextLength = 1_000_000,
        ).withContextPresets()
        assertEquals(393_216, model.warnContextTokens)
        assertEquals(524_288, model.maxContextTokens)
    }

    /** 回归：老版本把 384K/512K 落进了存量设置，升级后小窗口模型必须按窗口重算 */
    @Test
    fun `migrates the legacy preset pair to the model window`() {
        val model = Model(
            modelId = "small-model",
            displayName = "Small",
            contextLength = 128_000,
            warnContextTokens = 393_216,
            maxContextTokens = 524_288,
        ).withContextPresets()
        assertEquals(96_000, model.warnContextTokens)
        assertEquals(115_200, model.maxContextTokens)
    }

    @Test
    fun `keeps a large window on the legacy pair without rewriting it`() {
        val model = Model(
            modelId = "big-model",
            displayName = "Big",
            contextLength = 1_000_000,
            warnContextTokens = 393_216,
            maxContextTokens = 524_288,
        )
        assertEquals(model, model.withContextPresets())
    }

    private fun ContextThresholdPresets.Preset.pair() = warn to max
}
