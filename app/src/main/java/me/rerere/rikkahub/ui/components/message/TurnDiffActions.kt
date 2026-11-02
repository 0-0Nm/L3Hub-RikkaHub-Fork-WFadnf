package me.rerere.rikkahub.ui.components.message

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.workspace.GitFileStat
import me.rerere.rikkahub.data.workspace.UnifiedDiff
import me.rerere.rikkahub.data.workspace.WorkspaceGit
import androidx.compose.material3.Surface
import me.rerere.ui.richtext.DiffView
import org.koin.compose.koinInject

/**
 * 一轮改动结束后的 git 审阅入口：看整体 diff、按文件丢弃、一键提交。
 * 和 workspace_git 工具共用 [WorkspaceGit], 行为一致。
 */
@Composable
internal fun TurnDiffActions(
    workspaceId: String,
    files: List<String>,
    modifier: Modifier = Modifier,
) {
    if (files.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository: WorkspaceRepository = koinInject()
    val git = remember(repository) { WorkspaceGit(repository) }
    // write/edit 工具给的是 Rootfs 绝对路径，git 用的是仓库相对路径，先统一
    val normalizedFiles by produceState(files, workspaceId) {
        value = files
        val layout = withContext(Dispatchers.IO) {
            runCatching { repository.layout(workspaceId) }.getOrNull()
        }
        if (layout != null) {
            val prefix = layout.workspaceDir.trimEnd('/') + "/"
            value = files.map { it.removePrefix(prefix) }
        }
    }

    var showSheet by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<List<GitFileStat>>(emptyList()) }
    var statusLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var expandedPath by remember { mutableStateOf<String?>(null) }
    var expandedDiff by remember { mutableStateOf<String?>(null) }
    var pendingDiscard by remember { mutableStateOf<String?>(null) }
    var showCommit by remember { mutableStateOf(false) }
    var commitMessage by remember { mutableStateOf("") }
    var committing by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        withContext(Dispatchers.IO) {
            runCatching { git.status(workspaceId) }
                .onSuccess { status ->
                    statusLabels = status.files.associate { it.path to it.statusLabel }
                }
            runCatching { git.numstat(workspaceId, paths = normalizedFiles) }
                .onSuccess { stats = it }
        }
        loading = false
    }

    LaunchedEffect(showSheet) {
        if (showSheet) reload()
    }
    LaunchedEffect(expandedPath) {
        val path = expandedPath ?: return@LaunchedEffect
        expandedDiff = null
        expandedDiff = withContext(Dispatchers.IO) {
            runCatching { git.diff(workspaceId, paths = listOf(path)) }.getOrDefault("")
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = { showSheet = true },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        ) {
            Text("本轮改动", style = MaterialTheme.typography.labelMedium)
        }
        TextButton(
            onClick = { showCommit = true },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        ) {
            Text("提交", style = MaterialTheme.typography.labelMedium)
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "本轮改动",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                loading = true
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { git.discard(workspaceId, paths = normalizedFiles) }.getOrNull()
                                }
                                loading = false
                                Toast.makeText(
                                    context,
                                    if (result?.ok == true) "已丢弃本轮改动" else "丢弃失败：${result?.output.orEmpty().take(120)}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                reload()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = HugeIcons.Refresh01,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("全部丢弃", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (loading && stats.isEmpty()) {
                    Text("读取中…", style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(normalizedFiles) { path ->
                        val stat = stats.firstOrNull { it.path == path }
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedPath = if (expandedPath == path) null else path }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = statusLabels[path] ?: "M",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(
                                    text = path.substringAfterLast('/'),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = if (stat == null || stat.untracked) "新文件" else "+${stat.added} -${stat.removed}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { pendingDiscard = path }) {
                                    Text("丢弃", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (expandedPath == path) {
                                val diff = expandedDiff
                                if (diff == null) {
                                    Text("读取 diff…", style = MaterialTheme.typography.bodySmall)
                                } else if (diff.isBlank()) {
                                    Text("无 diff（可能为新文件）", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    val fileDiff = remember(diff) { UnifiedDiff.parse(diff).firstOrNull() }
                                    val hunks = fileDiff?.hunks.orEmpty()
                                    if (hunks.isEmpty()) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            DiffView(diff = diff, maxLines = 400, modifier = Modifier.fillMaxWidth())
                                        }
                                    } else {
                                        hunks.forEachIndexed { index, hunk ->
                                            val hunkText = remember(diff, index) {
                                                buildString {
                                                    appendLine("@@ -${hunk.oldStart},${hunk.oldCount} +${hunk.newStart},${hunk.newCount} @@")
                                                    hunk.lines.forEach { appendLine(it) }
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Hunk ${index + 1} · 自第 ${hunk.oldStart} 行起",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                TextButton(
                                                    onClick = {
                                                        scope.launch {
                                                            val result = withContext(Dispatchers.IO) {
                                                                runCatching {
                                                                    git.discardHunk(workspaceId, path = path, hunkIndex = index)
                                                                }.getOrNull()
                                                            }
                                                            Toast.makeText(
                                                                context,
                                                                if (result?.ok == true) "已回退该 Hunk" else "回退失败：${result?.output.orEmpty().take(100)}",
                                                                Toast.LENGTH_SHORT,
                                                            ).show()
                                                            expandedDiff = withContext(Dispatchers.IO) {
                                                                runCatching { git.diff(workspaceId, paths = listOf(path)) }.getOrDefault("")
                                                            }
                                                            reload()
                                                        }
                                                    },
                                                ) { Text("丢弃", style = MaterialTheme.typography.labelSmall) }
                                            }
                                            Surface(
                                                shape = MaterialTheme.shapes.small,
                                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                DiffView(diff = hunkText, maxLines = 60, showFileHeader = false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingDiscard != null) {
        val path = pendingDiscard!!
        AlertDialog(
            onDismissRequest = { pendingDiscard = null },
            title = { Text("丢弃改动") },
            text = { Text("将 ${path.substringAfterLast('/')} 恢复到 HEAD？未跟踪的新文件将被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDiscard = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { git.discard(workspaceId, paths = listOf(path)) }.getOrNull()
                            }
                            Toast.makeText(
                                context,
                                if (result?.ok == true) "已恢复" else "恢复失败：${result?.output.orEmpty().take(120)}",
                                Toast.LENGTH_SHORT,
                            ).show()
                            reload()
                        }
                    },
                ) { Text("丢弃") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDiscard = null }) { Text("取消") }
            },
        )
    }

    if (showCommit) {
        AlertDialog(
            onDismissRequest = { if (!committing) showCommit = false },
            title = { Text("提交改动") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "将先执行 git add -A，再使用以下信息提交。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text("提交信息") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = commitMessage.isNotBlank() && !committing,
                    onClick = {
                        val message = commitMessage.trim()
                        committing = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { git.commit(workspaceId, message = message) }.getOrNull()
                            }
                            committing = false
                            if (result?.ok == true) {
                                showCommit = false
                                commitMessage = ""
                                Toast.makeText(context, result.output.lineSequence().firstOrNull() ?: "已提交", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "提交失败：${result?.output.orEmpty().take(160)}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                ) { Text(if (committing) "提交中…" else "提交") }
            },
            dismissButton = {
                TextButton(onClick = { showCommit = false }, enabled = !committing) { Text("取消") }
            },
        )
    }
}
