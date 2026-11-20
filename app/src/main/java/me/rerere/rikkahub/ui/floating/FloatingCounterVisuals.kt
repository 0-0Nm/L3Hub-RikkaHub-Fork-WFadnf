package me.rerere.rikkahub.ui.floating

import kotlin.math.roundToInt

/** Muted ivory / aged gold / sage / smoky purple / brick red. Equal 25-count intervals; independent of task-state colors. */
internal object FloatingCounterVisuals {
    private val anchors = intArrayOf(
        0xFFD0C7B5.toInt(), 0xFFAD9566.toInt(), 0xFF75846C.toInt(),
        0xFF88758A.toInt(), 0xFFA4655D.toInt(),
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
