package me.rerere.rikkahub.ui.floating

import kotlin.math.roundToInt

/** Equal 25-count intervals; independent of orange/green task-state signaling. */
internal object FloatingCounterVisuals {
    private val anchors = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt(), 0xFF43A047.toInt(),
        0xFF9C27B0.toInt(), 0xFFE53935.toInt(),
    )
    fun colorArgb(count: Int): Int {
        val value = count.coerceIn(0, 100)
        if (value == 100) return anchors.last()
        val index = value / 25
        val fraction = (value % 25) / 25f
        val a = anchors[index]
        val b = anchors[index + 1]
        fun channel(shift: Int): Int {
            val start = (a ushr shift) and 255
            val end = (b ushr shift) and 255
            return (start + (end - start) * fraction).roundToInt().coerceIn(0, 255)
        }
        return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
