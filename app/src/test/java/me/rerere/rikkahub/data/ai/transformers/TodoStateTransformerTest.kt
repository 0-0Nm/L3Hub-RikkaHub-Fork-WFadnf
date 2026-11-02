package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.TodoStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoStateTransformerTest {
    private val todos = listOf(
        TodoItem("读代码", TodoStatus.COMPLETED, id = "t1"),
        TodoItem("改实现", TodoStatus.IN_PROGRESS, id = "t2"),
    )

    @Test
    fun `empty list changes nothing`() {
        val messages = listOf(UIMessage.user("hi"))
        assertEquals(messages, messages.withTodoState(emptyList()))
    }

    @Test
    fun `user as last message merges into it instead of adding another user`() {
        val messages = listOf(UIMessage.user("hi"))

        val result = messages.withTodoState(todos)

        assertEquals(1, result.size)
        val text = result.single().parts.filterIsInstance<UIMessagePart.Text>().joinToString { it.text }
        assertTrue(text.contains("读代码"))
        assertTrue(text.contains("改实现"))
    }

    @Test
    fun `assistant as last message appends one synthetic user message`() {
        val messages = listOf(
            UIMessage.user("hi"),
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("thinking"))),
        )

        val result = messages.withTodoState(todos)

        assertEquals(3, result.size)
        assertEquals(MessageRole.USER, result.last().role)
        val text = result.last().parts.filterIsInstance<UIMessagePart.Text>().joinToString { it.text }
        assertTrue(text.contains("改实现"))
    }

    @Test
    fun `a todo tool result in this turn suppresses the injection`() {
        val messages = listOf(
            UIMessage.user("hi"),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Tool(toolCallId = "1", toolName = "todo_update", input = "{}")),
            ),
        )

        assertEquals(messages, messages.withTodoState(todos))
    }

    @Test
    fun `no user message means no injection`() {
        val messages = listOf(
            UIMessage(role = MessageRole.SYSTEM, parts = listOf(UIMessagePart.Text("sys"))),
        )

        assertEquals(messages, messages.withTodoState(todos))
    }
}
