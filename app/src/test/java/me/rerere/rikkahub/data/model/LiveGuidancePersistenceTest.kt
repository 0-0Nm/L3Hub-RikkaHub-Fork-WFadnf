package me.rerere.rikkahub.data.model

import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class LiveGuidancePersistenceTest {
    @Test
    fun `old config backup without live guidance remains readable`() {
        val decoded = JsonInstant.decodeFromString<ConversationConfig>(
            """{"chatModelId":null,"reasoningLevel":"auto","enableWebSearch":false,"builtInSearch":false}""",
        )

        assertNull(decoded.liveGuidance)
        assertEquals(ConversationConfig(), decoded)
    }

    @Test
    fun `waiting delivered not used and final mode survive config json reopen`() {
        val run = Uuid.random()
        val waiting = LiveGuidance(content = "keep text", runId = run).withPersistence(true, run)
        val states = listOf(
            waiting,
            waiting.acknowledgeDelivery(run),
            waiting.finish(run),
        )

        states.forEach { state ->
            val encoded = JsonInstant.encodeToString(ConversationConfig(liveGuidance = state))
            val reopened = JsonInstant.decodeFromString<ConversationConfig>(encoded)
            assertEquals(state, reopened.liveGuidance)
        }
        assertEquals(LiveGuidanceDelivery.WAITING, states[0].delivery)
        assertEquals(LiveGuidanceDelivery.DELIVERED, states[1].delivery)
        assertEquals(LiveGuidanceDelivery.NOT_USED, states[2].delivery)
        assertEquals(LiveGuidanceMode.PERSISTENT, states[2].mode)
        assertEquals(true, states[2].finishLocked)
    }

    @Test
    fun `unknown fields in newer backups do not break decoding`() {
        val decoded = JsonInstant.decodeFromString<ConversationConfig>(
            """{"liveGuidance":null,"futureField":{"anything":true}}""",
        )
        assertNull(decoded.liveGuidance)
    }
}
