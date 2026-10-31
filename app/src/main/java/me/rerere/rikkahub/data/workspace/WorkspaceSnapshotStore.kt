package me.rerere.rikkahub.data.workspace

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceStorageArea
import java.io.File
import kotlin.uuid.Uuid

/**
 * 按「最后一条用户消息」分组的文件快照，用来一键撤销某一轮里 AI 对工作区文件的修改。
 *
 * 写文件前把原内容存到应用私有目录（不进工作区、不影响 git status），
 * 同一轮里同一个文件只记第一次的原内容，撤销后这一轮的快照整体删除。
 */
class WorkspaceSnapshotStore(
    context: Context,
    private val workspaceRepository: WorkspaceRepository,
    private val conversationRepository: ConversationRepository,
) {
    private val root = File(context.filesDir, "workspace_turns")
    private val mutex = Mutex()

    private val _revertEpoch = MutableStateFlow(0)

    /** 每次撤销成功后自增；界面拿它当 key 重新判定撤销按钮是否还在 */
    val revertEpoch: StateFlow<Int> = _revertEpoch.asStateFlow()

    @Serializable
    private data class Entry(
        val path: String,
        val area: String,
        val relativePath: String,
        val existed: Boolean,
        val file: String? = null,
    )

    /** 当前对话轮次：最后一条真正的用户消息；压缩检查点不算 */
    suspend fun turnKey(conversationId: Uuid): String = withContext(Dispatchers.IO) {
        runCatching {
            val conversation = conversationRepository.getConversationById(conversationId)
            conversation?.messageNodes
                ?.asReversed()
                ?.firstOrNull {
                    it.currentMessage.role == MessageRole.USER && !it.currentMessage.isContextCheckpoint
                }
                ?.id?.toString()
        }.getOrNull() ?: "initial"
    }

    /**
     * 记录 [absolutePath] 在本轮开始前的内容；[original] 为 null 表示文件当时不存在。
     * 返回本轮的快照 key，供工具结果带给界面。
     */
    suspend fun capture(
        conversationId: Uuid,
        workspaceId: String,
        absolutePath: String,
        area: WorkspaceStorageArea,
        relativePath: String,
        original: String?,
    ): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val turn = turnKey(conversationId)
            val dir = turnDir(conversationId, turn, workspaceId)
            val manifest = File(dir, "manifest.json")
            val entries = readEntries(manifest).toMutableList()
            if (entries.none { it.path == absolutePath }) {
                val file = original?.let { content ->
                    dir.mkdirs()
                    val snapshot = File(dir, "f${entries.size}.txt")
                    snapshot.writeText(content)
                    snapshot.name
                }
                entries += Entry(
                    path = absolutePath,
                    area = area.name,
                    relativePath = relativePath,
                    existed = original != null,
                    file = file,
                )
                dir.mkdirs()
                manifest.writeText(Json.encodeToString(entries))
            }
            turn
        }
    }

    /**
     * 这一轮是否还有可撤销的内容。半成品目录（capture 写到一半失败）不算，
     * 否则会出现「按钮在、点了却提示还原 0 个文件」。
     */
    suspend fun hasTurn(conversationId: Uuid, turnKey: String): Boolean = withContext(Dispatchers.IO) {
        val dir = File(File(root, conversationId.toString()), turnKey)
        dir.listFiles().orEmpty().any { workspaceDir ->
            workspaceDir.isDirectory && readEntries(File(workspaceDir, "manifest.json")).any { entry ->
                // 至少有一条真能还原：新建文件（删掉即可），或原文件快照还在
                !entry.existed || entry.payloadFile(workspaceDir)?.isFile == true
            }
        }
    }

    /** 把这一轮改过的文件还原；返回实际还原的文件数 */
    suspend fun revert(conversationId: Uuid, turnKey: String): Result<Int> = runCatching {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val dir = File(File(root, conversationId.toString()), turnKey)
                if (!dir.isDirectory) return@withContext 0
                var count = 0
                var failed = 0
                var skipped = 0
                dir.listFiles().orEmpty().filter { it.isDirectory }.forEach { workspaceDir ->
                    val workspaceId = workspaceDir.name
                    readEntries(File(workspaceDir, "manifest.json")).forEach { entry ->
                        if (entry.existed) {
                            val content = runCatching {
                                entry.payloadFile(workspaceDir)?.takeIf { it.isFile }?.readText()
                            }.getOrNull()
                            if (content == null) {
                                // 快照载荷丢了：不能算还原成功，也不能算删除失败，单独计数
                                skipped++
                                return@forEach
                            }
                            val ok = runCatching {
                                workspaceRepository.writeAbsoluteText(
                                    workspaceId,
                                    entry.path,
                                    content,
                                    overwrite = true,
                                )
                            }.isSuccess
                            if (ok) count++ else failed++
                        } else {
                            val ok = runCatching {
                                workspaceRepository.deleteFile(
                                    workspaceId,
                                    WorkspaceStorageArea.valueOf(entry.area),
                                    entry.relativePath,
                                    recursive = false,
                                )
                            }.isSuccess
                            if (ok) count++ else failed++
                        }
                    }
                }
                // 有失败/有缺件就保留快照，用户还能再点一次，不会出现「提示失败但文件已删了一半、按钮也没了」
                if (failed > 0 || skipped > 0) {
                    error("$count / ${count + failed + skipped}")
                }
                dir.deleteRecursively()
                count
            }
        }
    }.onSuccess { _revertEpoch.update { it + 1 } }

    private fun turnDir(conversationId: Uuid, turn: String, workspaceId: String): File =
        File(File(File(root, conversationId.toString()), turn), workspaceId)

    private fun Entry.payloadFile(workspaceDir: File): File? = file?.let { File(workspaceDir, it) }

    private fun readEntries(manifest: File): List<Entry> =
        if (!manifest.isFile) emptyList()
        else runCatching { Json.decodeFromString<List<Entry>>(manifest.readText()) }.getOrDefault(emptyList())
}
