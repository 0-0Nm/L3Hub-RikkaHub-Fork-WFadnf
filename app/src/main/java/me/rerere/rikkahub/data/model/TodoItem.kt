package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** 清单长度上限。模型写入口和用户手动添加共用一套, 免得界面塞得进去、工具却不认 */
const val MAX_TODO_ITEMS = 50

/** 单条任务文本上限 */
const val MAX_TODO_TEXT_CHARS = 300

/**
 * 会话级任务清单。存在 conversation 表里, 不放进消息历史, 上下文压缩后依然保留。
 */
@Serializable
data class TodoItem(
    val content: String,
    val status: TodoStatus = TodoStatus.PENDING,
    /**
     * 稳定标识，供 todo_update 单条翻状态用。
     * 老数据里没有这个字段（null），读出来照常显示；todo_write 或手动编辑会补上。
     */
    val id: String? = null,
)

@Serializable
enum class TodoStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("in_progress")
    IN_PROGRESS,

    @SerialName("completed")
    COMPLETED,

    /**
     * 用户手动取消的任务。
     * 仍然留在清单里, 好让模型知道「这条别做了」；直接删掉的话它下一轮会当成还没做过再写回来。
     */
    @SerialName("cancelled")
    CANCELLED,
}

/** 手动编辑时新项用的 id，格式和 todo_write 分配的短 id 一致 */
fun newTodoId(): String = Uuid.random().toString().take(8)

/**
 * 老清单里的项可能没有 id（todo_update 之前写的）。
 * 手动编辑时顺手补上，之后模型的 todo_update 才认得出是哪一条；已经有 id 的保持原样。
 */
internal fun List<TodoItem>.ensureTodoIds(): List<TodoItem> =
    map { item -> if (item.id.isNullOrBlank()) item.copy(id = newTodoId()) else item }

/**
 * 点一下标记 = 完成 / 撤回。
 *
 * 已完成和已取消的都退回未开始：这两者都是「不打算现在做」，点一下是撤回那次决定，
 * 而不是把它直接标成做完。未开始和进行中才算完成。
 */
fun List<TodoItem>.toggleTodoCompletedAt(index: Int): List<TodoItem> {
    val item = getOrNull(index) ?: return this
    val next = when (item.status) {
        TodoStatus.COMPLETED, TodoStatus.CANCELLED -> TodoStatus.PENDING
        TodoStatus.PENDING, TodoStatus.IN_PROGRESS -> TodoStatus.COMPLETED
    }
    return setTodoStatusAt(index, next)
}

/**
 * 手动改某一条的状态。
 *
 * 清单一直约定「同一时刻只有一项进行中」，所以标记新的进行中时，原来那一项退回未开始，
 * 否则模型下一轮会看到两条进行中，它自己也拿不准该接着做哪条。
 */
fun List<TodoItem>.setTodoStatusAt(index: Int, status: TodoStatus): List<TodoItem> {
    val item = getOrNull(index) ?: return this
    if (item.status == status) return this
    val next = mapIndexed { i, todo ->
        when {
            i == index -> todo.copy(status = status)
            status == TodoStatus.IN_PROGRESS && todo.status == TodoStatus.IN_PROGRESS ->
                todo.copy(status = TodoStatus.PENDING)

            else -> todo
        }
    }
    return next.ensureTodoIds()
}

/** 移除一条。用户嫌清单太长时用；只想表达「别做这个」用 [setTodoStatusAt] + [TodoStatus.CANCELLED] */
fun List<TodoItem>.removeTodoAt(index: Int): List<TodoItem> {
    if (index !in indices) return this
    return toMutableList().apply { removeAt(index) }.ensureTodoIds()
}

/**
 * 追加一条。空文本和超上限都不改清单，返回原样。
 *
 * 一律以未开始加入：留一个 status 参数的话，调用方传 IN_PROGRESS 就能造出两条进行中，
 * 而「同一时刻只有一项进行中」是这个清单的约定。要标进行中就接着调 [setTodoStatusAt]。
 */
fun List<TodoItem>.addTodo(content: String): List<TodoItem> {
    val text = content.trim().take(MAX_TODO_TEXT_CHARS)
    if (text.isEmpty() || size >= MAX_TODO_ITEMS) return this
    return (this + TodoItem(content = text, status = TodoStatus.PENDING, id = newTodoId())).ensureTodoIds()
}
