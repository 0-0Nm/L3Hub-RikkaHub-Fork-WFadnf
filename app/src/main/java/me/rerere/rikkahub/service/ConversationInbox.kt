package me.rerere.rikkahub.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Guidance
import me.rerere.rikkahub.data.model.GuidanceMode
import me.rerere.rikkahub.data.model.localFileUrls
import kotlin.uuid.Uuid

/** 收件箱里等待发给模型的一项 */
sealed interface InboxItem {
    val id: Uuid
}

/** 用户输入：等当前这一轮结束后各自开一轮 */
data class QueuedMessage(
    override val id: Uuid = Uuid.random(),
    val parts: List<UIMessagePart>,
    val answer: Boolean = true,
    val isEditing: Boolean = false,
    // 系统自动生成的通知（后台子代理回投），不是用户输入
    val notice: Boolean = false,
    // 关闭消息队列时提交的消息：只在收件箱里过渡到派发，界面别把它当成「排队中」。
    // 它照样可能碰上等审批，由 dispatchInbox 在关闭队列时整体放行（新一轮开头会取消待审批的工具）
    val sentWithoutQueue: Boolean = false,
    // Explicit send-after-current choice: queue-off approval interruption does not apply to it.
    val afterCurrent: Boolean = false,
    // This item is the complete USER turn converted from a live card. Do not bind older armed cards to it.
    val liveGuidanceTransfer: Boolean = false,
    // Optional in-memory observer; null result means the queued message was withdrawn.
    val reply: CompletableDeferred<String?>? = null,
) : InboxItem

/**
 * 运行时事件，例如后台命令结束：正在生成时在下一步请求模型前插进这一轮，空闲时单独开一轮。
 * [key] 标识事件来源，来源可以据此撤回已经不需要发送的通知。
 */
data class QueuedNotice(
    override val id: Uuid = Uuid.random(),
    val key: String,
    val notice: UIMessagePart.Notice,
) : InboxItem

data class InboxState(
    val items: List<InboxItem> = emptyList(),
    val paused: Boolean = false,
    /** 生效中的引导，按加入顺序；单轮的插入一次后自动移除 */
    val guidances: List<Guidance> = emptyList(),
) {
    val messages: List<QueuedMessage> get() = items.filterIsInstance<QueuedMessage>()
    val notices: List<QueuedNotice> get() = items.filterIsInstance<QueuedNotice>()
}

/** 收件箱决定的下一轮 */
sealed interface InboxTurn {
    /** 发送这条用户输入；要回复时等待中的通知存成它之前的节点 */
    data class Message(val message: QueuedMessage) : InboxTurn

    /** 只有通知在等，单独开一轮；通知在开始时用 [ConversationInbox.takeNotices] 取 */
    data object Notices : InboxTurn
}

internal fun unreferencedQueuedAttachmentUrls(
    previous: QueuedMessage,
    conversations: List<Conversation>,
    pendingMessages: List<QueuedMessage>,
): Set<String> {
    val retainedParts = conversations.flatMap { conversation ->
        conversation.messageNodes.flatMap { node -> node.messages.flatMap { it.parts } }
    } + pendingMessages.flatMap { it.parts }
    return previous.parts.localFileUrls() - retainedParts.localFileUrls()
}

/** Pending input is kept outside conversation history until dispatched. */
class MessageQueuePausedException : IllegalStateException()

/**
 * 会话收件箱：等待发给模型的用户输入和运行时事件，进入会话历史之前都放在这里。
 *
 * 暂停只挡住开始新的一轮；正在进行的一轮仍然会在步骤之间取走通知。
 */
class ConversationInbox {
    private val mutableState = MutableStateFlow(InboxState())
    val state = mutableState.asStateFlow()

    @Synchronized
    fun enqueue(
        parts: List<UIMessagePart>,
        answer: Boolean = true,
        reply: CompletableDeferred<String?>? = null,
        notice: Boolean = false,
        sentWithoutQueue: Boolean = false,
        afterCurrent: Boolean = false,
        liveGuidanceTransfer: Boolean = false,
    ) {
        if (parts.isEmptyInputMessage()) {
            reply?.complete(null)
            return
        }
        mutableState.value = state.value.copy(
            items = state.value.items + QueuedMessage(
                parts = parts.toList(),
                answer = answer,
                reply = reply,
                notice = notice,
                sentWithoutQueue = sentWithoutQueue,
                afterCurrent = afterCurrent,
                liveGuidanceTransfer = liveGuidanceTransfer,
            ),
        )
    }

    /** 插话用：插到队首，生成任务结束后第一个被派发 */
    @Synchronized
    fun enqueueFirst(parts: List<UIMessagePart>, answer: Boolean = true, notice: Boolean = false) {
        if (parts.isEmptyInputMessage()) return
        mutableState.value = state.value.copy(
            items = listOf(QueuedMessage(parts = parts.toList(), answer = answer, notice = notice)) + state.value.items,
        )
    }

    /** 放入一条通知；同一来源已有没发出的通知时原地替换 */
    @Synchronized
    fun post(key: String, notice: UIMessagePart.Notice) {
        val items = state.value.items
        val index = items.indexOfFirst { it is QueuedNotice && it.key == key }
        mutableState.value = state.value.copy(
            items = if (index == -1) {
                items + QueuedNotice(key = key, notice = notice)
            } else {
                items.toMutableList().apply { set(index, (get(index) as QueuedNotice).copy(notice = notice)) }
            },
        )
    }

    /** 撤回还没发出的通知；已经发出的不受影响 */
    @Synchronized
    fun withdraw(key: String) {
        mutableState.value = state.value.copy(
            items = state.value.items.filterNot { it is QueuedNotice && it.key == key },
        )
    }

    /**
     * 取出下一轮：先发用户输入，只剩通知时单独开一轮。
     * 正在编辑的队首挡住后面的用户输入，但不挡通知。
     */
    @Synchronized
    fun takeNext(): InboxTurn? {
        val current = state.value
        if (current.paused) return null
        val head = current.messages.firstOrNull()
        if (head != null && !head.isEditing) {
            mutableState.value = current.copy(items = current.items.filterNot { it.id == head.id })
            return InboxTurn.Message(head)
        }
        return InboxTurn.Notices.takeIf { current.notices.isNotEmpty() }
    }

    /** 取出全部通知，按放入顺序；不受暂停影响 */
    @Synchronized
    fun takeNotices(): List<UIMessagePart.Notice> {
        val current = state.value
        val notices = current.notices
        if (notices.isEmpty()) return emptyList()
        mutableState.value = current.copy(items = current.items.filterNot { it is QueuedNotice })
        return notices.map { it.notice }
    }

    /** 加入一条引导；单轮和多轮的区别只在取出时 */
    @Synchronized
    fun addGuidance(guidance: Guidance) {
        if (guidance.content.isBlank()) return
        mutableState.value = state.value.copy(guidances = state.value.guidances + guidance)
    }

    /** 切换某条引导的生效方式；找不到时什么也不做 */
    @Synchronized
    fun setGuidanceMode(id: Uuid, mode: GuidanceMode) {
        val current = state.value
        if (current.guidances.none { it.id == id && it.mode != mode }) return
        mutableState.value = current.copy(
            guidances = current.guidances.map { if (it.id == id) it.copy(mode = mode) else it },
        )
    }

    /** 用户手动取消一条引导 */
    @Synchronized
    fun removeGuidance(id: Uuid) {
        val current = state.value
        if (current.guidances.none { it.id == id }) return
        mutableState.value = current.copy(guidances = current.guidances.filterNot { it.id == id })
    }

    /**
     * 取出这一批要插入的引导，并把单轮的从收件箱删掉。
     * 多轮的留在原处，下一次工具批次再取一遍——「插入后继续生效」就是这里。
     *
     * [includeRepeating] 为 false 时只取单轮的：引导的插入间隔没到，多轮的这一批先不动，
     * 但单轮的一次指令不该陪着一起等。
     */
    @Synchronized
    fun takeGuidances(includeRepeating: Boolean = true): List<Guidance> {
        val current = state.value
        if (current.guidances.isEmpty()) return emptyList()
        val taken = current.guidances.filter { includeRepeating || it.mode == GuidanceMode.SINGLE }
        if (taken.isEmpty()) return emptyList()
        val takenIds = taken.mapTo(HashSet()) { it.id }
        val remaining = current.guidances.filter { guidance ->
            when {
                guidance.id !in takenIds -> true
                // 多轮的取走后放回去，单轮的取走即删
                else -> guidance.mode == GuidanceMode.REPEAT && includeRepeating
            }
        }
        mutableState.value = current.copy(guidances = remaining)
        return taken
    }

    @Synchronized
    fun remove(id: Uuid): InboxItem? {
        val removed = state.value.items.find { it.id == id } ?: return null
        mutableState.value = state.value.copy(items = state.value.items.filterNot { it.id == id })
        (removed as? QueuedMessage)?.reply?.complete(null)
        return removed
    }

    @Synchronized
    fun beginEdit(id: Uuid): QueuedMessage? {
        val message = state.value.messages.find { it.id == id && !it.isEditing } ?: return null
        mutableState.value = state.value.copy(
            items = state.value.items.map { if (it.id == id) message.copy(isEditing = true) else it },
        )
        return message
    }

    @Synchronized
    fun finishEdit(id: Uuid, parts: List<UIMessagePart>? = null): QueuedMessage? {
        if (parts != null && parts.isEmptyInputMessage()) return null
        val previous = state.value.messages.find { it.id == id } ?: return null
        mutableState.value = state.value.copy(
            items = state.value.items.map {
                if (it.id == id) {
                    previous.copy(parts = parts?.toList() ?: previous.parts, isEditing = false)
                } else {
                    it
                }
            },
        )
        // 取消编辑只释放占位，不能清理原附件。
        return previous.takeIf { parts != null }
    }

    @Synchronized
    fun pause() {
        mutableState.value = state.value.copy(paused = true)
        state.value.messages.forEach { it.reply?.completeExceptionally(MessageQueuePausedException()) }
    }

    fun failReplyWaiters(message: String) {
        state.value.messages.forEach {
            it.reply?.completeExceptionally(IllegalStateException(message))
        }
    }

    @Synchronized
    fun resume() {
        mutableState.value = state.value.copy(paused = false)
    }
}
