package me.rerere.rikkahub.data.ai.subagent

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

const val SUB_AGENT_STATE_RUNNING = "running"
const val SUB_AGENT_STATE_COMPLETED = "completed"
const val SUB_AGENT_STATE_FAILED = "failed"

private const val TAG = "SubAgentRunner"

// 主代理 → 子代理 → 孙代理，最多三代；depthOf 返回祖先数量，父会话在第 2 层起拒绝继续派发
private const val MAX_DEPTH = 2

// wait=true 时最多让父回合等多久；超时后按后台任务处理，结果照常投递
const val DEFAULT_SUB_AGENT_WAIT_SECONDS = 600
private const val MIN_WAIT_TIMEOUT_SECONDS = 30
private const val MAX_WAIT_TIMEOUT_SECONDS = 1800

/** 子代理的显示名：名字没填时用 id，回复里不会出现一对空引号 */
internal fun subAgentLabel(assistant: Assistant): String =
    assistant.name.ifBlank { assistant.id.toString() }

/**
 * 派发目标匹配：助手名（忽略大小写）或 id。
 *
 * 工具层先用它筛一遍，[SubAgentRunner] 再用它做最终解析。两边规则必须一致，
 * 否则会出现「工具说没有这个子代理，runner 其实能匹配上」这种自相矛盾的回复。
 */
internal fun Assistant.matchesSubAgentName(target: String): Boolean {
    val name = target.trim()
    if (name.isEmpty()) return false
    return this.name.equals(name, ignoreCase = true) || id.toString().equals(name, ignoreCase = true)
}

/**
 * 把任务交给一个普通助手会话，复用 ChatService 的会话、流式状态和生成生命周期。
 * 会话通过 [Conversation.parentConversationId] 挂在父会话下面，任务树据此渲染。
 *
 * 派发一律是并行的：调用立即返回。结果回传不在这里守着——子代理每跑完一轮都由
 * ChatService 把这一轮的结果送回父会话（`ChatService.reportSubAgentRound`），
 * 所以派发方只负责把子会话开起来。
 */
class SubAgentRunner(
    private val settingsStore: SettingsStore,
    private val conversationRepository: ConversationRepository,
    private val chatServiceProvider: () -> ChatService,
    private val subAgentWaits: SubAgentWaits,
) {
    suspend fun dispatch(
        parentConversationId: Uuid,
        target: String,
        task: String,
        wait: Boolean = false,
        timeoutSeconds: Int = DEFAULT_SUB_AGENT_WAIT_SECONDS,
    ): String {
        val settings = settingsStore.awaitLoaded()
        val parent = conversationRepository.getConversationById(parentConversationId)
            ?: return "The parent conversation no longer exists, so the task cannot be dispatched."
        val dispatcher = settings.getAssistantOf(parent)
        val named = settings.assistants.firstOrNull { assistant ->
            assistant.isSubAgent && assistant.matchesSubAgentName(target)
        } ?: return "No sub-agent named \"$target\". Use one of the names in the available sub-agent list."

        if (named.id == dispatcher.id) {
            return "A sub-agent cannot dispatch a task to itself. Pick another sub-agent."
        }

        // 工具层的目录已经按 allowedSubAgentIds 过滤过，这里再查一次：模型可能凭历史记录直接报名字，
        // 而设置可能在派发期间被改过
        val candidates = dispatchableSubAgents(dispatcher, settings.assistants)
        val candidate = candidates.firstOrNull { assistant ->
            assistant.matchesSubAgentName(target)
        } ?: return "Sub-agent \"${subAgentLabel(named)}\" is not in the allowed sub-agent list of \"${subAgentLabel(dispatcher)}\". Available: ${candidates.joinToString { subAgentLabel(it) }}"

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
        // 等待者要在启动子会话之前登记：子代理可能很快就跑完，先发后登记会错过这一轮
        // register 在已经有人等同一条子会话时返回 null；新会话 id 不会撞上，真撞上就按后台模式返回
        val waiter = if (wait) subAgentWaits.register(child.id) else null
        try {
            // 新会话一般不会触到上下文上限；真被拒的话子会话会停在 running、父会话永远等不到结果，
            // 所以这里按失败处理，至少任务树上能看到状态
            val accepted = chatService.sendMessage(
                conversationId = child.id,
                content = listOf(UIMessagePart.Text(task)),
                answer = true,
            )
            if (!accepted) {
                markFailed(chatService, child.id)
                waiter?.let { subAgentWaits.unregister(child.id, it) }
                return "Sub-agent \"${subAgentLabel(candidate)}\" could not start: the conversation refused the message."
            }
        } catch (e: CancellationException) {
            waiter?.let { subAgentWaits.unregister(child.id, it) }
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "start sub agent failed", e)
            markFailed(chatService, child.id)
            waiter?.let { subAgentWaits.unregister(child.id, it) }
            return "Sub-agent \"${subAgentLabel(candidate)}\" failed to start: ${e.message ?: e::class.simpleName}"
        }

        if (waiter == null) {
            return "Sub-agent \"${subAgentLabel(candidate)}\" is now running in parallel. Its result will be delivered back to this conversation automatically; open conversation ${child.id} to watch progress."
        }

        val timeout = waitTimeout(timeoutSeconds)
        val result = try {
            withTimeoutOrNull(timeout) { waiter.await() }
        } catch (e: CancellationException) {
            // 父回合被掐断：等待者摘掉，让下一轮的结果走通知投递
            subAgentWaits.unregister(child.id, waiter)
            throw e
        }
        // 超时和完成撞在同一刻时 withTimeoutOrNull 会返回 null，但值已经放进等待者里
        val received = result ?: if (waiter.isCompleted && !waiter.isCancelled) waiter.await() else null
        if (received == null) {
            subAgentWaits.unregister(child.id, waiter)
            return "Sub-agent \"${subAgentLabel(candidate)}\" is still running after ${timeout.inWholeSeconds} seconds. Its result will be delivered back to this conversation when it finishes; do not wait for it again."
        }
        // 这一份的账由子代理那一侧记：有人在等，它就按「已经通过 await 回传」认领，
        // 落地的那一份留在队里当兜底；父回合在这之后被掐断时认领会撤回，照常当通知补投。
        // 这里只负责把正文交回去
        return received
    }

    /**
     * 等一条已经派发出去的子会话，按「没有可等待对象」的几种情形依次处理：
     *
     * 1. 会话不存在（被删了）→ 直接说明；
     * 2. 已经完成、结果还排在待投递队列里 → 直接返回那一份，不再等；
     * 3. 已经完成、结果早就投递过 → 直接说明，不重复送；
     * 4. 还在跑 → 注册等待者，和 `dispatch(wait=true)` 共用同一套叫醒逻辑；
     * 5. 失败 → 直接返回失败说明。
     *
     * 先登记再查询：子代理可能正好在查询和登记之间跑完，先登记就不会漏掉那一轮。
     * 队列里那一份在这一步取走：取走就算回传（不再当通知投递）；父回合中途被掐断时
     * 会把正文退回待回传，照常补投。
     */
    suspend fun awaitResult(
        parentConversationId: Uuid,
        childConversationId: Uuid,
        timeoutSeconds: Int = DEFAULT_SUB_AGENT_WAIT_SECONDS,
    ): String {
        val chatService = chatServiceProvider()
        val child = chatService.getConversation(childConversationId)
            ?: return "Sub-agent conversation $childConversationId no longer exists, so there is nothing to wait for."
        if (child.parentConversationId != parentConversationId) {
            return "Conversation $childConversationId is not a sub-agent of this conversation, so it cannot be awaited here."
        }
        val name = subAgentLabel(settingsStore.awaitLoaded().getAssistantOf(child))
        val waiter = subAgentWaits.register(childConversationId)
            ?: return "Sub-agent \"$name\" is already being awaited by another call; that call will receive the next result."
        try {
            // 登记和叫醒撞在同一刻：值已经在等待者里
            if (waiter.isCompleted) return waiter.await()
            val latest = chatService.getConversation(childConversationId)
                ?: return "Sub-agent conversation $childConversationId no longer exists, so there is nothing to wait for."
            val state = latest.subAgentState
            // 还在跑就先等这一轮：队列里可能压着上一轮没投递的结果，但那是旧轮次，不能当成
            // 「下一轮」返回；它会在本轮结束后照常当通知送到
            if (state != SUB_AGENT_STATE_RUNNING) {
                val pending = chatService.takePendingSubAgentResult(parentConversationId, childConversationId)
                if (pending != null) {
                    return "This result was produced before the wait started and has not been delivered yet; it is from an earlier round, not a new one.\n\n$pending"
                }
                if (state == SUB_AGENT_STATE_FAILED) {
                    return "Sub-agent \"$name\" (conversation $childConversationId) ended without a result. Do not wait for it again."
                }
                val round = latest.currentMessages.count { message ->
                    message.role == MessageRole.USER && !message.isContextCheckpoint
                }
                return "Sub-agent \"$name\" (conversation $childConversationId) already finished round $round and has no undelivered result left. Do not wait for it again."
            }
            val timeout = waitTimeout(timeoutSeconds)
            val received = withTimeoutOrNull(timeout) { waiter.await() }
            // 超时和完成撞在同一刻时 withTimeoutOrNull 会返回 null，但值已经放进等待者里
            val result = received ?: if (waiter.isCompleted && !waiter.isCancelled) waiter.await() else null
            if (result != null) return result
            return "Sub-agent \"$name\" is still running after ${timeout.inWholeSeconds} seconds. Its result will be delivered back to this conversation when it finishes; do not wait for it again."
        } finally {
            subAgentWaits.unregister(childConversationId, waiter)
        }
    }

    private fun waitTimeout(timeoutSeconds: Int): Duration =
        timeoutSeconds.coerceIn(MIN_WAIT_TIMEOUT_SECONDS, MAX_WAIT_TIMEOUT_SECONDS).seconds

    private suspend fun markFailed(chatService: ChatService, conversationId: Uuid) {
        runCatching {
            chatService.updateSubAgentState(conversationId, SUB_AGENT_STATE_FAILED)
        }.onFailure {
            Log.w(TAG, "mark sub agent failed", it)
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
