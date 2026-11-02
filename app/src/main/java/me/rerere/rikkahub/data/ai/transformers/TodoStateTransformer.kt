package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.TODO_WRITE_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.buildTodoPrompt

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
    ): List<UIMessage> {
        val todos = ctx.todos
        if (todos.isEmpty()) return messages

        val lastUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
        if (lastUserIndex < 0) return messages

        val alreadyInContext = messages.drop(lastUserIndex + 1).any { message ->
            message.parts.any { part ->
                part is UIMessagePart.Tool && part.toolName == TODO_WRITE_TOOL_NAME
            }
        }
        if (alreadyInContext) return messages

        val state = UIMessage.user(buildTodoPrompt(todos)).copy(isSynthetic = true)
        return messages + state
    }
}
