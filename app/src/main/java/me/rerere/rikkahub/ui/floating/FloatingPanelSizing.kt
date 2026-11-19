package me.rerere.rikkahub.ui.floating

import kotlin.math.roundToInt

/**
 * 悬浮窗面板的尺寸范围。
 *
 * 设置页的滑块和面板上的手柄改的不完全是一回事：滑块让面板以原中轴线为中心放大缩小，
 * 手柄只动它按住的那条边。但两边的上下限都收在这里，各写一份会越调越对不上。
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

    /**
     * 拖面板手柄之后的宽度比例。
     *
     * 手柄在面板末端，拖它只该动这一条边：手指横向走了多少，宽度就变多少。
     * 以前这里把位移乘 2、窗口再按 TOP_CENTER 重新居中，看起来就是面板从中轴线
     * 往两边一起长，手指按住的那条边根本跟不上。
     */
    fun draggedPanelWidthRatio(current: Float, deltaDp: Float, extentDp: Float): Float =
        draggedRatio(current, deltaDp, extentDp, MIN_WIDTH_RATIO, MAX_WIDTH_RATIO)

    /** 垂直拖动改预览区高度：直接按 dp 加减，比套一个比例更容易对上拖了多少 */
    fun draggedPreviewHeight(currentDp: Float, deltaDp: Float): Float {
        val min = MIN_PREVIEW_HEIGHT_DP.toFloat()
        val max = MAX_PREVIEW_HEIGHT_DP.toFloat()
        if (!deltaDp.isFinite()) return currentDp.coerceIn(min, max)
        return (currentDp + deltaDp).coerceIn(min, max)
    }

    /**
     * 设置页滑块改宽度：面板以原来的中轴线为中心收缩 / 展开。
     *
     * [currentStartOffsetPx] 是起点边相对可用区起点的距离（LTR 下就是左边缘到屏幕左边的距离），
     * 返回新宽度下该保持的值。宽度变了 Δ 时起点边往中间挪 Δ/2，中轴线才不动。
     */
    fun centeredStartOffset(currentStartOffsetPx: Float, oldWidthPx: Float, newWidthPx: Float): Float =
        currentStartOffsetPx + (oldWidthPx - newWidthPx) / 2f

    /**
     * 窗口宽度（px）。Compose 侧的面板宽度是 `screenWidthDp * ratio` 这么多 dp，
     * 两边必须用同一套换算：窗口窄一个像素会裁掉面板的边，宽一个像素就又成了吃触摸的透明条。
     */
    fun windowWidthPx(screenWidthPx: Float, ratio: Float): Int =
        (screenWidthPx * clampWidthRatio(ratio)).roundToInt().coerceAtLeast(1)

    fun percentLabel(ratio: Float): String = "${(ratio * 100).roundToInt()}%"
}
