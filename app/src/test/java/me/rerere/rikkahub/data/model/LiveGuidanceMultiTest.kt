package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LiveGuidanceMultiTest {
    @Test
    fun `legacy singleton and ordered entries are backwards compatible`() {
        val first = LiveGuidance(content = "first")
        val second = LiveGuidance(content = "second")
        val legacy = ConversationConfig(liveGuidance = first)
        val multi = legacy.withLiveGuidanceEntries(legacy.liveGuidanceEntries() + second)

        assertEquals(listOf(first), legacy.liveGuidanceEntries())
        assertEquals(listOf(first, second), multi.liveGuidanceEntries())
        assertEquals(first, multi.liveGuidance)
        assertEquals(listOf(first, second), multi.liveGuidances)
        assertEquals(multi.liveGuidanceEntries(), multi.liveGuidanceEntries().distinctBy { it.id })
    }

    @Test
    fun `cancel and defer target only their requested id`() {
        val run = Uuid.random()
        val first = LiveGuidance(content = "cancel", runId = run).finish(run)
        val second = LiveGuidance(content = "defer", runId = run).finish(run)
        val third = LiveGuidance(content = "untouched", runId = run)
        val initial = ConversationConfig().withLiveGuidanceEntries(listOf(first, second, third))

        val cancelled = initial.withLiveGuidanceEntries(
            initial.liveGuidanceEntries().filterNot { it.id == first.id }
        )
        val deferred = cancelled.withLiveGuidanceEntries(
            cancelled.liveGuidanceEntries().map {
                if (it.id == second.id) it.armForNextRun() else it
            }
        )

        assertEquals(listOf(second.id, third.id), cancelled.liveGuidanceEntries().map { it.id })
        assertEquals(LiveGuidanceDelivery.WAITING, deferred.liveGuidanceEntries().first().delivery)
        assertNull(deferred.liveGuidanceEntries().first().runId)
        assertTrue(deferred.liveGuidanceEntries().first().canSwipe.not())
        assertEquals(third, deferred.liveGuidanceEntries().last())
        assertTrue(second.canSwipe)
        assertTrue(first.canSwipe)
        assertFalse(third.canSwipe)
    }

    @Test
    fun `provider acknowledgement changes only matching id and run`() {
        val run = Uuid.random()
        val first = LiveGuidance(content = "one", runId = run)
        val second = LiveGuidance(content = "two", runId = run)
        val config = ConversationConfig().withLiveGuidanceEntries(listOf(first, second))

        val acknowledged = config.withLiveGuidanceEntries(config.liveGuidanceEntries().map {
            if (it.id == second.id) it.acknowledgeDelivery(run) else it
        })

        assertEquals(LiveGuidanceDelivery.WAITING, acknowledged.liveGuidanceEntries()[0].delivery)
        assertEquals(LiveGuidanceDelivery.DELIVERED, acknowledged.liveGuidanceEntries()[1].delivery)
        assertEquals(run, acknowledged.liveGuidanceEntries()[1].deliveredRunId)
        assertEquals(first.id, acknowledged.liveGuidance?.id)
    }

    @Test
    fun `finish archives each delivered entry and retains missed entries in order`() {
        val run = Uuid.random()
        val first = LiveGuidance(content = "one", runId = run).acknowledgeDelivery(run)
        val missed = LiveGuidance(content = "missed", runId = run)
        val third = LiveGuidance(content = "three", runId = run)
            .withPersistence(true, run)
            .acknowledgeDelivery(run)
        val node = Uuid.random()
        val message = Uuid.random()
        val config = ConversationConfig().withLiveGuidanceEntries(listOf(first, missed, third))

        val finished = config.finishLiveGuidance(run) { node to message }

        assertEquals(listOf(missed.id), finished.liveGuidanceEntries().map { it.id })
        assertEquals(LiveGuidanceDelivery.NOT_USED, finished.liveGuidanceEntries().single().delivery)
        assertEquals(listOf(first.id, third.id), finished.liveGuidanceArchive.map { it.id })
        assertEquals(listOf(node, node), finished.liveGuidanceArchive.map { it.nodeId })
        assertEquals(LiveGuidanceMode.PERSISTENT, finished.liveGuidanceArchive.last().mode)
        assertEquals(missed.id, finished.liveGuidance?.id)
    }
}
