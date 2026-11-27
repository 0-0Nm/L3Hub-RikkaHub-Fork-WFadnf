package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * 就地任务清单卡片的主机节点解析。只测纯函数 todoAnchorNode：
 * 「无清单不渲染 / 归属优先 / 归属被裁时退最后一条助手 / 新清单换归属」四条不变量。
 */
class TodoAnchorNodeTest {

    private fun user(text: String) = UIMessage.user(text)
    private fun assistant(text: String) = UIMessage.assistant(text)

    private fun conversation(nodes: List<MessageNode>) =
        Conversation.ofId(Uuid.random()).copy(messageNodes = nodes)

    @Test
    fun `empty list has no anchor so nothing renders`() {
        val c = conversation(listOf(MessageNode.of(assistant("hi"))))
        assertNull(c.todoAnchorNode())
    }

    @Test
    fun `owner node is preferred even if not last`() {
        val owner = MessageNode.of(assistant("plan turn"))
        val later = MessageNode.of(assistant("unrelated later"))
        val c = conversation(listOf(owner, later)).copy(
            todos = listOf(TodoItem(content = "a")),
            todosOwnerNodeId = owner.id,
        )
        assertEquals(owner.id, c.todoAnchorNode()?.id)
    }

    @Test
    fun `falls back to last assistant when owner pruned`() {
        val a1 = MessageNode.of(assistant("first reply"))
        val u = MessageNode.of(user("more"))
        val a2 = MessageNode.of(assistant("latest reply"))
        val c = conversation(listOf(a1, u, a2)).copy(
            todos = listOf(TodoItem(content = "a")),
            todosOwnerNodeId = Uuid.random(), // pruned/unknown owner
        )
        assertEquals(a2.id, c.todoAnchorNode()?.id)
    }

    @Test
    fun `never anchors on a user-only turn`() {
        val a = MessageNode.of(assistant("only reply"))
        val trailingUser = MessageNode.of(user("and then"))
        val c = conversation(listOf(a, trailingUser)).copy(
            todos = listOf(TodoItem(content = "a")),
        )
        // 没有记录归属，退到最后一条助手消息，而不是最后那条 user
        assertEquals(a.id, c.todoAnchorNode()?.id)
    }

    @Test
    fun `new plan re-anchors to its owner so old plan is not current`() {
        val oldOwner = MessageNode.of(assistant("old plan"))
        val newOwner = MessageNode.of(assistant("new plan"))
        val c = conversation(listOf(oldOwner, newOwner)).copy(
            todos = listOf(TodoItem(content = "fresh")),
            todosOwnerNodeId = newOwner.id,
        )
        assertEquals(newOwner.id, c.todoAnchorNode()?.id)
    }

    @Test
    fun `pending or ongoing means current plan and completed does not`() {
        assertEquals(true, listOf(TodoItem(content = "next")).hasActiveTodo())
        assertEquals(true, listOf(TodoItem(content = "doing", status = TodoStatus.IN_PROGRESS)).hasActiveTodo())
        assertEquals(false, listOf(TodoItem(content = "done", status = TodoStatus.COMPLETED)).hasActiveTodo())
        assertEquals(false, listOf(TodoItem(content = "skip", status = TodoStatus.CANCELLED)).hasActiveTodo())
    }
}
