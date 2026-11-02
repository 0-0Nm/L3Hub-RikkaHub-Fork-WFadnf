package me.rerere.rikkahub.ui.floating

import kotlin.math.roundToInt

/**
 * 悬浮窗面板的尺寸范围。
 *
 * 设置页的滑块和面板上的手柄改的不完全是一回事：滑块调比例，手柄左右调比例、上下直接加减 dp。
 * 但两边的上下限都收在这里，各写一份会越调越对不上。
 */
internal object FloatingPanelSizing {
    const val MIN_WIDTH_RATIO = 0.45f
    const val MAX_WIDTH_RATIO = 1f

    // 0.4 是下限：再小的话标题栏加回复栏就把面板占满了，预览区一点位置都没有
    const val MIN_HEIGHT_RATIO = 0.4f
    const val MAX_HEIGHT_RATIO = 1f

    const val MIN_PREVIEW_HEIGHT_DP = 80
    const val MAX_PREVIEW_HEIGHT_DP = 400

    fun clampWidthRatio(value: Float): Float =
        if (value.isNaN()) MIN_WIDTH_RATIO else value.coerceIn(MIN_WIDTH_RATIO, MAX_WIDTH_RATIO)

    fun clampHeightRatio(value: Float): Float =
        if (value.isNaN()) MIN_HEIGHT_RATIO else value.coerceIn(MIN_HEIGHT_RATIO, MAX_HEIGHT_RATIO)

    fun clampPreviewHeight(dp: Int): Int = dp.coerceIn(MIN_PREVIEW_HEIGHT_DP, MAX_PREVIEW_HEIGHT_DP)

    /**
     * 拖动 [deltaDp] 之后的比例，[extentDp] 是这条边能用的长度。
     * 长度不是有限正数时保持原值：除以 0 得到的 NaN 会一路传进窗口尺寸。
     */
    fun draggedRatio(current: Float, deltaDp: Float, extentDp: Float, min: Float, max: Float): Float {
        if (!current.isFinite()) return min
        if (!extentDp.isFinite() || extentDp <= 0f || !deltaDp.isFinite()) return current.coerceIn(min, max)
        return (current + deltaDp / extentDp).coerceIn(min, max)
    }

    /** 垂直拖动改预览区高度：直接按 dp 加减，比套一个比例更容易对上拖了多少 */
    fun draggedPreviewHeight(currentDp: Float, deltaDp: Float): Float {
        val min = MIN_PREVIEW_HEIGHT_DP.toFloat()
        val max = MAX_PREVIEW_HEIGHT_DP.toFloat()
        if (!deltaDp.isFinite()) return currentDp.coerceIn(min, max)
        return (currentDp + deltaDp).coerceIn(min, max)
    }

    fun percentLabel(ratio: Float): String = "${(ratio * 100).roundToInt()}%"
}
