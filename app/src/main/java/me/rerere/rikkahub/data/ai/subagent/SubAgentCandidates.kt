package me.rerere.rikkahub.data.ai.subagent

import me.rerere.rikkahub.data.model.Assistant

/**
 * 一个助手可以派发的子代理。
 *
 * 三个条件缺一不可：目标被标记为子代理、不是派发者自己、
 * 派发者的 [Assistant.allowedSubAgentIds] 为空或包含目标。
 * 空集合表示不限制，这样新增字段不会改变老助手的行为。
 */
internal fun dispatchableSubAgents(
    dispatcher: Assistant,
    assistants: List<Assistant>,
): List<Assistant> = assistants.filter { candidate ->
    candidate.isSubAgent &&
        candidate.id != dispatcher.id &&
        (dispatcher.allowedSubAgentIds.isEmpty() || candidate.id in dispatcher.allowedSubAgentIds)
}
