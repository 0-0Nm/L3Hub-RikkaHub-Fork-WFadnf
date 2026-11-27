package me.rerere.rikkahub.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveGuidanceNextUserTurnTest {
    @Test
    fun `only a real user turn consumes armed guidance`() {
        assertTrue(GenerationRunOrigin.USER_MESSAGE.consumesNextMessageGuidance())
        assertFalse(GenerationRunOrigin.NOTICE.consumesNextMessageGuidance())
        assertFalse(GenerationRunOrigin.REGENERATION.consumesNextMessageGuidance())
        assertFalse(GenerationRunOrigin.LIVE_GUIDANCE_RETRY.consumesNextMessageGuidance())
        assertFalse(GenerationRunOrigin.TOOL_APPROVAL.consumesNextMessageGuidance())
    }
}
