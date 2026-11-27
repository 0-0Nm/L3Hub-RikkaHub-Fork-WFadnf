package me.rerere.rikkahub.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.ConversationConfig
import me.rerere.rikkahub.data.model.LiveGuidance
import me.rerere.rikkahub.data.model.LiveGuidanceDelivery
import me.rerere.rikkahub.data.model.LiveGuidanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class LiveGuidanceSessionTest {
    @Test
    fun `metadata persistence reopens final mode and delivery without rewriting messages`() = runBlocking {
        val id = Uuid.random()
        val run = Uuid.random()
        val original = LiveGuidance(content = "persist me", runId = run).acknowledgeDelivery(run)
        var persisted = Conversation.ofId(id).copy(config = ConversationConfig(liveGuidance = original))
        val originalNodes = persisted.messageNodes
        val session = ConversationSession(id, Conversation.ofId(id), this, FakeConversationStore(), {})
        session.initialize(load = { persisted })
        session.startGenerationRun(run)

        session.updateMetadata(
            transform = { latest ->
                val guidance = latest.config?.liveGuidance?.withPersistence(true, session.generationRunId)
                latest.copy(config = latest.config?.copy(liveGuidance = guidance))
            },
            persist = { persisted = persisted.copy(config = it.config) },
        )
        session.finishGenerationRun(run)
        session.updateMetadata(
            transform = { latest ->
                latest.copy(config = latest.config?.copy(liveGuidance = latest.config?.liveGuidance?.finish(run)))
            },
            persist = { persisted = persisted.copy(config = it.config) },
        )

        val reopened = ConversationSession(id, Conversation.ofId(id), this, FakeConversationStore(), {})
        reopened.initialize(load = { persisted })
        val restored = reopened.state.value.config?.liveGuidance
        assertEquals(LiveGuidanceMode.PERSISTENT, restored?.mode)
        assertEquals(LiveGuidanceDelivery.DELIVERED, restored?.delivery)
        assertEquals(true, restored?.finishLocked)
        assertEquals(originalNodes, reopened.state.value.messageNodes)
    }

    @Test
    fun `stale completion cannot clear or lock a successor run`() = runBlocking {
        val id = Uuid.random()
        val oldRun = Uuid.random()
        val newRun = Uuid.random()
        val session = ConversationSession(id, Conversation.ofId(id), this, FakeConversationStore(), {})
        session.initialize(load = { null }, create = { Conversation.ofId(id) })

        session.startGenerationRun(oldRun)
        val checkpoint = UIMessage.user("claimed")
        session.updateLiveGuidanceCheckpoint(oldRun, checkpoint)
        session.finishGenerationRun(newRun)
        assertEquals(oldRun, session.generationRunId)
        assertEquals(checkpoint, session.liveGuidanceRequestMessage(oldRun))
        session.finishGenerationRun(oldRun)
        assertNull(session.generationRunId)
        assertNull(session.liveGuidanceRequestMessage(oldRun))

        session.startGenerationRun(newRun)
        assertEquals(newRun, session.generationRunId)
        session.finishGenerationRun(newRun)
    }

    @Test
    fun `checkpoint keeps ordered ids and never leaks across runs`() = runBlocking {
        val id = Uuid.random()
        val session = ConversationSession(id, Conversation.ofId(id), this, FakeConversationStore(), {})
        session.initialize(load = { null }, create = { Conversation.ofId(id) })
        val oldRun = Uuid.random()
        val newRun = Uuid.random()
        val first = UIMessage.user("first").copy(id = Uuid.random())
        val second = UIMessage.user("second").copy(id = Uuid.random())
        session.startGenerationRun(oldRun)

        session.updateLiveGuidanceCheckpoints(oldRun, listOf(first, second, first))

        assertEquals(listOf(first, second), session.liveGuidanceRequestMessages(oldRun))
        assertEquals(emptyList<UIMessage>(), session.liveGuidanceRequestMessages(newRun))
        session.finishGenerationRun(oldRun)
        assertEquals(emptyList<UIMessage>(), session.liveGuidanceRequestMessages(oldRun))
    }

    @Test
    fun `duplicate and stale provider callbacks cannot change delivery twice`() {
        val oldRun = Uuid.random()
        val newRun = Uuid.random()
        val original = LiveGuidance(content = "once", runId = oldRun)
        val delivered = original.acknowledgeDelivery(oldRun)

        assertEquals(delivered, delivered.acknowledgeDelivery(oldRun))
        assertEquals(delivered, delivered.acknowledgeDelivery(newRun))
        assertEquals(oldRun, delivered.deliveredRunId)
    }

    @Test
    fun `finish and mode update serialize through session metadata lock`() = runBlocking {
        val id = Uuid.random()
        val run = Uuid.random()
        val item = LiveGuidance(content = "race", runId = run).acknowledgeDelivery(run)
        val session = ConversationSession(id, Conversation.ofId(id), this, FakeConversationStore(), {})
        session.initialize(load = {
            Conversation.ofId(id).copy(config = ConversationConfig(liveGuidance = item))
        })
        session.startGenerationRun(run)

        val modePersisting = CompletableDeferred<Unit>()
        val releaseMode = CompletableDeferred<Unit>()
        val modeWrite = launch {
            session.updateMetadata(
                transform = { latest ->
                    latest.copy(
                        config = latest.config?.copy(
                            liveGuidance = latest.config?.liveGuidance?.withPersistence(
                                true,
                                session.generationRunId,
                            ),
                        ),
                    )
                },
                persist = {
                    modePersisting.complete(Unit)
                    releaseMode.await()
                },
            )
        }
        modePersisting.await()
        val finishWrite = launch {
            session.finishGenerationRun(run)
            session.updateMetadata(
                transform = { latest ->
                    latest.copy(config = latest.config?.copy(liveGuidance = latest.config?.liveGuidance?.finish(run)))
                },
                persist = {},
            )
        }
        releaseMode.complete(Unit)
        modeWrite.join()
        finishWrite.join()

        val finished = session.state.value.config?.liveGuidance
        assertEquals(LiveGuidanceMode.PERSISTENT, finished?.mode)
        assertEquals(true, finished?.finishLocked)
    }
}
