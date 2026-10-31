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
            val items = raw.mapNotNull { element ->
                val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                val content = obj["content"]?.jsonPrimitive?.contentOrNull
                    ?.trim()
                    ?.take(MAX_TODO_TEXT_CHARS)
                    ?.takeIf { text -> text.isNotEmpty() }
                    ?: return@mapNotNull null
                TodoItem(content = content, status = obj.parseStatus())
            }.take(MAX_TODO_ITEMS)

            conversationRepository.updateTodos(conversationId, items)
            // 会话在内存里还有一份状态，不同步的话聊天页的卡片要等重新加载才更新
            runCatching {
                getKoin().get<me.rerere.rikkahub.service.ChatService>()
                    .syncTodos(conversationId, items)
            }

            val completed = items.count { it.status == TodoStatus.COMPLETED }
            val current = items.firstOrNull { it.status == TodoStatus.IN_PROGRESS }
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("total", items.size)
                        put("completed", completed)
                        put("inProgress", current?.content)
                        put("items", buildJsonArray {
                            items.forEach { item ->
                                add(buildJsonObject {
                                    put("content", item.content)
                                    put("status", item.status.name.lowercase())
                                })
                            }
                        })
                        put("hint", "Keep the list updated with todo_write as you work.")
                    }.toString() +
                        "\n\nCurrent todo list:\n" +
                        formatTodoLines(items)
                )
            )
        },
    )
)

/** 模型的输出不一定守规矩, 常见的同义写法都收下 */
private fun JsonObject.parseStatus(): TodoStatus {
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
        appendLine("- $mark ${item.content}")
    }
}
