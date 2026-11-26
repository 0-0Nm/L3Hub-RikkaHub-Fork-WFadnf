package me.rerere.rikkahub.ui.floating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingCounterGeometryTest {
    private val size = 52f
    private val epsilon = 0.0001f

    @Test
    fun `zero preserves the existing numeric badge centre on either side`() {
        assertCenter(-26f, 0f, horizontal = 0f, vertical = 0f, mirror = false)
        assertCenter(26f, 0f, horizontal = 0f, vertical = 0f, mirror = true)
    }

    @Test
    fun `zero displacement leaves the legacy Compose placement untouched`() {
        for (mirror in listOf(false, true)) {
            val displacement = FloatingCounterGeometry.displacement(0f, 0f, mirror, size)
            assertEquals(0f, displacement.x, epsilon)
            assertEquals(0f, displacement.y, epsilon)
        }
    }

    @Test
    fun `horizontal endpoints reach the centred three-diameter guide edges`() {
        for (mirror in listOf(false, true)) {
            assertCenter(-78f, 0f, horizontal = -1f, vertical = 0f, mirror = mirror)
            assertCenter(78f, 0f, horizontal = 1f, vertical = 0f, mirror = mirror)
        }
    }

    @Test
    fun `horizontal interpolation measures each sign from the existing centre`() {
        // Left-side centre -26: left is 52 away, right is 104 away.
        assertCenter(-52f, 0f, horizontal = -0.5f, vertical = 0f, mirror = false)
        assertCenter(26f, 0f, horizontal = 0.5f, vertical = 0f, mirror = false)
        // Mirroring swaps those distances rather than moving the zero point.
        assertCenter(-26f, 0f, horizontal = -0.5f, vertical = 0f, mirror = true)
        assertCenter(52f, 0f, horizontal = 0.5f, vertical = 0f, mirror = true)
    }

    @Test
    fun `vertical endpoints reach the centred two-point-four-diameter guide edges`() {
        assertCenter(-26f, -62.4f, horizontal = 0f, vertical = -1f, mirror = false)
        assertCenter(-26f, 62.4f, horizontal = 0f, vertical = 1f, mirror = false)
        assertCenter(26f, 31.2f, horizontal = 0f, vertical = 0.5f, mirror = true)
    }

    @Test
    fun `guide and zero point scale linearly with the actual ball size`() {
        val doubled = FloatingCounterGeometry.center(
            horizontal = 1f,
            vertical = -1f,
            mirror = false,
            bubbleSize = size * 2f,
        )
        assertEquals(156f, doubled.x, epsilon)
        assertEquals(-124.8f, doubled.y, epsilon)

        val zero = FloatingCounterGeometry.center(0f, 0f, mirror = true, bubbleSize = size * 0.5f)
        assertEquals(13f, zero.x, epsilon)
        assertEquals(0f, zero.y, epsilon)
    }

    @Test
    fun `zero and nearby legacy positions stay inside the live window`() {
        for (mirror in listOf(false, true)) {
            assertFalse(
                "zero must not acquire a companion at mirror=$mirror",
                FloatingCounterGeometry.needsDetachedWindow(0f, 0f, mirror, size, 1f),
            )
        }
        assertEquals(
            FloatingCounterGeometry.Bounds(-51f, -36f, 36f, 36f),
            FloatingCounterGeometry.hostBounds(mirror = false, bubbleSize = size),
        )
        assertEquals(
            FloatingCounterGeometry.Bounds(-36f, -36f, 51f, 36f),
            FloatingCounterGeometry.hostBounds(mirror = true, bubbleSize = size),
        )
    }

    @Test
    fun `guide edges request the non-touchable companion on both sides`() {
        for (mirror in listOf(false, true)) {
            assertTrue(FloatingCounterGeometry.needsDetachedWindow(-1f, 0f, mirror, size, 1f))
            assertTrue(FloatingCounterGeometry.needsDetachedWindow(1f, 0f, mirror, size, 1f))
            assertTrue(FloatingCounterGeometry.needsDetachedWindow(0f, -1f, mirror, size, 1f))
            assertTrue(FloatingCounterGeometry.needsDetachedWindow(0f, 1f, mirror, size, 1f))
        }
    }

    @Test
    fun `badge bounds use the same ball-centred transform as the inline layer`() {
        val normal = FloatingCounterGeometry.badgeBounds(0f, 0f, mirror = false, bubbleSize = size, visualScale = 1f)
        assertEquals(FloatingCounterGeometry.Bounds(-39f, -6.5f, -13f, 6.5f), normal)
        val scaled = FloatingCounterGeometry.badgeBounds(0f, 0f, mirror = false, bubbleSize = size, visualScale = 0.5f)
        // At 50%, the legacy left midpoint is -13dp and the badge half-width is 6.5dp.
        assertEquals(FloatingCounterGeometry.Bounds(-19.5f, -3.25f, -6.5f, 3.25f), scaled)
        val mirrored = FloatingCounterGeometry.badgeBounds(0f, 0f, mirror = true, bubbleSize = size, visualScale = 0.5f)
        assertEquals(FloatingCounterGeometry.Bounds(6.5f, -3.25f, 19.5f, 3.25f), mirrored)
        val endpoint = FloatingCounterGeometry.badgeBounds(1f, 0f, mirror = false, bubbleSize = size, visualScale = 1f)
        assertEquals(FloatingCounterGeometry.Bounds(65f, -6.5f, 91f, 6.5f), endpoint)
    }

    @Test
    fun `signed offsets clamp without turning invalid values into an endpoint`() {
        assertEquals(-1f, FloatingCounterGeometry.clampOffset(-4f), epsilon)
        assertEquals(1f, FloatingCounterGeometry.clampOffset(4f), epsilon)
        assertEquals(-0.25f, FloatingCounterGeometry.clampOffset(-0.25f), epsilon)
        assertEquals(0f, FloatingCounterGeometry.clampOffset(Float.NaN), epsilon)
        assertEquals(0f, FloatingCounterGeometry.clampOffset(Float.POSITIVE_INFINITY), epsilon)
        assertEquals(0f, FloatingCounterGeometry.clampOffset(Float.NEGATIVE_INFINITY), epsilon)
    }

    private fun assertCenter(
        expectedX: Float,
        expectedY: Float,
        horizontal: Float,
        vertical: Float,
        mirror: Boolean,
    ) {
        val actual = FloatingCounterGeometry.center(horizontal, vertical, mirror, size)
        assertEquals(expectedX, actual.x, epsilon)
        assertEquals(expectedY, actual.y, epsilon)
    }
}
