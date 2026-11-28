package me.rerere.rikkahub.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.ConversationConfig
import me.rerere.rikkahub.data.model.LiveGuidance
import me.rerere.rikkahub.data.model.LiveGuidanceActionTarget
import me.rerere.rikkahub.data.model.LiveGuidanceDelivery
import me.rerere.rikkahub.data.model.isAwaitingToolApproval
import me.rerere.rikkahub.data.model.liveGuidanceEntries
import me.rerere.rikkahub.data.model.withLiveGuidanceEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LiveGuidanceQueueTransferTest {
    private class Fixture(scope: CoroutineScope, missed: Boolean = false) {
        val run = Uuid.random()
        val id = Uuid.random()
        val legacy = LiveGuidance(content = "legacy armed", delivery = LiveGuidanceDelivery.WAITING, runId = null)
        val first = LiveGuidance(content = "first", runId = run).let { if (missed) it.finish(run) else it }
        val second = LiveGuidance(content = "second", runId = run)
        var persisted = Conversation.ofId(id).copy(
            config = ConversationConfig().withLiveGuidanceEntries(listOf(legacy, first, second)),
        )
        val dispatched = mutableListOf<QueuedMessage>()
        var writes = 0
        val session = ConversationSession(
            id, Conversation.ofId(id), scope, FakeConversationStore(), {},
            onGenerationFinished = { _, _, _ -> dispatchCompletedTurn() },
        )
        val entries get() = session.state.value.config?.liveGuidanceEntries().orEmpty()

        suspend fun load() = session.initialize(load = { persisted })

        suspend fun start() {
            load()
            session.startGenerationRun(run)
        }

        fun persist(current: Conversation): Boolean {
            writes++
            persisted = persisted.copy(config = current.config)
            return true
        }

        fun dispatchCompletedTurn() {
            if (canDispatchInbox(
                    hasJob = session.hasGenerationWork,
                    awaitingApproval = session.state.value.isAwaitingToolApproval,
                    queueEnabled = true,
                    hasUserMessage = session.inbox.state.value.messages.isNotEmpty(),
                )
            ) {
                session.inbox.nextMessage()?.let(dispatched::add)
            }
        }

        suspend fun transfer(
            target: LiveGuidanceActionTarget = first.actionTarget,
            admit: () -> Boolean = { true },
            persist: suspend (Conversation) -> Boolean = this::persist,
        ): Boolean = session.transferLiveGuidanceToInbox(
            target = target,
            admit = admit,
            enqueue = { text ->
                session.inbox.enqueue(
                    listOf(UIMessagePart.Text(text)),
                    answer = true,
                    afterCurrent = true,
                    liveGuidanceTransfer = true,
                )
            },
            persist = persist,
        )
    }

    @Test
    fun `finishing the active job dispatches once without more user input`() = runBlocking {
        val f = Fixture(this)
        f.start()
        val active = Job()
        f.session.setJob(active)

        assertTrue(f.transfer())
        assertTrue(active.isActive)
        assertTrue(f.dispatched.isEmpty())
        active.complete()
        withTimeout(5_000) { active.join() }

        assertEquals(listOf("first"), f.dispatched.map { (it.parts.single() as UIMessagePart.Text).text })
        assertTrue(f.dispatched.single().answer)
        assertTrue(f.dispatched.single().afterCurrent)
        assertTrue(f.dispatched.single().liveGuidanceTransfer)
        assertEquals(listOf(f.second.id), f.session.claimLiveGuidanceRequest(f.run).map { it.id })
        assertNull(f.session.inbox.nextMessage())
    }

    @Test
    fun `standalone transfer retains FIFO and is taken exactly once`() = runBlocking {
        val f = Fixture(this)
        f.start()
        f.session.inbox.enqueue(listOf(UIMessagePart.Text("ordinary")))

        assertTrue(f.transfer())
        f.dispatchCompletedTurn()
        f.dispatchCompletedTurn()

        assertEquals(listOf("ordinary", "first"), f.dispatched.map {
            (it.parts.single() as UIMessagePart.Text).text
        })
        assertFalse(f.dispatched[0].liveGuidanceTransfer)
        assertTrue(f.dispatched[1].liveGuidanceTransfer)
        assertEquals(listOf(f.legacy, f.second), f.entries)
        assertEquals(f.entries, f.persisted.config?.liveGuidanceEntries())
        assertNull(f.session.inbox.nextMessage())
    }

    @Test
    fun `idle missed card transfers without consuming legacy armed entries`() = runBlocking {
        val f = Fixture(this, missed = true)
        f.load()

        assertTrue(f.transfer())
        f.dispatchCompletedTurn()
        assertEquals(listOf("first"), f.dispatched.map { (it.parts.single() as UIMessagePart.Text).text })
        assertEquals(f.legacy, f.entries.single { it.id == f.legacy.id })

        val nextRun = Uuid.random()
        f.session.startGenerationRun(nextRun, GenerationRunOrigin.LIVE_GUIDANCE_RETRY, f::persist)
        assertNull(f.entries.single { it.id == f.legacy.id }.runId)
        assertTrue(f.session.claimLiveGuidanceRequest(nextRun).isEmpty())
    }

    @Test
    fun `claimed and stale targets reject without persistence or queue insertion`() = runBlocking {
        val f = Fixture(this)
        f.start()
        f.session.claimLiveGuidanceRequest(f.run)

        assertFalse(f.transfer())
        assertFalse(f.transfer(f.first.copy(delivery = LiveGuidanceDelivery.NOT_USED).actionTarget))
        assertEquals(0, f.writes)
        assertEquals(listOf(f.legacy, f.first, f.second), f.entries)
        assertTrue(f.session.inbox.state.value.messages.isEmpty())
    }

    @Test
    fun `admission rejection preserves text and prevents old-run disclosure`() = runBlocking {
        val f = Fixture(this)
        f.start()

        assertFalse(f.transfer(admit = { false }))

        assertEquals(0, f.writes)
        assertEquals(f.first.finish(f.run), f.entries.single { it.id == f.first.id })
        assertTrue(f.session.inbox.state.value.messages.isEmpty())
        assertEquals(listOf(f.second.id), f.session.claimLiveGuidanceRequest(f.run).map { it.id })
    }

    @Test
    fun `false or throwing persistence retains text and never enqueues`() = runBlocking {
        for (throws in listOf(false, true)) {
            val f = Fixture(this)
            f.start()
            val result = runCatching {
                f.transfer(persist = { if (throws) error("metadata write failed") else false })
            }
            if (throws) assertTrue(result.exceptionOrNull() is IllegalStateException)
            else assertFalse(result.getOrThrow())

            assertEquals(f.first.finish(f.run), f.entries.single { it.id == f.first.id })
            assertTrue(f.session.inbox.state.value.messages.isEmpty())
            assertEquals(listOf(f.second.id), f.session.claimLiveGuidanceRequest(f.run).map { it.id })
            assertEquals(listOf(f.legacy, f.first, f.second), f.persisted.config?.liveGuidanceEntries())
        }
    }

    @Test
    fun `cancellation during persistence completes one transfer and blocks the old claim`() = runBlocking {
        withTimeout(5_000) {
            val f = Fixture(this)
            f.start()
            val releaseWrite = CompletableDeferred<Unit>()
            val transfer = async(start = CoroutineStart.UNDISPATCHED) {
                f.transfer(persist = { current ->
                    releaseWrite.await()
                    f.persist(current)
                })
            }
            val snapshot = async(start = CoroutineStart.UNDISPATCHED) {
                f.session.claimLiveGuidanceRequest(f.run)
            }
            transfer.cancel()
            releaseWrite.complete(Unit)
            transfer.join()

            assertTrue(transfer.isCancelled)
            assertEquals(listOf("first"), f.session.inbox.state.value.messages.map {
                (it.parts.single() as UIMessagePart.Text).text
            })
            assertEquals(listOf(f.legacy, f.second), f.entries)
            assertEquals(f.entries, f.persisted.config?.liveGuidanceEntries())
            assertEquals(listOf(f.second.id), snapshot.await().map { it.id })
        }
    }
}
