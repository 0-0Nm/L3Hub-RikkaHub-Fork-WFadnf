package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.ConversationConfig
import me.rerere.rikkahub.data.model.LiveGuidance
import me.rerere.rikkahub.data.model.LiveGuidanceMode
import me.rerere.rikkahub.data.model.archiveRecord
import me.rerere.rikkahub.data.model.finishLiveGuidance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LiveGuidanceArchiveInjectionTest {
    private fun archived(persistent: Boolean): ConversationConfig {
        val run = Uuid.random()
        val item = LiveGuidance(content = "steer later replies", runId = run)
            .withPersistence(persistent, run)
            .acknowledgeDelivery(run)
        return ConversationConfig(liveGuidance = item).finishLiveGuidance(run) {
            Uuid.random() to Uuid.random()
        }
    }

    @Test
    fun `temporary archive never re-enters as an active end-of-request direction`() {
        val config = archived(persistent = false)

        assertEquals(null, config.liveGuidance)
        assertTrue(config.liveGuidanceRequestMessages(Uuid.random()).isEmpty())
        assertEquals(1, config.liveGuidanceArchive.size)
    }

    @Test
    fun `persistent archive also stays out of the active claim and ack channel`() {
        val config = archived(persistent = true)
        val entry = config.liveGuidanceArchive.single()

        assertEquals(LiveGuidanceMode.PERSISTENT, entry.mode)
        assertTrue(config.liveGuidanceRequestMessages(Uuid.random()).isEmpty())
        // Reading snapshots never re-acknowledges or rewrites the archived record.
        assertEquals(entry, config.liveGuidanceArchive.single())
        assertEquals(null, config.liveGuidance)
    }

    @Test
    fun `an eligible active card keeps its own id and never mixes with archived ids`() {
        val config = archived(persistent = true)
        val archivedId = config.liveGuidanceArchive.single().id
        val run = Uuid.random()
        val active = LiveGuidance(content = "new live note", runId = run)
        val withActive = config.copy(liveGuidance = active)

        val duringRun = withActive.liveGuidanceRequestMessages(run)
        assertEquals(listOf(active.id), duringRun.map { it.id })
        assertTrue(active.id != archivedId)
        // Once the new note is archived too, both records are history, not active directions.
        val deliveredActive = withActive.copy(liveGuidance = active.acknowledgeDelivery(run))
        val finished = deliveredActive.finishLiveGuidance(run) { Uuid.random() to Uuid.random() }
        assertEquals(2, finished.liveGuidanceArchive.size)
        assertEquals(null, finished.liveGuidance)
        assertTrue(finished.liveGuidanceRequestMessages(Uuid.random()).isEmpty())
    }

    @Test
    fun `archived history message keeps the original wording as a plain user text`() {
        val run = Uuid.random()
        val item = LiveGuidance(content = "original wording", runId = run).acknowledgeDelivery(run)
        val entry = item.finish(run).archiveRecord(Uuid.random(), Uuid.random())!!

        val message = entry.historyMessage()

        assertTrue(message.isSynthetic)
        assertEquals(MessageRole.USER, message.role)
        assertTrue(message.parts.none { it is me.rerere.ai.ui.UIMessagePart.Notice })
        assertEquals("original wording", message.toText())
        assertEquals(entry.content, item.content)
    }
}
