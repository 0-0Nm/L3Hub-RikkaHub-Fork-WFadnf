package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.TodoStatus
import me.rerere.rikkahub.data.repository.ConversationRepository
import org.koin.java.KoinJavaComponent.getKoin
import kotlin.uuid.Uuid

const val TODO_WRITE_TOOL_NAME = "todo_write"
const val TODO_UPDATE_TOOL_NAME = "todo_update"

private const val MAX_TODO_ITEMS = 50
private const val MAX_TODO_TEXT_CHARS = 300

/**
 * 会话级任务清单工具。
 *
 * 清单存在 conversation 表里而不是消息历史里, 上下文压缩不会把它删掉,
 * 每次请求还会重新拼进系统提示词, 所以长任务不会因为压缩而丢掉目标。
 */
fun createTodoTools(
    conversationRepository: ConversationRepository,
    conversationId: Uuid,
): List<Tool> = listOf(
    Tool(
        name = TODO_WRITE_TOOL_NAME,
        description = """
            Create or replace the task list for this conversation. Use it for multi-step work so progress survives context compression.
            Pass the full list every time: it replaces the previous one. Keep exactly one item in_progress, mark items completed as soon as they are done,
            and keep items short and concrete (one line each). The list is shown to the user as a pinned card in the chat.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("todos", buildJsonObject {
                        put("type", "array")
                        put("description", "Full task list, in execution order")
                        put("items", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {
                                put("content", buildJsonObject {
                                    put("type", "string")
                                    put("description", "One-line task description")
                                })
                                // 已存在的项把 id 原样带回来, 后续才能用 todo_update 单条更新
                                put("id", buildJsonObject {
                                    put("type", "string")
                                    put("description", "Omit for a new item; pass back the id shown in the current list to keep it")
                                })
                                put("status", buildJsonObject {
                                    put("type", "string")
                                    put("enum", buildJsonArray {
                                        add(kotlinx.serialization.json.JsonPrimitive("pending"))
                                        add(kotlinx.serialization.json.JsonPrimitive("in_progress"))
                                        add(kotlinx.serialization.json.JsonPrimitive("completed"))
                                    })
                                })
                            })
                            put("required", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("content")) })
                        })
                    })
                },
                required = listOf("todos"),
            )
        },
        needsApproval = { false },
        execute = {
            val params = it.jsonObject
            val raw = params["todos"] as? JsonArray
                ?: error("todos is required")
            var dropped = 0
            val parsed = raw.mapNotNull { element ->
                val obj = runCatching { element.jsonObject }.getOrNull()
                val content = obj?.get("content")?.jsonPrimitive?.contentOrNull
                    ?.trim()
                    ?.take(MAX_TODO_TEXT_CHARS)
                    ?.takeIf { text -> text.isNotEmpty() }
                // obj 为 null 时 content 必然也为 null, 但编译器不会替我们推，两个都判
                if (obj == null || content == null) {
                    dropped++
                    return@mapNotNull null
                }
                // 已经带 id 的原样保留，没有的补一个：后续 todo_update 靠它定位单条
                val id = obj.get("id")?.jsonPrimitive?.contentOrNull
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: Uuid.random().toString().take(8)
                TodoItem(content = content, status = obj.parseStatus(), id = id)
            }
            // 截断和丢弃都不能静默：模型以为自己写了 60 条，实际只存了 50 条，
            // 后面它就会按一张不存在的清单干活
            val truncated = parsed.size > MAX_TODO_ITEMS
            val items = parsed.take(MAX_TODO_ITEMS)

            writeTodos(conversationRepository, conversationId, items)

            val completed = items.count { it.status == TodoStatus.COMPLETED }
            val inProgress = items.count { it.status == TodoStatus.IN_PROGRESS }
            listOf(
                UIMessagePart.Text(
                    buildString {
                        appendLine(
                            "total=${items.size} completed=$completed " +
                                "inProgress=${items.firstOrNull { it.status == TodoStatus.IN_PROGRESS }?.content ?: "none"}"
                        )
                        if (dropped > 0) {
                            appendLine("warning: $dropped item(s) had no usable `content` and were dropped")
                        }
                        if (truncated) {
                            appendLine("warning: only the first $MAX_TODO_ITEMS items were kept, split the work or trim the list")
                        }
                        if (inProgress > 1) {
                            appendLine("warning: $inProgress items are in_progress, keep exactly one")
                        }
                        append("Current todo list:\n")
                        append(formatTodoLines(items))
                    }
                )
            )
        },
    ),
    createTodoUpdateTool(conversationRepository, conversationId),
)

/**
 * 列表写入口。会话在内存里也有一份状态，只写库的话聊天页要等重新加载才更新；
 * 只改内存的话归属节点要等下一次整条保存才落库。两者都交给 ChatService 处理。
 */
private suspend fun writeTodos(
    conversationRepository: ConversationRepository,
    conversationId: Uuid,
    items: List<TodoItem>,
) {
    val chatService = runCatching { getKoin().get<me.rerere.rikkahub.service.ChatService>() }.getOrNull()
    if (chatService != null) {
        chatService.updateTodos(conversationId, items)
    } else {
        conversationRepository.updateTodos(conversationId, items)
    }
}

/**
 * 单条更新。
 *
 * 让模型「翻一个状态」时不必把整张表复述一遍：既省 token，也少一类手滑改坏别的项。
 * id 由 todo_write 分配并显示在清单里。
 */
private fun createTodoUpdateTool(
    conversationRepository: ConversationRepository,
    conversationId: Uuid,
): Tool = Tool(
    name = TODO_UPDATE_TOOL_NAME,
    description = """
        Change the status of one task without rewriting the whole list.
        Use the id shown next to each item (for example `3f2a9c1d`). Prefer this over todo_write once the list exists.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("id", buildJsonObject {
                    put("type", "string")
                    put("description", "Item id from the current task list")
                })
                put("status", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("pending"))
                        add(kotlinx.serialization.json.JsonPrimitive("in_progress"))
                        add(kotlinx.serialization.json.JsonPrimitive("completed"))
                    })
                })
            },
            required = listOf("id", "status"),
        )
    },
    needsApproval = { false },
    execute = {
        val params = it.jsonObject
        val id = params["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        require(id.isNotEmpty()) { "id is required" }
        val status = params.parseStatus()
        val current = readTodos(conversationRepository, conversationId)
            ?: error("This conversation is not loaded; call todo_write with the full list instead")
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) {
            error(
                "Unknown todo id '$id'. Current ids: ${current.joinToString { it.id ?: "-" }}. " +
                    "If the list was written before ids existed, call todo_write once to assign them."
            )
        }
        val updated = current.toMutableList().apply { this[index] = this[index].copy(status = status) }
        writeTodos(conversationRepository, conversationId, updated)

        val completed = updated.count { it.status == TodoStatus.COMPLETED }
        val inProgress = updated.count { it.status == TodoStatus.IN_PROGRESS }
        listOf(
            UIMessagePart.Text(
                buildString {
                    appendLine("updated id=$id status=${status.name.lowercase()}")
                    appendLine("total=${updated.size} completed=$completed inProgress=$inProgress")
                    if (inProgress > 1) appendLine("warning: $inProgress items are in_progress, keep exactly one")
                    append("Current todo list:\n")
                    append(formatTodoLines(updated))
                }
            )
        )
    },
)

/** 当前清单：优先内存（会话已加载），否则读库 */
private suspend fun readTodos(
    conversationRepository: ConversationRepository,
    conversationId: Uuid,
): List<TodoItem>? {
    runCatching { getKoin().get<me.rerere.rikkahub.service.ChatService>() }
        .getOrNull()
        ?.currentTodos(conversationId)
        ?.let { return it }
    return runCatching { conversationRepository.getConversationById(conversationId)?.todos }.getOrNull()
}

/** 模型的输出不一定守规矩, 常见的同义写法都收下 */
internal fun JsonObject.parseStatus(): TodoStatus {
    val value = this["status"]?.jsonPrimitive?.contentOrNull
        ?.trim()
        ?.lowercase()
        ?.replace('-', '_')
        ?.replace(' ', '_')
        ?: return TodoStatus.PENDING
    return when (value) {
        "in_progress", "inprogress", "running", "doing", "active", "current" -> TodoStatus.IN_PROGRESS
        "completed", "complete", "done", "finished", "resolved" -> TodoStatus.COMPLETED
        else -> TodoStatus.PENDING
    }
}

/** 拼进系统提示词的任务清单, 用纯文本以免模型误以为是工具结果 */
fun buildTodoPrompt(todos: List<TodoItem>): String = buildString {
    appendLine("## Current task list")
    append(formatTodoLines(todos))
    append("Keep this list up to date with todo_write as you make progress. It survives context compression; the user sees it as a pinned card.")
}

/** 清单的纯文本形式：一行一条，带状态标记 */
private fun formatTodoLines(todos: List<TodoItem>): String = buildString {
    todos.forEach { item ->
        val mark = when (item.status) {
            TodoStatus.COMPLETED -> "[x]"
            TodoStatus.IN_PROGRESS -> "[>]"
            TodoStatus.PENDING -> "[ ]"
        }
        val id = item.id?.let { "$it " }.orEmpty()
        appendLine("- $mark $id${item.content}")
    }
}
