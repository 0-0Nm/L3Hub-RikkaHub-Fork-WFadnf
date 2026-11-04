package me.rerere.rikkahub.data.ai.subagent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val waiter = waits.register(child)

        assertTrue(waits.complete(child, "结果"))
        assertEquals("结果", waiter.await())
        assertFalse(waits.complete(child, "又一条"))
    }

    @Test
    fun `an unregistered waiter no longer consumes the result`() {
        val waits = SubAgentWaits()
        val child = Uuid.random()
        val waiter = waits.register(child)

        waits.unregister(child, waiter)

        assertFalse(waits.complete(child, "结果"))
        assertFalse(waiter.isCompleted)
    }

    @Test
    fun `a result for another sub-agent does not wake this waiter`() {
        val waits = SubAgentWaits()
        val child = Uuid.random()
        val waiter = waits.register(child)

        assertFalse(waits.complete(Uuid.random(), "别人的结果"))
        assertFalse(waiter.isCompleted)
    }

    @Test
    fun `a second waiter replaces the first instead of leaking it`() = runTest {
        val waits = SubAgentWaits()
        val child = Uuid.random()
        val first = waits.register(child)
        val second = waits.register(child)

        assertTrue(first.isCompleted)
        assertTrue(waits.complete(child, "结果"))
        assertEquals("结果", second.await())
    }
}
