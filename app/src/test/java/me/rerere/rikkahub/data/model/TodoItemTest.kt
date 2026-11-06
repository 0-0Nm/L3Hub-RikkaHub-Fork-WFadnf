package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用户手动编辑清单的那几条纯函数。卡片上的按钮全走这里。 */
class TodoItemTest {

    private fun item(
        content: String,
        status: TodoStatus = TodoStatus.PENDING,
        id: String? = "id-$content",
    ) = TodoItem(content = content, status = status, id = id)

    private val sample = listOf(
        item("read", TodoStatus.COMPLETED),
        item("edit", TodoStatus.IN_PROGRESS),
        item("test"),
    )

    @Test
    fun `tapping a pending item marks it completed`() {
        val next = sample.toggleTodoCompletedAt(2)

        assertEquals(TodoStatus.COMPLETED, next[2].status)
        // 别的项不许被顺手改掉
        assertEquals(sample[0], next[0])
        assertEquals(sample[1], next[1])
    }

    @Test
    fun `tapping a completed item reopens it`() {
        val next = sample.toggleTodoCompletedAt(0)

        assertEquals(TodoStatus.PENDING, next[0].status)
    }

    @Test
    fun `tapping an in progress item marks it completed`() {
        val next = sample.toggleTodoCompletedAt(1)

        assertEquals(TodoStatus.COMPLETED, next[1].status)
    }

    @Test
    fun `tapping a cancelled item reopens it instead of completing it`() {
        val cancelledList = listOf(item("dropped", TodoStatus.CANCELLED))

        val reopened = cancelledList.toggleTodoCompletedAt(0)

        assertEquals(TodoStatus.PENDING, reopened[0].status)
    }

    @Test
    fun `out of range index leaves the list untouched`() {
        assertEquals(sample, sample.toggleTodoCompletedAt(9))
        assertEquals(sample, sample.setTodoStatusAt(-1, TodoStatus.COMPLETED))
        assertEquals(sample, sample.removeTodoAt(9))
    }

    @Test
    fun `marking a new item in progress demotes the previous one`() {
        val next = sample.setTodoStatusAt(2, TodoStatus.IN_PROGRESS)

        assertEquals(TodoStatus.IN_PROGRESS, next[2].status)
        assertEquals(TodoStatus.PENDING, next[1].status)
        // 清单约定同一时刻只有一条进行中
        assertEquals(1, next.count { it.status == TodoStatus.IN_PROGRESS })
    }

    @Test
    fun `changing to a non running status leaves other items alone`() {
        val next = sample.setTodoStatusAt(2, TodoStatus.CANCELLED)

        assertEquals(TodoStatus.CANCELLED, next[2].status)
        assertEquals(TodoStatus.IN_PROGRESS, next[1].status)
    }

    @Test
    fun `setting the same status keeps the list as is`() {
        val next = sample.setTodoStatusAt(1, TodoStatus.IN_PROGRESS)

        assertEquals(sample, next)
    }

    @Test
    fun `cancelling only changes the status and keeps the item`() {
        val next = sample.setTodoStatusAt(0, TodoStatus.CANCELLED)

        assertEquals(3, next.size)
        assertEquals(TodoStatus.CANCELLED, next[0].status)
        assertEquals("read", next[0].content)
    }

    @Test
    fun `removing drops only the tapped item`() {
        val next = sample.removeTodoAt(1)

        assertEquals(listOf("read", "test"), next.map { it.content })
    }

    @Test
    fun `adding trims the text and assigns a fresh id`() {
        val next = sample.addTodo("  write docs  ")

        assertEquals(4, next.size)
        assertEquals("write docs", next[3].content)
        assertEquals(TodoStatus.PENDING, next[3].status)
        val id = next[3].id
        assertTrue(!id.isNullOrBlank())
        assertEquals(8, id?.length)
        // 新 id 不能和已有的撞上
        assertEquals(next.size, next.map { it.id }.toSet().size)
    }

    @Test
    fun `blank text is not added`() {
        assertEquals(sample, sample.addTodo("   "))
        assertEquals(sample, sample.addTodo(""))
    }

    @Test
    fun `adding is rejected once the list is full`() {
        val almostFull = (1 until MAX_TODO_ITEMS).map { item("task$it") }
        assertEquals(MAX_TODO_ITEMS, almostFull.addTodo("last one").size)

        val full = (1..MAX_TODO_ITEMS).map { item("task$it") }
        assertEquals(full, full.addTodo("one more"))
    }

    @Test
    fun `too long text is truncated to the limit`() {
        val next = emptyList<TodoItem>().addTodo("x".repeat(MAX_TODO_TEXT_CHARS + 50))

        assertEquals(MAX_TODO_TEXT_CHARS, next.single().content.length)
    }

    @Test
    fun `manual edit fills missing ids and keeps existing ones`() {
        val legacy = listOf(
            item("old", id = null),
            item("has id", id = "keep"),
        )

        val next = legacy.setTodoStatusAt(0, TodoStatus.COMPLETED)

        assertEquals("keep", next[1].id)
        val filled = next[0].id
        assertTrue(!filled.isNullOrBlank())
        assertEquals(8, filled?.length)
        assertNotEquals("keep", filled)
        // 补一次就固定下来了，再改状态不会换 id
        assertEquals(filled, next.setTodoStatusAt(0, TodoStatus.PENDING)[0].id)
    }
}
