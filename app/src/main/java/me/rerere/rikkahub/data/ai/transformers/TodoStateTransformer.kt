package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.TODO_UPDATE_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.TODO_WRITE_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.buildTodoPrompt
import me.rerere.rikkahub.data.model.TodoItem

/**
 * 任务清单注入。
 *
 * 清单不能放进系统提示词：系统提示词在最前面，改一个字节整段前缀缓存就作废，而清单
 * 每写一次就变一次。
 *
 * 也不能「贴着最后一条 user 消息」插：那一轮它虽然贴着用户，但到了下一轮，上一轮助手
 * 的整段回答就落在它后面，位置一变，这整段都失去缓存。追加到消息列表末尾才对——跨轮时
 * 前缀完整保留，同一个回合内移动的只是本来就要重算的末尾几条。
 *
 * 模型刚调完 todo_write 时跳过：那条工具结果里已经有同一份清单，再贴一遍只是重复。
 * 跳过后它依然能在「用户发了新消息」和「历史被压缩过」这两种时刻看到清单。
 */
object TodoStateTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = messages.withTodoState(ctx.todos)
}

/** 纯函数版本，方便单测；注入规则见 [TodoStateTransformer] 的类注释 */
internal fun List<UIMessage>.withTodoState(todos: List<TodoItem>): List<UIMessage> {
    if (todos.isEmpty()) return this

    val lastUserIndex = indexOfLast { it.role == MessageRole.USER }
    if (lastUserIndex < 0) return this

    // todo_update 也要算：它同样带着一份新鲜清单，而 todos 是本轮开始时的快照，
    // 此时再贴一遍会让请求里同时出现「A 已完成」的工具结果和「A 还没做」的旧清单
    val alreadyInContext = drop(lastUserIndex + 1).any { message ->
        message.parts.any { part ->
            part is UIMessagePart.Tool &&
                (part.toolName == TODO_WRITE_TOOL_NAME || part.toolName == TODO_UPDATE_TOOL_NAME)
        }
    }
    if (alreadyInContext) return this

    val prompt = buildTodoPrompt(todos)
    // 最后一条已经是 user 时不要新增一条：连续两条 user 有的 provider 会合并、有的会报错，
    // 直接并进它的 parts。中途（最后一条是 assistant）才追加一条 synthetic user。
    val last = last()
    return if (last.role == MessageRole.USER) {
        dropLast(1) + last.copy(parts = last.parts + UIMessagePart.Text("\n\n$prompt"))
    } else {
        this + UIMessage.user(prompt).copy(isSynthetic = true)
    }
}
