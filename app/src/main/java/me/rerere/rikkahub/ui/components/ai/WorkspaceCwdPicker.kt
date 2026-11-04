package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowTurnBackward
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.WorkspaceCwd
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.components.ui.shadow.FilledTonalButton
import me.rerere.rikkahub.ui.terminal.HorizontalDivider
import me.rerere.rikkahub.ui.terminal.IconButton
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.compose.koinInject
import java.util.concurrent.CancellationException

@Composable
fun WorkspaceCwdPickerSheet(
    workspaceId: String,
    workspaceDir: String,
    currentCwd: String?,
    onSelectCwd: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val workspaceRepository: WorkspaceRepository = koinInject()

    var browsePath by remember { mutableStateOf(WorkspaceCwd.normalize(workspaceDir, currentCwd).orEmpty()) }
    var entries by remember { mutableStateOf<List<WorkspaceFileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var truncated by remember { mutableStateOf(false) }
    // 默认 sheetState 允许半展开，show() 会停到半屏：看起来就是「打开后往下掉一截」
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    LaunchedEffect(browsePath) {
        loading = true
        loadFailed = false
        truncated = false
        // 换目录时先清空，免得加载期间还留着上一层的条目
        entries = emptyList()
        try {
            val result = withContext(Dispatchers.IO) {
                workspaceRepository.listFiles(workspaceId, WorkspaceStorageArea.FILES, browsePath)
            }
            // 后端为了判断截断会一次给到 2000 条，下拉只铺前 MAX_VISIBLE_ENTRIES 条
            val sorted = result
                .sortedWith(compareByDescending<WorkspaceFileEntry> { it.isDirectory }.thenBy { it.name })
            truncated = sorted.count { it.isDirectory } > MAX_VISIBLE_ENTRIES
            entries = sorted.take(MAX_VISIBLE_ENTRIES)
            loading = false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 连接断开/路径不存在时别伪装成「没有子目录」
            entries = emptyList()
            loadFailed = true
            loading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        // 面板高度只看窗口、不看内容：目录数怎么变都不会重算锚点往上/往下跳
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.workspace_cwd_select_directory),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = stringResource(R.string.workspace_cwd_select_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    enabled = browsePath.isNotBlank(),
                    onClick = {
                        browsePath = browsePath.substringBeforeLast('/', missingDelimiterValue = "")
                    },
                ) {
                    Icon(HugeIcons.ArrowTurnBackward, contentDescription = null)
                }
                Text(
                    text = WorkspaceCwd.absolute(workspaceDir, browsePath),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 逐级返回之外再给一个直接回工作区根的入口
                TextButton(
                    enabled = browsePath.isNotBlank(),
                    onClick = { browsePath = "" },
                ) {
                    Text(stringResource(R.string.workspace_cwd_root))
                }
            }

            HorizontalDivider()

            val dirs = remember(entries) { entries.filter { it.isDirectory } }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(dirs, key = { it.path }) { entry ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = entry.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = HugeIcons.Folder01,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            browsePath = entry.path
                        },
                    )
                }

                if (loading) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                } else if (loadFailed) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.workspace_cwd_load_failed),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else if (dirs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.workspace_cwd_no_subdirectories),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (!loading && !loadFailed && dirs.isNotEmpty() && truncated) {
                    item {
                        Text(
                            text = stringResource(R.string.workspace_cwd_truncated, MAX_VISIBLE_ENTRIES),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentCwd != null) {
                    TextButton(onClick = {
                        onSelectCwd(null)
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.workspace_cwd_reset))
                    }
                }
                FilledTonalButton(onClick = {
                    // 存相对路径：工作区目录改名后仍然有效
                    onSelectCwd(browsePath.ifBlank { null })
                    onDismiss()
                }) {
                    Text(stringResource(R.string.workspace_cwd_set))
                }
            }
        }
    }
}

private const val MAX_VISIBLE_ENTRIES = 500
