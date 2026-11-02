package me.rerere.rikkahub.service

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.ai.agent.AgentEvent
import me.rerere.ai.agent.AgentFinishReason
import me.rerere.ai.agent.RetryAttempt
import me.rerere.ai.agent.runAgent
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.ChatRequestFactory
import me.rerere.rikkahub.data.ai.ChatTurnContext
import me.rerere.rikkahub.data.ai.ConversationMetaGenerator
import me.rerere.rikkahub.data.ai.MemoryConsolidationScheduler
import me.rerere.rikkahub.data.ai.TranslationHandler
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.prompts.buildCompressPrompt
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_COMPLETED
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_FAILED
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_RUNNING
import me.rerere.rikkahub.data.ai.subagent.formatSubAgentRoundResult
import me.rerere.rikkahub.data.ai.subagent.formatSubAgentResults
import me.rerere.rikkahub.data.ai.tools.InvalidMcpServerNamesException
import me.rerere.rikkahub.data.ai.tools.shouldUseExternalWebSearch
import me.rerere.rikkahub.data.chat.DraftRestore
import me.rerere.rikkahub.data.chat.DraftStore
import me.rerere.rikkahub.data.chat.applyDraftRestore
import me.rerere.rikkahub.data.chat.decideDraftRestore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.event.ChatStatus
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AutoCompressTarget
import me.rerere.rikkahub.data.model.AutoCompressThreshold
import me.rerere.rikkahub.data.model.ContextTrim
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.autoCompressLimit
import me.rerere.rikkahub.data.model.bindConfig
import me.rerere.rikkahub.data.model.cancelInterruptedTools
import me.rerere.rikkahub.data.model.deleteMessage
import me.rerere.rikkahub.data.model.editMessage
import me.rerere.rikkahub.data.model.estimateContextTokens
import me.rerere.rikkahub.data.model.estimateMessageTokens
import me.rerere.rikkahub.data.model.estimateTokens
import me.rerere.rikkahub.data.model.fillModelSnapshots
import me.rerere.rikkahub.data.model.fork
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.model.getStoredAssistantOf
import me.rerere.rikkahub.data.model.insertContextCheckpoint
import me.rerere.rikkahub.data.model.resolveContextUsage
import me.rerere.rikkahub.data.model.isAwaitingToolApproval
import me.rerere.rikkahub.data.model.isContextLimitReached
import me.rerere.rikkahub.data.model.keepRecentContextForTokens
import me.rerere.rikkahub.data.model.lastContextTokens
import me.rerere.rikkahub.data.model.localFileUrls
import me.rerere.rikkahub.data.model.replaceUserRegexes
import me.rerere.rikkahub.data.model.resolveAutoCompressTrim
import me.rerere.rikkahub.data.model.selectBranch
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.model.withAssistantUpdate
import me.rerere.rikkahub.data.model.withNodeFavorite
import me.rerere.rikkahub.data.model.withToolApproval
import me.rerere.rikkahub.data.model.withTranslation
import me.rerere.rikkahub.data.model.withoutConversationFields
import me.rerere.rikkahub.data.model.withoutUnresumableToolCalls
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.web.BadRequestException
import me.rerere.rikkahub.web.NotFoundException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

private const val TAG = "ChatService"
private val CONTEXT_OVERFLOW_HINTS = listOf(
    "context_length_exceeded",
    "maximum context length",
    "context window",
    "context length",
    "prompt is too long",
    "input is too long",
    "too many tokens",
    "exceeds the maximum number of tokens",
    "reduce the length of the messages",
    "context limit",
    "maximum context",
    "token limit",
    "context length exceeded",
    "input tokens exceed",
    "request too large",
    "payload too large",
    "exceeds the context window",
    "maximum number of tokens",
    "input length",
    "上下文长度",
    "上下文窗口",
    "超出上下文",
    "超过上下文",
    "输入过长",
    "请求过长",
    "提示词过长",
    "令牌数",
)

data class ChatError(
    val id: Uuid = Uuid.random(),
    val title: String? = null,
    val error: Throwable,
    val conversationId: Uuid? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val solution: ChatErrorSolution? = null,
)

enum class ChatErrorSolution {
    CheckFastModelSettings,
}

/** 错误文案里没有列出的超限表达（常见于中转网关）再用一个宽松的语义匹配兜底 */
internal fun isContextOverflowMessage(message: String): Boolean {
    val text = message.lowercase()
    if (CONTEXT_OVERFLOW_HINTS.any { it in text }) return true
    return Regex(
        """(?:maximum|max|exceed(?:ed)?).{0,40}(?:context|prompt|input|token).{0,40}(?:length|window|token)""",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(text)
}

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val appEventBus: AppEventBus,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val requestFactory: ChatRequestFactory,
    private val memoryConsolidationScheduler: MemoryConsolidationScheduler,
    private val translationHandler: TranslationHandler,
    private val metaGenerator: ConversationMetaGenerator,
    val mcpManager: McpManager,
    private val filesManager: FilesManager,
    private val folderRepository: FolderRepository,
    private val draftStore: DraftStore,
) {
    /** 一轮生成的结果 */
    private sealed interface TurnResult {
        data class Finished(val reason: AgentFinishReason) : TurnResult

        /** 没能完成：错误已经报告给用户，消息队列也已暂停 */
        data object Failed : TurnResult
    }

    private val conversationStore = object : ConversationStore {
        override suspend fun prepareWrite(): (Conversation) -> Conversation {
            // 会话落库即视为开始，此时把助手的配置固定到会话上
            val settings = settingsStore.awaitLoaded()
            return { it.bindConfig(settings).fillModelSnapshots(settings) }
        }

        override suspend fun write(previous: Conversation?, current: Conversation) {
            if (previous == null && !conversationRepo.existsConversationById(current.id)) {
                conversationRepo.insertConversation(current)
            } else {
                conversationRepo.updateConversation(current)
            }
            if (previous != null && previous.messageNodes !== current.messageNodes) {
                withContext(Dispatchers.IO) { deleteUnreferencedFiles(previous, current) }
            }
        }
    }

    /** 会话当前内存里的清单；会话没加载时返回 null，由调用方决定是否读库 */
    fun currentTodos(conversationId: Uuid): List<TodoItem>? =
        sessionManager.get(conversationId)?.state?.value?.todos

    /**
     * 任务清单的唯一写入口。
     *
     * 之前是「工具直接写库 + 另一次调用同步内存」，两条路没有顺序保证：库先写、内存后改，
     * 中间任何一次 flush 都会把旧清单整条写回去；而内存里算出来的归属节点要等下一次整条保存
     * 才落库，进程在这之间被杀就丢了归属（压缩恰好会删掉那条 todo_write 消息）。
     * 这里按会话的既有规矩走：先 update 再 flush。
     */
    suspend fun updateTodos(conversationId: Uuid, todos: List<TodoItem>) {
        val session = sessionManager.get(conversationId)
        if (session == null) {
            // 会话没加载（比如从终端/Web 入口触发），只能直接落库
            conversationRepo.updateTodos(conversationId, todos)
            return
        }
        session.update { current ->
            // 清单跟着写下它的那条消息走：记住写入时的最后一个节点
            val ownerNodeId = current.messageNodes.lastOrNull()?.id
            if (current.todos == todos && current.todosOwnerNodeId == ownerNodeId) {
                current
            } else {
                current.copy(todos = todos, todosOwnerNodeId = ownerNodeId)
            }
        }
        session.flush()
    }

    private val sessionManager = ConversationSessionManager(
        scope = appScope,
        store = conversationStore,
        createInitialConversation = { id ->
            Conversation.ofId(id, assistantId = settingsStore.settingsFlow.value.getCurrentAssistant().id)
        },
        onGenerationFinished = ::onSessionGenerationFinished,
        onSessionRemoved = memoryConsolidationScheduler::onConversationLeft,
    )

    // 错误状态
    private val _errors = MutableStateFlow<List<ChatError>>(emptyList())
    val errors: StateFlow<List<ChatError>> = _errors.asStateFlow()

    fun addError(
        error: Throwable,
        conversationId: Uuid? = null,
        title: String? = null,
        solution: ChatErrorSolution? = null,
    ) {
        if (error is CancellationException) return
        _errors.update {
            it + ChatError(title = title, error = error, conversationId = conversationId, solution = solution)
        }
    }

    fun dismissError(id: Uuid) {
        _errors.update { list -> list.filter { it.id != id } }
    }

    fun clearAllErrors() {
        _errors.value = emptyList()
    }

    // 生成完成流
    private val _generationDoneFlow = MutableSharedFlow<Uuid>()
    val generationDoneFlow: SharedFlow<Uuid> = _generationDoneFlow.asSharedFlow()

    fun cleanup() = runCatching { sessionManager.cleanup() }

    private fun onSessionGenerationFinished(session: ConversationSession, cause: Throwable?) {
        // 取消只来自停止（已自行暂停）或删除，这里再暂停会盖掉停止后用户发送时恢复的队列
        if (cause != null && cause !is CancellationException) session.messageQueue.pause()
        if (session.state.value.isAwaitingToolApproval) {
            session.messageQueue.failReplyWaiters(context.getString(R.string.chat_page_voice_tool_approval))
        }
        appScope.launch {
            // 先回报这一轮（读到的还是刚跑完的状态），再放行下一条排队消息：
            // 否则新的一轮已经把用户消息追加进来，轮次和内容都会算错
            try {
                reportSubAgentRound(session, cause)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 回报失败不能拖住这个会话后面的排队消息和缓冲结果
                Log.w(TAG, "report sub agent round failed: ${session.id}", e)
            }
            dispatchNextQueuedMessage(session.id)
            flushSubAgentResults(session.id)
        }
    }

    // 保留 UI/Web 的入口，生命周期和状态查询统一交给 SessionManager。
    fun addConversationReference(conversationId: Uuid) {
        sessionManager.acquire(conversationId)
    }

    fun removeConversationReference(conversationId: Uuid) {
        sessionManager.release(conversationId)
    }

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> =
        sessionManager.getConversationFlow(conversationId)

    /** 会话的最新内容：已加载的以内存为准，否则读数据库 */
    suspend fun getConversation(conversationId: Uuid): Conversation? =
        sessionManager.get(conversationId)?.takeIf { it.isInitialized }?.state?.value
            ?: conversationRepo.getConversationById(conversationId)

    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> =
        sessionManager.getGenerationJobStateFlow(conversationId)

    fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> =
        sessionManager.getProcessingStatusFlow(conversationId)

    fun getReplyMessageIdFlow(conversationId: Uuid): StateFlow<Uuid?> =
        sessionManager.getReplyMessageIdFlow(conversationId)

    fun getGenerationStartedAtFlow(conversationId: Uuid): StateFlow<Long?> =
        sessionManager.getGenerationStartedAtFlow(conversationId)

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> = sessionManager.getConversationJobs()

    private fun launchGenerationJob(
        conversationId: Uuid,
        keepAliveInBackground: Boolean = true,
        block: suspend () -> Unit,
    ): Job {
        if (!keepAliveInBackground) return appScope.launch(start = CoroutineStart.LAZY) { block() }

        return appScope.launch(start = CoroutineStart.LAZY) {
            val generationId = Uuid.random()
            val foregroundStarted = ChatGenerationForegroundService.acquire(
                context = context,
                generationId = generationId,
                conversationId = conversationId,
            )
            try {
                block()
            } finally {
                if (foregroundStarted) {
                    ChatGenerationForegroundService.release(context, generationId)
                }
            }
        }
    }

    // ---- 初始化对话 ----

    suspend fun initializeConversation(conversationId: Uuid) {
        withLoadedSession(conversationId) { session ->
            // 子代理会话按普通会话列在历史里，打开时侧边栏跟着切到它自己的助手
            settingsStore.selectAssistant(session.state.value.assistantId)
            // 上一轮被进程杀掉时留下的草稿，进页面时补一次
            restoreDraft(session)
        }
        // 上一轮还在忙的时候攒下的子代理结果，等用户打开这个会话再投递
        flushSubAgentResults(conversationId)
    }

    private suspend fun ensureInitialized(session: ConversationSession) {
        session.initialize(
            load = { loadConversation(session.id) },
            create = { newConversation(session.id) },
        )
        restoreDraft(session)
    }

    // 每个会话在进程里只恢复一次草稿：之后进页面、重连都不再拿旧草稿盖库里的内容
    private val draftRestored = ConcurrentHashMap.newKeySet<Uuid>()

    /**
     * 上一轮生成写到一半进程就被杀掉的话，那份内容从来没落过库，这里用草稿补回来：
     * 节点还在就补这一条消息的 parts，节点不在了就把它作为助手消息接在会话末尾。
     * 补完把草稿删掉，下次打开不再恢复一遍。这件事发生在整轮生成之外，和流式循环无关。
     */
    private suspend fun restoreDraft(session: ConversationSession) {
        if (!session.isInitialized) return
        // 正在生成的那一轮自己在写草稿，不能把它当成上次崩掉留下的内容恢复出来
        if (session.isGenerating) return
        if (!draftRestored.add(session.id)) return
        val draft = draftStore.load(session.id) ?: return
        val restore = decideDraftRestore(draft, session.state.value)
        if (restore !is DraftRestore.Discard) {
            session.update { it.applyDraftRestore(restore) }
            // 先把恢复出来的内容落库，再删草稿：这中间被杀也不会丢
            runCatching { session.flush() }
        }
        draftStore.clear(session.id)
    }

    /** 持有会话引用并确保已加载后执行 [block]；[createIfMissing] 为 false 时数据库里没有就抛 NotFoundException */
    private suspend fun <T> withLoadedSession(
        conversationId: Uuid,
        createIfMissing: Boolean = true,
        block: suspend (ConversationSession) -> T,
    ): T = sessionManager.withSession(conversationId) { session ->
        session.initialize(
            load = { loadConversation(conversationId) },
            create = {
                if (!createIfMissing) throw NotFoundException("Conversation not found")
                newConversation(conversationId)
            },
        )
        block(session)
    }

    // 新建对话, 并添加预设消息
    private suspend fun newConversation(conversationId: Uuid): Conversation {
        val assistant = settingsStore.awaitLoaded().getCurrentAssistant()
        return Conversation.ofId(
            id = conversationId,
            assistantId = assistant.id,
            newConversation = true
        ).updateCurrentMessages(assistant.presetMessages)
    }

    // 引入会话配置之前创建的会话没有固定配置，加载时按助手当前的值补上，之后不再随助手变化。
    private suspend fun loadConversation(conversationId: Uuid): Conversation? {
        val conversation = conversationRepo.getConversationById(conversationId) ?: return null
        // settingsFlow 在启动初期还是占位值，固定配置必须基于真实设置
        val bound = conversation.bindConfig(settingsStore.awaitLoaded())
        if (bound !== conversation) conversationRepo.updateConversationConfig(bound)
        return bound
    }

    /**
     * 加载会话后在最新状态上修改并落库。
     * 删除消息或切换分支也可能解除工具审批阻塞，落库后重新检查队列。
     */
    private suspend fun mutate(conversationId: Uuid, transform: (Conversation) -> Conversation): Conversation =
        withLoadedSession(conversationId) { session ->
            session.update(transform)
            session.flush().also { dispatchNextQueuedMessage(conversationId) }
        }

    // 只删上次落库时引用、这次落库不再引用的文件；只在内存里出现过的文件宁可漏删也不误删
    private fun deleteUnreferencedFiles(previous: Conversation, current: Conversation) {
        val session = sessionManager.get(current.id)
        val queuedFiles = (session?.messageQueue?.state?.value?.messages.orEmpty() +
            listOfNotNull(session?.submittingMessage))
            .flatMap { it.parts }.localFileUrls().map { it.toUri() }
        val retainedFiles = (current.files + queuedFiles).toSet()
        val deletedFiles = previous.files.filter { it !in retainedFiles }
        if (deletedFiles.isNotEmpty()) {
            filesManager.deleteChatFiles(deletedFiles)
            Log.w(TAG, "deleteUnreferencedFiles: $deletedFiles")
        }
    }

    // ---- 发送消息 ----

    fun getMessageQueueFlow(conversationId: Uuid): StateFlow<MessageQueueState> =
        sessionManager.getMessageQueueFlow(conversationId)

    fun removeQueuedMessage(conversationId: Uuid, messageId: Uuid) {
        sessionManager.get(conversationId)?.messageQueue?.remove(messageId)?.let(::cleanupQueuedAttachments)
        dispatchNextQueuedMessage(conversationId)
    }

    fun beginEditQueuedMessage(conversationId: Uuid, messageId: Uuid): QueuedMessage? =
        sessionManager.get(conversationId)?.messageQueue?.beginEdit(messageId)

    fun finishEditQueuedMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>? = null
    ) {
        sessionManager.get(conversationId)?.messageQueue?.finishEdit(messageId, parts)
            ?.let(::cleanupQueuedAttachments)
        dispatchNextQueuedMessage(conversationId)
    }

    private fun cleanupQueuedAttachments(previous: QueuedMessage) {
        val candidates = previous.parts.localFileUrls()
        if (candidates.isEmpty()) return
        appScope.launch {
            try {
                // 未打开的会话及未选中的分支也可能引用同一附件。
                val persistedReferences =
                    candidates.filter { conversationRepo.hasFileReference(it) }.toSet()
                // 数据库查询挂起期间队列可能已推进，删除前重新读取内存引用。
                val currentSessions = sessionManager.snapshot()
                val unusedFiles = unreferencedQueuedAttachmentUrls(
                    previous = previous,
                    conversations = currentSessions.map { it.state.value },
                    pendingMessages = currentSessions.flatMap {
                        it.messageQueue.state.value.messages + listOfNotNull(it.submittingMessage)
                    },
                ) - persistedReferences
                if (unusedFiles.isNotEmpty()) {
                    filesManager.deleteChatFiles(unusedFiles.map { it.toUri() })
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // 无法确认引用时保留文件，避免误删。
                Log.w(TAG, "Failed to clean queued attachments", e)
            }
        }
    }

    fun resumeMessageQueue(conversationId: Uuid) {
        sessionManager.get(conversationId)?.messageQueue?.resume()
        dispatchNextQueuedMessage(conversationId)
    }

    /** 当前会话是否已经到达模型的 MAX 上下文门限 */
    private fun ConversationSession.maxContextReached(): Boolean {
        val conversation = state.value
        val model = settingsStore.settingsFlow.value.getChatModelOf(conversation)
        if (model?.enforceMaxContext == false) return false
        return isContextLimitReached(conversation.lastContextTokens(), model?.maxContextTokens)
    }

    private fun reportContextLimit(conversationId: Uuid) {
        addError(
            error = IllegalStateException(context.getString(R.string.chat_page_context_limit_reached)),
            conversationId = conversationId,
            title = context.getString(R.string.error_title_operation),
        )
    }

    /**
     * @param interject 强制插话：不等当前回复结束，直接打断并把这条消息插到队首
     */
    fun sendMessage(
        conversationId: Uuid,
        content: List<UIMessagePart>,
        answer: Boolean = true,
        interject: Boolean = false,
        notice: Boolean = false,
    ) {
        if (content.isEmptyInputMessage()) return
        val session = sessionManager.getOrCreate(conversationId)
        synchronized(session) {
            if (session.maxContextReached() && !autoCompressEnabled(session)) {
                reportContextLimit(conversationId)
                return
            }
            // 关掉消息队列后，生成期间发送的消息等价于插话
            val queueEnabled = settingsStore.settingsFlow.value.enableMessageQueue
            if (interject || (!queueEnabled && session.getJob() != null)) {
                session.messageQueue.enqueueFirst(content, answer)
                session.messageQueue.resume()
                val job = session.getJob()
                if (job != null) {
                    // 取消后 onSessionGenerationFinished 会立刻派发队首
                    job.cancel()
                } else {
                    dispatchNextQueuedMessage(conversationId)
                }
                return
            }
            if (session.messageQueue.state.value.messages.isEmpty()) session.messageQueue.resume()
            session.messageQueue.enqueue(content, answer, notice = notice)
            dispatchNextQueuedMessage(conversationId)
        }
    }

    /** 子代理结束时更新任务树状态；会话可能已经被回收，因此从数据库重新加载。 */
    suspend fun updateSubAgentState(conversationId: Uuid, state: String) {
        withLoadedSession(conversationId, createIfMissing = false) { session ->
            session.update { it.copy(subAgentState = state) }
            session.flush()
        }
    }

    /**
     * 子代理会话每跑完一轮就把这一轮的结果投回父会话。
     *
     * 一轮就是一次生成任务；被子代理回传的结果触发的新一轮同样会走到这里，所以中间层不会只回传第一次。
     * 只在生成任务的完成回调里触发，一个任务只完成一次，因此天然不会重复投递。
     */
    private suspend fun reportSubAgentRound(session: ConversationSession, cause: Throwable?) {
        val conversation = session.state.value
        val parentConversationId = conversation.parentConversationId ?: return
        // 停止和中途出错都只留下半截回复，不把半截内容当成「子代理完成」
        if (cause != null) {
            setSubAgentState(session, SUB_AGENT_STATE_FAILED)
            return
        }
        val answer = conversation.currentMessages
            .lastOrNull { it.role == MessageRole.ASSISTANT }
            ?.toText()
            ?.trim()
            .orEmpty()
        if (conversation.isAwaitingToolApproval || answer.isBlank()) {
            setSubAgentState(session, SUB_AGENT_STATE_FAILED)
            return
        }
        setSubAgentState(session, SUB_AGENT_STATE_COMPLETED)
        // 每轮开头都会追加一条用户消息，用它数这是第几轮：父会话靠这个数区分同一个子代理的多条结果
        val round = conversation.currentMessages.count { it.role == MessageRole.USER }
        // 只有自动跑起来的轮次才回传：第一轮是派发出来的，之后的轮次由子代理回传的结果触发。
        // 用户在子会话里手动追问的那一轮只更新状态，不打扰父会话
        val triggeredAutomatically = round <= 1 ||
            conversation.currentMessages.lastOrNull { it.role == MessageRole.USER }?.isSubAgentNotice == true
        if (!triggeredAutomatically) return
        val assistantName = settingsStore.awaitLoaded().getStoredAssistantOf(conversation).name
        deliverSubAgentResult(
            parentConversationId = parentConversationId,
            result = formatSubAgentRoundResult(assistantName, session.id, round, answer),
        )
    }

    /** 改任务树上的状态；会话被回收或还没加载时只记一条日志，不影响对话本身 */
    private suspend fun setSubAgentState(session: ConversationSession, state: String) {
        if (session.state.value.subAgentState == state) return
        try {
            session.update { it.copy(subAgentState = state) }
            session.flush()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "set sub agent state failed: ${session.id}", e)
        }
    }

    // ---- background 子代理的结果投递 ----

    // 父会话正忙时先攒着，空闲后合并成一条输入投递，避免打断正在进行的回合
    private val pendingSubAgentResults = mutableMapOf<Uuid, MutableList<String>>()

    // 正在投递的会话：同一时刻只送一条，投递期间新到的结果留到下一轮再合并
    private val deliveringSubAgentResults = mutableSetOf<Uuid>()

    /**
     * background 子代理完成后调用：父会话空闲就立刻把结果当作一条用户消息投递；
     * 正在生成、停在工具审批上或还没打开时先攒着，等这一轮结束或下次打开会话时合并投递。
     */
    suspend fun deliverSubAgentResult(parentConversationId: Uuid, result: String) {
        synchronized(pendingSubAgentResults) {
            pendingSubAgentResults.getOrPut(parentConversationId) { mutableListOf() }.add(result)
        }
        flushSubAgentResults(parentConversationId)
    }

    private suspend fun flushSubAgentResults(conversationId: Uuid) {
        val hasPending = synchronized(pendingSubAgentResults) {
            pendingSubAgentResults.containsKey(conversationId) && conversationId !in deliveringSubAgentResults
        }
        if (!hasPending) return
        val session = sessionManager.get(conversationId)
        // 会话没打开时：子代理会话只能自己接着跑，普通会话等用户下次打开再送
        val unopenedSubAgent = session == null && canDeliverToUnopened(conversationId)
        val batch = synchronized(pendingSubAgentResults) {
            if (!pendingSubAgentResults.containsKey(conversationId)) return
            if (!deliveringSubAgentResults.add(conversationId)) return
            // 上下文已满又没开自动压缩时也先留着，等用户压缩或删消息后再投递
            val canDeliver = if (session == null) {
                unopenedSubAgent
            } else {
                !session.isGenerating &&
                    !session.state.value.isAwaitingToolApproval &&
                    !(session.maxContextReached() && !autoCompressEnabled(session))
            }
            if (!canDeliver) {
                deliveringSubAgentResults.remove(conversationId)
                return
            }
            pendingSubAgentResults.remove(conversationId).orEmpty()
        }
        try {
            // 系统通知：走 USER 角色进请求，界面和导出都按系统消息展示
            sendMessage(
                conversationId = conversationId,
                content = listOf(UIMessagePart.Text(formatSubAgentResults(batch))),
                answer = true,
                notice = true,
            )
        } finally {
            synchronized(pendingSubAgentResults) { deliveringSubAgentResults.remove(conversationId) }
        }
    }

    /**
     * 会话没打开时能不能直接投递。
     *
     * 普通会话等用户下次打开再送，免得后台突然开始生成；子代理会话没人会打开它，
     * 等下去整条子代理链就断在中间这一层，所以它自己接着跑。
     */
    private suspend fun canDeliverToUnopened(conversationId: Uuid): Boolean {
        val conversation = conversationRepo.getConversationById(conversationId) ?: return false
        if (conversation.parentConversationId == null) return false
        val settings = settingsStore.settingsFlow.value
        val model = settings.getChatModelOf(conversation)
        if (model?.enforceMaxContext == false) return true
        return !isContextLimitReached(conversation.lastContextTokens(), model?.maxContextTokens) ||
            settings.getAssistantOf(conversation).autoCompress
    }

    /** Enqueue immediately; the result belongs to this item even after edits or later turns. */
    fun enqueueVoiceMessage(conversationId: Uuid, text: String): Deferred<String?> {
        val session = sessionManager.getOrCreate(conversationId)
        val reply = CompletableDeferred<String?>()
        synchronized(session) {
            check(text.isNotBlank()) { context.getString(R.string.chat_page_voice_empty) }
            check(!session.messageQueue.state.value.paused || session.messageQueue.state.value.messages.isEmpty()) {
                context.getString(R.string.chat_page_voice_resume_queue)
            }
            check(!session.state.value.isAwaitingToolApproval) {
                context.getString(R.string.chat_page_voice_tools_before_resume)
            }
            check(!session.maxContextReached()) {
                context.getString(R.string.chat_page_context_limit_reached)
            }
            if (session.messageQueue.state.value.messages.isEmpty()) session.messageQueue.resume()
            session.messageQueue.enqueue(listOf(UIMessagePart.Text(text)), reply = reply)
            dispatchNextQueuedMessage(conversationId)
        }
        return reply
    }

    private fun dispatchNextQueuedMessage(conversationId: Uuid): Job? {
        val session = sessionManager.get(conversationId) ?: return null
        synchronized(session) {
            // A pending tool approval is still part of the current turn.
            // 压缩不是会话 job，单靠自己不会被 getJob() 挡住，这里显式让路
            if (session.getJob() != null ||
                session.state.value.isAwaitingToolApproval ||
                session.compressMutex.isLocked
            ) {
                return null
            }
            // 队列里还有消息但已经到达 MAX: 暂停队列, 压缩上下文后可以手动恢复
            if (session.messageQueue.state.value.messages.isNotEmpty() &&
                session.maxContextReached() &&
                !autoCompressEnabled(session)
            ) {
                session.messageQueue.pause()
                reportContextLimit(conversationId)
                return null
            }
            val next = session.messageQueue.takeNext() ?: return null
            session.submittingMessage = next
            return sendQueuedMessage(session, next)
        }
    }

    private fun sendQueuedMessage(session: ConversationSession, queued: QueuedMessage): Job {
        val conversationId = session.id
        // 新消息开始初始化前就清掉旧回复，初始化失败或任务启动前取消也不能留下旧预览。
        session.replyMessageId.value = null
        val job = launchGenerationJob(
            conversationId = conversationId,
            keepAliveInBackground = queued.answer,
        ) {
            try {
                ensureInitialized(session)
                finishInterruptedPendingTools(session)
                val compressAttempt = autoCompressIfNeeded(session)

                val assistant = settingsStore.awaitLoaded().getStoredAssistantOf(session.state.value)
                val processedContent = queued.parts.replaceUserRegexes(assistant)

                // 添加消息到列表
                val previousIds = session.state.value.currentMessages.mapTo(HashSet()) { it.id }
                session.update {
                    it.copy(
                        messageNodes = it.messageNodes + UIMessage(
                            role = MessageRole.USER,
                            parts = processedContent,
                            isSubAgentNotice = queued.notice,
                        ).toMessageNode(),
                        // 子代理会话每开一轮就回到 running：上一轮之后还会被回传的结果再触发一轮，任务树不该停在「已完成」
                        subAgentState = if (it.parentConversationId != null) SUB_AGENT_STATE_RUNNING else it.subAgentState,
                    )
                }
                session.flush()
                session.submittingMessage = null

                // 用户消息可能刚好把上下文推过门限，加入后再检查一次；
                // 但这一轮已经尝试过就不再试：失败时摘要请求已经付过钱，再跑一遍只是再付一次。
                // 代价是这一轮会带着超门限的上下文继续，真到硬上限由循环压缩或 provider 报错兜底
                if (compressAttempt == CompressAttempt.SKIPPED) autoCompressIfNeeded(session)

                // 开始补全
                val result = if (queued.answer) runTurn(session) else null

                queued.reply?.completeWith(runCatching {
                    check(result != TurnResult.Failed) { context.getString(R.string.chat_page_voice_generation_failed) }
                    check((result as? TurnResult.Finished)?.reason !is AgentFinishReason.AwaitingApproval) {
                        context.getString(R.string.chat_page_voice_tool_approval)
                    }
                    session.state.value.currentMessages
                        .filter { it.id !in previousIds && it.role == MessageRole.ASSISTANT }
                        .joinToString("\n") { it.toText() }
                })
                // Voice owns playback, including when its observer has already left the page.
                // The ordinary autoplay collector must not read a late voice reply again.
                if (queued.reply == null) _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                queued.reply?.completeExceptionally(e)
                e.printStackTrace()
                reportFailure(session, e, R.string.error_title_send_message)
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause != null) queued.reply?.completeExceptionally(cause)
            synchronized(session) {
                if (session.submittingMessage?.id == queued.id) session.submittingMessage = null
            }
        }
        session.setJob(job)
        return job
    }

    // 发送、重新生成和审批失败时报告错误并暂停队列，排队的消息等用户处理完再继续
    private fun reportFailure(session: ConversationSession, error: Exception, @StringRes title: Int) {
        if (error is CancellationException) throw error
        session.messageQueue.pause()
        addError(error, session.id, title = context.getString(title))
    }

    // ---- 重新生成消息 ----

    /** 重新生成用户消息时去掉它之后的节点再回复；重新生成助手消息时在它的节点上新开一个分支 */
    fun regenerateAtMessage(conversationId: Uuid, messageId: Uuid) {
        val session = sessionManager.getOrCreate(conversationId)
        synchronized(session) {
            val previousJob = session.getJob()
            val job = launchGenerationJob(conversationId = conversationId) {
                try {
                    previousJob?.join()
                    session.replyMessageId.value = null
                    ensureInitialized(session)
                    val node = session.state.value.getMessageNodeByMessageId(messageId)
                        ?: return@launchGenerationJob

                    if (node.role == MessageRole.USER) {
                        var todosCleared = false
                        session.tryUpdate { conversation ->
                            val index = conversation.messageNodes.indexOfFirst { it.id == node.id }
                            // 查找之后这条消息被删掉了
                            if (index == -1) return@tryUpdate null
                            val keptNodes = conversation.messageNodes.take(index + 1)
                            // 截掉的消息里就有清单的来源时，清单一起清掉
                            val ownerRemoved = conversation.todosOwnerNodeId?.let { owner ->
                                keptNodes.none { it.id == owner }
                            } == true
                            if (ownerRemoved) todosCleared = true
                            conversation.copy(
                                messageNodes = keptNodes,
                                todos = if (ownerRemoved) emptyList() else conversation.todos,
                                todosOwnerNodeId = if (ownerRemoved) null else conversation.todosOwnerNodeId,
                            )
                        } ?: return@launchGenerationJob
                        session.flush()
                        // 整条保存会把库里还在的清单当成内存没同步捡回来，清空要单独落库
                        if (todosCleared) conversationRepo.updateTodos(conversationId, emptyList())
                        // 上一次就是因为超限失败的，重新生成前先压一次，否则必然再失败
                        autoCompressIfNeeded(session)
                        runTurn(session)
                    } else {
                        autoCompressIfNeeded(session)
                        runTurn(session, regenerateNodeId = node.id)
                    }

                    _generationDoneFlow.emit(conversationId)
                } catch (e: Exception) {
                    reportFailure(session, e, R.string.error_title_regenerate_message)
                }
            }

            session.setJob(job)
        }
    }

    // ---- 处理工具调用审批 ----

    fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
        answer: String? = null,
    ) {
        val session = sessionManager.getOrCreate(conversationId)
        synchronized(session) {
            val previousJob = session.getJob()

            val hasOtherPendingTools = session.state.value.currentMessages.any { message ->
                message.getTools().any { it.isPending && it.toolCallId != toolCallId }
            }

            val job = launchGenerationJob(
                conversationId = conversationId,
                keepAliveInBackground = !hasOtherPendingTools,
            ) {
                try {
                    afterPreviousGeneration(previousJob) {
                        ensureInitialized(session)
                        val newApprovalState = when {
                            answer != null -> ToolApprovalState.Answered(answer)
                            approved -> ToolApprovalState.Approved
                            else -> ToolApprovalState.Denied(reason)
                        }

                        val updated = session.tryUpdate { it.withToolApproval(toolCallId, newApprovalState) }
                            ?: return@afterPreviousGeneration
                        session.flush()

                        // Only continue generation when all pending tools are handled
                        if (!updated.isAwaitingToolApproval) {
                            runTurn(session)
                        }

                        _generationDoneFlow.emit(conversationId)
                    }
                } catch (e: Exception) {
                    reportFailure(session, e, R.string.error_title_tool_approval)
                }
            }

            session.setJob(job, cancelPrevious = false)
        }
    }

    // ---- 生成回复 ----

    /**
     * 生成一轮回复：请求模型、执行工具，直到模型不再调用工具、有工具需要审批或达到步数上限。
     *
     * 默认接着当前分支的末尾回复；[regenerateNodeId] 不为空时只发送这个节点之前的消息，回复作为它的新分支。
     * 发送的最后一条是助手消息时从它继续（比如审批之后），回复写回那条消息。
     * 失败时报告错误、暂停消息队列并返回 [TurnResult.Failed]；取消时直接抛出。
     */
    private suspend fun runTurn(
        session: ConversationSession,
        regenerateNodeId: Uuid? = null,
        contextRetryCount: Int = 0,
    ): TurnResult {
        val conversationId = session.id
        // 先清理上一轮状态；确定历史后，仅为真正的续写恢复消息 ID。
        session.replyMessageId.value = null
        // 生成开始前失败时通知只用来取消 Live Update，用不到发送者名称
        var senderName = ""
        try {
            val settings = settingsStore.awaitLoaded()
            // 清掉上一轮的建议；没法继续执行的工具调用发给模型会被拒绝，先去掉。
            // 重新生成时只清理要发给模型的节点，被重新生成的节点保持原样，旧回复留作分支
            val conversation = session.update { latest ->
                val untilNode = regenerateNodeId
                    ?.let { nodeId -> latest.messageNodes.indexOfFirst { it.id == nodeId } }
                    ?.takeIf { it != -1 }
                    ?: latest.messageNodes.size
                latest.copy(chatSuggestions = emptyList()).withoutUnresumableToolCalls(untilNode)
            }

            val nodes = conversation.messageNodes
            val history = if (regenerateNodeId != null) {
                val index = nodes.indexOfFirst { it.id == regenerateNodeId }
                check(index != -1) { "Message node $regenerateNodeId not found" }
                conversation.currentMessages.take(index)
            } else {
                conversation.currentMessages
            }
            val replyNodeId = when {
                history.lastOrNull()?.role == MessageRole.ASSISTANT -> nodes[history.lastIndex].id
                regenerateNodeId != null -> regenerateNodeId
                else -> Uuid.random()
            }
            // 接着上一条助手消息写时就是它，否则等第一次输出。要在 createContext 之前换掉，
            // 它会挂起也可能失败，期间或失败后不能还指着上一轮的回复
            session.replyMessageId.value = history.lastOrNull()?.takeIf { it.role == MessageRole.ASSISTANT }?.id

            val turn = requestFactory.createContext(settings, conversation)
            senderName = if (turn.assistant.useAssistantAvatar) {
                turn.assistant.name.ifEmpty { context.getString(R.string.assistant_page_default_assistant) }
            } else {
                turn.model.displayName
            }
            warnIfToolsUnavailable(turn, conversationId)

            val (request, hooks) = requestFactory.createAgent(
                turn = turn,
                messages = history,
                processingStatus = session.processingStatus,
            )
            var finishReason: AgentFinishReason? = null
            try {
                runAgent(request, hooks).flowOn(Dispatchers.IO).collect { event ->
                    when (event) {
                        is AgentEvent.MessageUpdated -> {
                            // 生成过程中只有网络重试的提示会留着，收到新内容说明重试已经成功
                            session.processingStatus.value = null
                            session.update { it.upsertMessage(replyNodeId, event.message) }
                            session.replyMessageId.value = event.message.id
                            // 内存更新完就把这一帧投给草稿文件：进程被杀时这一轮还能从这里恢复
                            draftStore.record(conversationId, replyNodeId, event.message)
                            // 通知等边缘副作用由 ChatNotificationManager 消费；
                            // tryEmit 不挂起，事件丢失只影响单次通知更新，不能反压生成链
                            appEventBus.tryEmit(
                                AppEvent.ChatGenerationUpdate(conversationId, event.message, senderName)
                            )
                            requestLoopCompressionIfNeeded(session, turn, event.message)
                        }

                        is AgentEvent.Retrying -> {
                            logRetry(event.attempt)
                            session.processingStatus.value = retryStatus(event.attempt)
                        }

                        is AgentEvent.Finished -> {
                            Log.i(TAG, "runTurn: finished (${event.reason})")
                            finishReason = event.reason
                        }
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    session.processingStatus.value = null
                    // 可能被取消了，或者意外结束，兜底保存
                    val finished = session.finishGeneration()
                    // 成功、失败、取消都走这里：内容已经落库，草稿留着只会被下次打开当成更新的一份恢复出来
                    draftStore.clear(conversationId)
                    // 生成结束：取消 Live Update 通知，后台时发送完成通知
                    appEventBus.emit(
                        AppEvent.ChatGenerationEnded(
                            conversationId = conversationId,
                            senderName = senderName,
                            contentPreview = finished.currentMessages.lastOrNull()
                                ?.toText()?.take(50)?.trim() ?: "",
                        )
                    )
                }
            }

            val reason = checkNotNull(finishReason)
            // 等待审批时这一轮还没结束，标题、建议和记忆整理等审批之后再做
            if (reason !is AgentFinishReason.AwaitingApproval) {
                sessionManager.launchWithSession(conversationId) { generateTitle(conversationId) }
                sessionManager.launchWithSession(conversationId) { generateSuggestion(conversationId) }
                if (turn.assistant.enableMemory) memoryConsolidationScheduler.onTurnFinished(session.state.value)
            }
            return TurnResult.Finished(reason)
        } catch (e: CancellationException) {
            appEventBus.tryEmit(AppEvent.ChatGenerationEnded(conversationId, senderName, null))
            throw e
        } catch (e: InvalidMcpServerNamesException) {
            session.messageQueue.pause()
            addError(
                error = IllegalStateException(
                    context.getString(R.string.error_mcp_invalid_server_name, e.names.joinToString(", "))
                ),
                conversationId = conversationId,
            )
            return TurnResult.Failed
        } catch (e: Exception) {
            // 模型循环里上下文超限：压缩后从当前状态继续这一轮，最多重试一次
            if (contextRetryCount < 2 && retryWithCompression(session, e)) {
                return runTurn(session, regenerateNodeId, contextRetryCount = contextRetryCount + 1)
            }
            // 兜底取消 Live Update 通知（生成开始前失败时上面的 finally 不会执行）
            appEventBus.tryEmit(AppEvent.ChatGenerationEnded(conversationId, senderName, null))
            session.messageQueue.pause()
            e.printStackTrace()
            addError(e, conversationId, title = context.getString(R.string.error_title_generation))
            Logging.log(TAG, "runTurn: $e")
            Logging.log(TAG, e.stackTraceToString())
            return TurnResult.Failed
        }
    }

    // 模型不支持工具调用时，搜索和 MCP 工具都不会生效，提醒一下
    private fun warnIfToolsUnavailable(turn: ChatTurnContext, conversationId: Uuid) {
        if (ModelAbility.TOOL in turn.model.abilities) return
        if (shouldUseExternalWebSearch(turn.assistant, turn.model) ||
            mcpManager.getAllAvailableTools(turn.assistant).isNotEmpty()
        ) {
            addError(
                IllegalStateException(context.getString(R.string.tools_warning)),
                conversationId,
                title = context.getString(R.string.error_title_tool_unavailable)
            )
        }
    }

    private fun logRetry(attempt: RetryAttempt) {
        Log.w(
            TAG,
            "Provider connection failed, retrying in ${attempt.delay} (${attempt.retry}/${attempt.maxRetries})",
            attempt.cause,
        )
    }

    private fun retryStatus(attempt: RetryAttempt): String {
        val reason = when (attempt.cause) {
            is UnknownHostException -> R.string.chat_generation_network_unknown_host
            is SocketTimeoutException -> R.string.chat_generation_network_timeout
            is ConnectException, is NoRouteToHostException -> R.string.chat_generation_network_unreachable
            else -> R.string.chat_generation_network_disconnected
        }
        return context.getString(
            R.string.chat_generation_network_retrying,
            context.getString(reason),
            attempt.retry,
            attempt.maxRetries,
        )
    }

    private suspend fun finishInterruptedPendingTools(session: ConversationSession) {
        session.tryUpdate { it.cancelInterruptedTools() }
        session.flush()
    }

    // ---- 生成标题 ----

    suspend fun generateTitle(
        conversationId: Uuid,
        force: Boolean = false
    ): Unit = withContext(Dispatchers.IO) {
        withLoadedSession<Unit>(conversationId) { session ->
            val incarnation = session.incarnation
            val conversation = session.state.value
            if (!force && conversation.title.isNotBlank()) return@withLoadedSession

            val settings = settingsStore.awaitLoaded()
            // 关掉自动生成后只跳过对话结束时的自动调用，手动重新生成照常执行
            if (!force && !settings.enableAutoTitle) return@withLoadedSession

            runCatching {
                val title = metaGenerator.generateTitle(settings, conversation)
                // 只写标题这一列：这时可能已经在生成下一轮，不能把还没生成完的内容一起落库。
                // 生成期间会话被删除的话不写入
                session.updateMetadata(
                    transform = { it.copy(title = title) },
                    persist = { conversationRepo.updateTitle(conversationId, title) },
                    expectedIncarnation = incarnation,
                )
            }.onFailure {
                it.printStackTrace()
                addError(
                    error = it,
                    conversationId = conversationId,
                    title = context.getString(R.string.error_title_generate_title),
                    solution = ChatErrorSolution.CheckFastModelSettings,
                )
            }
        }
    }

    // ---- 生成建议 ----

    private suspend fun generateSuggestion(conversationId: Uuid): Unit = withContext(Dispatchers.IO) {
        sessionManager.withSession<Unit>(conversationId) { session ->
            runCatching {
                val settings = settingsStore.awaitLoaded()
                if (!settings.enableSuggestion) return@runCatching

                // 上一轮的建议在这一轮开始时已经清掉了
                ensureInitialized(session)
                val incarnation = session.incarnation
                val suggestions = metaGenerator.generateSuggestions(settings, session.state.value)
                    ?: return@runCatching

                // 只写建议这一列，理由同标题
                session.updateMetadata(
                    transform = { it.copy(chatSuggestions = suggestions) },
                    persist = { conversationRepo.updateChatSuggestions(conversationId, suggestions) },
                    expectedIncarnation = incarnation,
                )
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    // ---- 压缩对话历史 ----

    /**
     * 把上一个检查点之后的消息压缩成摘要检查点。
     *
     * [keepRecentMessages] 为 0 时压缩全部消息，请求和上一轮对话前缀一致，缓存命中最好；
     * 大于 0 时最近的这些消息不参与压缩、留在检查点之后，对话已经超出模型上下文、整段发不出去时用来缩小压缩请求。
     */
    suspend fun compressConversation(
        conversationId: Uuid,
        additionalPrompt: String,
        keepRecentMessages: Int = 0,
    ): Result<Unit> = runCatching {
        withLoadedSession<Unit>(conversationId) { session ->
            // 生成中的回复还没写完，压缩不到它，检查点也不能插到它前面
            check(!session.isGenerating) { context.getString(R.string.chat_page_compress_blocked_generating) }
            // 和自动压缩共用一把锁：两边同时跑会插两份摘要，后插的那份还可能把前一份顶到后面去
            session.compressMutex.withLock {
                val size = session.state.value.messageNodes.size
                compressSession(
                    session = session,
                    additionalPrompt = additionalPrompt,
                    trim = ContextTrim((size - keepRecentMessages).coerceIn(0, size)),
                    requireIdle = true,
                )
                // 手动压过之后，自动压缩不该再为同一份上下文总结一遍
                session.compressedNodeCount = session.state.value.messageNodes.size
            }
        }
    }

    /**
     * 工具执行完、下一次请求之前主动检查上下文。等提供商报错再压缩时，有些服务会把超限
     * 包装成普通 400，甚至直接截断输入，循环压缩就永远不会触发；这里按估算值提前拦住。
     */
    private fun requestLoopCompressionIfNeeded(
        session: ConversationSession,
        turn: ChatTurnContext,
        message: UIMessage,
    ) {
        if (!turn.assistant.autoCompress || !turn.assistant.autoCompressLoop) return
        // 工具都执行完后检查一次；不要求工具一定在 parts 末尾，避免中间夹着思考/正文时漏掉
        val tools = message.getTools()
        if (tools.isEmpty() || tools.any { !it.isExecuted }) return
        val limit = turn.model.autoCompressLimit(turn.assistant.autoCompressThreshold) ?: return
        val systemPrompt = if (turn.assistant.allowConversationSystemPrompt &&
            !turn.conversationSystemPrompt.isNullOrBlank()) {
            turn.conversationSystemPrompt.orEmpty()
        } else {
            turn.assistant.systemPrompt
        }
        // 和生成前那次检查用同一把尺子：真实 usage 与估算取大
        val real = message.contextTokens()?.takeIf { it > 0 }
        val used = resolveContextUsage(
            realTokens = real,
            visibleTokens = session.state.value.estimateContextTokens(turn.assistant.contextMessageLimit),
            fixedTokens = estimateTokens(systemPrompt),
        )
        if (used < limit) return
        val source = if (real != null) "usage" else "estimate"
        Log.i(TAG, "runTurn: context $used ($source) >= $limit, compress before next step")
        throw LoopCompressionRequested(used, limit)
    }

    /** 循环压缩：模型循环里上下文超限时，保留正在生成的回复，压缩它之前的历史 */
    private suspend fun retryWithCompression(session: ConversationSession, error: Throwable): Boolean {
        if (error !is LoopCompressionRequested && !error.isContextOverflow()) return false
        val assistant = settingsStore.awaitLoaded().getAssistantOf(session.state.value)
        if (!assistant.autoCompress || !assistant.autoCompressLoop) return false
        Log.i(TAG, "runTurn: context overflow, compress and retry")
        return autoCompressIfNeeded(session, force = true, keepTailNodes = 1) ==
            CompressAttempt.COMPRESSED
    }

    /**
     * 一次自动压缩的结果。
     *
     * [SKIPPED] 是「没压」——门限没到、被别的压缩占用、没有可压范围；[FAILED] 是「试过但失败」，
     * 这时摘要请求可能已经付过钱。调用方据此决定要不要在同一轮再试一次。
     */
    private enum class CompressAttempt { SKIPPED, COMPRESSED, FAILED }

    /**
     * 自动压缩：上下文占用到 WARN/MAX 门限时，在生成本轮回复之前先压缩一次。
     *
     * [force] 跳过门限检查，用于模型循环中已经确定超限的场景；[keepTailNodes] 指定末尾保留的
     * 消息条数，循环压缩时保留正在生成的那条回复，压缩请求只发送它之前的历史。
     *
     * 生成中的加载球复用 [ConversationSession.processingStatus]，显示「正在压缩……」，不弹浮窗。
     * 插入检查点后 [Conversation.lastContextTokens] 会返回 null，同一轮不会重复触发。
     */
    private suspend fun autoCompressIfNeeded(
        session: ConversationSession,
        force: Boolean = false,
        keepTailNodes: Int? = null,
    ): CompressAttempt {
        // 同一份上下文只压一次：并发/连续触发时后面的直接放弃，避免把同一段历史总结两遍
        if (!session.compressMutex.tryLock()) {
            Log.d(TAG, "autoCompress: another compression is running, skip")
            return CompressAttempt.SKIPPED
        }
        return try {
            if (!force && session.state.value.messageNodes.size == session.compressedNodeCount) {
                Log.d(TAG, "autoCompress: context unchanged since last compression, skip")
                CompressAttempt.SKIPPED
            } else {
                autoCompressLocked(session, force, keepTailNodes)
            }
        } finally {
            session.compressMutex.unlock()
        }
    }

    private suspend fun autoCompressLocked(
        session: ConversationSession,
        force: Boolean,
        keepTailNodes: Int?,
    ): CompressAttempt {
        val settings = settingsStore.awaitLoaded()
        val conversation = session.state.value
        val assistant = settings.getAssistantOf(conversation)
        if (!assistant.autoCompress) return CompressAttempt.SKIPPED

        val model = settings.getChatModelOf(conversation) ?: return CompressAttempt.SKIPPED
        val limit = model.autoCompressLimit(assistant.autoCompressThreshold)

        // 真实用量偶尔缺失（回复没写入 usage、压缩后还没开始下一轮），退回按字符估算。
        // 两个口径各有盲区：usage 看不到刚追加、还没发出去的消息，估算看不到工具定义，
        // 所以都算一遍取大的
        val real = conversation.lastContextTokens()?.takeIf { it > 0 }
        val systemPrompt = if (assistant.allowConversationSystemPrompt &&
            !conversation.customSystemPrompt.isNullOrBlank()) {
            conversation.customSystemPrompt.orEmpty()
        } else {
            assistant.systemPrompt
        }
        val systemTokens = estimateTokens(systemPrompt)
        val used = resolveContextUsage(
            realTokens = real,
            visibleTokens = conversation.estimateContextTokens(assistant.contextMessageLimit),
            fixedTokens = systemTokens,
        )
        // 摘要和 systemPrompt 压缩之后仍然占着上下文：截断目标先扣掉这块固定开销
        val checkpointMessage = conversation.messageNodes
            .lastOrNull { it.currentMessage.isContextCheckpoint }
            ?.currentMessage
        val reservedTokens = systemTokens +
            (checkpointMessage?.let { estimateMessageTokens(it) } ?: 0)
        if (!force && (limit == null || used < limit)) {
            Log.d(TAG, "autoCompress: skip used=$used real=$real limit=$limit")
            return CompressAttempt.SKIPPED
        }

        val trim = conversation.resolveAutoCompressTrim(
            target = assistant.autoCompressTarget,
            targetValue = assistant.autoCompressTargetValue,
            keepTailNodes = keepTailNodes,
            force = force,
            reservedTokens = reservedTokens,
            // 用门限判断实际用掉的那个数：两边尺子不一致会留出一段「判超限但没有可压范围」
            limitTokens = used,
        ) ?: run {
            Log.d(TAG, "autoCompress: no compressible range")
            return CompressAttempt.SKIPPED
        }

        Log.i(
            TAG,
            "autoCompress: conversation=${session.id} used=$used real=$real " +
                "limit=$limit reserved=$reservedTokens trim=$trim",
        )
        val statusSender = assistant.name.ifEmpty { context.getString(R.string.assistant_page_default_assistant) }
        appEventBus.tryEmit(AppEvent.ChatGenerationStatus(session.id, statusSender, ChatStatus.COMPRESSING))
        session.processingStatus.value = context.getString(R.string.chat_page_compressing)
        return try {
            compressSession(session, additionalPrompt = "", trim = trim, requireIdle = false)
            session.compressedNodeCount = session.state.value.messageNodes.size
            Log.i(TAG, "autoCompress: compressed conversation=${session.id}")
            CompressAttempt.COMPRESSED
        } catch (e: Exception) {
            Log.w(TAG, "autoCompress: failed", e)
            addError(
                error = e,
                conversationId = session.id,
                title = context.getString(R.string.chat_page_auto_compress_failed),
            )
            CompressAttempt.FAILED
        } finally {
            session.processingStatus.value = null
            appEventBus.tryEmit(AppEvent.ChatGenerationStatus(session.id, statusSender, null))
        }
    }

    /** 自动压缩是否开启；MAX 拦截在开启时先放行，由本轮生成前的自动压缩处理 */
    private fun autoCompressEnabled(session: ConversationSession): Boolean =
        settingsStore.settingsFlow.value.getAssistantOf(session.state.value).autoCompress

    /** 各家 SDK 的上下文超限错误文案不一，按关键字兜底识别 */
    private fun Throwable.isContextOverflow(): Boolean {
        val text = generateSequence(this) { it.cause }
            .mapNotNull { it.message }
            .joinToString("\n")
        return isContextOverflowMessage(text)
    }

    private suspend fun compressSession(
        session: ConversationSession,
        additionalPrompt: String,
        trim: ContextTrim,
        requireIdle: Boolean,
    ) {
        if (requireIdle) {
            check(!session.isGenerating) { context.getString(R.string.chat_page_compress_blocked_generating) }
        }

        val conversation = session.state.value
        val nodes = conversation.messageNodes
        val checkpointIndex = nodes.indexOfLast { it.currentMessage.isContextCheckpoint }
        // 压缩范围是 (checkpointIndex, endIndex)，之后的节点原样保留
        val endIndex = trim.nodeIndex
        // 上一个检查点之后没有可压缩的消息；partIndex > 0 时允许切分检查点后的第一条消息
        val onlyCheckpointTail = endIndex == checkpointIndex + 1
        if (endIndex < checkpointIndex + 1 || endIndex > nodes.size ||
            (onlyCheckpointTail && (trim.partIndex == 0 || checkpointIndex < 0))
        ) {
            throw IllegalStateException(
                context.getString(
                    if (endIndex < nodes.size) {
                        R.string.chat_page_compress_not_enough_messages
                    } else {
                        R.string.chat_page_compress_nothing_to_compress
                    }
                )
            )
        }
        // partIndex 指向 endIndex 这条消息内部：前半段进摘要，后半段留在检查点之后
        val splitNode = if (trim.partIndex > 0) {
            nodes.getOrNull(endIndex)?.takeIf { trim.partIndex < it.currentMessage.parts.size }
        } else {
            null
        }
        val summarizedParts = splitNode?.currentMessage?.parts?.take(trim.partIndex).orEmpty()
        // 未完成的工具调用没有结果，请求会被服务端拒绝；压到检查点之前也再不会执行。
        // 保留区里的不发给压缩请求，压缩后照常留在检查点之后，不用检查
        check(nodes.subList(checkpointIndex + 1, endIndex).none { node ->
            node.currentMessage.getTools().any { it.isPending || it.canResumeExecution }
        } && summarizedParts.none { part ->
            part is UIMessagePart.Tool && (part.isPending || part.canResumeExecution)
        }) { context.getString(R.string.chat_page_compress_handle_pending_tools) }

        // 必须和对话用同一个模型、同一套请求组装，才能命中提示词缓存
        val turn = requestFactory.createContext(settingsStore.awaitLoaded(), conversation)
        // 上一个检查点本身要进摘要输入，否则上次压缩之前的信息全丢；
        // 但它之前的原文已经由它替代，再发一遍既重复付费，也可能把压缩请求顶出模型窗口
        val checkpointMessage = nodes.getOrNull(checkpointIndex)?.currentMessage
        val summaryMessages = buildList {
            checkpointMessage?.let { add(it) }
            addAll(conversation.currentMessages.subList(checkpointIndex + 1, endIndex))
            // 被截掉的前半段仍要参与摘要，否则这部分内容会凭空消失
            if (splitNode != null) {
                add(splitNode.currentMessage.copy(parts = summarizedParts))
            }
        }
        val summary = requestFactory.generateCompressionSummary(
            turn = turn,
            messages = summaryMessages,
            compressPrompt = buildCompressPrompt(
                additionalPrompt = additionalPrompt,
                locale = Locale.getDefault().displayName,
            ),
        )

        // 原消息原样保留，摘要插在压缩范围的最后一条消息之后。摘要生成期间对话可能已变化，
        // 因此按节点定位插入到最新状态。
        session.update { latest ->
            if (requireIdle) {
                check(!session.isGenerating) { context.getString(R.string.chat_page_compress_blocked_generating) }
            }
            // 幂等兜底：并发/连续两次压缩算出同样的摘要时，别把同一份文本插两遍
            val afterIndex = latest.messageNodes.indexOfFirst { it.id == nodes[endIndex - 1].id }
            if (afterIndex >= 0) {
                val next = latest.messageNodes.getOrNull(afterIndex + 1)?.currentMessage
                if (next?.isContextCheckpoint == true && next.toText().trim() == summary.trim()) {
                    return@update latest
                }
            }
            val updated = latest.insertContextCheckpoint(
                afterNodeId = nodes[endIndex - 1].id,
                summary = summary,
                splitMessageId = splitNode?.currentMessage?.id,
                keepPartsFrom = trim.partIndex,
            ) ?: throw IllegalStateException(context.getString(R.string.chat_page_compress_conversation_changed))
            updated.copy(chatSuggestions = emptyList())
        }
        session.flush()
    }

    private class LoopCompressionRequested(
        val estimated: Int,
        val limit: Int,
    ) : Exception("estimated context $estimated exceeds limit $limit")

    // ---- 聊天页配置 ----

    /**
     * 聊天页对助手的修改：会话持有的字段只改当前会话，其余写回助手设置。
     *
     * @param update 接收会话视角下的助手，返回修改后的助手
     */
    suspend fun updateChatAssistant(conversationId: Uuid, update: (Assistant) -> Assistant) {
        withLoadedSession(conversationId) { session ->
            val settings = settingsStore.awaitLoaded()
            val conversation = session.state.value
            val stored = settings.getStoredAssistantOf(conversation)
            val updated = update(settings.getAssistantOf(conversation))
            val assistant = updated.withoutConversationFields(conversation, stored)
            session.updateMetadata(
                transform = { it.withAssistantUpdate(updated, settings) },
                persist = conversationRepo::updateConversationConfig,
            )
            if (assistant != stored) {
                settingsStore.update { latest ->
                    latest.copy(assistants = latest.assistants.map { if (it.id == assistant.id) assistant else it })
                }
            }
        }
    }

    /**
     * 切换搜索方式，传 null 的一项保持不变。
     * 模型内置搜索在会话开始前是模型自身的开关，开始后固定在会话上。
     */
    suspend fun updateChatSearch(
        conversationId: Uuid,
        enableWebSearch: Boolean? = null,
        builtInSearch: Boolean? = null,
    ) {
        withLoadedSession(conversationId) { session ->
            val conversation = session.state.value
            if (conversation.config != null) {
                session.updateMetadata(
                    transform = {
                        it.copy(
                            config = it.config?.let { config ->
                                config.copy(
                                    enableWebSearch = enableWebSearch ?: config.enableWebSearch,
                                    builtInSearch = builtInSearch ?: config.builtInSearch,
                                )
                            }
                        )
                    },
                    persist = conversationRepo::updateConversationConfig,
                )
                return@withLoadedSession
            }
            settingsStore.update { settings ->
                val assistant = settings.getAssistantOf(conversation)
                val model = settings.getChatModelOf(conversation)
                settings.copy(
                    assistants = if (enableWebSearch == null) {
                        settings.assistants
                    } else {
                        settings.assistants.map {
                            if (it.id == assistant.id) it.copy(enableWebSearch = enableWebSearch) else it
                        }
                    },
                    providers = if (builtInSearch == null || model == null) {
                        settings.providers
                    } else {
                        settings.providers.map { provider ->
                            provider.editModel(
                                model.copy(
                                    tools = if (builtInSearch) {
                                        model.tools + BuiltInTools.Search
                                    } else {
                                        model.tools - BuiltInTools.Search
                                    }
                                )
                            )
                        }
                    },
                )
            }
        }
    }

    // 工作目录和配置一起按列落库；还没落过库的新会话先留在内存里，发出第一条消息时一起写入
    suspend fun updateWorkspaceCwd(conversationId: Uuid, workspaceId: Uuid, workspaceCwd: String?) {
        withLoadedSession(conversationId) { session ->
            session.updateMetadata(
                transform = { conversation ->
                    val key = workspaceId.toString()
                    val cwds = if (workspaceCwd.isNullOrBlank()) {
                        conversation.workspaceCwds - key
                    } else {
                        conversation.workspaceCwds + (key to workspaceCwd)
                    }
                    conversation.copy(workspaceCwds = cwds)
                },
                persist = conversationRepo::updateConversationConfig,
            )
        }
    }

    suspend fun updateConversationSystemPrompt(conversationId: Uuid, systemPrompt: String?) {
        updateColumn(
            conversationId = conversationId,
            transform = { it.copy(customSystemPrompt = systemPrompt) },
            persist = { conversationRepo.updateCustomSystemPrompt(conversationId, systemPrompt) },
        )
    }

    suspend fun updateTitle(conversationId: Uuid, title: String) {
        updateColumn(
            conversationId = conversationId,
            transform = { it.copy(title = title) },
            persist = { conversationRepo.updateTitle(conversationId, title) },
        )
    }

    /**
     * 用户在聊天页改的单列字段：已落库的会话只写这一列，生成中也不会顺带写入还没生成完的回复；
     * 还没落过库的新会话整份落库，留下记录（没有内容的新会话仍然不写）。
     */
    private suspend fun updateColumn(
        conversationId: Uuid,
        transform: (Conversation) -> Conversation,
        persist: suspend () -> Unit,
    ) {
        withLoadedSession(conversationId) { session ->
            session.updateMetadata(transform, persist = { persist() })
            if (!session.existsInDatabase) session.flush()
        }
    }

    // ---- 会话元数据 ----

    private suspend fun updateConversationMetadata(
        conversationId: Uuid,
        transform: (Conversation) -> Conversation,
        persist: suspend (Conversation) -> Unit,
    ) {
        withLoadedSession(conversationId, createIfMissing = false) { session ->
            session.updateMetadata(transform, persist)
        }
    }

    suspend fun toggleConversationPinned(conversationId: Uuid) {
        updateConversationMetadata(
            conversationId = conversationId,
            transform = { it.copy(isPinned = !it.isPinned) },
            persist = { conversationRepo.updatePinStatus(conversationId, it.isPinned) },
        )
    }

    suspend fun moveConversationToAssistant(conversationId: Uuid, assistantId: Uuid) {
        updateConversationMetadata(
            conversationId = conversationId,
            // 文件夹属于助手，移动后清除原助手的文件夹归属。
            transform = { it.copy(assistantId = assistantId, folderId = null) },
            persist = { conversationRepo.updateConversationAssistant(conversationId, it.assistantId) },
        )
    }

    /**
     * 移动会话到文件夹（folderId 为 null 表示移出到未归类）。
     * 有 session 的会话先改内存，免得之后整份落库时写回旧的文件夹；没有的只改这一列，不必加载整个会话。
     */
    suspend fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        if (sessionManager.get(conversationId) == null) {
            conversationRepo.updateConversationFolderId(conversationId, folderId)
            // 写库期间页面可能刚好打开了这个会话，加载时读到的可能是旧值，再同步一次内存
            if (sessionManager.get(conversationId) == null) return
        }
        updateConversationMetadata(
            conversationId = conversationId,
            transform = { it.copy(folderId = folderId) },
            persist = { conversationRepo.updateConversationFolderId(conversationId, folderId) },
        )
    }

    /**
     * 还没开始的新会话跟随抽屉里选中的文件夹，首次落库时归入；已经落库的会话不受影响，移动走 [moveConversationToFolder]。
     */
    suspend fun updateDraftConversationFolder(conversationId: Uuid, folderId: Uuid?) {
        val folder = folderId?.let { folderRepository.getFolderById(it) }
        withLoadedSession(conversationId) { session ->
            session.updateDraft { latest ->
                // 文件夹属于助手：已被删除或不属于会话的助手时不归入
                val target = folder?.takeIf { it.assistantId == latest.assistantId }?.id
                if (latest.folderId == target) latest else latest.copy(folderId = target)
            }
        }
    }

    /**
     * 文件夹内是否存在正在生成回复的会话。
     * 仅活跃 session 可能在生成；内存态 folderId 为权威（移动会先同步内存态）。
     */
    fun hasGeneratingConversationInFolder(folderId: Uuid): Boolean {
        return sessionManager.snapshot().any { it.isGenerating && it.state.value.folderId == folderId }
    }

    /**
     * 删除文件夹（folder_id 归属会被清空，会话本身保留）。
     * 已加载的会话先改内存再删库，之后落库不会写回已删除的文件夹。
     */
    suspend fun deleteFolder(folderId: Uuid) {
        sessionManager.snapshot()
            .filter { it.isInitialized && it.state.value.folderId == folderId }
            .forEach { session ->
                // 这一列由下面删文件夹时统一清空，这里只改内存，不让会话因此变成有未落库的修改
                session.updateMetadata(
                    transform = { if (it.folderId == folderId) it.copy(folderId = null) else it },
                    persist = {},
                )
            }
        folderRepository.deleteFolder(folderId)
    }

    // ---- 删除会话 ----

    /**
     * 删除会话：停掉生成后在会话的写锁内删库，内存换成一个还没落过库的新会话。
     * 还停在这个会话上的页面看到的是空会话，之后再发消息会新建记录，不会写回已删除的那条。
     */
    suspend fun deleteConversation(conversationId: Uuid) {
        val session = sessionManager.getOrCreate(conversationId)
        // 正在生成的这一轮必须先停掉：否则它会把内容写回刚被清空的会话，甚至把会话重新落库
        val jobs = synchronized(session) {
            session.messageQueue.pause()
            session.cancelJobs()
        }
        jobs.forEach { job -> runCatching { job.join() } }
        discardQueuedMessages(session)
        session.replace(newConversation(conversationId), existsInDatabase = false) { previous ->
            conversationRepo.getConversationById(conversationId)?.let { conversationRepo.deleteConversation(it) }
            // 数据库那份不知道只在内存里出现过的附件
            previous?.let { filesManager.deleteChatFiles(it.files) }
        }
        // 会话没了草稿也没用了，否则同一个 id 再打开会恢复出已经删掉的内容
        draftStore.clear(conversationId)
        draftRestored.remove(conversationId)
        sessionManager.removeIfIdle(session)
    }

    /** 撤销删除：写回删除前的会话，还停在这个会话上的页面也一并换回 */
    suspend fun restoreConversation(conversation: Conversation) {
        val session = sessionManager.getOrCreate(conversation.id)
        session.replace(conversation, existsInDatabase = true) {
            conversationStore.write(previous = null, current = conversation)
        }
        sessionManager.removeIfIdle(session)
    }

    suspend fun deleteConversationsOfAssistant(assistantId: Uuid) {
        val persisted = conversationRepo.getConversationsOfAssistant(assistantId).first().map { it.id }
        val loaded = sessionManager.snapshot()
            .filter { it.isInitialized && it.state.value.assistantId == assistantId }
            .map { it.id }
        (persisted + loaded).distinct().forEach { deleteConversation(it) }
    }

    private fun discardQueuedMessages(session: ConversationSession) {
        session.messageQueue.pause()
        session.messageQueue.state.value.messages.forEach { queued ->
            session.messageQueue.remove(queued.id)?.let(::cleanupQueuedAttachments)
        }
    }

    // ---- 翻译消息 ----

    fun translateMessage(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguage: Locale
    ) {
        appScope.launch(Dispatchers.IO) {
            sessionManager.withSession<Unit>(conversationId) { session ->
                try {
                    ensureInitialized(session)
                    val settings = settingsStore.awaitLoaded()

                    val messageText = message.parts.filterIsInstance<UIMessagePart.Text>()
                        .joinToString("\n\n") { it.text }
                        .trim()

                    if (messageText.isBlank()) return@withSession

                    // Set loading state for translation
                    val loadingText = context.getString(R.string.translating)
                    session.update { it.withTranslation(message.id, loadingText) }

                    translationHandler.translateText(
                        settings = settings,
                        sourceText = messageText,
                        targetLanguage = targetLanguage
                    ) { translatedText ->
                        // Update translation field in real-time
                        session.update { it.withTranslation(message.id, translatedText) }
                    }.collect { /* Final translation already handled in onStreamUpdate */ }

                    session.flush()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Clear translation field on error
                    if (session.isInitialized) session.update { it.withTranslation(message.id, null) }
                    addError(e, conversationId, title = context.getString(R.string.error_title_translate_message))
                }
            }
        }
    }

    suspend fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        mutate(conversationId) { it.withTranslation(messageId, null) }
    }

    // ---- 消息操作 ----

    suspend fun editMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>
    ) {
        if (parts.isEmptyInputMessage()) return
        val settings = settingsStore.awaitLoaded()

        mutate(conversationId) { conversation ->
            conversation.editMessage(messageId, parts.replaceUserRegexes(settings.getStoredAssistantOf(conversation)))
        }
    }

    suspend fun forkConversationAtMessage(
        conversationId: Uuid,
        messageId: Uuid
    ): Conversation {
        val source = withLoadedSession(conversationId) { it.state.value }
        val existingTitles = conversationRepo.getTitlesOfAssistant(source.assistantId)
        val fork = source.fork(messageId, existingTitles, copyFile = ::copyChatFile)
            ?: throw NotFoundException("Message not found")
        // 新会话直接落库，打开时再从数据库加载
        val forkConversation = conversationStore.prepareWrite()(fork)
        conversationRepo.insertConversation(forkConversation)
        memoryConsolidationScheduler.onConversationForked(forkConversation)
        return forkConversation
    }

    private fun copyChatFile(url: String): String =
        filesManager.createChatFilesByContents(listOf(url.toUri())).firstOrNull()?.toString() ?: url

    suspend fun selectMessageNode(
        conversationId: Uuid,
        nodeId: Uuid,
        selectIndex: Int
    ) {
        mutate(conversationId) { conversation ->
            val targetNode = conversation.messageNodes.firstOrNull { it.id == nodeId }
                ?: throw NotFoundException("Message node not found")
            if (selectIndex !in targetNode.messages.indices) {
                throw BadRequestException("Invalid selectIndex")
            }
            if (targetNode.selectIndex == selectIndex) return@mutate conversation
            conversation.selectBranch(nodeId, selectIndex)
        }
    }

    // 收藏记录在单独的表里，节点上的标记只用于显示，不需要落库
    suspend fun setNodeFavorite(conversationId: Uuid, nodeId: Uuid, favorite: Boolean) {
        val session = sessionManager.get(conversationId)?.takeIf { it.isInitialized } ?: return
        session.updateMetadata(transform = { it.withNodeFavorite(nodeId, favorite) }, persist = {})
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        messageId: Uuid,
        failIfMissing: Boolean = true,
    ) {
        var todosCleared = false
        mutate(conversationId) { conversation ->
            val nodeId = conversation.getMessageNodeByMessageId(messageId)?.id
            val updated = conversation.deleteMessage(messageId)
                ?: if (failIfMissing) throw NotFoundException("Message not found") else conversation
            // 清单是这条消息写下的，消息没了清单也就没有来源
            if (nodeId != null && nodeId == updated.todosOwnerNodeId) {
                todosCleared = true
                updated.copy(todos = emptyList(), todosOwnerNodeId = null)
            } else {
                updated
            }
        }
        // 整条保存会把库里还在的清单当成内存没同步捡回来，清空要单独落库
        if (todosCleared) conversationRepo.updateTodos(conversationId, emptyList())
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        message: UIMessage,
    ) {
        deleteMessage(conversationId, message.id, failIfMissing = false)
    }

    // 停止当前会话生成任务（不清理会话缓存）
    suspend fun stopGeneration(conversationId: Uuid) {
        val session = sessionManager.get(conversationId) ?: return
        val jobs = synchronized(session) {
            session.messageQueue.pause()
            session.cancelJobs()
        }
        if (jobs.isEmpty()) return
        jobs.forEach { it.join() }
        if (session.isInitialized) finishInterruptedPendingTools(session)
        draftStore.clear(conversationId)
    }
}
