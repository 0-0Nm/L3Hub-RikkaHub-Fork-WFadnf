package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LiveGuidanceTest {
    @Test
    fun `active submission defaults to temporary and waiting`() {
        val run = Uuid.random()
        val item = LiveGuidance(content = "keep the current tool batch", runId = run)

        assertEquals(LiveGuidanceMode.TEMPORARY, item.mode)
        assertEquals(LiveGuidanceDelivery.WAITING, item.delivery)
        assertTrue(item.shouldInject(run))
        assertFalse(item.shouldInject(Uuid.random()))
        assertEquals(null, item.deliveredRunId)
    }

    @Test
    fun `provider output acknowledges once and temporary delivery is isolated from later runs`() {
        val run = Uuid.random()
        val original = LiveGuidance(content = "read the log first", runId = run)
        val delivered = original.acknowledgeDelivery(run)

        assertEquals(original.id, delivered.id)
        assertEquals(LiveGuidanceDelivery.DELIVERED, delivered.delivery)
        assertEquals(run, delivered.deliveredRunId)
        assertTrue(delivered.shouldInject(run))
        assertEquals(delivered, delivered.acknowledgeDelivery(run))
        assertFalse(delivered.shouldInject(Uuid.random()))
    }

    @Test
    fun `active card switches the same id after delivery and persistent survives a new run`() {
        val firstRun = Uuid.random()
        val secondRun = Uuid.random()
        val original = LiveGuidance(content = "stay on this API", runId = firstRun)
        val delivered = original.acknowledgeDelivery(firstRun)
        val persistent = delivered.withPersistence(persistent = true, activeRunId = firstRun)
        val finished = persistent.finish(firstRun)

        assertEquals(original.id, persistent.id)
        assertEquals(original.content, persistent.content)
        assertTrue(finished.finishLocked)
        assertEquals(LiveGuidanceMode.PERSISTENT, finished.mode)
        assertTrue(finished.shouldInject(secondRun))
        assertEquals(finished, finished.withPersistence(false, secondRun))
    }

    @Test
    fun `finish without provider output preserves text as not used and never marks delivered`() {
        val run = Uuid.random()
        val original = LiveGuidance(content = "do not stop", runId = run)
        val persistent = original.withPersistence(true, run)
        assertTrue(persistent.shouldInject(run))
        assertEquals(original, original.acknowledgeDelivery(Uuid.random()))
        val finished = persistent.finish(run)

        assertEquals(LiveGuidanceDelivery.NOT_USED, finished.delivery)
        assertEquals(original.content, finished.content)
        assertEquals(null, finished.deliveredRunId)
        assertFalse(finished.shouldInject(run))
        assertFalse(finished.shouldInject(Uuid.random()))
        assertEquals(finished, finished.acknowledgeDelivery(run))
        assertTrue(finished.finishLocked)
    }

    @Test
    fun `missed text needs explicit arming before a later run can deliver it`() {
        val oldRun = Uuid.random()
        val nextRun = Uuid.random()
        val missed = LiveGuidance(content = "retry safely", runId = oldRun).finish(oldRun)

        assertEquals(missed, missed.beginRun(nextRun))
        val armed = missed.armForNextRun()
        assertEquals(LiveGuidanceDelivery.WAITING, armed.delivery)
        assertEquals(null, armed.runId)
        val bound = armed.beginRun(nextRun)
        val delivered = bound.acknowledgeDelivery(nextRun)

        assertEquals(nextRun, bound.runId)
        assertEquals(LiveGuidanceDelivery.DELIVERED, delivered.delivery)
        assertTrue(delivered.shouldInject(nextRun))
    }

    @Test
    fun `stale finish and stale mode changes cannot affect a replacement card`() {
        val oldRun = Uuid.random()
        val newRun = Uuid.random()
        val replacement = LiveGuidance(content = "new run", runId = newRun).acknowledgeDelivery(newRun)

        assertEquals(replacement, replacement.finish(oldRun))
        assertEquals(replacement, replacement.withPersistence(true, oldRun))
        assertFalse(replacement.finishLocked)
    }

    @Test
    fun `finish locks the last accepted temporary choice too`() {
        val run = Uuid.random()
        val item = LiveGuidance(content = "once only", runId = run)
            .withPersistence(true, run)
            .withPersistence(false, run)
            .acknowledgeDelivery(run)
            .finish(run)

        assertEquals(LiveGuidanceMode.TEMPORARY, item.mode)
        assertTrue(item.finishLocked)
        assertEquals(item, item.withPersistence(true, run))
    }

    @Test
    fun `reopen seals an interrupted run but preserves an explicitly armed next request`() {
        val interruptedRun = Uuid.random()
        val waiting = LiveGuidance(content = "process died", runId = interruptedRun)
        val delivered = waiting.acknowledgeDelivery(interruptedRun).withPersistence(true, interruptedRun)
        val armed = waiting.finish(interruptedRun).armForNextRun()

        assertEquals(LiveGuidanceDelivery.NOT_USED, waiting.recoverAfterReopen().delivery)
        assertTrue(waiting.recoverAfterReopen().finishLocked)
        assertEquals(LiveGuidanceMode.PERSISTENT, delivered.recoverAfterReopen().mode)
        assertTrue(delivered.recoverAfterReopen().finishLocked)
        assertEquals(armed, armed.recoverAfterReopen())
    }

}
