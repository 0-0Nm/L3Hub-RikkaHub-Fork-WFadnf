package me.rerere.rikkahub.ui.components.message

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Share08
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.workspace.WorkspaceSnapshotStore
import me.rerere.rikkahub.ui.terminal.Card
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.compose.koinInject
import java.io.File
import kotlin.uuid.Uuid

private const val DEFAULT_VISIBLE_COUNT = 3
// workspace_shell 也列进来：SSH 之类只能用命令改文件的工作区没有写文件工具，
// 本轮改了什么只能从命令结果里的 editedFiles 读出来
private val WORKSPACE_FILE_TOOL_NAMES = setOf(
    "workspace_write_file",
    "workspace_edit_file",
    "workspace_apply_patch",
    "workspace_shell",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditedFilesList(
    parts: List<UIMessagePart>,
    assistant: Assistant?,
    conversationId: Uuid? = null,
    isLastMessage: Boolean = false,
) {
    val workspaceId = assistant?.primaryWorkspaceId?.toString() ?: return
    val editedFiles = remember(parts) {
        parts.filterIsInstance<UIMessagePart.Tool>()
            .filter { it.toolName in WORKSPACE_FILE_TOOL_NAMES && it.isExecuted }
            .flatMap { tool -> tool.editedPaths() }
            .distinct()
    }
    if (editedFiles.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workspaceRepository: WorkspaceRepository = koinInject()
    val snapshots: WorkspaceSnapshotStore = koinInject()

    // 工具结果里带着本轮快照 key，界面据此提供撤销
    val snapshotTurn = remember(parts) {
        parts.filterIsInstance<UIMessagePart.Tool>()
            .filter { it.toolName in WORKSPACE_FILE_TOOL_NAMES && it.isExecuted }
            .mapNotNull { tool ->
                val text = (tool.output.firstOrNull() as? UIMessagePart.Text)?.text
                    ?: return@mapNotNull null
                val obj = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
                    ?: return@mapNotNull null
                val turn = obj["snapshotTurn"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val conversation = obj["conversationId"]?.jsonPrimitive?.contentOrNull
                    ?: return@mapNotNull null
                conversation to turn
            }
            .lastOrNull()
    }
    var showRevertDialog by remember { mutableStateOf(false) }
    // 撤销成功时 store 会自增版本号：同轮的其他卡片也会一起失效，不再挂着「还原 0 个文件」的按钮
    val revertEpoch by snapshots.revertEpoch.collectAsState()
    // 快照可能写失败（工具结果里没有 snapshotTurn），所以以磁盘上的快照为准：
    // 先信工具结果里的 key；没有时只在最后一条消息上按当前轮次反查，避免挂到旧轮次。
    val revertTarget by produceState<Pair<Uuid, String>?>(
        null,
        snapshotTurn,
        revertEpoch,
        conversationId,
        isLastMessage,
    ) {
        // fork 出来的会话会原样带走工具输出里的 key，跨会话的快照不认
        val fromPayload = snapshotTurn
            ?.takeIf { conversationId == null || it.first == conversationId.toString() }
            ?.let { (conversation, turn) ->
                runCatching { Uuid.parse(conversation) }.getOrNull()?.let { it to turn }
            }
        val candidate = fromPayload ?: if (isLastMessage && conversationId != null) {
            conversationId to snapshots.turnKey(conversationId)
        } else {
            null
        }
        value = if (candidate != null && runCatching {
                snapshots.hasTurn(candidate.first, candidate.second)
            }.getOrDefault(false)
        ) {
            candidate
        } else {
            null
        }
    }

    var selectedPath by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val visibleFiles = if (expanded) editedFiles else editedFiles.take(DEFAULT_VISIBLE_COUNT)
    val hasMore = editedFiles.size > DEFAULT_VISIBLE_COUNT

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val path = selectedPath.also { selectedPath = null } ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val outputStream = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri) }
                    ?: return@runCatching
                outputStream.use { output ->
                    workspaceRepository.exportAbsoluteFile(workspaceId, path, output)
                }
            }
        }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        visibleFiles.forEach { path ->
            val fileName = remember(path) { path.substringAfterLast('/') }
            ChatMessageFileChip(
                text = fileName,
                onClick = { selectedPath = path },
                icon = {
                    Icon(
                        imageVector = HugeIcons.File02,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
        if (hasMore && !expanded) {
            ChatMessageFileChip(
                text = "+${editedFiles.size - DEFAULT_VISIBLE_COUNT}",
                onClick = { expanded = true },
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
    }

    val revertKey = revertTarget
    if (revertKey != null) {
        TextButton(
            onClick = { showRevertDialog = true },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        ) {
            Icon(
                imageVector = HugeIcons.Refresh01,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.workspace_revert_turn),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }

    // TODO(l3hub): SSH 工作区也会走这里（用命令改文件），diff/提交按钮暂时照旧；撤销按钮只依赖快照，没有快照就不会出现
    TurnDiffActions(
        workspaceId = workspaceId,
        files = editedFiles,
    )

    if (showRevertDialog && revertKey != null) {
        RikkaConfirmDialog(
            show = true,
            title = stringResource(R.string.workspace_revert_turn),
            confirmText = stringResource(R.string.workspace_revert_confirm_action),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                showRevertDialog = false
                val target = revertKey
                scope.launch {
                    snapshots.revert(target.first, target.second)
                        .onSuccess { count ->
                            // 目录已被别处撤掉时 count=0，此时不该报「已还原 0 个文件」
                            val text = if (count > 0) {
                                context.getString(R.string.workspace_revert_done, count)
                            } else {
                                context.getString(R.string.workspace_revert_nothing)
                            }
                            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.workspace_revert_failed, error.message ?: ""),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                }
            },
            onDismiss = { showRevertDialog = false },
            text = { Text(stringResource(R.string.workspace_revert_confirm)) },
        )
    }

    if (selectedPath != null) {
        val path = selectedPath!!
        val fileName = remember(path) { path.substringAfterLast('/') }
        ModalBottomSheet(
            onDismissRequest = { selectedPath = null },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Card(
                    onClick = {
                        val p = selectedPath ?: return@Card
                        exportLauncher.launch(p.substringAfterLast('/'))
                    },
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = HugeIcons.FileImport,
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp),
                        )
                        Text(
                            text = stringResource(R.string.common_export),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Card(
                    onClick = {
                        val p = selectedPath ?: return@Card
                        selectedPath = null
                        scope.launch {
                            runCatching {
                                val dir = File(context.cacheDir, "workspace_share").apply { mkdirs() }
                                val file = File(dir, p.substringAfterLast('/'))
                                file.outputStream().use { output ->
                                    workspaceRepository.exportAbsoluteFile(workspaceId, p, output)
                                }
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Share08,
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp),
                        )
                        Text(
                            text = stringResource(R.string.common_share),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 优先看工具结果里的 `editedFiles`：工作区工具都会带一份仅路径的清单，
 * SSH 工作区没有快照，本轮改了什么只能靠它列出来。
 * 老消息里没有这份清单，再退回原来的取法。
 */
private fun UIMessagePart.Tool.editedPaths(): List<String> {
    val text = (output.firstOrNull() as? UIMessagePart.Text)?.text
    val obj = text?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    val listed = (obj?.get("editedFiles") as? JsonArray).orEmpty().mapNotNull { element ->
        (element as? JsonObject)?.get("path")?.jsonPrimitive?.contentOrNull
    }
    if (listed.isNotEmpty()) return listed
    // 命令本身不改文件的写法没有输入 path，也没有 files 列表
    if (toolName == "workspace_shell") return emptyList()
    if (toolName != "workspace_apply_patch") {
        val path = (inputAsJson() as? JsonObject)?.get("path")?.jsonPrimitive?.contentOrNull
        return listOfNotNull(path)
    }
    val files = obj?.get("files") as? JsonArray ?: return emptyList()
    return files.mapNotNull { element ->
        (element as? JsonObject)?.get("path")?.jsonPrimitive?.contentOrNull
    }
}
