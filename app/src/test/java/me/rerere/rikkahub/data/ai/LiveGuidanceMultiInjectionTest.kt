package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.ArchivedLiveGuidance
import me.rerere.rikkahub.data.model.ConversationConfig
import me.rerere.rikkahub.data.model.LiveGuidance
import me.rerere.rikkahub.data.model.LiveGuidanceMode
import me.rerere.rikkahub.data.model.withLiveGuidanceEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LiveGuidanceMultiInjectionTest {
    @Test
    fun `active request keeps eligible order and archives stay chronological`() {
        val run = Uuid.random()
        val reply = UIMessage.assistant("reply")
        val archived = ArchivedLiveGuidance(
            id = Uuid.random(), runId = Uuid.random(), content = "persistent archive",
            mode = LiveGuidanceMode.PERSISTENT, nodeId = Uuid.random(), messageId = reply.id,
        )
        val temporaryArchive = archived.copy(
            id = Uuid.random(), content = "temporary archive", mode = LiveGuidanceMode.TEMPORARY,
        )
        val first = LiveGuidance(content = "active one", runId = run)
        val second = LiveGuidance(content = "active two", runId = run)
        val config = ConversationConfig(
            liveGuidanceArchive = listOf(archived, temporaryArchive),
        ).withLiveGuidanceEntries(listOf(first, second))

        // Active channel: only the two eligible cards, stable order, no archives.
        assertEquals(
            listOf(first.id, second.id),
            config.liveGuidanceRequestMessages(run).map { it.id },
        )
        assertTrue(config.liveGuidanceRequestMessages(Uuid.random()).isEmpty())

        // History channel: both archives follow their anchor in archival order.
        val history = listOf(reply, UIMessage.user("next question"))
            .withArchivedGuidance(config.liveGuidanceArchive)
        assertEquals(
            listOf(reply.id, archived.id, temporaryArchive.id),
            history.dropLast(1).map { it.id },
        )
        assertEquals("next question", history.last().toText())
    }
}
