package me.rerere.rikkahub.data.ai.subagent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.uuid.Uuid

class SubAgentResultStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `results come back in arrival order`() = runTest {
        val store = SubAgentResultStore(temp.root, Dispatchers.IO)
        val parent = Uuid.random()

        store.append(parent, "第一份")
        store.append(parent, "第二份")

        assertEquals(listOf("第一份", "第二份"), store.peek(parent))
    }

    @Test
    fun `drop removes the delivered prefix and keeps later arrivals`() = runTest {
        val store = SubAgentResultStore(temp.root, Dispatchers.IO)
        val parent = Uuid.random()
        store.append(parent, "已投递")
        store.append(parent, "投递期间到达")

        store.drop(parent, 1)

        assertEquals(listOf("投递期间到达"), store.peek(parent))
    }

    @Test
    fun `dropping everything deletes the file`() = runTest {
        val store = SubAgentResultStore(temp.root, Dispatchers.IO)
        val parent = Uuid.random()
        store.append(parent, "唯一一份")

        store.drop(parent, 1)

        assertTrue(store.peek(parent).isEmpty())
        assertFalse(File(temp.root, "$parent.json").exists())
    }

    @Test
    fun `listAll only reports queues that still have results`() = runTest {
        val store = SubAgentResultStore(temp.root, Dispatchers.IO)
        val kept = Uuid.random()
        val emptied = Uuid.random()
        store.append(kept, "还在")
        store.append(emptied, "已投递")
        store.drop(emptied, 1)

        assertEquals(setOf(kept), store.listAll().toSet())
    }

    @Test
    fun `peek does not drop anything`() = runTest {
        val store = SubAgentResultStore(temp.root, Dispatchers.IO)
        val parent = Uuid.random()
        store.append(parent, "同一份")

        assertEquals(listOf("同一份"), store.peek(parent))
        assertEquals(listOf("同一份"), store.peek(parent))
    }
}
