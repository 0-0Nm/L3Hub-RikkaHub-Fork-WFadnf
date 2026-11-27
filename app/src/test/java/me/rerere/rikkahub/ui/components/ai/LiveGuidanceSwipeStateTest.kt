package me.rerere.rikkahub.ui.components.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LiveGuidanceSwipeStateTest {
    private val reveal = 100f
    private val threshold = 200f

    @Test
    fun physicalRightFiresCancelOnlyOnReleaseAndOnlyOnce() {
        val drag = LiveGuidanceSwipeState().begin().dragBy(240f, 400f)
        assertEquals(240f, drag.offset)
        val released = drag.release(reveal, threshold)
        assertEquals(LiveGuidanceSwipeAction.CANCEL, released.action)
        assertEquals(0f, released.state.offset)
        assertFalse(released.state.dragging)
        assertNull(released.state.release(reveal, threshold).action)
    }

    @Test
    fun physicalLeftFiresNextOnlyOnReleaseAndOnlyOnce() {
        val released = LiveGuidanceSwipeState().begin().dragBy(-240f, 400f).release(reveal, threshold)
        assertEquals(LiveGuidanceSwipeAction.NEXT_MESSAGE, released.action)
        assertNull(released.state.release(reveal, threshold).action)
    }

    @Test
    fun crossingAndRetreatingBeforeReleaseDoesNotFire() {
        val released = LiveGuidanceSwipeState().begin().dragBy(250f, 400f)
            .dragBy(-170f, 400f).release(reveal, threshold)
        assertNull(released.action)
        assertEquals(reveal, released.state.offset)
    }

    @Test
    fun bothPartialRevealsStayOpenWithoutDispatching() {
        listOf(-1f, 1f).forEach { sign ->
            val released = LiveGuidanceSwipeState().begin().dragBy(70f * sign, 400f)
                .release(reveal, threshold)
            assertNull(released.action)
            assertEquals(reveal * sign, released.state.offset)
        }
    }

    @Test
    fun smallMovementClosesAndVelocityIsNotAnInput() {
        listOf(-30f, 0f, 30f).forEach { dx ->
            val released = LiveGuidanceSwipeState().begin().dragBy(dx, 400f).release(reveal, threshold)
            assertNull(released.action)
            assertEquals(0f, released.state.offset)
        }
    }

    @Test
    fun cancelIncludingBeyondThresholdNeverFires() {
        val cancelled = LiveGuidanceSwipeState().begin().dragBy(-300f, 400f).cancel()
        assertEquals(0f, cancelled.offset)
        assertNull(cancelled.release(reveal, threshold).action)
    }

    @Test
    fun offsetCannotExceedRowBoundsAndDragRequiresBegin() {
        assertEquals(0f, LiveGuidanceSwipeState().dragBy(100f, 400f).offset)
        assertEquals(400f, LiveGuidanceSwipeState().begin().dragBy(900f, 400f).offset)
        assertEquals(-400f, LiveGuidanceSwipeState().begin().dragBy(-900f, 400f).offset)
    }

    @Test
    fun exactThresholdIsInclusiveInBothDirections() {
        assertEquals(LiveGuidanceSwipeAction.CANCEL,
            LiveGuidanceSwipeState().begin().dragBy(threshold, 400f).release(reveal, threshold).action)
        assertEquals(LiveGuidanceSwipeAction.NEXT_MESSAGE,
            LiveGuidanceSwipeState().begin().dragBy(-threshold, 400f).release(reveal, threshold).action)
    }

    @Test
    fun alreadyRevealedActionDoesNotFireOnAnUnclaimedTap() {
        assertNull(LiveGuidanceSwipeState(reveal).release(reveal, threshold).action)
        val released = LiveGuidanceSwipeState(reveal).begin().dragBy(-80f, 400f).release(reveal, threshold)
        assertEquals(0f, released.state.offset)
        assertNull(released.action)
    }
}
