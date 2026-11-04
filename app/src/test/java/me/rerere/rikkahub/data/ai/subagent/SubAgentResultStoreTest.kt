package me.rerere.rikkahub.data.ai.subagent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        val child = Uuid.random()

        store.append(parent, child, "第一份")
        store.append(parent, child, "第二份")

        assertEquals(listOf("第一份", "第二份"), store.peek(parent))
    }

    @Test
    fun `drop removes the delivered prefix and keeps later arrivals`() = runTest {
        val store = SubAgentResultStore(temp.root, Dispatchers.IO)
        val parent = Uuid.random()
        val child = Uuid.random()
        store.append(parent, child, "已投递")
        store.append(parent, child, "投递期间到达")

        store.drop(parent, 1)

        assertEquals(listOf("投递期间到达"), store.peek(parent))
    }

    @Test
    fun `dropping everything deletes the file`() = runTest {
        val store = SubAgentResultStore(temp.root, Dispatchers.IO)
        val parent = Uuid.random()
        store.append(parent, Uuid.random(), "唯一一份")

        store.drop(parent, 1)

        assertTrue(store.peek(parent).isEmpty())
        assertFalse(File(temp.root, "$parent.json").exists())
    }

    @Test
    fun `findFor only reports the child that produced the result`() = runTest {
        val store = SubAgentResultStore(temp.root, Dispatchers.IO)
        val parent = Uuid.random()
        val first = Uuid.random()
        val second = Uuid.random()
        store.append(parent, first, "第一个子代理的结果")
        store.append(parent, second, "第二个子代理的结果")

        assertEquals("第一个子代理的结果", store.findFor(parent, first))
        assertEquals("第二个子代理的结果", store.findFor(parent, second))
        assertNull(store.findFor(parent, Uuid.random()))
    }

    @Test
    fun `findFor does not take the result out of the queue`() = runTest {
        val store = SubAgentResultStore(temp.root, Dispatchers.IO)
        val parent = Uuid.random()
        val child = Uuid.random()
        store.append(parent, child, "还排着")

        assertEquals("还排着", store.findFor(parent, child))
        assertEquals(listOf("还排着"), store.peek(parent))
    }

    @Test
    fun `an old file without conversation ids is still readable`() = runTest {
        val store = SubAgentResultStore(temp.root, Dispatchers.IO)
        val parent = Uuid.random()
        File(temp.root, "$parent.json").writeText("""{"results":["老文件里的结果"]}""")

        assertEquals(listOf("老文件里的结果"), store.peek(parent))
        assertNull(store.findFor(parent, Uuid.random()))
    }

    @Test
    fun `listAll only reports queues that still have results`() = runTest {
        val store = SubAgentResultStore(temp.root, Dispatchers.IO)
        val kept = Uuid.random()
        val emptied = Uuid.random()
        store.append(kept, Uuid.random(), "还在")
        store.append(emptied, Uuid.random(), "已投递")
        store.drop(emptied, 1)

        assertEquals(setOf(kept), store.listAll().toSet())
    }
}
