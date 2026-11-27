package me.rerere.rikkahub.service

import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Guidance
import me.rerere.rikkahub.data.model.GuidanceMode
import me.rerere.rikkahub.data.model.LiveGuidance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LiveGuidanceInboxSeparationTest {
    @Test
    fun `live lifecycle does not consume ordinary messages or legacy guidance`() {
        val inbox = ConversationInbox()
        inbox.enqueue(listOf(UIMessagePart.Text("ordinary queued message")))
        inbox.addGuidance(Guidance(content = "legacy preset", mode = GuidanceMode.REPEAT))
        val before = inbox.state.value
        val run = Uuid.random()
        val live = LiveGuidance(content = "separate live route", runId = run)

        live.acknowledgeDelivery(run).withPersistence(true, run).finish(run)

        assertEquals(before, inbox.state.value)
        assertEquals("ordinary queued message", inbox.state.value.messages.single().parts.single().let {
            (it as UIMessagePart.Text).text
        })
        assertEquals(GuidanceMode.REPEAT, inbox.state.value.guidances.single().mode)
        assertEquals(1, inbox.state.value.items.size)
    }
}
