package me.rerere.rikkahub.ui.floating

import me.rerere.rikkahub.data.model.FloatingCounterPalette
import kotlin.math.roundToInt

/** Interpolate opaque RGB in equal 25-count intervals, independently of task-state colors. */
internal object FloatingCounterVisuals {
    fun colorArgb(count: Int, palette: FloatingCounterPalette = FloatingCounterPalette.Default): Int {
        fun anchor(stop: Int) = (255 shl 24) or palette.hexAt(stop).substring(1).toInt(16)
        val value = count.coerceIn(0, 100)
        val lower = value / 25 * 25
        val a = anchor(lower)
        if (value == lower) return a
        val fraction = (value % 25) / 25f
        val b = anchor(lower + 25)
        fun channel(shift: Int): Int {
            val start = (a ushr shift) and 255
            val end = (b ushr shift) and 255
            return (start + (end - start) * fraction).roundToInt().coerceIn(0, 255)
        }
        return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
