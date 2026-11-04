package me.rerere.rikkahub.data.model

import me.rerere.ai.provider.Model
import me.rerere.ai.registry.ModelRegistry

/**
 * 上下文预警门限的预设。
 *
 * 大窗口模型统一用一组值：WARN 384K、MAX 512K，不再按型号分档。
 * 只在模型还没有手动设置过门限时生效（见 [withContextPresets]）。
 */
object ContextThresholdPresets {
    data class Preset(val warn: Int, val max: Int)

    val default = Preset(warn = 393_216, max = 524_288)

    val fallback = default

    @Suppress("UNUSED_PARAMETER")
    fun resolve(modelId: String): Preset = default

    /**
     * 按模型窗口收窄后的预设。
     *
     * 预设本身是给 1M 窗口的模型定的；模型窗口更小时必须收窄，否则 128k / 200k
     * 的模型会拿到 384k / 512k 的门限，MAX 拦截和自动压缩都永远不会触发。
     * 设置页的「使用预设」也走这里，避免和自动填充的口径不一致。
     */
    fun forModel(model: Model): Preset {
        val preset = resolve(model.modelId)
        val window = model.contextLength.takeIf { it > 0 } ?: return preset
        val max = minOf(preset.max, window * MAX_WINDOW_PERCENT / 100).coerceAtLeast(1)
        return Preset(
            warn = minOf(preset.warn, window * WARN_WINDOW_PERCENT / 100).coerceIn(1, max),
            max = max,
        )
    }
}

/** WARN 占模型窗口的百分比：留四分之一给输出和下一轮输入 */
private const val WARN_WINDOW_PERCENT = 75

/** MAX 占模型窗口的百分比：门限贴着硬窗口时，输入刚好占满、回复一个 token 都放不下 */
private const val MAX_WINDOW_PERCENT = 90

/**
 * 自动模式下（[Model.contextThresholdsAuto]）给还没设置过门限的模型填预设。
 * 只要用户填过其中一个，就完全尊重用户的值。
 *
 * 预设不能超过模型的 [Model.contextLength]：超过真实窗口的门限等于没有门限，
 * `isContextLimitReached` 永远为 false，自动压缩也只能等 provider 先拒绝。
 *
 * 老版本给所有模型都填的是同一组默认值，存量设置里这组值也会出现在 [contextThresholdsAuto]
 * 为 true 的模型上。它不是用户设置，必须按新窗口重算，否则升级后小窗口模型仍旧超窗。
 * 手动改过门限时设置页会把 [contextThresholdsAuto] 关掉，两者不会混淆。
 */
fun Model.withContextPresets(): Model {
    if (!contextThresholdsAuto) return this
    val legacyPreset = warnContextTokens == ContextThresholdPresets.default.warn &&
        maxContextTokens == ContextThresholdPresets.default.max
    if (!legacyPreset && (warnContextTokens != null || maxContextTokens != null)) return this
    // 从 provider 拉到的模型只填了 modelId/displayName，contextLength 会停在默认的 1M，
    // 按它算出来的预设等于没有门限。界面不维护这个值，所以把默认值当成「没设置」。
    val model = withResolvedContextLength()
    val preset = ContextThresholdPresets.forModel(model)
    if (model === this && warnContextTokens == preset.warn && maxContextTokens == preset.max) return this
    return model.copy(warnContextTokens = preset.warn, maxContextTokens = preset.max)
}

private val DEFAULT_CONTEXT_LENGTH = Model().contextLength

/**
 * 目录里有真实窗口时补上。只用于自动预填和设置页的「使用预设」，
 * 用户手动设置过门限的模型不会走到这里。
 */
internal fun Model.withResolvedContextLength(): Model {
    if (contextLength > 0 && contextLength != DEFAULT_CONTEXT_LENGTH) return this
    val resolved = ModelRegistry.MODEL_CONTEXT_LENGTH.getData(modelId) ?: return this
    if (resolved == contextLength) return this
    return copy(contextLength = resolved)
}
