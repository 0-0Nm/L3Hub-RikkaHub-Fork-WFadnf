package me.rerere.rikkahub.ui.pages.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PullNewChatThresholdTest {
    @Test
    fun `blank and zero keep original distance`() {
        assertEquals(0, parsePullNewChatThresholdInput(""))
        assertEquals(0, parsePullNewChatThresholdInput("   "))
        assertEquals(0, parsePullNewChatThresholdInput("0"))
        assertEquals(300, effectivePullNewChatThresholdDp(0))
    }

    @Test
    fun `positive values have forty eight dp minimum`() {
        assertEquals(48, effectivePullNewChatThresholdDp(1))
        assertEquals(48, effectivePullNewChatThresholdDp(47))
        assertEquals(48, effectivePullNewChatThresholdDp(48))
        assertEquals(49, effectivePullNewChatThresholdDp(49))
        assertEquals(300, effectivePullNewChatThresholdDp(300))
        assertEquals(600, effectivePullNewChatThresholdDp(600))
    }

    @Test
    fun `invalid negative and overflow are rejected not shrunk`() {
        assertNull(parsePullNewChatThresholdInput("-1"))
        assertNull(parsePullNewChatThresholdInput("12dp"))
        assertNull(parsePullNewChatThresholdInput("1.5"))
        assertNull(parsePullNewChatThresholdInput("999999999999999999999"))
        assertEquals(300, effectivePullNewChatThresholdDp(-1))
        assertEquals(300, effectivePullNewChatThresholdDp(Int.MIN_VALUE))
    }

    @Test
    fun `normal valid values survive parsing`() {
        assertEquals(48, parsePullNewChatThresholdInput("48"))
        assertEquals(300, parsePullNewChatThresholdInput("300"))
        assertEquals(1200, parsePullNewChatThresholdInput("1200"))
    }
}
