package me.rerere.rikkahub.ui.floating

import org.junit.Assert.assertEquals
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
}
