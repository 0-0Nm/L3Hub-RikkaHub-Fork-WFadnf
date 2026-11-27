package me.rerere.rikkahub.service

import kotlinx.coroutines.runBlocking
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.ConversationConfig
import me.rerere.rikkahub.data.model.LiveGuidance
import me.rerere.rikkahub.data.model.finishLiveGuidance
import me.rerere.rikkahub.data.model.liveGuidanceAnchor
import me.rerere.rikkahub.data.model.recoverLiveGuidance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/** Session-level archival: same config-column write path ChatService uses at run finish. */
class LiveGuidanceArchiveSessionTest {
    private suspend fun ConversationSession.finishRun(
        runId: Uuid,
        persist: suspend (Conversation) -> Boolean,
    ): Boolean {
        val replyId = replyMessageId.value
        return updateMetadataChecked(
            transform = { latest ->
                val config = latest.config ?: return@updateMetadataChecked latest
                val updated = config.finishLiveGuidance(runId) { latest.liveGuidanceAnchor(replyId) }
                if (updated == config) latest else latest.copy(config = updated)
            },
            persist = persist,
        )
    }

    private fun conversation(id: Uuid, run: Uuid, text: String = "partial reply"): Conversation =
        Conversation.ofId(id)
            .updateCurrentMessages(listOf(UIMessage.user("q"), UIMessage.assistant(text)))
            .copy(
                config = ConversationConfig(
                    liveGuidance = LiveGuidance(content = "note", runId = run).acknowledgeDelivery(run)
                )
            )

    @Test
    fun `completion archives through the metadata column without rewriting message nodes`() = runBlocking {
        val id = Uuid.random()
        val run = Uuid.random()
        var persisted = conversation(id, run)
        val session = ConversationSession(id, Conversation.ofId(id), this, FakeConversationStore(), {})
        session.initialize(load = { persisted })
        session.replyMessageId.value = persisted.currentMessages[1].id

        val retained = session.finishRun(run) { candidate ->
            persisted = persisted.copy(config = candidate.config)
            true
        }

        assertTrue(retained)
        assertNull(session.state.value.config?.liveGuidance)
        assertEquals(1, session.state.value.config?.liveGuidanceArchive?.size)
        assertEquals(persisted.messageNodes, session.state.value.messageNodes)
        val entry = persisted.config?.liveGuidanceArchive?.single()!!
        assertEquals(persisted.messageNodes[1].id, entry.nodeId)

        val reopened = ConversationSession(id, Conversation.ofId(id), this, FakeConversationStore(), {})
        reopened.initialize(load = { persisted.recoverLiveGuidance() })
        assertNull(reopened.state.value.config?.liveGuidance)
        assertEquals(listOf(entry), reopened.state.value.config?.liveGuidanceArchive)
    }

    @Test
    fun `an error after provider output still archives the note and keeps the partial reply`() = runBlocking {
        val id = Uuid.random()
        val run = Uuid.random()
        var persisted = conversation(id, run, text = "partial before the failure")
        val session = ConversationSession(id, Conversation.ofId(id), this, FakeConversationStore(), {})
        session.initialize(load = { persisted })
        session.replyMessageId.value = persisted.currentMessages[1].id

        session.finishRun(run) { candidate ->
            persisted = persisted.copy(config = candidate.config)
            true
        }

        val entry = persisted.config?.liveGuidanceArchive?.single()!!
        assertEquals(persisted.currentMessages[1].id, entry.messageId)
        assertEquals("partial before the failure", persisted.currentMessages[1].toText())
    }

    @Test
    fun `failed archival write keeps history intact and a later full save persists the entry`() = runBlocking {
        val id = Uuid.random()
        val run = Uuid.random()
        val history = conversation(id, run)
        val store = FakeConversationStore(history)
        val session = ConversationSession(id, Conversation.ofId(id), this, store, {})
        session.initialize(load = { history })

        val retained = session.finishRun(run) { false }

        assertFalse(retained)
        assertEquals(history.messageNodes, session.state.value.messageNodes)
        assertEquals(1, session.state.value.config?.liveGuidanceArchive?.size)
        session.flush()
        assertEquals(history.messageNodes, store.persisted?.messageNodes)
        assertEquals(1, store.persisted?.config?.liveGuidanceArchive?.size)
        assertNull(store.persisted?.config?.liveGuidance)
    }

    @Test
    fun `two runs archive once each and a stale finish changes nothing`() = runBlocking {
        val id = Uuid.random()
        val firstRun = Uuid.random()
        val secondRun = Uuid.random()
        var persisted = conversation(id, firstRun)
        val session = ConversationSession(id, Conversation.ofId(id), this, FakeConversationStore(), {})
        session.initialize(load = { persisted })
        session.replyMessageId.value = persisted.currentMessages[1].id
        session.finishRun(firstRun) { candidate ->
            persisted = persisted.copy(config = candidate.config)
            true
        }

        val second = LiveGuidance(content = "second", runId = secondRun).acknowledgeDelivery(secondRun)
        session.update { it.copy(config = it.config?.copy(liveGuidance = second)) }
        session.finishRun(secondRun) { candidate ->
            persisted = persisted.copy(config = candidate.config)
            true
        }
        session.finishRun(firstRun) { candidate ->
            persisted = persisted.copy(config = candidate.config)
            true
        }

        assertEquals(listOf("note", "second"), persisted.config?.liveGuidanceArchive?.map { it.content })
        assertEquals(2, persisted.config?.liveGuidanceArchive?.map { it.id }?.distinct()?.size)
        assertNull(persisted.config?.liveGuidance)
    }
}
