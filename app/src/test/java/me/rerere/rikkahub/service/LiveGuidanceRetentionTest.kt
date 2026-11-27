package me.rerere.rikkahub.service

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.ConversationConfig
import me.rerere.rikkahub.data.model.LiveGuidance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LiveGuidanceRetentionTest {
    @Test
    fun `only user message and explicit guidance retry consume next-message guidance`() {
        assertTrue(GenerationRunOrigin.USER_MESSAGE.consumesNextMessageGuidance())
        assertTrue(GenerationRunOrigin.LIVE_GUIDANCE_RETRY.consumesNextMessageGuidance())
        assertFalse(GenerationRunOrigin.NOTICE.consumesNextMessageGuidance())
        assertFalse(GenerationRunOrigin.REGENERATION.consumesNextMessageGuidance())
        assertFalse(GenerationRunOrigin.TOOL_APPROVAL.consumesNextMessageGuidance())
    }

    @Test
    fun `missing-row metadata update rejects but retains text and history for a later full save`() = runBlocking {
        val id = Uuid.random()
        val history = Conversation.ofId(id).updateCurrentMessages(listOf(UIMessage.user("keep history")))
        val store = FakeConversationStore(history)
        val session = ConversationSession(id, Conversation.ofId(id), this, store, {})
        session.initialize(load = { null }, create = { history })

        val retained = session.updateMetadataChecked(
            transform = {
                it.copy(config = ConversationConfig(liveGuidance = LiveGuidance(content = "keep draft")))
            },
            persist = { false },
        )

        assertFalse(retained)
        assertEquals("keep draft", session.state.value.config?.liveGuidance?.content)
        assertEquals(history.messageNodes, session.state.value.messageNodes)
        session.flush()
        assertEquals("keep draft", store.persisted?.config?.liveGuidance?.content)
        assertEquals(history.messageNodes, store.persisted?.messageNodes)
    }

    @Test
    fun `retry reservation cannot cancel active work or be acquired twice`() = runBlocking {
        val id = Uuid.random()
        val session = ConversationSession(id, Conversation.ofId(id), this, FakeConversationStore(), {})
        session.initialize(load = { Conversation.ofId(id) })
        val active = Job()
        session.setJob(active)

        assertFalse(session.reserveGenerationSlot())
        assertTrue(active.isActive)
        active.complete()
        session.setJob(null)

        assertTrue(session.reserveGenerationSlot())
        assertFalse(session.reserveGenerationSlot())
        assertFalse(session.canStartNextTurn)
        session.releaseGenerationSlot()
        assertTrue(session.canStartNextTurn)
    }

    @Test
    fun `reserved retry slot is consumed only when its job is installed`() = runBlocking {
        val id = Uuid.random()
        val session = ConversationSession(id, Conversation.ofId(id), this, FakeConversationStore(), {})
        session.initialize(load = { Conversation.ofId(id) })
        assertTrue(session.reserveGenerationSlot())

        val retry = launch { }
        session.setJob(retry)
        retry.join()

        assertFalse(session.generationSlotReserved)
        assertFalse(session.hasGenerationWork)
    }
}
