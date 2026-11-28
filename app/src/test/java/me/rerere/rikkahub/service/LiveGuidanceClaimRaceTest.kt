package me.rerere.rikkahub.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.rikkahub.data.model.ArchivedLiveGuidance
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.ConversationConfig
import me.rerere.rikkahub.data.model.LiveGuidance
import me.rerere.rikkahub.data.model.LiveGuidanceDelivery
import me.rerere.rikkahub.data.model.LiveGuidanceMode
import me.rerere.rikkahub.data.model.finishLiveGuidance
import me.rerere.rikkahub.data.model.liveGuidanceEntries
import me.rerere.rikkahub.data.model.withLiveGuidanceEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LiveGuidanceClaimRaceTest {
    private class Fixture(scope: CoroutineScope) {
        val run = Uuid.random()
        val id = Uuid.random()
        val archive = ArchivedLiveGuidance(
            id = Uuid.random(), runId = Uuid.random(), content = "earlier",
            mode = LiveGuidanceMode.PERSISTENT, nodeId = Uuid.random(), messageId = Uuid.random(),
        )
        val first = LiveGuidance(content = "first", runId = run)
        val second = LiveGuidance(content = "second", runId = run)
        var persisted = Conversation.ofId(id).copy(config = ConversationConfig(liveGuidanceArchive = listOf(archive))
            .withLiveGuidanceEntries(listOf(first, second)))
        val session = ConversationSession(id, Conversation.ofId(id), scope, FakeConversationStore(), {})
        val entries get() = session.state.value.config?.liveGuidanceEntries().orEmpty()

        suspend fun start() {
            session.initialize(load = { persisted })
            session.startGenerationRun(run)
        }

        fun persist(current: Conversation): Boolean {
            persisted = persisted.copy(config = current.config)
            return true
        }

        suspend fun finish(runId: Uuid) {
            session.finishGenerationRun(runId)
            session.updateMetadataChecked(transform = { latest ->
                latest.copy(config = latest.config?.finishLiveGuidance(runId) { archive.nodeId to archive.messageId })
            }, persist = ::persist)
        }

        suspend fun start(runId: Uuid, origin: GenerationRunOrigin) =
            session.startGenerationRun(runId, origin, ::persist)
    }

    @Test
    fun `cancel wins against request already waiting for snapshot`() = swipeWins(defer = false)

    @Test
    fun `defer wins against request already waiting for snapshot`() = swipeWins(defer = true)

    private fun swipeWins(defer: Boolean): Unit = runBlocking {
        withTimeout(5_000) {
            val f = Fixture(this)
            f.start()
            val releaseWrite = CompletableDeferred<Unit>()
            val swipe = async(start = CoroutineStart.UNDISPATCHED) {
                f.session.swipeLiveGuidance(f.first.actionTarget, defer) { current ->
                    releaseWrite.await()
                    f.persist(current)
                }
            }
            val snapshot = async(start = CoroutineStart.UNDISPATCHED) { f.session.claimLiveGuidanceRequest(f.run) }
            assertFalse(snapshot.isCompleted)
            releaseWrite.complete(Unit)
            assertTrue(swipe.await())
            assertEquals(listOf(f.second.id), snapshot.await().map { it.id })
            assertEquals(listOf(f.archive), f.persisted.config?.liveGuidanceArchive)
            val saved = f.entries.firstOrNull { it.id == f.first.id }
            if (defer) {
                assertEquals(f.first.copy(runId = null), saved)
            } else {
                assertNull(saved)
            }
            assertEquals(f.second, f.entries.last())
            f.finish(f.run)
            if (defer) {
                for (origin in GenerationRunOrigin.entries.filterNot { it.consumesNextMessageGuidance() }) {
                    val unrelated = Uuid.random()
                    f.start(unrelated, origin)
                    assertTrue(f.session.claimLiveGuidanceRequest(unrelated).isEmpty())
                    f.finish(unrelated)
                }
                val actualUserTurn = Uuid.random()
                f.start(actualUserTurn, GenerationRunOrigin.USER_MESSAGE)
                assertFalse(f.session.swipeLiveGuidance(f.first.actionTarget, true, ::persistUnexpected))
                assertEquals(
                    listOf(f.first.id),
                    f.session.claimLiveGuidanceRequest(actualUserTurn).map { it.id },
                )
            }
        }
    }

    @Test
    fun `swipe queued during user turn binding cannot rearm the card into the same run`() = runBlocking {
        withTimeout(5_000) {
            val f = Fixture(this)
            f.start()
            assertTrue(f.session.swipeLiveGuidance(f.first.actionTarget, true, f::persist))
            f.finish(f.run)

            val nextRun = Uuid.random()
            val releaseBinding = CompletableDeferred<Unit>()
            val start = async(start = CoroutineStart.UNDISPATCHED) {
                f.session.startGenerationRun(nextRun, GenerationRunOrigin.USER_MESSAGE) {
                    releaseBinding.await()
                    f.persist(it)
                }
            }
            val target = f.entries.first().actionTarget
            assertEquals(nextRun, target.runId)
            val defer = async(start = CoroutineStart.UNDISPATCHED) {
                f.session.swipeLiveGuidance(target, true, f::persist)
            }
            val snapshot = async(start = CoroutineStart.UNDISPATCHED) { f.session.claimLiveGuidanceRequest(nextRun) }
            assertFalse(defer.isCompleted)
            releaseBinding.complete(Unit)
            start.await()
            assertTrue(defer.await())
            assertTrue(snapshot.await().isEmpty())
            assertEquals(f.first.copy(runId = null), f.entries.first())
        }
    }

    @Test
    fun `claim wins against pending cancel and defer without acknowledging either id`() = runBlocking {
        val f = Fixture(this)
        f.start()
        assertEquals(emptySet<Uuid>(), f.session.liveGuidanceClaims.value?.ids)
        val snapshot = f.session.claimLiveGuidanceRequest(f.run)
        assertEquals(listOf(f.first.id, f.second.id), snapshot.map { it.id })
        assertEquals(snapshot.map { it.id }.toSet(), f.session.liveGuidanceClaims.value?.ids)
        for (defer in listOf(false, true)) {
            assertFalse(f.session.swipeLiveGuidance(f.first.actionTarget, defer, ::persistUnexpected))
        }
        assertEquals(listOf(f.first, f.second), f.entries)
        assertEquals(snapshot.map { it.id }, f.session.claimLiveGuidanceRequest(f.run).map { it.id })
        f.finish(f.run) // Same finish path for cancellation/no provider output.
        assertNull(f.session.liveGuidanceClaims.value)
        assertEquals(listOf(f.first.finish(f.run), f.second.finish(f.run)), f.entries)
        assertEquals(listOf(f.archive), f.persisted.config?.liveGuidanceArchive)
        // Archives are projected as history, never claimed or acknowledged as active directions.
        assertEquals(listOf(f.archive), f.session.archivedLiveGuidanceSnapshot())
        assertFalse(f.session.swipeLiveGuidance(f.first.actionTarget, true, ::persistUnexpected))
        assertTrue(f.session.swipeLiveGuidance(f.entries.first().actionTarget, true, f::persist))
    }

    @Test
    fun `failed swipe persistence preserves text but cannot disclose it`() = runBlocking {
        for (defer in listOf(false, true)) {
            for (throws in listOf(false, true)) {
                val f = Fixture(this)
                f.start()
                val result = runCatching {
                    f.session.swipeLiveGuidance(f.first.actionTarget, defer) {
                        if (throws) error("metadata write failed") else false
                    }
                }
                if (throws) assertTrue(result.exceptionOrNull() is IllegalStateException)
                else assertFalse(result.getOrThrow())
                assertEquals(f.first.finish(f.run), f.entries.first())
                assertEquals(listOf(f.second.id), f.session.claimLiveGuidanceRequest(f.run).map { it.id })
                assertEquals(listOf(f.first, f.second), f.persisted.config?.liveGuidanceEntries())
            }
        }
    }

    @Test
    fun `cancellation during persisted defer does not expose or lose the note`() = runBlocking {
        withTimeout(5_000) {
            val f = Fixture(this)
            f.start()
            val releaseWrite = CompletableDeferred<Unit>()
            val swipe = async(start = CoroutineStart.UNDISPATCHED) {
                f.session.swipeLiveGuidance(f.first.actionTarget, defer = true) { current ->
                    releaseWrite.await()
                    f.persist(current)
                }
            }
            val snapshot = async(start = CoroutineStart.UNDISPATCHED) { f.session.claimLiveGuidanceRequest(f.run) }
            swipe.cancel()
            releaseWrite.complete(Unit)
            swipe.join()
            assertTrue(swipe.isCancelled)
            assertEquals(f.first.copy(runId = null), f.entries.first())
            assertEquals(f.entries, f.persisted.config?.liveGuidanceEntries())
            assertEquals(listOf(f.second.id), snapshot.await().map { it.id })
        }
    }

    @Test
    fun `a claim does not block swiping a note added after snapshot or leak into another session`() = runBlocking {
        val f = Fixture(this)
        f.start()
        f.session.claimLiveGuidanceRequest(f.run)
        val later = LiveGuidance(content = "added after snapshot", runId = f.run)
        f.session.updateMetadataChecked(transform = { latest ->
            val config = checkNotNull(latest.config)
            latest.copy(config = config.withLiveGuidanceEntries(config.liveGuidanceEntries() + later))
        }, persist = f::persist)
        assertTrue(f.session.swipeLiveGuidance(later.actionTarget, false, f::persist))

        val other = Uuid.random()
        val otherConversation = f.persisted.copy(id = other)
        val otherSession = ConversationSession(other, Conversation.ofId(other), this, FakeConversationStore(), {})
        otherSession.initialize(load = { otherConversation })
        otherSession.startGenerationRun(f.run)
        assertTrue(otherSession.swipeLiveGuidance(f.first.actionTarget, false) { true })
        assertEquals(listOf(f.second.id), otherSession.claimLiveGuidanceRequest(f.run).map { it.id })
        assertEquals(LiveGuidanceDelivery.WAITING, f.entries.first().delivery)
    }

    private fun persistUnexpected(@Suppress("UNUSED_PARAMETER") current: Conversation): Boolean =
        error("A rejected swipe must not persist")
}
