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

/** 一条待投递结果：谁产出的（子会话 id），正文是什么 */
private data class PendingSubAgentResult(
    val conversationId: String,
    val text: String,
)

/**
 * 磁盘上的格式。
 *
 * [results] 与 [conversationIds] 按位置对齐；分开存是为了兼容只有 `results` 的老文件——
 * 老文件读出来 conversationId 为空串，await_sub_agent 认不出它属于谁，只能按普通通知投递。
 */
@Serializable
internal data class PendingSubAgentResults(
    val results: List<String> = emptyList(),
    val conversationIds: List<String> = emptyList(),
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
 * 投递用 [peek] + [drop] 两步：先取出一批，父会话收下之后再丢掉队首。投递被拒（上下文已满）
 * 或者进程在两步之间被杀，结果都还在文件里，下一轮会重新投递。
 */
class SubAgentResultStore(
    private val root: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val fileMutex = Mutex()

    /**
     * 追加一条结果；父会话的多条结果按到达顺序排队，投递时合并成一条消息。
     *
     * @return 是否落盘成功。false 表示这一条只在本进程里排队，进程被杀就没了
     */
    suspend fun append(
        parentConversationId: Uuid,
        childConversationId: Uuid,
        result: String,
    ): Boolean = withContext(dispatcher) {
        fileMutex.withLock {
            write(
                parentConversationId,
                read(parentConversationId) + PendingSubAgentResult(childConversationId.toString(), result),
            )
        }
    }

    /** 取出当前排队的结果正文，但不删除；父会话收下后由 [drop] 丢掉 */
    suspend fun peek(parentConversationId: Uuid): List<String> = withContext(dispatcher) {
        fileMutex.withLock { read(parentConversationId).map { it.text } }
    }

    /**
     * 某条子会话最早一份还没投递的结果，不取走。
     *
     * await_sub_agent 先用它把已经完成、还排着队的那一份直接返回；正文仍留在队列里，
     * 等工具输出真的写进会话后，投递时会按正文认出来再剔掉，中间被掐断也不会丢。
     */
    suspend fun findFor(parentConversationId: Uuid, childConversationId: Uuid): String? =
        withContext(dispatcher) {
            fileMutex.withLock {
                read(parentConversationId)
                    .firstOrNull { it.conversationId == childConversationId.toString() }
                    ?.text
            }
        }

    /**
     * 丢掉队首的前 [count] 条。
     *
     * 按条数而不是整个文件清空：投递期间新到的结果追加在队尾，不能跟着一起删掉。
     */
    suspend fun drop(conversationId: Uuid, count: Int) = withContext(dispatcher) {
        fileMutex.withLock {
            val rest = read(conversationId).drop(count)
            if (rest.isEmpty()) {
                file(conversationId).delete()
                tempFile(conversationId).delete()
            } else {
                write(conversationId, rest)
            }
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

    private fun read(conversationId: Uuid): List<PendingSubAgentResult> {
        val file = file(conversationId)
        if (!file.isFile) return emptyList()
        return runCatching {
            val stored = JsonInstant.decodeFromString(PendingSubAgentResults.serializer(), file.readText())
            stored.results.mapIndexed { index, text ->
                PendingSubAgentResult(
                    conversationId = stored.conversationIds.getOrNull(index).orEmpty(),
                    text = text,
                )
            }
        }.onFailure {
            // 解析失败意味着这一队结果一起没了，至少把丢掉的规模记下来
            Log.e(TAG, "read: dropping unreadable results of $conversationId (${file.length()} bytes)", it)
            file.delete()
        }.getOrDefault(emptyList())
    }

    private fun write(conversationId: Uuid, results: List<PendingSubAgentResult>): Boolean {
        val target = file(conversationId)
        return runCatching {
            val parent = target.parentFile
            if (parent != null && !parent.isDirectory) parent.mkdirs()
            val temp = tempFile(conversationId)
            temp.writeText(
                JsonInstant.encodeToString(
                    PendingSubAgentResults.serializer(),
                    PendingSubAgentResults(
                        results = results.map { it.text },
                        conversationIds = results.map { it.conversationId },
                    ),
                )
            )
            // rename 是原子的：要么还是旧的一份完整结果，要么已经是新的
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }.onFailure {
            Log.e(TAG, "write: failed for $conversationId, ${results.size} result(s) not persisted", it)
        }.isSuccess
    }

    private fun file(conversationId: Uuid): File = File(root, "$conversationId$RESULT_SUFFIX")

    private fun tempFile(conversationId: Uuid): File = File(root, "$conversationId$RESULT_TEMP_SUFFIX")
}
