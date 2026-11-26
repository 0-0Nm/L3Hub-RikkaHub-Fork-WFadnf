package me.rerere.rikkahub.ui.floating

/**
 * Position of the child-task numeric badge relative to the floating ball centre.
 *
 * The invisible guide is centred on the ball and is 3.0 ball widths by 2.4 ball heights.
 * Zero is the existing badge centre (the left/right ball midpoint), not the guide centre.
 * Horizontal interpolation is therefore piecewise: the two signs can have different distances
 * from that existing centre to their respective guide edges. Input and output use the same unit.
 */
internal object FloatingCounterGeometry {
    const val MIN_OFFSET = -1f
    const val MAX_OFFSET = 1f
    const val MIN_COUNTER_SCALE = 0.5f
    const val MAX_COUNTER_SCALE = 2f
    const val REFERENCE_WIDTH_BUBBLES = 3f
    const val REFERENCE_HEIGHT_BUBBLES = 2.4f

    /** Unscaled ball and the fixed live-bubble content padding used by ChatFloatingContent. */
    const val BUBBLE_SIZE_DP = 52f
    const val HOST_LEADING_PADDING_DP = 25f
    const val HOST_TRAILING_PADDING_DP = 10f
    const val HOST_VERTICAL_PADDING_DP = 10f

    data class Center(val x: Float, val y: Float)
    data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun contains(other: Bounds): Boolean =
            other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom
    }

    /** Invalid legacy values return to the existing position; valid values remain signed. */
    fun clampOffset(value: Float): Float =
        if (!value.isFinite()) 0f else value.coerceIn(MIN_OFFSET, MAX_OFFSET)

    fun clampCounterScale(value: Float): Float =
        if (!value.isFinite()) 1f else value.coerceIn(MIN_COUNTER_SCALE, MAX_COUNTER_SCALE)

    fun center(
        horizontal: Float,
        vertical: Float,
        mirror: Boolean,
        bubbleSize: Float,
    ): Center {
        val size = if (bubbleSize.isFinite()) bubbleSize.coerceAtLeast(0f) else 0f
        val x = clampOffset(horizontal)
        val y = clampOffset(vertical)
        // The number is opposite the status dots: false = left midpoint, true = right midpoint.
        val existingX = if (mirror) size / 2f else -size / 2f
        val left = -REFERENCE_WIDTH_BUBBLES * size / 2f
        val right = REFERENCE_WIDTH_BUBBLES * size / 2f
        val verticalEdge = REFERENCE_HEIGHT_BUBBLES * size / 2f

        val movedX = if (x < 0f) {
            existingX + x * (existingX - left)
        } else {
            existingX + x * (right - existingX)
        }
        return Center(x = movedX, y = y * verticalEdge)
    }

    /**
     * Actual numeric-badge bounds relative to the ball centre after the bubble's visual scale.
     * Base width is D/2 and height D/4; glyph scaling expands this box about its centre.
     */
    fun badgeBounds(
        horizontal: Float,
        vertical: Float,
        mirror: Boolean,
        bubbleSize: Float,
        visualScale: Float,
        counterScale: Float = 1f,
    ): Bounds {
        val scale = if (visualScale.isFinite()) visualScale.coerceAtLeast(0f) else 0f
        // Compose scales the entire layer about the ball centre, including the legacy side offset.
        val target = center(horizontal, vertical, mirror, bubbleSize)
        val glyphScale = clampCounterScale(counterScale)
        val halfWidth = bubbleSize * scale / 4f * glyphScale
        val halfHeight = bubbleSize * scale / 8f * glyphScale
        val centerX = target.x * scale
        val centerY = target.y * scale
        return Bounds(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
    }

    /**
     * Fixed live-bubble window content bounds relative to the ball centre. Padding does not scale;
     * the whole visual layer (ball and badges) scales inside it. Anything outside is clipped by the
     * system window rectangle, regardless of Compose clip=false.
     */
    fun hostBounds(mirror: Boolean, bubbleSize: Float): Bounds {
        val size = if (bubbleSize.isFinite()) bubbleSize.coerceAtLeast(0f) else 0f
        val leading = if (mirror) HOST_TRAILING_PADDING_DP else HOST_LEADING_PADDING_DP
        val trailing = if (mirror) HOST_LEADING_PADDING_DP else HOST_TRAILING_PADDING_DP
        val ballCentreX = leading + size / 2f
        val ballCentreY = HOST_VERTICAL_PADDING_DP + size / 2f
        return Bounds(
            left = -ballCentreX,
            top = -ballCentreY,
            right = size + leading + trailing - ballCentreX,
            bottom = size + 2f * HOST_VERTICAL_PADDING_DP - ballCentreY,
        )
    }

    /** True only when the numeric badge would be clipped by the live bubble's window rectangle. */
    fun needsDetachedWindow(
        horizontal: Float,
        vertical: Float,
        mirror: Boolean,
        bubbleSize: Float,
        visualScale: Float,
        counterScale: Float = 1f,
    ): Boolean {
        if (visualScale <= 0f || !visualScale.isFinite()) return false
        return !hostBounds(mirror, bubbleSize).contains(
            badgeBounds(horizontal, vertical, mirror, bubbleSize, visualScale, counterScale)
        )
    }

    /** Movement to add to the legacy side alignment; zero must remain an exact no-op in Compose. */
    fun displacement(
        horizontal: Float,
        vertical: Float,
        mirror: Boolean,
        bubbleSize: Float,
    ): Center {
        val size = if (bubbleSize.isFinite()) bubbleSize.coerceAtLeast(0f) else 0f
        val existingX = if (mirror) size / 2f else -size / 2f
        val target = center(horizontal, vertical, mirror, size)
        return Center(x = target.x - existingX, y = target.y)
    }
}
