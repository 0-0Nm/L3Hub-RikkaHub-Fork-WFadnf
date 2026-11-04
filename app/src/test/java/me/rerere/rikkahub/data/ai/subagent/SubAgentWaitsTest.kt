package me.rerere.rikkahub.data.ai.subagent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class SubAgentWaitsTest {
    @Test
    fun `a result nobody waits for is left to the store`() {
        val waits = SubAgentWaits()
        assertFalse(waits.complete(Uuid.random(), "结果"))
    }

    @Test
    fun `a registered waiter receives the round result once`() = runTest {
        val waits = SubAgentWaits()
        val child = Uuid.random()
        val waiter = checkNotNull(waits.register(child))

        assertTrue(waits.complete(child, "结果"))
        assertEquals("结果", waiter.await())
        assertFalse(waits.complete(child, "又一条"))
    }

    @Test
    fun `an unregistered waiter no longer consumes the result`() {
        val waits = SubAgentWaits()
        val child = Uuid.random()
        val waiter = checkNotNull(waits.register(child))

        waits.unregister(child, waiter)

        assertFalse(waits.complete(child, "结果"))
        assertFalse(waiter.isCompleted)
    }

    @Test
    fun `a result for another sub-agent does not wake this waiter`() {
        val waits = SubAgentWaits()
        val child = Uuid.random()
        val waiter = checkNotNull(waits.register(child))

        assertFalse(waits.complete(Uuid.random(), "别人的结果"))
        assertFalse(waiter.isCompleted)
    }

    @Test
    fun `a second waiter is rejected and the first one keeps the result`() = runTest {
        val waits = SubAgentWaits()
        val child = Uuid.random()
        val first = checkNotNull(waits.register(child))

        assertNull(waits.register(child))

        assertTrue(waits.complete(child, "结果"))
        assertEquals("结果", first.await())
    }
}
