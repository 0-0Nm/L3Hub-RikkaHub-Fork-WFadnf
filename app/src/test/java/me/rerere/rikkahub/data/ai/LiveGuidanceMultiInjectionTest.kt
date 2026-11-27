package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.model.ArchivedLiveGuidance
import me.rerere.rikkahub.data.model.ConversationConfig
import me.rerere.rikkahub.data.model.LiveGuidance
import me.rerere.rikkahub.data.model.LiveGuidanceMode
import me.rerere.rikkahub.data.model.withLiveGuidanceEntries
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class LiveGuidanceMultiInjectionTest {
    @Test
    fun `request keeps persistent archive scope then eligible active order`() {
        val run = Uuid.random()
        val archived = ArchivedLiveGuidance(
            id = Uuid.random(), runId = Uuid.random(), content = "persistent archive",
            mode = LiveGuidanceMode.PERSISTENT, nodeId = Uuid.random(), messageId = Uuid.random(),
        )
        val temporaryArchive = archived.copy(
            id = Uuid.random(), content = "temporary archive", mode = LiveGuidanceMode.TEMPORARY,
        )
        val first = LiveGuidance(content = "active one", runId = run)
        val second = LiveGuidance(content = "active two", runId = run)
        val config = ConversationConfig(
            liveGuidanceArchive = listOf(archived, temporaryArchive),
        ).withLiveGuidanceEntries(listOf(first, second))

        val requests = config.liveGuidanceRequestMessages(run)

        assertEquals(listOf(archived.id, first.id, second.id), requests.map { it.id })
        assertEquals(listOf(archived.id), config.liveGuidanceRequestMessages(Uuid.random()).map { it.id })
    }
}
