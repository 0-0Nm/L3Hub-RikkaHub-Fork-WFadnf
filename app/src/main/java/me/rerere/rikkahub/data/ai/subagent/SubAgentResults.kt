package me.rerere.rikkahub.data.ai.subagent

import kotlin.uuid.Uuid

/**
 * background 子代理完成后送回父会话的提示。
 *
 * 父会话正忙时结果先缓冲，[formatSubAgentResults] 把积攒的多条结果合并成一条消息，
 * 让父代理空闲后一次读完，而不是被逐条打断。
 *
 * 这条消息是系统通知，不是用户发言：[NOTICE_FRAMING] 只在组装请求时加到最前面，
 * 界面和导出看到的是 [formatSubAgentResults] 的正文。
 */
internal const val SUB_AGENT_NOTICE_FRAMING = "(This is an automatic system notice carrying results from sub-agent tasks, not something the user typed. Continue from it directly.)"

internal fun formatSubAgentResults(results: List<String>): String = buildString {
    append(results.size)
    append(" parallel sub-agent task(s) have finished; their results are below. Continue the current work from them and do not repeat what is already done.")
    results.forEachIndexed { index, result ->
        appendLine()
        appendLine()
        append("## ")
        append(index + 1)
        appendLine()
        append(result.trim())
    }
}

/**
 * 一轮子代理结果的正文。
 *
 * 同一个子代理会分多轮回传：它自己派出去的任务回来后它又跑了一轮，这一轮同样要送回父会话。
 * [round] 是子代理会话的第几轮（每条用户消息开一轮），父会话据此区分「新的一轮」和「重复投递」。
 */
internal fun formatSubAgentRoundResult(
    assistantName: String,
    conversationId: Uuid,
    round: Int,
    answer: String,
): String = buildString {
    append("Sub-agent \"")
    append(assistantName.ifBlank { conversationId.toString() })
    append("\" finished round ")
    append(round)
    append(". Conversation ID: ")
    append(conversationId)
    appendLine()
    if (round > 1) {
        appendLine("This round was started by a result its own sub-agents sent back, so only its newest reply is below; earlier rounds are in that conversation.")
    } else {
        appendLine("The full transcript is in that conversation; it is also listed under history and in the task tree.")
    }
    append(answer.trim())
}
