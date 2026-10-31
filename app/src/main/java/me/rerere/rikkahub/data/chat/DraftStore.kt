package me.rerere.rikkahub.data.chat

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File
import kotlin.uuid.Uuid

private const val TAG = "DraftStore"

/** 距上次落盘至少隔这么久才写下一次 */
internal const val DRAFT_MIN_INTERVAL_MS = 800L

/** 攒够这么多字节就不等间隔了 */
internal const val DRAFT_MIN_BYTES = 2048

private const val DRAFT_SUFFIX = ".json"
private const val DRAFT_TEMP_SUFFIX = ".json.tmp"

/**
 * 一轮流式生成写了一半的内容。
 *
 * 只放恢复消息本体要用的东西：其它字段（用量、耗时、分支选择等）以会话里的那份为准，
 * 草稿只回答「这条消息当时已经写到了哪一段」。
 */
@Serializable
data class Draft(
    val conversationId: Uuid,
    val nodeId: Uuid,
    val messageId: Uuid,
    val parts: List<UIMessagePart>,
    /** 写入时刻（epoch 毫秒）：早于会话里最后一条消息的草稿是过期的 */
    val updatedAt: Long,
)

/**
 * 流式生成的草稿落盘。
 *
 * 生成期间每一帧只改内存，整轮结束才写数据库，进程被杀这一轮就没了。这里把最新的一帧写到
 * [root] 下的独立文件：不进数据库，也不改变 [Conversation] 的落库语义，进程被杀后下次打开会话再从草稿补回来。
 *
 * [record] 不挂起、不阻塞调用方：帧投进容量为 1 的通道，由 writer 协程节流落盘，中间帧被覆盖，
 * 最后那一帧一定会写下去。写文件先写 `<name>.tmp` 再 rename，任何时候被 kill 都不会留下半个 JSON。
 */
class DraftStore(
    private val root: File,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val minIntervalMs: Long = DRAFT_MIN_INTERVAL_MS,
    private val minBytes: Int = DRAFT_MIN_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Frame(val draft: Draft, val size: Int)

    // 只留最新的一帧：写不动的时候也不会在内存里堆帧
    private val frames = Channel<Frame>(Channel.CONFLATED)
    private val fileMutex = Mutex()

    // 已经 clear 过的会话，队列里还没落盘的帧不能再写回去，否则删掉的文件会被写回来
    private val cleared = LinkedHashSet<Uuid>()

    // 只有 writer 协程更新它，读不用加锁
    private var lastWriteAt = 0L

    init {
        scope.launch(dispatcher) { writeLoop() }
    }

    /** 记下这一帧。非挂起，任何线程都能调，真正的落盘交给 writer */
    fun record(conversationId: Uuid, nodeId: Uuid, message: UIMessage) {
        val draft = Draft(
            conversationId = conversationId,
            nodeId = nodeId,
            messageId = message.id,
            parts = message.parts,
            updatedAt = clock(),
        )
        synchronized(cleared) { cleared.remove(conversationId) }
        frames.trySend(Frame(draft, draft.parts.approximateBytes()))
    }

    /** 这一轮结束了（或者会话被删了）：删掉草稿，队列里还没写下去的帧一并作废 */
    suspend fun clear(conversationId: Uuid) {
        synchronized(cleared) { cleared.add(conversationId) }
        withContext(dispatcher) {
            fileMutex.withLock {
                draftFile(conversationId).delete()
                tempFile(conversationId).delete()
            }
        }
    }

    /** 读回草稿；文件坏了就当没有，顺手删掉 */
    suspend fun load(conversationId: Uuid): Draft? = withContext(dispatcher) {
        fileMutex.withLock {
            val file = draftFile(conversationId)
            if (!file.isFile) return@withLock null
            runCatching { JsonInstant.decodeFromString(Draft.serializer(), file.readText()) }
                .onFailure {
                    Log.w(TAG, "load: drop unreadable draft of $conversationId", it)
                    file.delete()
                }
                .getOrNull()
        }
    }

    /** 现有草稿对应的会话 id */
    fun listAll(): List<Uuid> = root.listFiles().orEmpty()
        .asSequence()
        .filter { it.isFile && it.name.endsWith(DRAFT_SUFFIX) }
        .mapNotNull { runCatching { Uuid.parse(it.name.removeSuffix(DRAFT_SUFFIX)) }.getOrNull() }
        .toList()

    /**
     * 单个 writer：拿一帧，确认到了写的时间（或者攒够了字节）再写，写之前先把排队的帧全取出来，
     * 只写最新的那一份；间隔没到就等着，等到之后写的一定是这期间最新的一帧，最后那一帧不会丢。
     */
    private suspend fun writeLoop() {
        var frame = frames.receive()
        while (true) {
            var bytes = frame.size
            while (true) {
                val wait = minIntervalMs - (clock() - lastWriteAt)
                if (wait <= 0 || bytes >= minBytes) break
                delay(wait)
                while (true) {
                    val next = frames.tryReceive().getOrNull() ?: break
                    frame = next
                    bytes += next.size
                }
            }
            writeFrame(frame)
            lastWriteAt = clock()
            frame = frames.receive()
        }
    }

    private suspend fun writeFrame(frame: Frame) {
        withContext(dispatcher) {
            fileMutex.withLock {
                val conversationId = frame.draft.conversationId
                // 这一轮已经结束（clear 过了），别把删掉的文件写回来
                if (synchronized(cleared) { conversationId in cleared }) return@withLock
                runCatching {
                    val payload = JsonInstant.encodeToString(Draft.serializer(), frame.draft)
                    val target = draftFile(conversationId)
                    val parent = target.parentFile
                    if (parent != null && !parent.isDirectory) parent.mkdirs()
                    val temp = tempFile(conversationId)
                    temp.writeText(payload)
                    // rename 是原子的：要么还是旧的一份完整草稿，要么已经是新的
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                }.onFailure { Log.w(TAG, "writeFrame: failed for $conversationId", it) }
            }
        }
    }

    private fun draftFile(conversationId: Uuid): File = File(root, "$conversationId$DRAFT_SUFFIX")

    private fun tempFile(conversationId: Uuid): File = File(root, "$conversationId$DRAFT_TEMP_SUFFIX")
}

/** 恢复草稿的判定结果 */
internal sealed interface DraftRestore {
    /** 节点还在：用草稿的内容覆盖它当前这条消息的 parts */
    data class ReplaceParts(val nodeId: Uuid, val messageId: Uuid, val parts: List<UIMessagePart>) : DraftRestore

    /** 节点已经不在会话里（这条回复还没落过库）：把草稿作为助手消息追加到末尾 */
    data class AppendMessage(val nodeId: Uuid, val messageId: Uuid, val parts: List<UIMessagePart>) : DraftRestore

    /** 草稿比会话旧，或者不比会话里的长：丢掉 */
    data object Discard : DraftRestore
}

/**
 * 判断这份草稿还有没有用：
 * - 比会话里最后一条消息还旧的草稿不管（那一轮后来的内容已经落库了）；
 * - 节点还在、且草稿比节点当前的消息长才覆盖，否则说明库里的更完整；
 * - 节点不在了就补一条助手消息，生成被杀时这条回复还没进过数据库。
 */
internal fun decideDraftRestore(
    draft: Draft,
    conversation: Conversation,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): DraftRestore {
    val lastMessageAt = conversation.messageNodes
        .lastOrNull()
        ?.messages
        ?.maxOfOrNull { it.createdAt.toInstant(timeZone).toEpochMilliseconds() }
    if (lastMessageAt != null && draft.updatedAt < lastMessageAt) return DraftRestore.Discard

    val node = conversation.messageNodes.lastOrNull { it.id == draft.nodeId }
        ?: return DraftRestore.AppendMessage(draft.nodeId, draft.messageId, draft.parts)
    val message = node.messages.firstOrNull { it.id == draft.messageId }
        ?: node.messages.getOrNull(node.selectIndex)
        ?: return DraftRestore.AppendMessage(draft.nodeId, draft.messageId, draft.parts)

    return if (draft.parts.textLength() > message.parts.textLength()) {
        DraftRestore.ReplaceParts(node.id, message.id, draft.parts)
    } else {
        DraftRestore.Discard
    }
}

/** 把判定结果落到会话上：只换 parts 或者补一条消息，清单归属、分支选择等其它字段都不动 */
internal fun Conversation.applyDraftRestore(restore: DraftRestore): Conversation = when (restore) {
    DraftRestore.Discard -> this
    is DraftRestore.ReplaceParts -> copy(
        messageNodes = messageNodes.map { node ->
            if (node.id != restore.nodeId) {
                node
            } else {
                node.copy(messages = node.messages.map { message ->
                    if (message.id == restore.messageId) message.copy(parts = restore.parts) else message
                })
            }
        }
    )

    is DraftRestore.AppendMessage -> copy(
        messageNodes = messageNodes + MessageNode(
            id = restore.nodeId,
            messages = listOf(
                UIMessage(
                    id = restore.messageId,
                    role = MessageRole.ASSISTANT,
                    parts = restore.parts,
                )
            ),
        )
    )
}

/** 草稿里已经写了多少正文，用来和会话里的那份比长短 */
internal fun List<UIMessagePart>.textLength(): Int = sumOf { part ->
    when (part) {
        is UIMessagePart.Text -> part.text.length
        is UIMessagePart.Reasoning -> part.reasoning.length
        else -> 0
    }
}

/** 粗略估算这一帧有多大，决定要不要提前落盘；不做真正的编码，每帧都调用也不能变慢 */
internal fun List<UIMessagePart>.approximateBytes(): Int = sumOf { part ->
    when (part) {
        is UIMessagePart.Text -> part.text.length
        is UIMessagePart.Reasoning -> part.reasoning.length
        is UIMessagePart.Tool -> part.input.length + part.output.approximateBytes()
        is UIMessagePart.ServerTool -> 128
        is UIMessagePart.Image, is UIMessagePart.Video, is UIMessagePart.Audio -> 128
        is UIMessagePart.Document -> 128 + part.fileName.length
        else -> 64
    }
}
