package me.rerere.rikkahub.ui.components.ai

import kotlin.math.abs
import kotlin.math.sign

/** Physical directions, deliberately independent of LayoutDirection and velocity. */
internal enum class LiveGuidanceSwipeAction { CANCEL, NEXT_MESSAGE }

/** Pure release policy: crossing a threshold while moving does not dispatch anything. */
internal data class LiveGuidanceSwipeState(
    val offset: Float = 0f,
    val dragging: Boolean = false,
) {
    fun begin() = copy(dragging = true)

    fun dragBy(delta: Float, limit: Float): LiveGuidanceSwipeState =
        if (dragging) copy(offset = (offset + delta).coerceIn(-limit, limit)) else this

    fun cancel() = LiveGuidanceSwipeState()

    fun release(reveal: Float, threshold: Float): LiveGuidanceSwipeRelease {
        require(reveal > 0f && threshold > reveal)
        if (!dragging) return LiveGuidanceSwipeRelease(this)
        val action = when {
            offset >= threshold -> LiveGuidanceSwipeAction.CANCEL
            offset <= -threshold -> LiveGuidanceSwipeAction.NEXT_MESSAGE
            else -> null
        }
        val settled = if (action == null && abs(offset) >= reveal * 0.4f) {
            LiveGuidanceSwipeState(reveal * sign(offset))
        } else {
            LiveGuidanceSwipeState()
        }
        return LiveGuidanceSwipeRelease(settled, action)
    }
}

internal data class LiveGuidanceSwipeRelease(
    val state: LiveGuidanceSwipeState,
    val action: LiveGuidanceSwipeAction? = null,
)
