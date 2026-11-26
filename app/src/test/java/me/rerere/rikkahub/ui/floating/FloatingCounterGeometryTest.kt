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
    fun `horizontal endpoints reach opposite physical edges when mirrored`() {
        assertCenter(-78f, 0f, horizontal = -1f, vertical = 0f, mirror = false)
        assertCenter(78f, 0f, horizontal = 1f, vertical = 0f, mirror = false)
        assertCenter(78f, 0f, horizontal = -1f, vertical = 0f, mirror = true)
        assertCenter(-78f, 0f, horizontal = 1f, vertical = 0f, mirror = true)
    }

    @Test
    fun `horizontal interpolation measures each sign from the existing centre`() {
        // Left-side centre -26: left is 52 away, right is 104 away.
        assertCenter(-52f, 0f, horizontal = -0.5f, vertical = 0f, mirror = false)
        assertCenter(26f, 0f, horizontal = 0.5f, vertical = 0f, mirror = false)
        // The physical left dock reflects both the distances and the travel direction.
        assertCenter(52f, 0f, horizontal = -0.5f, vertical = 0f, mirror = true)
        assertCenter(-26f, 0f, horizontal = 0.5f, vertical = 0f, mirror = true)
    }

    @Test
    fun `signed offsets reflect centres and inline displacement on the left dock`() {
        val offsets = listOf(-1f, -.75f, -.5f, -.25f, 0f, .25f, .5f, .75f, 1f)
        for (horizontal in offsets) {
            for (vertical in listOf(-1f, -.35f, 0f, .6f, 1f)) {
                val canonical = FloatingCounterGeometry.center(horizontal, vertical, false, size)
                val mirrored = FloatingCounterGeometry.center(horizontal, vertical, true, size)
                assertEquals("centre x at $horizontal/$vertical", -canonical.x, mirrored.x, epsilon)
                assertEquals("centre y at $horizontal/$vertical", canonical.y, mirrored.y, epsilon)

                val canonicalDisplacement = FloatingCounterGeometry.displacement(horizontal, vertical, false, size)
                val mirroredDisplacement = FloatingCounterGeometry.displacement(horizontal, vertical, true, size)
                assertEquals(
                    "displacement x at $horizontal/$vertical",
                    -canonicalDisplacement.x,
                    mirroredDisplacement.x,
                    epsilon,
                )
                assertEquals(
                    "displacement y at $horizontal/$vertical",
                    canonicalDisplacement.y,
                    mirroredDisplacement.y,
                    epsilon,
                )
            }
        }
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
    fun `detached bounds and clip decisions mirror from 50 through 200 percent`() {
        val canonicalHost = FloatingCounterGeometry.hostBounds(mirror = false, bubbleSize = size)
        assertEquals(
            FloatingCounterGeometry.Bounds(
                -canonicalHost.right,
                canonicalHost.top,
                -canonicalHost.left,
                canonicalHost.bottom,
            ),
            FloatingCounterGeometry.hostBounds(mirror = true, bubbleSize = size),
        )
        for (counterScale in listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f)) {
            for (horizontal in listOf(-1f, -.5f, 0f, .5f, 1f)) {
                val canonical = FloatingCounterGeometry.badgeBounds(
                    horizontal = horizontal,
                    vertical = .3f,
                    mirror = false,
                    bubbleSize = size,
                    visualScale = .8f,
                    counterScale = counterScale,
                )
                val mirrored = FloatingCounterGeometry.badgeBounds(
                    horizontal = horizontal,
                    vertical = .3f,
                    mirror = true,
                    bubbleSize = size,
                    visualScale = .8f,
                    counterScale = counterScale,
                )
                assertEquals("left at $horizontal/$counterScale", -canonical.right, mirrored.left, epsilon)
                assertEquals("top at $horizontal/$counterScale", canonical.top, mirrored.top, epsilon)
                assertEquals("right at $horizontal/$counterScale", -canonical.left, mirrored.right, epsilon)
                assertEquals("bottom at $horizontal/$counterScale", canonical.bottom, mirrored.bottom, epsilon)
                assertEquals(
                    "clip at $horizontal/$counterScale",
                    FloatingCounterGeometry.needsDetachedWindow(horizontal, .3f, false, size, .8f, counterScale),
                    FloatingCounterGeometry.needsDetachedWindow(horizontal, .3f, true, size, .8f, counterScale),
                )
            }
        }
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

    @Test
    fun `glyph scaling preserves centre and scales both dimensions`() {
        for (mirror in listOf(false, true)) {
            val old = FloatingCounterGeometry.badgeBounds(.24f, -.34f, mirror, size, .64f)
            assertEquals(old, FloatingCounterGeometry.badgeBounds(.24f, -.34f, mirror, size, .64f, 1f))
            for (factor in listOf(.5f, 2f)) {
                val scaled = FloatingCounterGeometry.badgeBounds(.24f, -.34f, mirror, size, .64f, factor)
                assertEquals(old.left + old.right, scaled.left + scaled.right, epsilon)
                assertEquals(old.top + old.bottom, scaled.top + scaled.bottom, epsilon)
                assertEquals((old.right - old.left) * factor, scaled.right - scaled.left, epsilon)
                assertEquals((old.bottom - old.top) * factor, scaled.bottom - scaled.top, epsilon)
            }
        }
    }

    @Test
    fun `larger glyph hands off when it no longer fits the original host`() {
        for (mirror in listOf(false, true)) {
            assertFalse(FloatingCounterGeometry.needsDetachedWindow(0f, 0f, mirror, size, 1f, 1f))
            assertTrue(FloatingCounterGeometry.needsDetachedWindow(0f, 0f, mirror, size, 1f, 2f))
        }
        assertEquals(.5f, FloatingCounterGeometry.clampCounterScale(-1f), epsilon)
        assertEquals(2f, FloatingCounterGeometry.clampCounterScale(3f), epsilon)
        assertEquals(1f, FloatingCounterGeometry.clampCounterScale(Float.NaN), epsilon)
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
