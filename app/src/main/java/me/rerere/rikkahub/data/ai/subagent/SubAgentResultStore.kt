package me.rerere.rikkahub.data.ai.subagent

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File
import kotlin.uuid.Uuid

private const val TAG = "SubAgentResultStore"

private const val RESULT_SUFFIX = ".json"
private const val RESULT_TEMP_SUFFIX = ".json.tmp"

/** 一段还没回传给父会话的子代理正文：哪条子会话产出的、正文是什么 */
private data class PendingSubAgentResult(
    val conversationId: String,
    val text: String,
)

/** 读出来的队列：条目按到达顺序，欠账按子会话 */
private class QueuedResults(
    val entries: MutableList<PendingSubAgentResult>,
    val holds: MutableMap<String, Int>,
)

/**
 * 一次投递的快照。
 *
 * [texts] 是这次要当通知送给父会话的正文；[settledChildren] 是这批里结清了账的子会话（送完就清掉
 * 它们的排队结果和欠账）；[snapshotSize] 是快照覆盖了几条，投递期间新到的在队尾、不会被带上。
 */
class SubAgentResultBatch internal constructor(
    val texts: List<String>,
    internal val snapshotSize: Int,
    internal val settledChildren: Set<String>,
) {
    val isEmpty: Boolean get() = texts.isEmpty()
}

/**
 * 磁盘上的格式。
 *
 * [results] 与 [conversationIds] 按位置对齐；分开存是为了兼容只有 `results` 的老文件——
 * 老文件读出来 conversationId 为空串，只能按普通通知投递。
 *
 * [holds] 是每个子会话的欠账：子代理异常中止一次记一笔，抵掉它下一次正常结尾的自动回传。
 * 没有这个 key 的老文件按 0 补齐。
 */
@Serializable
internal data class PendingSubAgentResults(
    val results: List<String> = emptyList(),
    val conversationIds: List<String> = emptyList(),
    val holds: Map<String, Int> = emptyMap(),
)

/**
 * 子代理跑完、父会话还没接收的那批结果。
 *
 * 这些结果原来只放在内存里：父会话正忙、停在工具审批上、或者根本没打开时，进程一被杀
 * 整批就没了，子代理会话里的正文还在库中，但再也不会回投。这里照 [me.rerere.rikkahub.data.chat.DraftStore]
 * 的做法落到独立文件：不进数据库，也不改变 [me.rerere.rikkahub.data.model.Conversation] 的落库语义。
 *
 * 写入先写 `<name>.tmp` 再 rename，任何时候被 kill 都不会留下半个 JSON。读失败的文件直接删掉，
 * 当作没有待投递结果——坏掉的一份结果不值得让整个会话再也开不了。写盘失败会记 error 并返回
 * false：这一条只在本进程内能投出去，进程重启后就没有了，除此之外没有补偿手段。
 *
 * 投递用 [peekDeliverable] + [commit] 两步：先取出一批，父会话收下之后再丢掉这批。投递被拒（上下文已满）
 * 或者进程在两步之间被杀，结果都还在文件里，下一轮会重新投递。
 *
 * 每个子会话的账：`还在排队的结果数 - 欠账数`。
 * - 正常跑完一轮（不管自动跑的还是用户手动拉起的）→ +1，多一段排队的结果
 * - 被 await_sub_agent 当场接走 → +1 之后立刻 -1，不进队列
 * - 异常中止（中断、被掐断、待审批、空回复）→ -1，记一笔欠账
 * - 回传被父会话收下 → 清掉这一段（也就是 -1），这批涉及的子会话欠账一起归零
 * - 用户手动把停下来的任务重新拉起 → 不动账；正常结尾、取走、异常中止、回传这四件事才会改它
 *
 * 所以「账 > 0」才回传：刚中止过一次的子代理，下一次正常结尾只排队、不打扰父会话，
 * 留到再下一次正常结尾一起送。
 */
class SubAgentResultStore(
    private val root: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val fileMutex = Mutex()

    /**
     * 记下一段正常结尾；父会话的多条结果按到达顺序排队，投递时合并成一条消息。
     *
     * 要不要现在回传由 [peekDeliverable] 按每个子会话的账决定，不由调用方判断。
     *
     * @return 是否落盘成功。false 表示这一条只在本进程里排队，进程被杀就没了
     */
    suspend fun append(
        parentConversationId: Uuid,
        childConversationId: Uuid,
        result: String,
    ): Boolean = withContext(dispatcher) {
        fileMutex.withLock {
            val queue = read(parentConversationId)
            queue.entries += PendingSubAgentResult(childConversationId.toString(), result)
            write(parentConversationId, queue)
        }
    }

    /** 子代理异常中止：记一笔欠账，抵掉它下一次正常结尾的自动回传 */
    suspend fun recordAbort(
        parentConversationId: Uuid,
        childConversationId: Uuid,
    ) = withContext(dispatcher) {
        fileMutex.withLock {
            val queue = read(parentConversationId)
            val child = childConversationId.toString()
            queue.holds[child] = (queue.holds[child] ?: 0) + 1
            write(parentConversationId, queue)
            Unit
        }
    }

    /**
     * 取走某条子会话最早一段还没回传的正文（await_sub_agent 用）。
     *
     * 取走就算回传：这一段不再当通知投递，对应的账同时 -1。
     *
     * @return 取到的正文；这条子会话没有待投递结果时返回 null
     */
    suspend fun take(
        parentConversationId: Uuid,
        childConversationId: Uuid,
    ): String? = withContext(dispatcher) {
        fileMutex.withLock {
            val queue = read(parentConversationId)
            val child = childConversationId.toString()
            val index = queue.entries.indexOfFirst { it.conversationId == child }
            if (index < 0) return@withLock null
            val taken = queue.entries.removeAt(index)
            write(parentConversationId, queue)
            taken.text
        }
    }

    /** 取出这一批要当通知送出去的正文，但不删除；父会话收下后由 [commit] 丢掉 */
    suspend fun peekDeliverable(parentConversationId: Uuid): SubAgentResultBatch =
        withContext(dispatcher) {
            fileMutex.withLock {
                val queue = read(parentConversationId)
                val counts = queue.entries.groupingBy { it.conversationId }.eachCount()
                // 账 > 0 的子会话才回传：排队的结果比欠账多，说明至少有一段是欠父会话的
                val settled = counts.filterValues { count -> count - (queue.holds[it] ?: 0) > 0 }.keys
                SubAgentResultBatch(
                    texts = queue.entries.filter { it.conversationId in settled }.map { it.text },
                    snapshotSize = queue.entries.size,
                    settledChildren = settled,
                )
            }
        }

    /**
     * 投递完成：把这批送出去的清掉，涉及到的子会话欠账归零。
     *
     * 按条数而不是整个文件清空：投递期间新到的结果追加在队尾，不能跟着一起删掉。
     * 这一批里没被选中的子会话（账 <= 0，正被欠账压着）保持原样，留到下一次。
     */
    suspend fun commit(parentConversationId: Uuid, batch: SubAgentResultBatch) = withContext(dispatcher) {
        fileMutex.withLock {
            val queue = read(parentConversationId)
            val count = minOf(batch.snapshotSize, queue.entries.size)
            val kept = queue.entries.take(count).filterNot { it.conversationId in batch.settledChildren } +
                queue.entries.drop(count)
            batch.settledChildren.forEach { queue.holds.remove(it) }
            queue.entries.clear()
            queue.entries += kept
            write(parentConversationId, queue)
            Unit
        }
    }

    /** 会话被删除：这批结果已经没有接收方了 */
    suspend fun clear(conversationId: Uuid) = withContext(dispatcher) {
        fileMutex.withLock {
            file(conversationId).delete()
            tempFile(conversationId).delete()
            Unit
        }
    }

    /** 还有待投递结果的会话 id；进程重启后按这个补投一次 */
    suspend fun listAll(): List<Uuid> = withContext(dispatcher) {
        root.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile && it.name.endsWith(RESULT_SUFFIX) }
            .mapNotNull { runCatching { Uuid.parse(it.name.removeSuffix(RESULT_SUFFIX)) }.getOrNull() }
            .toList()
    }

    private fun read(conversationId: Uuid): QueuedResults {
        val file = file(conversationId)
        if (!file.isFile) return QueuedResults(mutableListOf(), mutableMapOf())
        val stored = runCatching {
            JsonInstant.decodeFromString(PendingSubAgentResults.serializer(), file.readText())
        }.onFailure {
            // 解析失败意味着这一队结果一起没了，至少把丢掉的规模记下来
            Log.e(TAG, "read: dropping unreadable results of $conversationId (${file.length()} bytes)", it)
            file.delete()
        }.getOrNull() ?: return QueuedResults(mutableListOf(), mutableMapOf())

        val entries = stored.results.mapIndexed { index, text ->
            PendingSubAgentResult(
                conversationId = stored.conversationIds.getOrNull(index).orEmpty(),
                text = text,
            )
        }.toMutableList()
        // 只保留正的欠账；老文件没有这一列，等于全是 0
        val holds = stored.holds.filterValues { it > 0 }.toMutableMap()
        return QueuedResults(entries, holds)
    }

    private fun write(conversationId: Uuid, queue: QueuedResults): Boolean {
        if (queue.entries.isEmpty() && queue.holds.isEmpty()) {
            file(conversationId).delete()
            tempFile(conversationId).delete()
            return true
        }
        val target = file(conversationId)
        return runCatching {
            val parent = target.parentFile
            if (parent != null && !parent.isDirectory) parent.mkdirs()
            val temp = tempFile(conversationId)
            temp.writeText(
                JsonInstant.encodeToString(
                    PendingSubAgentResults.serializer(),
                    PendingSubAgentResults(
                        results = queue.entries.map { it.text },
                        conversationIds = queue.entries.map { it.conversationId },
                        holds = queue.holds.toMap(),
                    ),
                )
            )
            // rename 是原子的：要么还是旧的一份完整结果，要么已经是新的
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }.onFailure {
            Log.e(TAG, "write: failed for $conversationId, ${queue.entries.size} result(s) not persisted", it)
        }.isSuccess
    }

    private fun file(conversationId: Uuid): File = File(root, "$conversationId$RESULT_SUFFIX")

    private fun tempFile(conversationId: Uuid): File = File(root, "$conversationId$RESULT_TEMP_SUFFIX")
}
