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

    private fun store() = SubAgentResultStore(temp.root, Dispatchers.IO)

    @Test
    fun `results come back in arrival order`() = runTest {
        val store = store()
        val parent = Uuid.random()
        val child = Uuid.random()

        store.append(parent, child, "第一份")
        store.append(parent, child, "第二份")

        assertEquals(listOf("第一份", "第二份"), store.peekDeliverable(parent).texts)
    }

    @Test
    fun `commit removes the delivered batch and keeps later arrivals`() = runTest {
        val store = store()
        val parent = Uuid.random()
        val child = Uuid.random()
        store.append(parent, child, "已投递")

        val batch = store.peekDeliverable(parent)
        store.append(parent, child, "投递期间到达")
        store.commit(parent, batch)

        assertEquals(listOf("投递期间到达"), store.peekDeliverable(parent).texts)
    }

    @Test
    fun `committing everything deletes the file`() = runTest {
        val store = store()
        val parent = Uuid.random()
        store.append(parent, Uuid.random(), "唯一一份")

        store.commit(parent, store.peekDeliverable(parent))

        assertTrue(store.peekDeliverable(parent).texts.isEmpty())
        assertFalse(File(temp.root, "$parent.json").exists())
    }

    // ---- 账本：异常中止抵掉下一次，回传之后归零 ----

    @Test
    fun `an abort holds back the next normal ending`() = runTest {
        val store = store()
        val parent = Uuid.random()
        val child = Uuid.random()
        store.append(parent, child, "A")

        // 异常中止一次：欠账压过排队的一份，这一轮先不打扰父会话
        store.recordAbort(parent, child)
        assertTrue(store.peekDeliverable(parent).texts.isEmpty())

        // 下一轮正常结束：两段一起送
        store.append(parent, child, "B")
        assertEquals(listOf("A", "B"), store.peekDeliverable(parent).texts)
    }

    @Test
    fun `delivering settles the ledger so the next round shows up`() = runTest {
        val store = store()
        val parent = Uuid.random()
        val child = Uuid.random()
        store.append(parent, child, "A")
        store.recordAbort(parent, child)
        store.append(parent, child, "B")
        store.commit(parent, store.peekDeliverable(parent))

        store.append(parent, child, "C")

        // 上一次投递把欠账一起结清了，C 单独一轮就该送
        assertEquals(listOf("C"), store.peekDeliverable(parent).texts)
    }

    @Test
    fun `a manual restart changes nothing and the next ending repays the debt`() = runTest {
        val store = store()
        val parent = Uuid.random()
        val child = Uuid.random()
        store.append(parent, child, "停之前的那一份")
        store.recordAbort(parent, child)
        assertTrue(store.peekDeliverable(parent).texts.isEmpty())

        // 用户手动重新拉起不改账；这一轮正常结尾，两段一起送，欠账跟着结清
        store.append(parent, child, "重新拉起之后的那一份")
        assertEquals(
            listOf("停之前的那一份", "重新拉起之后的那一份"),
            store.peekDeliverable(parent).texts,
        )
        store.commit(parent, store.peekDeliverable(parent))

        store.append(parent, child, "再下一轮")
        assertEquals(listOf("再下一轮"), store.peekDeliverable(parent).texts)
    }

    @Test
    fun `an abort only holds back its own child`() = runTest {
        val store = store()
        val parent = Uuid.random()
        val held = Uuid.random()
        val other = Uuid.random()
        store.append(parent, held, "被欠账压着的")
        store.recordAbort(parent, held)
        store.append(parent, other, "别人的")

        assertEquals(listOf("别人的"), store.peekDeliverable(parent).texts)

        store.commit(parent, store.peekDeliverable(parent))

        // 另一条子会话送完就清了，被压着的那份还在队里
        assertTrue(store.peekDeliverable(parent).texts.isEmpty())

        // 它再跑完一轮：账转正，两段一起送
        store.append(parent, held, "下一轮")
        assertEquals(listOf("被欠账压着的", "下一轮"), store.peekDeliverable(parent).texts)
    }

    @Test
    fun `the debt survives a restart`() = runTest {
        val parent = Uuid.random()
        val child = Uuid.random()
        val first = store()
        first.append(parent, child, "中止之后的那一份")
        first.recordAbort(parent, child)

        val reopened = store()
        assertTrue(reopened.peekDeliverable(parent).texts.isEmpty())

        reopened.append(parent, child, "再下一轮")
        assertEquals(listOf("中止之后的那一份", "再下一轮"), reopened.peekDeliverable(parent).texts)
    }

    // ---- await_sub_agent 取走 ----

    @Test
    fun `take hands over the oldest result and stops notifying it`() = runTest {
        val store = store()
        val parent = Uuid.random()
        val child = Uuid.random()
        store.append(parent, child, "第一轮")
        store.append(parent, child, "第二轮")

        assertEquals("第一轮", store.take(parent, child))

        assertEquals(listOf("第二轮"), store.peekDeliverable(parent).texts)
        assertEquals("第二轮", store.take(parent, child))
        assertNull(store.take(parent, child))
    }

    @Test
    fun `take only touches the child it was asked about`() = runTest {
        val store = store()
        val parent = Uuid.random()
        val first = Uuid.random()
        val second = Uuid.random()
        store.append(parent, first, "第一个子代理的结果")
        store.append(parent, second, "第二个子代理的结果")

        assertEquals("第一个子代理的结果", store.take(parent, first))
        assertEquals("第二个子代理的结果", store.take(parent, second))
        assertNull(store.take(parent, Uuid.random()))
    }

    @Test
    fun `taking a result does not clear the debt`() = runTest {
        val store = store()
        val parent = Uuid.random()
        val child = Uuid.random()
        store.append(parent, child, "被欠账压着的一份")
        store.recordAbort(parent, child)

        // await 把这一份取走了（+1 之后当场 -1），但欠账还在：下一轮仍然不自动回传
        assertEquals("被欠账压着的一份", store.take(parent, child))

        store.append(parent, child, "下一轮")
        assertTrue(store.peekDeliverable(parent).texts.isEmpty())
    }

    // ---- 队列本身 ----

    @Test
    fun `an old file without conversation ids is still readable`() = runTest {
        val store = store()
        val parent = Uuid.random()
        File(temp.root, "$parent.json").writeText("""{"results":["老文件里的结果"]}""")

        assertEquals(listOf("老文件里的结果"), store.peekDeliverable(parent).texts)
        assertNull(store.take(parent, Uuid.random()))
    }

    @Test
    fun `listAll only reports queues that still have results`() = runTest {
        val store = store()
        val kept = Uuid.random()
        val emptied = Uuid.random()
        store.append(kept, Uuid.random(), "还在")
        store.append(emptied, Uuid.random(), "已投递")
        store.commit(emptied, store.peekDeliverable(emptied))

        assertEquals(setOf(kept), store.listAll().toSet())
    }

    @Test
    fun `appending to an old file keeps ids aligned`() = runTest {
        val store = store()
        val parent = Uuid.random()
        val child = Uuid.random()
        File(temp.root, "$parent.json").writeText("""{"results":["老结果"]}""")

        store.append(parent, child, "新结果")

        assertEquals(listOf("老结果", "新结果"), store.peekDeliverable(parent).texts)
        assertNull(store.take(parent, Uuid.random()))
        assertEquals("新结果", store.take(parent, child))
    }

    @Test
    fun `committing more than queued is harmless`() = runTest {
        val store = store()
        val parent = Uuid.random()
        val child = Uuid.random()
        store.append(parent, child, "唯一一份")

        store.commit(
            parent,
            SubAgentResultBatch(
                texts = emptyList(),
                snapshotSize = 5,
                settledChildren = setOf(child.toString()),
            ),
        )

        assertTrue(store.peekDeliverable(parent).texts.isEmpty())
        assertFalse(File(temp.root, "$parent.json").exists())
    }
}
