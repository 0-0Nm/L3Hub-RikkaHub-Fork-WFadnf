package me.rerere.rikkahub.data.ai.subagent

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.isAwaitingToolApproval
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import kotlin.uuid.Uuid

const val SUB_AGENT_STATE_RUNNING = "running"
const val SUB_AGENT_STATE_COMPLETED = "completed"
const val SUB_AGENT_STATE_FAILED = "failed"

private const val TAG = "SubAgentRunner"
// 主代理 → 子代理 → 孙代理，最多三代；depthOf 返回祖先数量，父会话在第 2 层起拒绝继续派发
private const val MAX_DEPTH = 2

/**
 * 把任务交给一个普通助手会话，复用 ChatService 的会话、流式状态和生成生命周期。
 * 会话通过 [Conversation.parentConversationId] 挂在父会话下面，任务树据此渲染。
 *
 * 派发一律是并行的：调用立即返回，结果在子代理跑完后自动回送父会话。
 */
class SubAgentRunner(
    private val settingsStore: SettingsStore,
    private val conversationRepository: ConversationRepository,
    private val chatServiceProvider: () -> ChatService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun dispatch(
        parentConversationId: Uuid,
        target: String,
        task: String,
    ): String {
        val settings = settingsStore.awaitLoaded()
        val candidate = settings.assistants.firstOrNull { assistant ->
            assistant.isSubAgent && (
                assistant.id.toString().equals(target.trim(), ignoreCase = true) ||
                    assistant.name.equals(target.trim(), ignoreCase = true)
                )
        } ?: return "No sub-agent named \"$target\". Use one of the names in the available sub-agent list."

        val parent = conversationRepository.getConversationById(parentConversationId)
            ?: return "The parent conversation no longer exists, so the task cannot be dispatched."

        if (settings.getAssistantOf(parent).id == candidate.id) {
            return "A sub-agent cannot dispatch a task to itself. Pick another sub-agent."
        }

        val depth = depthOf(parentConversationId)
        if (depth >= MAX_DEPTH) {
            return "Sub-agent nesting is limited to three generations (main -> child -> grandchild), and this conversation is already the deepest one. Finish the task here instead of dispatching it again."
        }

        val child = Conversation(
            assistantId = candidate.id,
            title = task.trim().lineSequence().firstOrNull()?.take(40)?.ifBlank { "Sub-agent task" }
                ?: "Sub-agent task",
            messageNodes = emptyList(),
            parentConversationId = parent.id,
            subAgentTask = task,
            subAgentState = SUB_AGENT_STATE_RUNNING,
        )
        conversationRepository.insertConversation(child)

        val chatService = chatServiceProvider()
        try {
            chatService.sendMessage(
                conversationId = child.id,
                content = listOf(UIMessagePart.Text(task)),
                answer = true,
            )
        } catch (e: Exception) {
            Log.w(TAG, "start sub agent failed", e)
            markFailed(chatService, child.id)
            return "Sub-agent \"${candidate.name}\" failed to start: ${e.message ?: e::class.simpleName}"
        }

        scope.launch {
            // 被用户停止或中途出错时返回 null：不打搅主代理，状态留在任务树里，
            // 用户想继续就在子会话里直接发消息，不会被一条「异常终止」把话头堵死
            val result = finishChild(chatService, candidate, child) ?: return@launch
            runCatching { chatService.deliverSubAgentResult(parent.id, result) }
                .onFailure { Log.w(TAG, "deliver sub agent result failed", it) }
        }
        return "Sub-agent \"${candidate.name}\" is now running in parallel. Its result will be delivered back to this conversation automatically; open conversation ${child.id} to watch progress."
    }

    private suspend fun finishChild(
        chatService: ChatService,
        candidate: Assistant,
        child: Conversation,
    ): String? {
        val awaited = try {
            chatService.awaitGenerationResult(child.id)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                markStopped(chatService, child.id)
            }
            return null
        } catch (e: Exception) {
            Log.w(TAG, "sub agent run failed", e)
            markStopped(chatService, child.id)
            return null
        }
        // 用户手动停止时上面的 join() 是正常返回的，只会留下半截回复；
        // 不看 isCancelled 就会把半截内容当成「子代理完成」投回主代理
        if (awaited.cancelled) {
            withContext(NonCancellable) { markStopped(chatService, child.id) }
            return null
        }
        val conversation = awaited.conversation

        val awaitingApproval = conversation.isAwaitingToolApproval
        val answer = conversation.currentMessages
            .lastOrNull { it.role == MessageRole.ASSISTANT }
            ?.toText()
            ?.trim()
            .orEmpty()

        return if (awaitingApproval) {
            updateState(chatService, child.id, SUB_AGENT_STATE_FAILED)
            // 等审批也是「没结束」：同样不打搅主代理
            null
        } else if (answer.isBlank()) {
            updateState(chatService, child.id, SUB_AGENT_STATE_FAILED)
            null
        } else {
            updateState(chatService, child.id, SUB_AGENT_STATE_COMPLETED)
            buildString {
                appendLine("Sub-agent \"${candidate.name}\" finished. Conversation ID: ${child.id}")
                appendLine("The full transcript is in that conversation; it is also listed under history and in the task tree.")
                appendLine()
                append(answer)
            }
        }
    }
    private suspend fun markFailed(chatService: ChatService, conversationId: Uuid) {
        updateState(chatService, conversationId, SUB_AGENT_STATE_FAILED)
    }

    /** 被用户停止 / 中途出错：只改任务树状态，不往主会话投递消息 */
    private suspend fun markStopped(chatService: ChatService, conversationId: Uuid) {
        updateState(chatService, conversationId, SUB_AGENT_STATE_FAILED)
    }

    private suspend fun updateState(chatService: ChatService, conversationId: Uuid, state: String) {
        runCatching {
            chatService.updateSubAgentState(conversationId, state)
        }.onFailure {
            Log.w(TAG, "update sub agent state failed", it)
        }
    }

    private suspend fun depthOf(conversationId: Uuid): Int {
        var current = conversationRepository.getConversationById(conversationId)
        var depth = 0
        while (depth < 32) {
            val parentId = current?.parentConversationId ?: break
            depth++
            current = conversationRepository.getConversationById(parentId)
        }
        return depth
    }
}
