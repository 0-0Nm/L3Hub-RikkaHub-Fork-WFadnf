package me.rerere.rikkahub.data.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.uuid.Uuid

class DraftStoreTest {
    private lateinit var root: File
    private lateinit var scope: CoroutineScope

    private val conversationId = Uuid.random()
    private val nodeId = Uuid.random()
    private val messageId = Uuid.random()

    @Before
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "draft-store-${Uuid.random()}")
            .apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        root.deleteRecursively()
    }

    private fun assistantMessage(text: String): UIMessage = UIMessage(
        id = messageId,
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text(text)),
    )

    private fun draftOf(parts: List<UIMessagePart>, updatedAt: Long = System.currentTimeMillis()): Draft =
        Draft(
            conversationId = conversationId,
            nodeId = nodeId,
            messageId = messageId,
            parts = parts,
            updatedAt = updatedAt,
        )

    /** 等到草稿里已经写下的正文长度变成 [textLength]；草稿写入是异步的，不能直接断言 */
    private suspend fun awaitDraft(store: DraftStore, textLength: Int): Draft = withTimeout(5_000) {
        var draft = store.load(conversationId)
        while (draft == null || draft.parts.textLength() != textLength) {
            delay(10)
            draft = store.load(conversationId)
        }
        requireNotNull(draft)
    }

    @Test
    fun draftRoundTripsThroughTheFile() = runBlocking {
        val store = DraftStore(root = root, scope = scope, minIntervalMs = 0)
        val parts = listOf(
            UIMessagePart.Text("你好，世界"),
            UIMessagePart.Image(url = "file:///files/upload/a.png"),
        )
        store.record(conversationId, nodeId, UIMessage(id = messageId, role = MessageRole.ASSISTANT, parts = parts))

        val draft = awaitDraft(store, textLength = "你好，世界".length)

        assertEquals(conversationId, draft.conversationId)
        assertEquals(nodeId, draft.nodeId)
        assertEquals(messageId, draft.messageId)
        assertEquals(parts, draft.parts)
        assertTrue(draft.updatedAt > 0L)
        assertEquals(listOf(conversationId), store.listAll())
    }

    @Test
    fun throttledWriterKeepsTheLastFrame() = runBlocking {
        // 节流窗口 300ms：连发三帧，最后落盘的必须是最后那一帧，中间帧不会成为最终内容
        val store = DraftStore(root = root, scope = scope, minIntervalMs = 300, minBytes = Int.MAX_VALUE)
        store.record(conversationId, nodeId, assistantMessage("第一帧"))
        store.record(conversationId, nodeId, assistantMessage("第二帧"))
        store.record(conversationId, nodeId, assistantMessage("第三帧，最长的那一帧"))

        val draft = awaitDraft(store, textLength = "第三帧，最长的那一帧".length)

        assertEquals(listOf<UIMessagePart>(UIMessagePart.Text("第三帧，最长的那一帧")), draft.parts)
    }

    @Test
    fun clearDropsQueuedFramesSoNothingComesBack() = runBlocking {
        val store = DraftStore(root = root, scope = scope, minIntervalMs = 300, minBytes = Int.MAX_VALUE)
        store.record(conversationId, nodeId, assistantMessage("写到一半"))
        // 整轮结束：草稿删掉，队列里还没落盘的那一帧也不能再写回来
        store.clear(conversationId)

        delay(700)

        assertNull(store.load(conversationId))
        assertEquals(emptyList<Uuid>(), store.listAll())
        assertEquals(0, root.listFiles().orEmpty().size)
    }

    @Test
    fun restoreOverwritesTheNodeWhenTheDraftIsLonger() {
        val conversation = Conversation.ofId(
            id = conversationId,
            messages = listOf(MessageNode(id = nodeId, messages = listOf(assistantMessage("写到一半")))),
        )
        val draft = draftOf(listOf(UIMessagePart.Text("写到一半，后面还有更多内容")))

        val restore = decideDraftRestore(draft, conversation)
        assertTrue(restore is DraftRestore.ReplaceParts)

        val restored = conversation.applyDraftRestore(restore)
        val node = restored.messageNodes.single()
        assertEquals(nodeId, node.id)
        // 只换 parts：消息 id、角色、分支选择都不动
        assertEquals(1, node.messages.size)
        assertEquals(0, node.selectIndex)
        assertEquals(messageId, node.currentMessage.id)
        assertEquals(MessageRole.ASSISTANT, node.currentMessage.role)
        assertEquals(draft.parts, node.currentMessage.parts)
    }

    @Test
    fun restoreKeepsTheStoredMessageWhenTheDraftIsShorter() {
        val conversation = Conversation.ofId(
            id = conversationId,
            messages = listOf(MessageNode(id = nodeId, messages = listOf(assistantMessage("库里已经是完整的一轮回答")))),
        )
        val draft = draftOf(listOf(UIMessagePart.Text("短")))

        assertEquals(DraftRestore.Discard, decideDraftRestore(draft, conversation))
        assertEquals(conversation, conversation.applyDraftRestore(DraftRestore.Discard))
    }

    @Test
    fun restoreAppendsAnAssistantMessageWhenTheNodeIsGone() {
        val conversation = Conversation.ofId(
            id = conversationId,
            messages = listOf(
                MessageNode(
                    id = Uuid.random(),
                    messages = listOf(
                        UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("问题"))),
                    ),
                ),
            ),
        )
        val draft = draftOf(listOf(UIMessagePart.Text("还没落过库的回复")))

        val restore = decideDraftRestore(draft, conversation)
        assertTrue(restore is DraftRestore.AppendMessage)

        val restored = conversation.applyDraftRestore(restore)
        assertEquals(2, restored.messageNodes.size)
        val appended = restored.messageNodes.last()
        assertEquals(nodeId, appended.id)
        assertEquals(MessageRole.ASSISTANT, appended.currentMessage.role)
        assertEquals(messageId, appended.currentMessage.id)
        assertEquals(draft.parts, appended.currentMessage.parts)
    }

    @Test
    fun restoreDiscardsADraftOlderThanTheConversation() {
        val conversation = Conversation.ofId(
            id = conversationId,
            messages = listOf(MessageNode(id = nodeId, messages = listOf(assistantMessage("已经完整落库的回答")))),
        )
        // 草稿比会话里最后一条消息还旧：这一轮后来的内容已经落库了
        val draft = draftOf(listOf(UIMessagePart.Text("很长很长很长很长的草稿")), updatedAt = 1_000L)

        assertEquals(DraftRestore.Discard, decideDraftRestore(draft, conversation))
    }
}
