package me.rerere.rikkahub.ui.floating

import me.rerere.rikkahub.data.model.FloatingCounterPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingCounterVisualsTest {
    @Test fun `five equally spaced anchors match`() {
        val expected = listOf(0 to 0xFFD0C7B5.toInt(), 25 to 0xFFAD9566.toInt(),
            50 to 0xFF75846C.toInt(), 75 to 0xFF88758A.toInt(), 100 to 0xFFA4655D.toInt())
        expected.forEach { (n, color) -> assertEquals(color, FloatingCounterVisuals.colorArgb(n)) }
    }
    @Test fun `over one hundred remains red and negative uses white`() {
        assertEquals(FloatingCounterVisuals.colorArgb(0), FloatingCounterVisuals.colorArgb(-10))
        for (n in listOf(101, 999, Int.MAX_VALUE)) {
            assertEquals(FloatingCounterVisuals.colorArgb(100), FloatingCounterVisuals.colorArgb(n))
        }
    }
    @Test fun `intermediate counts interpolate rather than snap`() {
        for (n in 1 until 100) {
            if (n % 25 != 0) {
                assertTrue(FloatingCounterVisuals.colorArgb(n) != FloatingCounterVisuals.colorArgb(n / 25 * 25))
            }
            assertEquals(255, FloatingCounterVisuals.colorArgb(n) ushr 24)
        }
    }

    @Test
    fun `every count blends adjacent custom stops in opaque RGB`() {
        val rgb = listOf(0x0055AA, 0xFF0010, 0x120AFE, 0x908070, 0xABCDEF)
        val palette = FloatingCounterPalette.stops.zip(rgb).fold(FloatingCounterPalette.Default) { p, (stop, color) ->
            p.withHex(stop, color.toString(16).padStart(6, '0'))
        }
        for (count in -1..101) {
            val n = count.coerceIn(0, 100)
            val i = (n / 25).coerceAtMost(3)
            val t = n - 25 * i
            var expected = 0xFF000000.toInt()
            for (shift in listOf(0, 8, 16)) {
                val a = (rgb[i] ushr shift) and 255
                val b = (rgb[i + 1] ushr shift) and 255
                expected = expected or ((((25 - t) * a + t * b + 12) / 25) shl shift)
            }
            assertEquals("count=$count", expected, FloatingCounterVisuals.colorArgb(count, palette))
        }
    }

    @Test
    fun `only a complete six digit RGB replaces the selected stop`() {
        val defaults = FloatingCounterPalette.Default
        for (stop in FloatingCounterPalette.stops) {
            val valid = defaults.withHex(stop, "aB0cdE")
            assertEquals("#AB0CDE", valid.hexAt(stop))
            assertEquals(valid, defaults.withHex(stop, "#Ab0cDe"))
            for (other in FloatingCounterPalette.stops.filter { it != stop }) {
                assertEquals(defaults.hexAt(other), valid.hexAt(other))
            }
            for (draft in listOf("", "#", "#abc", "abcde", "1234567", "#ff123456", "##123456", "12345g", "+12345", "１２３４５６")) {
                assertSame("$stop: $draft", valid, valid.withHex(stop, draft))
            }
        }
    }

    @Test
    fun `one malformed imported string does not discard other colors`() {
        val palette = FloatingCounterPalette(at0 = "invalid", at25 = "abCDef")
        assertEquals(0xFFD0C7B5.toInt(), FloatingCounterVisuals.colorArgb(Int.MIN_VALUE, palette))
        assertEquals(0xFFABCDEF.toInt(), FloatingCounterVisuals.colorArgb(25, palette))
    }
}
