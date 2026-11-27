package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.LiveGuidance
import me.rerere.rikkahub.data.model.LiveGuidanceDelivery
import me.rerere.rikkahub.data.model.LiveGuidanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LiveGuidanceInjectionTest {
    @Test
    fun `delivered steering is one stable synthetic pure-text user message`() {
        val run = Uuid.random()
        val item = LiveGuidance(content = "prefer a focused patch", runId = run).acknowledgeDelivery(run)

        val first = item.requestMessage(run)!!
        val second = item.requestMessage(run)!!

        assertEquals(item.id, first.id)
        assertEquals(first.id, second.id)
        assertEquals(MessageRole.USER, first.role)
        assertTrue(first.isSynthetic)
        assertTrue(first.parts.single() is UIMessagePart.Text)
        assertTrue(first.getTools().isEmpty())
        assertTrue((first.parts.single() as UIMessagePart.Text).text.endsWith(item.content))
    }

    @Test
    fun `building waiting request text does not acknowledge it or widen its scope`() {
        val run = Uuid.random()
        val waiting = LiveGuidance(content = "wait", runId = run)
        val missed = waiting.finish(run)
        val delivered = waiting.acknowledgeDelivery(run)

        assertEquals(waiting.id, waiting.requestMessage(run)?.id)
        assertEquals(LiveGuidanceDelivery.WAITING, waiting.delivery)
        assertNull(waiting.deliveredRunId)
        assertNull(waiting.requestMessage(Uuid.random()))
        assertNull(missed.requestMessage(run))
        assertNull(delivered.requestMessage(Uuid.random()))
    }

    @Test
    fun `persistent text is available to future requests in the same conversation state`() {
        val firstRun = Uuid.random()
        val item = LiveGuidance(content = "same conversation only", runId = firstRun)
            .withPersistence(true, firstRun)
            .acknowledgeDelivery(firstRun)
            .finish(firstRun)

        assertEquals(LiveGuidanceMode.PERSISTENT, item.mode)
        assertEquals(item.id, item.requestMessage(Uuid.random())?.id)
    }
    @Test
    fun `request assembly deduplicates one id and replacement excludes old text`() {
        val run = Uuid.random()
        val original = LiveGuidance(content = "old", runId = run).acknowledgeDelivery(run).requestMessage(run)!!
        val duplicate = original.copy()
        val replacement = LiveGuidance(content = "new", runId = run).acknowledgeDelivery(run).requestMessage(run)!!
        val base = listOf(UIMessage.user("ordinary history"))

        val once = base.withRequestInjections(listOf(original, duplicate))
        val replaced = base.withRequestInjections(listOf(replacement))

        assertEquals(1, once.count { it.id == original.id })
        assertEquals(2, once.size)
        assertEquals(listOf(replacement.id), replaced.drop(1).map { it.id })
        assertTrue(replaced.none { it.id == original.id })
    }

}
