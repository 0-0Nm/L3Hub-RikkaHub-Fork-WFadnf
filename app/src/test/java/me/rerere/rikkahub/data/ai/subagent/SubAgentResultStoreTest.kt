package me.rerere.rikkahub.data.ai.subagent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `remove takes away only the result the tool already returned`() = runTest {
        val store = SubAgentResultStore(temp.root, Dispatchers.IO)
        val parent = Uuid.random()
        store.append(parent, "被工具取走")
        store.append(parent, "还在排队")

        store.remove(parent, "被工具取走")

        assertEquals(listOf("还在排队"), store.peek(parent))
    }

    @Test
    fun `removing an unknown result changes nothing`() = runTest {
        val store = SubAgentResultStore(temp.root, Dispatchers.IO)
        val parent = Uuid.random()
        store.append(parent, "唯一一份")

        store.remove(parent, "没有这一份")

        assertEquals(listOf("唯一一份"), store.peek(parent))
    }

    @Test
    fun `an emptied queue deletes its file`() = runTest {
        val store = SubAgentResultStore(temp.root, Dispatchers.IO)
        val parent = Uuid.random()
        store.append(parent, "唯一一份")

        store.remove(parent, "唯一一份")

        assertFalse(File(temp.root, "$parent.json").exists())
    }
}
