package me.rerere.rikkahub.data.ai.subagent

import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class SubAgentCandidatesTest {
    private val dispatcher = Assistant(id = Uuid.random(), name = "main")
    private val childA = Assistant(id = Uuid.random(), name = "Alpha", isSubAgent = true)
    private val childB = Assistant(id = Uuid.random(), name = "Beta", isSubAgent = true)
    private val plainAssistant = Assistant(id = Uuid.random(), name = "Plain")

    private fun candidatesOf(dispatcher: Assistant): List<Assistant> =
        dispatchableSubAgents(
            dispatcher = dispatcher,
            assistants = listOf(this.dispatcher, childA, childB, plainAssistant),
        )

    private fun candidates(vararg allowed: Uuid): List<Assistant> =
        candidatesOf(dispatcher.copy(allowedSubAgentIds = allowed.toSet()))

    @Test
    fun `empty allowlist keeps every sub-agent except the dispatcher`() {
        assertEquals(listOf(childA, childB), candidates())
    }

    @Test
    fun `allowlist keeps only the listed sub-agents`() {
        assertEquals(listOf(childB), candidates(childB.id))
    }

    @Test
    fun `an assistant that is not a sub-agent never becomes a target`() {
        assertTrue(candidates(plainAssistant.id).isEmpty())
    }

    @Test
    fun `the dispatcher cannot be its own target even when listed`() {
        assertTrue(candidates(dispatcher.id, childA.id).none { it.id == dispatcher.id })
        assertEquals(listOf(childA), candidates(dispatcher.id, childA.id))
    }

    @Test
    fun `ids that no longer exist are ignored`() {
        assertTrue(candidates(Uuid.random()).isEmpty())
    }

    @Test
    fun `a sub-agent uses its own allowlist when dispatching the next generation`() {
        val restricted = childA.copy(allowedSubAgentIds = setOf(childB.id))
        assertEquals(listOf(childB), candidatesOf(restricted))
    }

    @Test
    fun `target matching accepts the name case-insensitively and the id`() {
        assertTrue(childA.matchesSubAgentName("alpha"))
        assertTrue(childA.matchesSubAgentName("  ALPHA  "))
        assertTrue(childA.matchesSubAgentName(childA.id.toString()))
        assertFalse(childA.matchesSubAgentName(""))
        assertFalse(childA.matchesSubAgentName("Gamma"))
    }
}
