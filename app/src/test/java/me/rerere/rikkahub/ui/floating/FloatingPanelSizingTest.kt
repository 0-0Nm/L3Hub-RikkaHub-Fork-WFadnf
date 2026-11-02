package me.rerere.rikkahub.ui.floating

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingPanelSizingTest {
    @Test
    fun `width ratio is clamped to the allowed range`() {
        assertEquals(FloatingPanelSizing.MIN_WIDTH_RATIO, FloatingPanelSizing.clampWidthRatio(0.2f), 0.0001f)
        assertEquals(FloatingPanelSizing.MAX_WIDTH_RATIO, FloatingPanelSizing.clampWidthRatio(1.8f), 0.0001f)
        assertEquals(0.7f, FloatingPanelSizing.clampWidthRatio(0.7f), 0.0001f)
    }

    @Test
    fun `height ratio is clamped to the allowed range`() {
        assertEquals(FloatingPanelSizing.MIN_HEIGHT_RATIO, FloatingPanelSizing.clampHeightRatio(0.1f), 0.0001f)
        assertEquals(FloatingPanelSizing.MAX_HEIGHT_RATIO, FloatingPanelSizing.clampHeightRatio(2f), 0.0001f)
        assertEquals(0.5f, FloatingPanelSizing.clampHeightRatio(0.5f), 0.0001f)
    }

    @Test
    fun `preview height is clamped to the allowed range`() {
        assertEquals(
            FloatingPanelSizing.MIN_PREVIEW_HEIGHT_DP,
            FloatingPanelSizing.clampPreviewHeight(10),
        )
        assertEquals(
            FloatingPanelSizing.MAX_PREVIEW_HEIGHT_DP,
            FloatingPanelSizing.clampPreviewHeight(10_000),
        )
        assertEquals(200, FloatingPanelSizing.clampPreviewHeight(200))
    }

    @Test
    fun `dragging moves the ratio by the dragged fraction of the edge`() {
        val moved = FloatingPanelSizing.draggedRatio(
            current = 0.8f,
            deltaDp = 40f,
            extentDp = 400f,
            min = FloatingPanelSizing.MIN_WIDTH_RATIO,
            max = FloatingPanelSizing.MAX_WIDTH_RATIO,
        )
        assertEquals(0.9f, moved, 0.0001f)
    }

    @Test
    fun `dragging past the limit stops at the limit`() {
        val wider = FloatingPanelSizing.draggedRatio(0.95f, 200f, 400f, 0.45f, 1f)
        assertEquals(1f, wider, 0.0001f)

        val narrower = FloatingPanelSizing.draggedRatio(0.5f, -200f, 400f, 0.45f, 1f)
        assertEquals(0.45f, narrower, 0.0001f)
    }

    @Test
    fun `zero extent keeps the ratio instead of producing NaN`() {
        // 屏幕尺寸还没量出来时拖动不能让比例变成 NaN，否则窗口尺寸会被撑坏
        val result = FloatingPanelSizing.draggedRatio(0.7f, 30f, 0f, 0.45f, 1f)
        assertEquals(0.7f, result, 0.0001f)
        assertEquals(false, result.isNaN())
    }

    @Test
    fun `dragging the preview area adds the dragged distance`() {
        assertEquals(250f, FloatingPanelSizing.draggedPreviewHeight(200f, 50f), 0.0001f)
        assertEquals(150f, FloatingPanelSizing.draggedPreviewHeight(200f, -50f), 0.0001f)
    }

    @Test
    fun `preview height drag stops at both limits`() {
        assertEquals(
            FloatingPanelSizing.MAX_PREVIEW_HEIGHT_DP.toFloat(),
            FloatingPanelSizing.draggedPreviewHeight(380f, 200f),
            0.0001f,
        )
        assertEquals(
            FloatingPanelSizing.MIN_PREVIEW_HEIGHT_DP.toFloat(),
            FloatingPanelSizing.draggedPreviewHeight(100f, -200f),
            0.0001f,
        )
    }

    @Test
    fun `percent label rounds to whole percent`() {
        assertEquals("70%", FloatingPanelSizing.percentLabel(0.7f))
        assertEquals("100%", FloatingPanelSizing.percentLabel(1f))
        assertEquals("45%", FloatingPanelSizing.percentLabel(0.4512f))
    }
}
