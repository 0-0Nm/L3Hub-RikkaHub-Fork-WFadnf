package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.subagent.SubAgentRunner
import me.rerere.rikkahub.data.ai.subagent.matchesSubAgentName
import me.rerere.rikkahub.data.ai.subagent.subAgentLabel
import me.rerere.rikkahub.data.model.Assistant
import kotlin.uuid.Uuid

const val SUB_AGENT_TOOL_NAME = "dispatch_sub_agent"

internal fun buildSubAgentTool(
    conversationId: Uuid,
    subAgents: List<Assistant>,
    runner: SubAgentRunner,
): Tool {
    val catalog = subAgents.joinToString(separator = "\n") { assistant ->
        val name = assistant.name.ifBlank { assistant.id.toString() }
        val intro = assistant.systemPrompt.lineSequence().firstOrNull()?.trim()?.take(60).orEmpty()
        if (intro.isEmpty()) "- $name" else "- $name: $intro"
    }

    return Tool(
        name = SUB_AGENT_TOOL_NAME,
        description = """
            Dispatch a self-contained task to a sub-agent. Sub-agents run in parallel in their own isolated context, so they do not consume this conversation's context.
            The call returns immediately. When the sub-agent finishes, its result is delivered back to this conversation automatically; do not poll for it and do not assume it has finished before the result arrives.
            Sub-agents can dispatch further sub-agents. Nesting is limited to three generations (main -> child -> grandchild); a dispatch from the third generation is rejected, so finish that work in the current conversation.
            Do not repeat a task after dispatching it, and do not run the same task yourself while a sub-agent is working on it.
            The task description must be self-contained: background, goal, constraints, and the expected output format. The sub-agent cannot see this conversation.
            Available sub-agents:
            $catalog
        """.trimIndent(),
        systemPrompt = { _, _ ->
            """
            ## Sub-agents
            When a task can be finished independently, needs multi-step research or heavy file/terminal work, or is unrelated to the current thread, prefer calling dispatch_sub_agent instead of doing everything step by step yourself.
            Every dispatch runs in parallel: the call returns immediately and the result is delivered back to this conversation automatically when the sub-agent finishes, even if this conversation is still busy (multiple results are merged and delivered once you are idle).
            Sub-agents may dispatch their own sub-agents. Nesting stops after three generations (main -> child -> grandchild); at the deepest level finish the work yourself.
            Good candidates: research and comparison, batch file processing, independent code changes, summarizing long documents, and subtasks that can run in parallel. Dispatch several of them in one turn when the work splits cleanly.
            Not worth dispatching: one-line questions, tasks that need details of this conversation, or anything that needs the user's confirmation or approval.
            Do not repeat a task after dispatching it; results arrive automatically, so do not poll and do not assume a result is ready before it arrives.
            """.trimIndent()
        },
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("assistant", buildJsonObject {
                        put("type", "string")
                        put("description", "Sub-agent name. Must be one of the names in the available sub-agent list.")
                    })
                    put("task", buildJsonObject {
                        put("type", "string")
                        put("description", "The task for the sub-agent. Must be self-contained and independently executable.")
                    })
                },
                required = listOf("assistant", "task"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val target = obj["assistant"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val task = obj["task"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val result = when {
                target.isEmpty() -> "Missing required parameter: assistant."
                task.isEmpty() -> "Missing required parameter: task."
                // 和 runner 用同一套匹配规则，避免工具层先一步否掉一个其实能派发的目标
                subAgents.none { it.matchesSubAgentName(target) } ->
                    "No sub-agent named \"$target\". Available: ${subAgents.joinToString { subAgentLabel(it) }}"
                else -> runner.dispatch(conversationId, target, task)
            }
            listOf(UIMessagePart.Text(result))
        },
    )
}
