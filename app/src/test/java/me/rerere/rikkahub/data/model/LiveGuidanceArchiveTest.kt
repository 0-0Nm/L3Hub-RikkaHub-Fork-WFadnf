package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LiveGuidanceArchiveTest {
    private fun history(id: Uuid = Uuid.random()): Conversation =
        Conversation.ofId(id).updateCurrentMessages(
            listOf(UIMessage.user("question"), UIMessage.assistant("first reply"))
        )

    private fun delivered(run: Uuid, text: String = "focus the patch", persistent: Boolean = false) =
        LiveGuidance(content = text, runId = run)
            .withPersistence(persistent, run)
            .acknowledgeDelivery(run)

    @Test
    fun `delivered note archives after its own reply and the active card is gone`() {
        val run = Uuid.random()
        val conversation = history()
        val reply = conversation.currentMessages[1]
        val item = delivered(run)
        val config = ConversationConfig(liveGuidance = item)

        val finished = config.finishLiveGuidance(run) { conversation.liveGuidanceAnchor(reply.id) }

        assertNull(finished.liveGuidance)
        val entry = finished.liveGuidanceArchive.single()
        assertEquals(item.id, entry.id)
        assertEquals(run, entry.runId)
        assertEquals(item.content, entry.content)
        assertEquals(LiveGuidanceMode.TEMPORARY, entry.mode)
        assertEquals(conversation.messageNodes[1].id, entry.nodeId)
        assertEquals(reply.id, entry.messageId)
        // A repeated completion callback cannot duplicate or resurrect anything.
        assertSame(finished, finished.finishLiveGuidance(run) { conversation.liveGuidanceAnchor(reply.id) })
        assertEquals(
            finished.liveGuidanceArchive,
            finished.liveGuidanceArchive.upsertArchivedGuidance(entry).also { assertEquals(1, it.size) },
        )
    }

    @Test
    fun `missed note stays a distinct active card with original text and next message recovery`() {
        val run = Uuid.random()
        val conversation = history()
        val waiting = LiveGuidance(content = "never reached the provider", runId = run)

        val finished = ConversationConfig(liveGuidance = waiting)
            .finishLiveGuidance(run) { conversation.liveGuidanceAnchor(null) }

        assertTrue(finished.liveGuidanceArchive.isEmpty())
        val missed = finished.liveGuidance!!
        assertEquals(LiveGuidanceDelivery.NOT_USED, missed.delivery)
        assertEquals(true, missed.finishLocked)
        assertEquals(waiting.content, missed.content)

        val nextRun = Uuid.random()
        val armed = missed.armForNextRun()
        assertEquals(LiveGuidanceDelivery.WAITING, armed.delivery)
        assertNull(armed.runId)
        assertEquals(nextRun, armed.beginRun(nextRun).runId)
        assertEquals(armed.beginRun(nextRun).content, waiting.content)
    }

    @Test
    fun `multiple runs keep separate anchored entries and later runs never resurrect a banner`() {
        val firstRun = Uuid.random()
        val secondRun = Uuid.random()
        val conversation = history().updateCurrentMessages(
            listOf(UIMessage.user("question"), UIMessage.assistant("first"), UIMessage.assistant("second"))
        )
        val first = ConversationConfig(liveGuidance = delivered(firstRun, "first note", persistent = true))
            .finishLiveGuidance(firstRun) { conversation.liveGuidanceAnchor(conversation.currentMessages[1].id) }
        val second = first.copy(liveGuidance = delivered(secondRun, "second note"))
            .finishLiveGuidance(secondRun) { conversation.liveGuidanceAnchor(conversation.currentMessages[2].id) }

        assertNull(second.liveGuidance)
        assertEquals(listOf("first note", "second note"), second.liveGuidanceArchive.map { it.content })
        assertEquals(LiveGuidanceMode.PERSISTENT, second.liveGuidanceArchive[0].mode)
        val byNode = second.archivedGuidanceByNode()
        assertEquals(setOf(conversation.messageNodes[1].id, conversation.messageNodes[2].id), byNode.keys)
        assertEquals("first note", byNode.getValue(conversation.messageNodes[1].id).single().content)
        assertEquals("second note", byNode.getValue(conversation.messageNodes[2].id).single().content)

        val thirdRun = Uuid.random()
        assertSame(second, second.finishLiveGuidance(thirdRun) { conversation.liveGuidanceAnchor(null) })
        assertSame(second, conversation.copy(config = second).recoverLiveGuidance().config)
    }

    @Test
    fun `old delivered config migrates without touching history or losing text`() {
        val run = Uuid.random()
        val conversation = history()
        val old = conversation.copy(
            config = ConversationConfig(liveGuidance = delivered(run, "legacy note", persistent = true).finish(run))
        )

        val recovered = old.recoverLiveGuidance()

        assertNull(recovered.config?.liveGuidance)
        val entry = recovered.config?.liveGuidanceArchive?.single()!!
        assertEquals("legacy note", entry.content)
        assertEquals(LiveGuidanceMode.PERSISTENT, entry.mode)
        assertEquals(conversation.messageNodes[1].id, entry.nodeId)
        assertEquals(old.messageNodes, recovered.messageNodes)
        // Recovery is idempotent and older unfinished runs still degrade to NOT_USED, not history.
        assertSame(recovered, recovered.recoverLiveGuidance())
        val unfinished = conversation.copy(
            config = ConversationConfig(liveGuidance = LiveGuidance(content = "still waiting", runId = run))
        ).recoverLiveGuidance()
        assertEquals(LiveGuidanceDelivery.NOT_USED, unfinished.config?.liveGuidance?.delivery)
        assertEquals(true, unfinished.config?.liveGuidanceArchive?.isEmpty())
        // No assistant reply means no anchor: keep the note instead of dropping it.
        val empty = Conversation.ofId(Uuid.random()).copy(
            config = ConversationConfig(liveGuidance = delivered(run, "unanchored").finish(run))
        )
        assertEquals("unanchored", empty.recoverLiveGuidance().config?.liveGuidance?.content)
    }

    @Test
    fun `archive survives config json and old backups stay readable`() {
        val run = Uuid.random()
        val entry = delivered(run).finish(run).archiveRecord(Uuid.random(), Uuid.random())!!
        val config = ConversationConfig(liveGuidanceArchive = listOf(entry))

        val reopened = JsonInstant.decodeFromString<ConversationConfig>(JsonInstant.encodeToString(config))
        assertEquals(config, reopened)
        val legacy = JsonInstant.decodeFromString<ConversationConfig>(
            """{"chatModelId":null,"reasoningLevel":"auto","enableWebSearch":false,"builtInSearch":false}"""
        )
        assertEquals(ConversationConfig(), legacy)
        assertTrue(legacy.liveGuidanceArchive.isEmpty())
    }

    @Test
    fun `fork keeps steering history inside the source conversation`() {
        val run = Uuid.random()
        val conversation = history()
        val source = conversation.copy(
            config = ConversationConfig(liveGuidanceArchive = listOf(delivered(run).finish(run).archiveRecord(
                conversation.messageNodes[1].id,
                conversation.currentMessages[1].id,
            )!!)).withLiveGuidanceEntries(listOf(delivered(run, "active one"), delivered(run, "active two")))
        )

        val forked = source.fork(conversation.currentMessages[1].id, emptySet()) { it }!!

        assertTrue(forked.config?.liveGuidanceArchive?.isEmpty() == true)
        assertNull(forked.config?.liveGuidance)
        assertTrue(forked.config?.liveGuidances?.isEmpty() == true)
        assertTrue(forked.config?.liveGuidanceEntries()?.isEmpty() == true)
        assertEquals(2, source.config?.liveGuidanceEntries()?.size)
        assertEquals(1, source.config?.liveGuidanceArchive?.size)
    }
}
