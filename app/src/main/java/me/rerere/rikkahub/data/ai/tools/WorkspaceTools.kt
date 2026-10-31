package me.rerere.rikkahub.data.ai.tools

import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.DiffMetadata
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.toMetadata
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.ExecutionPermission
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.workspace.ApplyOutcome
import me.rerere.rikkahub.data.workspace.FileDiff
import me.rerere.rikkahub.data.workspace.GitIgnoreMatcher
import me.rerere.rikkahub.data.workspace.GitIgnoreRule
import me.rerere.rikkahub.data.workspace.RepoMap
import me.rerere.rikkahub.data.workspace.RepoMapFile
import me.rerere.rikkahub.data.workspace.UnifiedDiff
import me.rerere.rikkahub.data.workspace.WorkspaceSnapshotStore
import me.rerere.rikkahub.utils.generateUnifiedDiff
import me.rerere.workspace.WorkspaceCapabilities
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceLayout
import kotlin.uuid.Uuid
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.WorkspaceShellStatus
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.java.KoinJavaComponent.getKoin
import java.io.ByteArrayOutputStream
import java.nio.file.FileSystems
import java.nio.file.Paths

private const val SHELL_TIMEOUT_MAX_SECONDS = 600L
private const val MAX_READ_FILE_BYTES = 8L * 1024 * 1024
private const val MAX_SNAPSHOT_FILE_BYTES = 4L * 1024 * 1024
private const val DEFAULT_READ_LINES = 2000
private const val MAX_READ_LINES = 5000
private const val MAX_LIST_ENTRIES = 500
private const val MAX_GLOB_RESULTS = 500
private const val MAX_GREP_RESULTS = 200
private const val MAX_SEARCH_ENTRIES = 4000
private const val MAX_SEARCH_DEPTH = 12
private const val MAX_GREP_FILE_BYTES = 512L * 1024
private const val MAX_GREP_SCANNED_FILES = 800
private const val MAX_GREP_LINE_CHARS = 400

// 依赖目录和构建产物没有检索价值，遍历时整棵跳过
private val SEARCH_SKIPPED_DIRS = setOf(
    ".git", ".gradle", ".idea", "node_modules", "build", "dist", "out", "target",
    "__pycache__", ".venv", "venv", ".dart_tool", "Pods", ".next", "coverage",
)

val WorkspaceToolDefaultApprovals: Map<String, Boolean> = mapOf(
    "workspace_read_file" to false,
    "workspace_list" to false,
    "workspace_glob" to false,
    "workspace_grep" to false,
    "workspace_write_file" to false,
    "workspace_edit_file" to false,
    "workspace_apply_patch" to false,
    "workspace_shell" to true,
    "workspace_git" to true,
    "workspace_test" to true,
    "workspace_repo_map" to false,
)

/** 直接写文件的工具, 只在支持 [WorkspaceCapabilities.fileWriteTools] 的工作区提供 */
val WorkspaceFileWriteToolNames: Set<String> = setOf(
    "workspace_write_file",
    "workspace_edit_file",
    "workspace_apply_patch",
)

fun resolveWorkspaceToolApproval(name: String, overrides: Map<String, Boolean>): Boolean =
    overrides[name] ?: WorkspaceToolDefaultApprovals[name] ?: false

private const val TAG = "WorkspaceTools"

internal data class WorkspaceTarget(
    val id: String,
    val name: String,
    val layout: WorkspaceLayout,
    val approvalOverrides: Map<String, Boolean>,
    val supportsFileWrite: Boolean,
    val executionPermission: ExecutionPermission = ExecutionPermission.APPROVAL,
)

suspend fun createWorkspaceTools(
    workspaceIds: Collection<String>,
    workspaceRepository: WorkspaceRepository,
    cwds: Map<String, String> = emptyMap(),
    conversationId: Uuid? = null,
    planMode: Boolean = false,
    executionPermission: ExecutionPermission = ExecutionPermission.APPROVAL,
): List<Tool> {
    // 只读档位沿用计划模式的过滤规则: 写文件 / 改仓库 / 跑测试都不注册, shell 只放只读命令
    val readOnly = planMode || executionPermission == ExecutionPermission.READ_ONLY
    val targets = workspaceIds
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .mapNotNull { id ->
            val workspace = workspaceRepository.getById(id) ?: return@mapNotNull null
            if (workspace.shellStatus != WorkspaceShellStatus.READY.name) {
                Log.d(TAG, "skip workspace tools: workspace=$id, status=${workspace.shellStatus}")
                return@mapNotNull null
            }
            val layout = workspaceRepository.layout(id) ?: return@mapNotNull null
            WorkspaceTarget(
                id = id,
                name = workspace.name,
                layout = layout,
                approvalOverrides = workspace.toolApprovalOverrides(),
                supportsFileWrite = workspace.capabilities().fileWriteTools,
                executionPermission = executionPermission,
            )
        }
        .toList()
    if (targets.isEmpty()) return emptyList()

    return buildList {
        add(createReadFileTool(targets, workspaceRepository))
        add(createListTool(targets, workspaceRepository))
        add(createGlobTool(targets, workspaceRepository))
        add(createGrepTool(targets, workspaceRepository))
        add(createRepoMapTool(targets, workspaceRepository))
        val snapshots = conversationId?.let {
            runCatching { getKoin().get<WorkspaceSnapshotStore>() }.getOrNull()
        }
        // 计划模式和「只读」档位只放只读工具：写文件、跑测试这类有副作用的工具直接不注册
        if (targets.any { it.supportsFileWrite } && !readOnly) {
            add(createWriteFileTool(targets, workspaceRepository, snapshots, conversationId))
            add(createEditFileTool(targets, workspaceRepository, snapshots, conversationId))
            add(createApplyPatchTool(targets, workspaceRepository, snapshots, conversationId))
        }
        add(createGitTool(targets, workspaceRepository, readOnly))
        if (!readOnly) add(createTestTool(targets, workspaceRepository))
        add(createShellTool(targets, workspaceRepository, cwds, readOnly))
    }
}

internal fun List<WorkspaceTarget>.workspaceNote(): String = if (size <= 1) {
    ""
} else {
    " Multiple workspaces are bound: ${joinToString { "${it.id} (${it.name})" }}." +
        " Set `workspace` to one of these IDs; defaults to ${first().id}."
}

internal fun JsonObjectBuilder.putWorkspaceProperty(targets: List<WorkspaceTarget>) {
    if (targets.size <= 1) return
    put("workspace", buildJsonObject {
        put("type", "string")
        put(
            "description",
            "Workspace ID to use. One of: ${targets.joinToString { "${it.id} (${it.name})" }}." +
                " Defaults to ${targets.first().id}. Prefer the ID; names may contain quotes."
        )
    })
}

internal fun JsonObject.resolveWorkspace(targets: List<WorkspaceTarget>): WorkspaceTarget {
    if (targets.size == 1) return targets.first()
    val requested = string("workspace")?.trim().orEmpty()
    if (requested.isBlank()) return targets.first()

    targets.firstOrNull { target ->
        target.id.equals(requested, ignoreCase = true) || target.name.equals(requested, ignoreCase = true)
    }?.let { return it }

    val normalized = requested.normalizeWorkspaceSelector()
    if (normalized.isNotEmpty()) {
        val matches = targets.filter { target ->
            target.id.normalizeWorkspaceSelector() == normalized ||
                target.name.normalizeWorkspaceSelector() == normalized
        }
        if (matches.size == 1) return matches.single()
        if (matches.size > 1) {
            error("Ambiguous workspace '$requested'. Use the workspace ID. Available: ${targets.availableWorkspaces()}")
        }
    }

    val idPrefixMatches = targets.filter { it.id.startsWith(requested, ignoreCase = true) }
    if (idPrefixMatches.size == 1) return idPrefixMatches.single()

    error("Unknown workspace '$requested'. Use the workspace ID. Available: ${targets.availableWorkspaces()}")
}

/** 审批回调不能抛异常，否则会中断整轮生成；解析失败时交给调用方按需要审批处理。 */
internal fun JsonObject.resolveWorkspaceOrNull(targets: List<WorkspaceTarget>): WorkspaceTarget? =
    runCatching { resolveWorkspace(targets) }.getOrNull()

private fun List<WorkspaceTarget>.availableWorkspaces(): String =
    joinToString { "${it.id} (${it.name})" }

/** 名称可能带引号，模型有时只保留其中一部分；比较时去掉各类引号并归一小写与空白。 */
private fun String.normalizeWorkspaceSelector(): String =
    filterNot { it in WORKSPACE_QUOTES }
        .replace(WHITESPACE, " ")
        .trim()
        .lowercase()

private val WORKSPACE_QUOTES = setOf('"', '\'', '“', '”', '‘', '’', '＂')
private val WHITESPACE = Regex("\\s+")

/**
 * 单条工具在当前执行权限下是否需要用户确认。
 *
 * [ExecutionPermission.READ_ONLY] 下写类工具根本不会注册, 这里保守地要求确认;
 * [ExecutionPermission.READ_WRITE] 只放行写文件, 命令类工具仍然确认;
 * [ExecutionPermission.FULL] 一律放行。
 */
// TODO(l3hub): 工作区详情页里的单工具审批开关只在 APPROVAL 档位生效，其它档位以档位为准，界面还没提示这一点
internal fun WorkspaceTarget.needsApproval(name: String): Boolean = when (executionPermission) {
    ExecutionPermission.FULL -> false
    ExecutionPermission.READ_WRITE ->
        if (name in WorkspaceFileWriteToolNames) false else WorkspaceToolDefaultApprovals[name] ?: true
    ExecutionPermission.READ_ONLY -> true
    ExecutionPermission.APPROVAL -> resolveWorkspaceToolApproval(name, approvalOverrides)
}

/**
 * 写文件类工具的审批: [ExecutionPermission.FULL] 全部放行; 其余档位下写到可写区之外一律确认,
 * 免得「免审批写入」被用来改系统目录。
 */
internal fun WorkspaceTarget.requiresWriteApproval(name: String, outsideWritableRoots: Boolean): Boolean =
    when (executionPermission) {
        ExecutionPermission.FULL -> false
        ExecutionPermission.READ_WRITE -> outsideWritableRoots
        ExecutionPermission.READ_ONLY -> true
        ExecutionPermission.APPROVAL -> needsApproval(name) || outsideWritableRoots
    }

/**
 * `workspace_shell` 的审批。
 *
 * 审批档位沿用旧规则 (破坏性命令必确认、只读命令免确认);
 * 只读档位不打扰用户, 非只读命令在执行时直接拒绝并告诉模型原因;
 * [ExecutionPermission.FULL] 连破坏性命令也不再拦, 这是用户在设置页显式选择的取舍。
 */
internal fun WorkspaceTarget.requiresShellApproval(command: String): Boolean {
    val risk = WorkspaceCommandPolicy.classify(command)
    return when (executionPermission) {
        ExecutionPermission.FULL -> false
        ExecutionPermission.READ_ONLY -> false
        ExecutionPermission.READ_WRITE -> true
        ExecutionPermission.APPROVAL -> when {
            // 破坏性命令始终要确认，用户关掉审批也不放行
            risk == WorkspaceCommandPolicy.Risk.DANGEROUS -> true
            !needsApproval("workspace_shell") -> false
            // 只读命令免审批：编码时大量 ls / grep / git diff，逐条确认没法用
            risk == WorkspaceCommandPolicy.Risk.READ_ONLY -> false
            else -> true
        }
    }
}

/** 「已修改文件」清单: 只带路径和是否新建, 没有内容快照 */
internal fun JsonObjectBuilder.putEditedFiles(files: List<JsonObject>) {
    if (files.isEmpty()) return
    put("editedFiles", buildJsonArray { files.forEach { add(it) } })
}

/** 单条「已修改文件」记录, [created] 为 null 表示拿不到创建时间, 不确定是不是本轮新建的 */
internal fun editedFileJson(path: String, created: Boolean? = null): JsonObject = buildJsonObject {
    put("path", path)
    if (created != null) put("created", created)
}

private val IMAGE_EXTENSIONS = setOf(
    "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "heif", "avif", "ico",
)

private fun String.isImagePath(): Boolean =
    substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

private fun createReadFileTool(
    targets: List<WorkspaceTarget>,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_read_file",
    description = """
        Read a file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use ${targets.first().layout.workspaceDir} for the workspace files area.
        Supports UTF-8 text files and image files (png, jpg, jpeg, gif, webp, bmp, svg, heic, heif, avif, ico).
        Returns at most `limit` lines starting at `offset` (1-based); check totalLines to page through large files.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                putPathProperty(targets.first().layout, required = true)
                put("offset", buildJsonObject {
                    put("type", "integer")
                    put("description", "1-based line number to start reading from. Defaults to 1.")
                })
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum number of lines to read. Defaults to $DEFAULT_READ_LINES, max $MAX_READ_LINES.")
                })
            },
            required = listOf("path"),
        )
    },
    needsApproval = { params ->
        params.jsonObject.resolveWorkspaceOrNull(targets)?.needsApproval("workspace_read_file") ?: true
    },
    isReadOnly = true,
    execute = {
        val params = it.jsonObject
        val target = params.resolveWorkspace(targets)
        val path = params.absolutePath("path")
        if (path.isImagePath()) {
            workspaceRepository.readImageInRootfs(target.id, path)
        } else {
            val text = workspaceRepository.readTextInRootfs(target.id, path)
            val page = pageText(
                text = text,
                offset = params.int("offset") ?: 1,
                limit = params.int("limit") ?: DEFAULT_READ_LINES,
            )
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("path", path)
                        put("text", page.text)
                        put("startLine", page.startLine)
                        put("endLine", page.endLine)
                        put("totalLines", page.totalLines)
                        if (page.truncated) put("truncated", true)
                        put("workspace", target.name)
                    }.toString()
                )
            )
        }
    },
)

private fun createWriteFileTool(
    targets: List<WorkspaceTarget>,
    workspaceRepository: WorkspaceRepository,
    snapshots: WorkspaceSnapshotStore?,
    conversationId: Uuid?,
) = Tool(
    name = "workspace_write_file",
    description = """
        Write a UTF-8 text file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use ${targets.first().layout.workspaceDir} for the workspace files area.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                putPathProperty(targets.first().layout, required = true)
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "UTF-8 text content to write")
                })
                put("overwrite", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether to overwrite an existing file. Defaults to true.")
                })
            },
            required = listOf("path", "text"),
        )
    },
    needsApproval = { params ->
        val target = params.jsonObject.resolveWorkspaceOrNull(targets)
        target == null || target.requiresWriteApproval(
            name = "workspace_write_file",
            outsideWritableRoots = params.jsonObject.pathOutsideWritableRoots(target.layout, "path"),
        )
    },
    execute = {
        val params = it.jsonObject
        val target = params.resolveWorkspace(targets)
        require(target.supportsFileWrite) {
            "Workspace '${target.name}' does not support file write tools"
        }
        val path = params.absolutePath("path")
        val text = params.string("text") ?: error("text is required")
        val overwrite = params["overwrite"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
        // 写之前先看文件在不在: 既是快照的判据, 也是「已修改文件」里的新建标记
        val existingSize = runCatching { workspaceRepository.absoluteFileSize(target.id, path) }.getOrNull()
        // 覆盖前留一份原内容，供聊天页撤销这一轮的修改
        var snapshotTurn: String? = null
        if (snapshots != null && conversationId != null) {
            val original = if (existingSize != null && existingSize <= MAX_SNAPSHOT_FILE_BYTES) {
                runCatching { workspaceRepository.readTextInRootfs(target.id, path) }.getOrNull()
            } else {
                null
            }
            if (existingSize == null || original != null) {
                val (area, relative) = resolveStorageArea(target.layout, path)
                snapshotTurn = runCatching {
                    snapshots.capture(conversationId, target.id, path, area, relative, original)
                }.onFailure { Log.w(TAG, "snapshot capture failed: $path", it) }
                    .getOrNull()
            }
        }
        val entry = workspaceRepository.writeAbsoluteText(target.id, path, text, overwrite)
        // 快照可能写不出来（SSH 工作区根本不做快照），所以另带一份仅路径的清单给聊天页
        val payload = buildJsonObject {
            entry.toJson().forEach { (key, value) -> put(key, value) }
            putEditedFiles(listOf(editedFileJson(entry.path, created = existingSize == null)))
            if (snapshotTurn != null) {
                put("snapshotTurn", snapshotTurn)
                put("conversationId", conversationId.toString())
            }
        }.toString()
        listOf(UIMessagePart.Text(payload))
    },
)

private fun createEditFileTool(
    targets: List<WorkspaceTarget>,
    workspaceRepository: WorkspaceRepository,
    snapshots: WorkspaceSnapshotStore?,
    conversationId: Uuid?,
) = Tool(
    name = "workspace_edit_file",
    description = """
        Edit a UTF-8 text file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use ${targets.first().layout.workspaceDir} for the workspace files area.
        Provide old_text and new_text. By default old_text must occur exactly once; set replace_all=true to replace every occurrence.
        If no exact match is found, whitespace-tolerant line matching is attempted automatically.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                putPathProperty(targets.first().layout, required = true)
                put("old_text", buildJsonObject {
                    put("type", "string")
                    put("description", "Exact text to replace")
                })
                put("new_text", buildJsonObject {
                    put("type", "string")
                    put("description", "Replacement text")
                })
                put("replace_all", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether to replace every occurrence. Defaults to false.")
                })
            },
            required = listOf("path", "old_text", "new_text"),
        )
    },
    needsApproval = { params ->
        val target = params.jsonObject.resolveWorkspaceOrNull(targets)
        target == null || target.requiresWriteApproval(
            name = "workspace_edit_file",
            outsideWritableRoots = params.jsonObject.pathOutsideWritableRoots(target.layout, "path"),
        )
    },
    execute = {
        val params = it.jsonObject
        val target = params.resolveWorkspace(targets)
        require(target.supportsFileWrite) {
            "Workspace '${target.name}' does not support file write tools"
        }
        val path = params.absolutePath("path")
        val oldText = params.string("old_text") ?: error("old_text is required")
        val newText = params.string("new_text") ?: error("new_text is required")
        val replaceAll = params["replace_all"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        require(oldText.isNotEmpty()) { "old_text must not be empty" }

        val original = workspaceRepository.readTextInRootfs(target.id, path)
        // 改之前留一份原内容，供聊天页撤销这一轮的修改
        var snapshotTurn: String? = null
        if (snapshots != null && conversationId != null) {
            val (area, relative) = resolveStorageArea(target.layout, path)
            snapshotTurn = runCatching {
                snapshots.capture(conversationId, target.id, path, area, relative, original)
            }.onFailure { Log.w(TAG, "snapshot capture failed: $path", it) }
                .getOrNull()
        }
        // 逐级尝试 exact -> line_trimmed -> block_anchor 替换器, 见 TextReplacers.kt
        val result = try {
            replaceText(original, oldText, newText, replaceAll)
        } catch (e: IllegalArgumentException) {
            error("${e.message} (path: $path)")
        }
        val entry = workspaceRepository.writeAbsoluteText(target.id, path, result.updated, overwrite = true)
        val diff = generateUnifiedDiff(original, result.updated, entry.path)
        listOf(
            UIMessagePart.Text(
                text = buildJsonObject {
                    put("path", entry.path)
                    put("replacements", result.replacements)
                    if (result.strategy != ExactReplacer.name) put("matchStrategy", result.strategy)
                    put("sizeBytes", entry.sizeBytes)
                    put("updatedAt", entry.updatedAt)
                    put("workspace", target.name)
                    putEditedFiles(listOf(editedFileJson(entry.path)))
                    snapshotTurn?.let {
                        put("snapshotTurn", it)
                        put("conversationId", conversationId.toString())
                    }
                }.toString(),
                // diff 存入 metadata 供 UI 渲染 diff view, 不会随工具结果发送给 API
                metadata = diff?.let { d -> DiffMetadata(diff = d).toMetadata() },
            )
        )
    },
)

private fun createApplyPatchTool(
    targets: List<WorkspaceTarget>,
    workspaceRepository: WorkspaceRepository,
    snapshots: WorkspaceSnapshotStore?,
    conversationId: Uuid?,
) = Tool(
    name = "workspace_apply_patch",
    description = """
        Apply a unified diff to the assistant's bound workspace Rootfs. Paths in the patch are relative to the workspace files root (${targets.first().layout.workspaceDir});
        absolute paths inside Rootfs are accepted too. Supports multiple files and multiple hunks per file, new files (/dev/null old path) and deletions.
        Hunks are matched by context; a missing or ambiguous context rejects the whole patch and nothing is written, so always read the file before patching.
        Returns per-file added/removed counts and any conflicts with hunk index and line. Use dry_run=true to validate without writing.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                put("patch", buildJsonObject {
                    put("type", "string")
                    put("description", "Unified diff text, e.g. --- a/src/Foo.kt / +++ b/src/Foo.kt / @@ -1,3 +1,4 @@ headers")
                })
                put("dry_run", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Only validate and report, do not write files. Defaults to false.")
                })
            },
            required = listOf("patch"),
        )
    },
    needsApproval = { params ->
        val obj = params.jsonObject
        val target = obj.resolveWorkspaceOrNull(targets)
        target == null || target.requiresWriteApproval(
            name = "workspace_apply_patch",
            outsideWritableRoots = obj.patchOutsideWritableRoots(target.layout),
        )
    },
    execute = {
        val params = it.jsonObject
        val target = params.resolveWorkspace(targets)
        require(target.supportsFileWrite) {
            "Workspace '${target.name}' does not support file write tools"
        }
        val patchText = params.string("patch") ?: error("patch is required")
        val dryRun = params["dry_run"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        val result = applyUnifiedPatch(
            target = target,
            workspaceRepository = workspaceRepository,
            snapshots = snapshots,
            conversationId = conversationId,
            patchText = patchText,
            dryRun = dryRun,
        )
        listOf(UIMessagePart.Text(result.toString()))
    },
)

private data class PlannedPatchWrite(
    val displayPath: String,
    val absolutePath: String,
    val original: String?,
    val content: String?,
    val added: Int,
    val removed: Int,
)

/** 补丁先全部校验再落盘：任何一个文件冲突都整体放弃，不留半截修改 */
private suspend fun applyUnifiedPatch(
    target: WorkspaceTarget,
    workspaceRepository: WorkspaceRepository,
    snapshots: WorkspaceSnapshotStore?,
    conversationId: Uuid?,
    patchText: String,
    dryRun: Boolean,
): JsonObject {
    val diffs = UnifiedDiff.parse(patchText)
    if (diffs.isEmpty()) {
        return buildJsonObject {
            put("ok", false)
            put("error", "No unified diff hunks found. Expected ---/+++ file headers and @@ hunks.")
        }
    }

    val writes = mutableListOf<PlannedPatchWrite>()
    val conflicts = mutableListOf<JsonObject>()
    val skipped = mutableListOf<JsonObject>()

    diffs.forEach { diff ->
        if (diff.isBinary) {
            skipped += buildJsonObject {
                put("path", diff.newPath ?: diff.oldPath.orEmpty())
                put("reason", "binary patch is not supported")
            }
            return@forEach
        }
        val rawPath = (diff.newPath ?: diff.oldPath).orEmpty()
        val absolutePath = runCatching { target.layout.resolvePatchPath(rawPath) }.getOrNull()
        if (absolutePath == null) {
            conflicts += buildJsonObject {
                put("path", rawPath)
                put("hunk", 0)
                put("line", 0)
                put("reason", "invalid path")
            }
            return@forEach
        }
        if (!target.layout.isWritable(absolutePath)) {
            conflicts += buildJsonObject {
                put("path", rawPath)
                put("hunk", 0)
                put("line", 0)
                put("reason", "path is outside the writable area")
            }
            return@forEach
        }
        val existing = runCatching {
            workspaceRepository.readTextInRootfs(target.id, absolutePath)
        }.getOrNull()
        val original = if (diff.isNewFile) {
            // /dev/null 开头但目标已存在：直接拒绝，否则会覆盖原文件，撤销时还会把它删掉
            if (existing != null) {
                conflicts += buildJsonObject {
                    put("path", rawPath)
                    put("hunk", 0)
                    put("line", 0)
                    put("reason", "file already exists; read it and send a normal diff instead of /dev/null")
                }
                return@forEach
            }
            null
        } else {
            if (existing == null) {
                conflicts += buildJsonObject {
                    put("path", rawPath)
                    put("hunk", 0)
                    put("line", 0)
                    put("reason", "file not found")
                }
                return@forEach
            }
            existing
        }
        when (val outcome = UnifiedDiff.apply(original.orEmpty(), diff)) {
            is ApplyOutcome.Success -> writes += PlannedPatchWrite(
                displayPath = rawPath,
                absolutePath = absolutePath,
                original = original,
                content = if (diff.isDeletedFile) null else outcome.content,
                added = outcome.added,
                removed = outcome.removed,
            )

            is ApplyOutcome.Conflict -> conflicts += buildJsonObject {
                put("path", outcome.path ?: rawPath)
                put("hunk", outcome.hunkIndex)
                put("line", outcome.line)
                put("reason", outcome.reason)
            }
        }
    }

    if (conflicts.isNotEmpty()) {
        return buildJsonObject {
            put("ok", false)
            put("conflicts", buildJsonArray { conflicts.forEach { add(it) } })
            put("skipped", buildJsonArray { skipped.forEach { add(it) } })
            put("hint", "Nothing was written. Read the current file content and regenerate the patch with matching context.")
        }
    }

    var snapshotTurn: String? = null
    if (!dryRun) {
        writes.forEach { write ->
            if (snapshots != null && conversationId != null) {
                val (area, relative) = resolveStorageArea(target.layout, write.absolutePath)
                snapshotTurn = runCatching {
                    snapshots.capture(
                        conversationId = conversationId,
                        workspaceId = target.id,
                        absolutePath = write.absolutePath,
                        area = area,
                        relativePath = relative,
                        original = write.original,
                    )
                }.onFailure { Log.w(TAG, "snapshot capture failed: ${write.absolutePath}", it) }
                    .getOrNull() ?: snapshotTurn
            }
            val content = write.content
            if (content == null) {
                val (area, relative) = resolveStorageArea(target.layout, write.absolutePath)
                workspaceRepository.deleteFile(target.id, area, relative, recursive = false)
            } else {
                workspaceRepository.writeAbsoluteText(target.id, write.absolutePath, content, overwrite = true)
            }
        }
    }

    return buildJsonObject {
        put("ok", true)
        put("dryRun", dryRun)
        put("applied", writes.size)
        put("files", buildJsonArray {
            writes.forEach { write ->
                add(buildJsonObject {
                    put("path", write.displayPath)
                    put("added", write.added)
                    put("removed", write.removed)
                    if (write.content == null) put("deleted", true)
                    if (write.original == null) put("created", true)
                })
            }
        })
        if (skipped.isNotEmpty()) {
            put("skipped", buildJsonArray { skipped.forEach { add(it) } })
        }
        if (!dryRun) {
            putEditedFiles(writes.map { write -> editedFileJson(write.displayPath, created = write.original == null) })
        }
        if (snapshotTurn != null && conversationId != null) {
            put("snapshotTurn", snapshotTurn)
            put("conversationId", conversationId.toString())
        }
    }
}

/** 补丁里的路径是仓库相对路径, 统一映射到 Rootfs 内的工作区目录 */
private fun WorkspaceLayout.resolvePatchPath(raw: String): String {
    val cleaned = raw.replace('\\', '/').trim()
    require(cleaned.isNotEmpty()) { "empty path in patch" }
    require(!cleaned.contains('\u0000')) { "invalid character in patch path" }
    val normalized = if (cleaned.startsWith("/")) {
        cleaned
    } else {
        "${workspaceDir.trimEnd('/')}/${cleaned.trimStart('/')}"
    }
    require(normalized.split('/').none { it == ".." }) { "patch path must not escape the workspace: $raw" }
    return normalized
}

private fun kotlinx.serialization.json.JsonObject.patchOutsideWritableRoots(layout: WorkspaceLayout): Boolean =
    runCatching {
        val text = string("patch").orEmpty()
        UnifiedDiff.parse(text).any { diff ->
            val raw = diff.newPath ?: diff.oldPath ?: return@any false
            !layout.isWritable(layout.resolvePatchPath(raw))
        }
    }.getOrDefault(true)

private fun createShellTool(
    targets: List<WorkspaceTarget>,
    workspaceRepository: WorkspaceRepository,
    workspaceCwds: Map<String, String> = emptyMap(),
    readOnly: Boolean = false,
) = Tool(
    name = "workspace_shell",
    description = buildString {
        append(
            "Run a shell command in the assistant's bound workspace Rootfs. The workspace files area is mounted at ${targets.first().layout.workspaceDir}. "
        )
        append("Use cwd for a path relative to the workspace files root. ")
        if (workspaceCwds.isNotEmpty()) {
            append("Defaults to the directory picked for each workspace. ")
        }
        append("Set background=true for long-running commands (dev servers, watchers): it returns a pid and a log file immediately.")
        append("Requires Rootfs to be installed and ready.")
        if (readOnly) {
            append(
                " Only read-only commands are allowed in this mode: anything that writes files, installs packages" +
                    " or changes state is rejected."
            )
        }
        append(targets.workspaceNote())
    },
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                put("command", buildJsonObject {
                    put("type", "string")
                    put("description", "Shell command to run")
                })
                put("cwd", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "Working directory relative to the workspace files root. " +
                            "Defaults to the directory picked for that workspace, or root."
                    )
                })
                put("timeout", buildJsonObject {
                    put("type", "integer")
                    put(
                        "description",
                        "Command timeout in seconds. Defaults to 30, max $SHELL_TIMEOUT_MAX_SECONDS."
                    )
                })
                put("background", buildJsonObject {
                    put("type", "boolean")
                    put(
                        "description",
                        "Run the command detached and return at once with a pid and a log path. " +
                            "Read the log with `tail -n 100 <log>` and stop it with `kill -- -<pid>`. " +
                            "On local Rootfs workspaces the job may end when the container exits; SSH workspaces keep it running."
                    )
                })
            },
            required = listOf("command"),
        )
    },
    needsApproval = { params ->
        val target = params.jsonObject.resolveWorkspaceOrNull(targets)
        val command = params.jsonObject.string("command").orEmpty()
        target == null || target.requiresShellApproval(command)
    },
    execute = {
        val params = it.jsonObject
        val target = params.resolveWorkspace(targets)
        val command = params.string("command") ?: error("command is required")
        val fallbackCwd = workspaceCwds[target.id].orEmpty()
        val cwd = target.layout.relativeToWorkspace(params.string("cwd") ?: fallbackCwd)
        val timeoutMillis = params.string("timeout")?.toLongOrNull()
            ?.coerceIn(1L, SHELL_TIMEOUT_MAX_SECONDS)
            ?.times(1_000L)
            ?: WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS
        val background = params["background"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        if (readOnly && !WorkspaceCommandPolicy.isReadOnly(command)) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put(
                            "error",
                            "Only read-only commands are allowed right now. Turn plan mode off or raise the " +
                                "execution permission in the assistant settings to run this."
                        )
                        put("command", command)
                    }.toString()
                )
            )
        }
        if (background) {
            val log = "/tmp/l3hub-job-${System.currentTimeMillis()}.log"
            val quoted = command.replace("'", "'\\''")
            // 有 setsid 就脱离会话，没有就退回 nohup；整段放子 shell 里后台执行，$! 就是实际进程号
            val wrapped = "if command -v setsid >/dev/null 2>&1; then " +
                "exec setsid nohup sh -c '$quoted' > '$log' 2>&1; " +
                "else exec nohup sh -c '$quoted' > '$log' 2>&1; fi & echo \$!"
            val started = workspaceRepository.executeCommand(target.id, wrapped, cwd, 15_000)
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("background", true)
                        put("pid", started.stdout.trim())
                        put("log", log)
                        put("workspace", target.name)
                        put("hint", "Read output with workspace_shell + `tail -n 100 $log`; stop with `kill -- -<pid>`.")
                    }.toString()
                )
            )
        } else {
            // 只能用 shell 改文件的工作区（SSH）没有文件快照，跑完扫一份本轮改过的路径给聊天页
            // TODO(l3hub): 只对 supportsFileWrite=false 的工作区扫；本地工作区用 shell 改文件（git commit、格式化）
            //  目前不会出「已修改文件」卡片，等快照能覆盖这类改动再打开
            val scan = if (target.supportsFileWrite) null else ShellChangeScan.create(target.layout.workspaceDir)
            val result = workspaceRepository.executeCommand(
                target.id,
                scan?.wrap(command) ?: command,
                cwd,
                timeoutMillis,
            )
            val changed = scan?.collect { collectCommand ->
                runCatching {
                    workspaceRepository.executeCommand(target.id, collectCommand, "", SHELL_SCAN_TIMEOUT_MILLIS)
                }.getOrNull()?.stdout
            }.orEmpty()
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("exitCode", result.exitCode)
                        put("stdout", result.stdout)
                        put("stderr", result.stderr)
                        put("timedOut", result.timedOut)
                        if (result.truncated) put("truncated", true)
                        put("workspace", target.name)
                        putEditedFiles(changed.map { editedFileJson(it.path, it.created) })
                    }.toString()
                )
            )
        }
    },
)

private fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

private suspend fun WorkspaceRepository.readTextInRootfs(
    workspaceId: String,
    path: String,
): String = readRootfsBuffer(workspaceId, path).toString(Charsets.UTF_8.name())

/**
 * 按环境内绝对路径读入内存。路径映射交给工作区后端, 本地工作区由它统一处理
 * /workspace、bind mount 与 Rootfs 内部路径。
 */
private suspend fun WorkspaceRepository.readRootfsBuffer(
    workspaceId: String,
    path: String,
): ByteArrayOutputStream {
    val size = absoluteFileSize(workspaceId, path)
    require(size <= MAX_READ_FILE_BYTES) {
        "File is too large to read: $path (${size / 1024 / 1024}MB, max ${MAX_READ_FILE_BYTES / 1024 / 1024}MB). Use shell commands like head, tail, or grep to read parts of it."
    }
    return ByteArrayOutputStream(size.toInt()).also { exportAbsoluteFile(workspaceId, path, it) }
}

private suspend fun WorkspaceRepository.readImageInRootfs(
    workspaceId: String,
    path: String,
): List<UIMessagePart> {
    val bytes = readRootfsBuffer(workspaceId, path).toByteArray()

    val filesManager = getKoin().get<FilesManager>()
    val uris = filesManager.createChatFilesByByteArrays(listOf(bytes))
    return listOf(
        UIMessagePart.Image(url = uris.first().toString()),
        UIMessagePart.Text(
            buildJsonObject {
                put("path", path)
                put("description", "Image file read successfully")
            }.toString()
        ),
    )
}

private fun kotlinx.serialization.json.JsonObject.absolutePath(name: String): String {
    val path = string(name)?.replace('\\', '/')?.trim() ?: error("$name is required")
    require(path.isNotBlank()) { "$name is required" }
    require(path.startsWith("/")) { "$name must be an absolute path inside Rootfs" }
    require(!path.contains('\u0000')) { "$name contains invalid character" }
    return path
}

// 免强制审批的可写安全区由工作区后端给出, 本地为工作区文件目录、临时目录和技能目录
private fun kotlinx.serialization.json.JsonElement.pathOutsideWritableRoots(
    layout: WorkspaceLayout,
    name: String,
): Boolean =
    runCatching {
        !layout.isWritable(jsonObject.absolutePath(name))
    }.getOrDefault(true)

private fun JsonObjectBuilder.putPathProperty(layout: WorkspaceLayout, required: Boolean) {
    put("path", buildJsonObject {
        put("type", "string")
        put(
            "description",
            if (required) {
                "Absolute path inside Rootfs. Use ${layout.workspaceDir} for the workspace files area."
            } else {
                "Optional absolute path inside Rootfs. Use ${layout.workspaceDir} for the workspace files area."
            }
        )
    })
}

private fun WorkspaceFileEntry.toJson() = buildJsonObject {
    put("path", path)
    put("name", name)
    put("isDirectory", isDirectory)
    put("sizeBytes", sizeBytes)
    put("updatedAt", updatedAt)
}

// ---------------------------------------------------------------------------
// 检索类工具：列目录 / glob / grep
//
// 都走 backend 的 listFiles + exportAbsoluteFile，本地与 SSH 工作区共用同一套实现；
// 遍历有层数、条目数、单文件大小上限，避免在大仓库上把手机拖死。
// ---------------------------------------------------------------------------

private data class SearchMatch(val path: String, val line: Int, val text: String)

internal data class PagedText(
    val text: String,
    val startLine: Int,
    val endLine: Int,
    val totalLines: Int,
    val truncated: Boolean,
)

/** 按 1-based 行号切片；offset 超出总行数时返回空文本，不报错 */
internal fun pageText(text: String, offset: Int, limit: Int): PagedText {
    val lines = if (text.isEmpty()) {
        emptyList()
    } else {
        text.split('\n').let { parts -> if (parts.last().isEmpty()) parts.dropLast(1) else parts }
    }
    if (lines.isEmpty()) return PagedText(text = "", startLine = 1, endLine = 0, totalLines = 0, truncated = false)

    val start = offset.coerceAtLeast(1)
    val count = limit.coerceIn(1, MAX_READ_LINES)
    if (start > lines.size) {
        return PagedText(text = "", startLine = start, endLine = start - 1, totalLines = lines.size, truncated = false)
    }
    val end = (start + count - 1).coerceAtMost(lines.size)
    return PagedText(
        text = lines.subList(start - 1, end).joinToString("\n"),
        startLine = start,
        endLine = end,
        totalLines = lines.size,
        truncated = end < lines.size,
    )
}

/** 环境内绝对路径落在文件区还是 Rootfs 区；返回值可直接交给 backend.listFiles */
internal fun resolveStorageArea(layout: WorkspaceLayout, path: String): Pair<WorkspaceStorageArea, String> {
    val normalized = path.trim().trimEnd('/').ifBlank { "/" }
    val root = layout.workspaceDir.trimEnd('/').ifBlank { "/" }
    return when {
        normalized == root -> WorkspaceStorageArea.FILES to ""
        root != "/" && normalized.startsWith("$root/") ->
            WorkspaceStorageArea.FILES to normalized.removePrefix("$root/")
        normalized == "/" -> WorkspaceStorageArea.LINUX to ""
        else -> WorkspaceStorageArea.LINUX to normalized.trimStart('/')
    }
}

private fun joinAbsolute(directory: String, name: String): String {
    val base = directory.trimEnd('/')
    return if (base.isEmpty()) "/$name" else "$base/$name"
}

/** 预编译 glob，避免逐个文件重新解析；pattern 非法时 isValid 为 false */
internal class GlobMatcher(pattern: String) {
    private val matcher = runCatching {
        FileSystems.getDefault().getPathMatcher("glob:${pattern.trim()}")
    }.getOrNull()
    private val matchFileNameOnly = !pattern.contains('/')

    val isValid: Boolean = matcher != null

    fun matches(relativePath: String, fileName: String): Boolean {
        val compiled = matcher ?: return false
        val path = runCatching { Paths.get(relativePath.trimStart('/')) }.getOrNull() ?: return false
        return compiled.matches(path) || (matchFileNameOnly && compiled.matches(path.fileName))
    }
}

/**
 * 按目录逐层展开，跳过依赖和构建目录；条目数和层数都有上限。
 * 每进一个目录读一次它自己的 .gitignore，规则向下继承，命中忽略的条目直接跳过。
 */
private suspend fun walkWorkspace(
    repository: WorkspaceRepository,
    target: WorkspaceTarget,
    root: String,
    maxEntries: Int,
    onEntry: suspend (absolutePath: String, entry: WorkspaceFileEntry) -> Boolean,
) {
    val start = root.trim().trimEnd('/').ifBlank { "/" }
    val queue = ArrayDeque<Triple<String, Int, List<GitIgnoreRule>>>()
    queue.add(Triple(start, 0, emptyList()))
    var visited = 0
    while (queue.isNotEmpty() && visited < maxEntries) {
        val (directory, depth, inheritedRules) = queue.removeFirst()
        val (area, relative) = resolveStorageArea(target.layout, directory)
        val entries = runCatching { repository.listFiles(target.id, area, relative) }
            .getOrElse { emptyList() }
            .sortedWith(compareByDescending<WorkspaceFileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })

        val gitignore = entries.firstOrNull { !it.isDirectory && it.name == ".gitignore" }
        val rules = if (gitignore != null) {
            val text = repository.readTextForSearch(
                workspaceId = target.id,
                path = joinAbsolute(directory, gitignore.name),
                sizeBytes = gitignore.sizeBytes,
            )
            if (text != null) {
                inheritedRules + GitIgnoreMatcher.parse(directory, text)
            } else {
                inheritedRules
            }
        } else {
            inheritedRules
        }

        for (entry in entries) {
            visited++
            val absolute = joinAbsolute(directory, entry.name)
            // 规则的基准目录就是它所在的目录，直接用绝对路径判断即可
            if (GitIgnoreMatcher.isIgnored(rules, absolute, entry.isDirectory)) {
                if (visited >= maxEntries) return
                continue
            }
            if (!onEntry(absolute, entry)) return
            if (entry.isDirectory && depth < MAX_SEARCH_DEPTH && entry.name !in SEARCH_SKIPPED_DIRS) {
                queue.add(Triple(absolute, depth + 1, rules))
            }
            if (visited >= maxEntries) return
        }
    }
}

/** 只读小文本文件；超过大小上限、读失败或像二进制时返回 null */
private suspend fun WorkspaceRepository.readTextForSearch(
    workspaceId: String,
    path: String,
    sizeBytes: Long,
): String? {
    if (sizeBytes > MAX_GREP_FILE_BYTES) return null
    val bytes = runCatching { readRootfsBuffer(workspaceId, path) }.getOrNull() ?: return null
    val array = bytes.toByteArray()
    if (array.take(4096).any { it == 0.toByte() }) return null
    return array.toString(Charsets.UTF_8)
}

/** 仓库地图：文件树 + 顶层符号，省掉逐个 read 的往返 */
private fun createRepoMapTool(
    targets: List<WorkspaceTarget>,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_repo_map",
    description = """
        Build a compact map of the workspace: file tree grouped by directory with top-level symbols per file
        (Kotlin/Java/Python/TS/JS/Go/Rust/Swift/Ruby/C#/C++). Respects .gitignore.
        Use it before reading files in a large repository; pass path to map a subdirectory and max_files to bound the scan.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                putPathProperty(targets.first().layout, required = false)
                put("max_files", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum files to scan. Defaults to 300, max 800.")
                })
            },
        )
    },
    needsApproval = { params ->
        params.jsonObject.resolveWorkspaceOrNull(targets)?.needsApproval("workspace_repo_map") ?: true
    },
    isReadOnly = true,
    execute = {
        val params = it.jsonObject
        val target = params.resolveWorkspace(targets)
        val root = params.string("path")?.let { params.absolutePath("path") } ?: target.layout.workspaceDir
        val maxFiles = params.string("max_files")?.toIntOrNull()?.coerceIn(1, MAX_REPO_MAP_FILES) ?: DEFAULT_REPO_MAP_FILES
        val prefix = target.layout.workspaceDir.trimEnd('/') + "/"

        val files = mutableListOf<RepoMapFile>()
        walkWorkspace(
            repository = workspaceRepository,
            target = target,
            root = root,
            maxEntries = MAX_SEARCH_ENTRIES,
        ) { absolute, entry ->
            if (!entry.isDirectory && files.size < maxFiles && absolute.isRepoMapSource()) {
                val text = workspaceRepository.readTextForSearch(target.id, absolute, entry.sizeBytes)
                if (text != null) {
                    val relative = absolute.removePrefix(prefix)
                    files += RepoMapFile(
                        path = relative,
                        lines = text.count { it == '\n' } + 1,
                        symbols = RepoMap.extractSymbols(relative, text),
                    )
                }
            }
            true
        }

        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("path", root)
                    put("fileCount", files.size)
                    put("truncated", files.size >= maxFiles)
                    put("map", RepoMap.render(files))
                }.toString()
            )
        )
    },
)

private const val DEFAULT_REPO_MAP_FILES = 300
private const val MAX_REPO_MAP_FILES = 800

private fun String.isRepoMapSource(): Boolean =
    substringAfterLast('.', "").lowercase() in setOf(
        "kt", "kts", "java", "py", "ts", "tsx", "js", "jsx", "go", "rs", "swift", "rb", "cs", "cpp", "h",
    )

private fun createListTool(
    targets: List<WorkspaceTarget>,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_list",
    description = """
        List a directory in the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Returns name, absolute path, type, size and mtime, directories first. Defaults to the workspace files area.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                putPathProperty(targets.first().layout, required = false)
            },
        )
    },
    needsApproval = { params ->
        params.jsonObject.resolveWorkspaceOrNull(targets)?.needsApproval("workspace_list") ?: true
    },
    isReadOnly = true,
    execute = {
        val params = it.jsonObject
        val target = params.resolveWorkspace(targets)
        val path = params.string("path")?.let { params.absolutePath("path") } ?: target.layout.workspaceDir
        val (area, relative) = resolveStorageArea(target.layout, path)
        val all = workspaceRepository.listFiles(target.id, area, relative)
            .sortedWith(compareByDescending<WorkspaceFileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        val entries = all.take(MAX_LIST_ENTRIES)
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("path", path)
                    put("entries", buildJsonArray {
                        entries.forEach { entry ->
                            add(buildJsonObject {
                                put("name", entry.name)
                                put("path", joinAbsolute(path, entry.name))
                                put("type", if (entry.isDirectory) "directory" else "file")
                                put("sizeBytes", entry.sizeBytes)
                                put("updatedAt", entry.updatedAt)
                            })
                        }
                    })
                    if (all.size > entries.size) put("truncated", true)
                    put("workspace", target.name)
                }.toString()
            )
        )
    },
)

private fun createGlobTool(
    targets: List<WorkspaceTarget>,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_glob",
    description = """
        Find files by glob pattern inside the assistant's bound workspace Rootfs, for example `**/*.kt` or `src/**/*.md`.
        Skips .git, node_modules, build and other dependency or output directories.
        Returns absolute paths. Defaults to the workspace files area; paths must be absolute inside Rootfs.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                put("pattern", buildJsonObject {
                    put("type", "string")
                    put("description", "Glob pattern matched against paths relative to `path`, e.g. **/*.kt")
                })
                putPathProperty(targets.first().layout, required = false)
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum number of files to return. Defaults to $MAX_GLOB_RESULTS, max 2000.")
                })
            },
            required = listOf("pattern"),
        )
    },
    needsApproval = { params ->
        params.jsonObject.resolveWorkspaceOrNull(targets)?.needsApproval("workspace_glob") ?: true
    },
    isReadOnly = true,
    execute = {
        val params = it.jsonObject
        val target = params.resolveWorkspace(targets)
        val pattern = params.string("pattern")?.trim().orEmpty()
        require(pattern.isNotEmpty()) { "pattern is required" }
        val root = params.string("path")?.let { params.absolutePath("path") } ?: target.layout.workspaceDir
        val limit = (params.int("limit") ?: MAX_GLOB_RESULTS).coerceIn(1, 2000)
        val matcher = GlobMatcher(pattern)
        require(matcher.isValid) { "Invalid glob pattern: $pattern" }

        val matches = mutableListOf<WorkspaceFileEntry>()
        val prefix = root.trimEnd('/')
        walkWorkspace(workspaceRepository, target, root, MAX_SEARCH_ENTRIES) { absolute, entry ->
            if (!entry.isDirectory) {
                val relative = absolute.removePrefix(prefix).trimStart('/')
                if (matcher.matches(relative, entry.name)) {
                    matches += entry.copy(path = absolute)
                }
            }
            matches.size < limit
        }
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("pattern", pattern)
                    put("path", root)
                    put("files", buildJsonArray {
                        matches.forEach { entry ->
                            add(buildJsonObject {
                                put("path", entry.path)
                                put("sizeBytes", entry.sizeBytes)
                            })
                        }
                    })
                    if (matches.size >= limit) put("truncated", true)
                    put("workspace", target.name)
                }.toString()
            )
        )
    },
)

private fun createGrepTool(
    targets: List<WorkspaceTarget>,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_grep",
    description = """
        Search file contents inside the assistant's bound workspace Rootfs. Returns absolute file path, 1-based line number and the matched line.
        Skips binary files, files larger than 512KB, and .git, node_modules, build and other dependency or output directories.
        Use `include` to restrict file names, e.g. `*.kt`. Defaults to the workspace files area; paths must be absolute inside Rootfs.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "Text to search for, or a regular expression when regex=true")
                })
                putPathProperty(targets.first().layout, required = false)
                put("include", buildJsonObject {
                    put("type", "string")
                    put("description", "Only search files whose name matches this glob, e.g. *.kt")
                })
                put("regex", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Treat query as a regular expression. Defaults to false (literal text).")
                })
                put("ignore_case", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Case-insensitive match. Defaults to true.")
                })
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum number of matching lines. Defaults to $MAX_GREP_RESULTS, max 1000.")
                })
            },
            required = listOf("query"),
        )
    },
    needsApproval = { params ->
        params.jsonObject.resolveWorkspaceOrNull(targets)?.needsApproval("workspace_grep") ?: true
    },
    isReadOnly = true,
    execute = {
        val params = it.jsonObject
        val target = params.resolveWorkspace(targets)
        val query = params.string("query").orEmpty()
        require(query.isNotEmpty()) { "query is required" }
        val root = params.string("path")?.let { params.absolutePath("path") } ?: target.layout.workspaceDir
        val limit = (params.int("limit") ?: MAX_GREP_RESULTS).coerceIn(1, 1000)
        val include = params.string("include")?.trim()?.takeIf { it.isNotEmpty() }
        val includeMatcher = include?.let(::GlobMatcher)
        require(includeMatcher?.isValid != false) { "Invalid include glob: $include" }
        val options = if (params.boolean("ignore_case") ?: true) setOf(RegexOption.IGNORE_CASE) else emptySet()
        val matcher = runCatching {
            if (params.boolean("regex") == true) Regex(query, options) else Regex(Regex.escape(query), options)
        }.getOrElse { error("Invalid regular expression: ${it.message}") }

        val matches = mutableListOf<SearchMatch>()
        var scanned = 0
        var scanLimitReached = false
        walkWorkspace(workspaceRepository, target, root, MAX_SEARCH_ENTRIES) { absolute, entry ->
            val included = includeMatcher == null || includeMatcher.matches(entry.name, entry.name)
            if (!entry.isDirectory && entry.sizeBytes <= MAX_GREP_FILE_BYTES && included) {
                if (scanned >= MAX_GREP_SCANNED_FILES) {
                    scanLimitReached = true
                    return@walkWorkspace false
                }
                scanned++
                val text = workspaceRepository.readTextForSearch(target.id, absolute, entry.sizeBytes)
                if (text != null) {
                    text.lineSequence().forEachIndexed { index, line ->
                        if (matches.size < limit && matcher.containsMatchIn(line)) {
                            matches += SearchMatch(absolute, index + 1, line.take(MAX_GREP_LINE_CHARS))
                        }
                    }
                }
            }
            matches.size < limit
        }
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("query", query)
                    put("path", root)
                    put("matches", buildJsonArray {
                        matches.forEach { match ->
                            add(buildJsonObject {
                                put("path", match.path)
                                put("line", match.line)
                                put("text", match.text)
                            })
                        }
                    })
                    put("scannedFiles", scanned)
                    if (matches.size >= limit || scanLimitReached) put("truncated", true)
                    put("workspace", target.name)
                }.toString()
            )
        )
    },
)

private fun kotlinx.serialization.json.JsonObject.int(name: String): Int? =
    this[name]?.jsonPrimitive?.contentOrNull?.trim()?.toIntOrNull()

private fun kotlinx.serialization.json.JsonObject.boolean(name: String): Boolean? =
    this[name]?.jsonPrimitive?.contentOrNull?.trim()?.toBooleanStrictOrNull()
