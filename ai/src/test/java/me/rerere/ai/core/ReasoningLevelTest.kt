package me.rerere.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningLevelTest {

    @Test
    fun `minimal passes the effort through and falls back to a 512 token budget`() {
        assertEquals("minimal", ReasoningLevel.MINIMAL.effort)
        assertEquals(512, ReasoningLevel.MINIMAL.budgetTokens)
        assertTrue(ReasoningLevel.MINIMAL.isEnabled)
    }

    @Test
    fun `budget tokens map back to the nearest level`() {
        assertEquals(ReasoningLevel.AUTO, ReasoningLevel.fromBudgetTokens(null))
        assertEquals(ReasoningLevel.OFF, ReasoningLevel.fromBudgetTokens(0))
        assertEquals(ReasoningLevel.MINIMAL, ReasoningLevel.fromBudgetTokens(512))
        assertEquals(ReasoningLevel.LOW, ReasoningLevel.fromBudgetTokens(1_000))
        assertEquals(ReasoningLevel.MEDIUM, ReasoningLevel.fromBudgetTokens(2_000))
    }
}
