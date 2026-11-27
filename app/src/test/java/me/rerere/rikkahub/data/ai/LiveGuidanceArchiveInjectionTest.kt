package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.ConversationConfig
import me.rerere.rikkahub.data.model.LiveGuidance
import me.rerere.rikkahub.data.model.LiveGuidanceMode
import me.rerere.rikkahub.data.model.archiveRecord
import me.rerere.rikkahub.data.model.finishLiveGuidance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `temporary archive is UI only and never enters a request again`() {
        val config = archived(persistent = false)

        assertNull(config.liveGuidance)
        assertNull(config.liveGuidanceRequestMessage(Uuid.random()))
        assertEquals(1, config.liveGuidanceArchive.size)
    }

    @Test
    fun `persistent archive keeps steering only as a synthetic request message`() {
        val config = archived(persistent = true)
        val entry = config.liveGuidanceArchive.single()

        val request = config.liveGuidanceRequestMessage(Uuid.random())!!

        assertEquals(entry.id, request.id)
        assertEquals(MessageRole.USER, request.role)
        assertTrue(request.isSynthetic)
        assertTrue(request.getTools().isEmpty())
        assertEquals(LiveGuidanceMode.PERSISTENT, entry.mode)
        // Reading a request snapshot never re-acknowledges or rewrites the archived record.
        assertEquals(entry, config.liveGuidanceArchive.single())
        assertNull(config.liveGuidance)
    }

    @Test
    fun `an eligible active card wins over the archived persistent note and ids never mix`() {
        val config = archived(persistent = true)
        val archivedId = config.liveGuidanceArchive.single().id
        val run = Uuid.random()
        val active = LiveGuidance(content = "new live note", runId = run)
        val withActive = config.copy(liveGuidance = active)

        val duringRun = withActive.liveGuidanceRequestMessage(run)!!
        assertEquals(active.id, duringRun.id)
        assertTrue(duringRun.id != archivedId)
        // Once the new note is archived too it is temporary, so the persistent record still steers.
        val finished = withActive.finishLiveGuidance(run) { Uuid.random() to Uuid.random() }
        assertEquals(2, finished.liveGuidanceArchive.size)
        assertNull(finished.liveGuidance)
        assertEquals(archivedId, finished.liveGuidanceRequestMessage(Uuid.random())!!.id)
    }

    @Test
    fun `archived request message keeps the original text and never becomes a history turn`() {
        val run = Uuid.random()
        val item = LiveGuidance(content = "original wording", runId = run).acknowledgeDelivery(run)
        val entry = item.finish(run).archiveRecord(Uuid.random(), Uuid.random())!!

        val message = entry.requestMessage()

        assertTrue(message.isSynthetic)
        assertTrue(message.parts.none { it is me.rerere.ai.ui.UIMessagePart.Notice })
        assertTrue(message.toText().endsWith("original wording"))
        assertEquals(entry.content, item.content)
    }
}
