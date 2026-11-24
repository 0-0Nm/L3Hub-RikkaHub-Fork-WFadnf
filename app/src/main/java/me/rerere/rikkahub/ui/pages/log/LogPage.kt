package me.rerere.rikkahub.ui.pages.log

import android.content.ClipData
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Download04
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpDiagnostics
import me.rerere.rikkahub.data.datastore.LogPreferences
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.JsonTree
import me.rerere.rikkahub.ui.terminal.Card
import me.rerere.rikkahub.ui.terminal.HorizontalDivider
import me.rerere.rikkahub.ui.terminal.IconButton
import me.rerere.rikkahub.ui.terminal.Switch
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.utils.JsonInstantPretty
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun LogPage() {
    var logs by remember { mutableStateOf(Logging.getRecentLogs()) }
    var showMcp by remember { mutableStateOf(false) }
    val diagnostics by McpDiagnostics.entries.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var requestLoggingEnabled by remember { mutableStateOf(Logging.isRequestLoggingEnabled()) }
    var maxLogBytes by remember { mutableStateOf(Logging.getMaxLogBytes()) }
    val context = LocalContext.current
    val logPreferences = remember(context) { LogPreferences(context) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val exporter = rememberLogExporter()
    // 导出顺序和列表顺序取同一份，免得"看到的是降序、导出的是另一个顺序"
    val sortedLogs = remember(logs) { logs.sortedByDescending { it.timestamp } }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Logs") },
                navigationIcon = { BackButton() },
                actions = {
                    if (!showMcp) IconButton(
                        onClick = { exporter.export(sortedLogs) },
                        enabled = sortedLogs.isNotEmpty(),
                    ) {
                        Icon(HugeIcons.Download04, "Export all logs")
                    }
                    IconButton(
                        onClick = {
                            if (showMcp) McpDiagnostics.clear() else {
                                Logging.clear()
                                logs = Logging.getRecentLogs()
                            }
                        }
                    ) {
                        Icon(HugeIcons.Delete01, null)
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showMcp, onClick = { showMcp = false; logs = Logging.getRecentLogs() },
                    label = { Text(stringResource(R.string.setting_page_request_logs)) })
                FilterChip(selected = showMcp, onClick = { showMcp = true },
                    label = { Text(stringResource(R.string.mcp_diagnostics_tab)) })
            }
            if (showMcp) {
                TextButton(
                    onClick = {
                        val summary = diagnostics.asReversed().joinToString("\n") { it.summary() }
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("MCP diagnostics", summary))) }
                    },
                    enabled = diagnostics.isNotEmpty(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text(stringResource(R.string.mcp_diagnostics_copy)) }
                LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (diagnostics.isEmpty()) item { Text(stringResource(R.string.mcp_diagnostics_empty)) }
                    items(diagnostics) { entry ->
                        Text(entry.summary(), style = MaterialTheme.typography.bodySmall, fontFamily = JetbrainsMono)
                    }
                }
            } else UnifiedLogList(
            logs = sortedLogs,
            requestLoggingEnabled = requestLoggingEnabled,
            onRequestLoggingChange = {
                requestLoggingEnabled = it
                Logging.setRequestLoggingEnabled(it)
                logPreferences.requestLoggingEnabled = it
            },
            maxLogBytes = maxLogBytes,
            onMaxLogBytesChange = {
                maxLogBytes = it
                Logging.setMaxLogBytes(it)
                logPreferences.maxBytes = it
                // 调低上限会立刻删掉旧记录，列表跟着重取，免得屏幕上还留着缓冲里已经没有的条目
                logs = Logging.getRecentLogs()
            },
            onExport = { exporter.export(it) },
            modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun UnifiedLogList(
    logs: List<LogEntry>,
    requestLoggingEnabled: Boolean,
    onRequestLoggingChange: (Boolean) -> Unit,
    maxLogBytes: Long,
    onMaxLogBytesChange: (Long) -> Unit,
    onExport: (List<LogEntry>) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedLog by remember { mutableStateOf<LogEntry.RequestLog?>(null) }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            RequestLoggingSwitchCard(
                enabled = requestLoggingEnabled,
                onEnabledChange = onRequestLoggingChange
            )
        }

        // 文本日志同样受这个上限管，所以不做"关掉记录就隐藏"：藏起来会变成看不见但在生效
        item {
            LogSizeLimitCard(
                maxBytes = maxLogBytes,
                onMaxBytesChange = onMaxLogBytesChange,
            )
        }

        items(logs, key = { it.id }, contentType = { it.javaClass.simpleName }) { log ->
            when (log) {
                is LogEntry.RequestLog -> RequestLogCard(
                    log = log,
                    onClick = {
                        selectedLog = log
                        scope.launch { sheetState.show() }
                    },
                    onExport = { onExport(listOf(log)) },
                )

                is LogEntry.TextLog -> TextLogCard(
                    log = log,
                    onExport = { onExport(listOf(log)) },
                )
            }
        }
    }

    selectedLog?.let { log ->
        ModalBottomSheet(
            onDismissRequest = { selectedLog = null },
            sheetState = sheetState
        ) {
            RequestLogDetail(log)
        }
    }
}

@Composable
private fun RequestLoggingSwitchCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.log_page_record_requests),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.log_page_record_requests_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
}

@Composable
private fun RequestLogCard(
    log: LogEntry.RequestLog,
    onClick: () -> Unit,
    onExport: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = log.method,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = dateFormat.format(Date(log.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = log.url,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetbrainsMono,
                    maxLines = 2
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    log.responseCode?.let { code ->
                        Text(
                            text = "Status: $code",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (code in 200..299) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    log.durationMs?.let { duration ->
                        Text(
                            text = "${duration}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                log.error?.let { error ->
                    Text(
                        text = "Error: $error",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            ExportLogButton(onClick = onExport)
        }
    }
}

@Composable
private fun RequestLogDetail(log: LogEntry.RequestLog) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) }
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    SelectionContainer {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Request Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                DetailSection("Time", dateFormat.format(Date(log.timestamp)))
            }

            item {
                DetailSection("URL", log.url)
            }

            item {
                DetailSection("Method", log.method)
            }

            log.responseCode?.let { code ->
                item {
                    DetailSection("Status Code", code.toString())
                }
            }

            log.durationMs?.let { duration ->
                item {
                    DetailSection("Duration", "${duration}ms")
                }
            }

            log.error?.let { error ->
                item {
                    DetailSection("Error", error)
                }
            }

            if (log.requestHeaders.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Text(
                        text = "Request Headers",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                log.requestHeaders.forEach { (key, value) ->
                    item {
                        HeaderItem(key, value)
                    }
                }
            }

            log.requestBody?.let { body ->
                item {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Request Body",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        clipboard.setClipEntry(
                                            ClipEntry(ClipData.newPlainText("Request Body", body))
                                        )
                                    } catch (e: RuntimeException) {
                                        // 内容过大时 Binder 会抛出 TransactionTooLargeException (被包装为 RuntimeException)
                                        Toast.makeText(
                                            context,
                                            "Copy failed: content too large for clipboard",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = HugeIcons.Copy01,
                                contentDescription = stringResource(R.string.copy)
                            )
                        }
                    }
                    val jsonElement = remember(body) {
                        runCatching { JsonInstantPretty.parseToJsonElement(body) }.getOrNull()
                    }
                    if (jsonElement != null) {
                        JsonTree(
                            json = jsonElement,
                            modifier = Modifier.padding(top = 4.dp),
                            initialExpandLevel = 2
                        )
                    } else {
                        Text(
                            text = body,
                            fontFamily = JetbrainsMono,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            if (log.responseHeaders.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Text(
                        text = "Response Headers",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                log.responseHeaders.forEach { (key, value) ->
                    item {
                        HeaderItem(key, value)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = JetbrainsMono
        )
    }
}

@Composable
private fun HeaderItem(key: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = JetbrainsMono
        )
    }
}

@Composable
private fun TextLogCard(log: LogEntry.TextLog, onExport: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.padding(
                        start = 12.dp,
                        top = 12.dp,
                        end = 4.dp,
                        bottom = 12.dp,
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = log.tag,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = dateFormat.format(Date(log.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = log.message,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetbrainsMono
                    )
                }
            }

            ExportLogButton(onClick = onExport)
        }
    }
}

/** 卡片右侧的「导出这一条」按钮，两种卡片共用。 */
@Composable
private fun ExportLogButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.padding(top = 2.dp, end = 2.dp),
    ) {
        Icon(
            imageVector = HugeIcons.Download04,
            contentDescription = "Export this log",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 单条和全部共用一条导出路径：按当前时间取文件名，选定位置后把内容写进去，最后报一句结果。
 *
 * 内容先放在 pending 里，是因为系统文件选择器异步返回 Uri，回调里拿不到点击那一刻的数据。
 */
@Stable
private class LogExporter(
    private val write: (List<LogEntry>) -> Unit,
) {
    fun export(logs: List<LogEntry>) = write(logs)
}

@Composable
private fun rememberLogExporter(): LogExporter {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = pending
        pending = null
        if (uri != null && content != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                            output.write(content.toByteArray())
                        } ?: error("openOutputStream returned null for $uri")
                    }.isSuccess
                }
                Toast.makeText(
                    context,
                    if (saved) "Logs exported" else "Export failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    return remember(launcher) {
        LogExporter { logs ->
            pending = LogExport.toJson(logs)
            launcher.launch(LogExport.fileName())
        }
    }
}

/** 上限的档位：32 KB 起，一路翻倍到 1024 MB，滑块上一格就是一档。 */
private val LOG_SIZE_OPTIONS: List<Long> = buildList {
    var bytes = Logging.MIN_MAX_LOG_BYTES
    while (bytes <= 1024L * 1024 * 1024) {
        add(bytes)
        bytes *= 2
    }
}

private fun formatLogSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "${bytes / (1024L * 1024)} MB"
    bytes >= 1024L -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

private fun nearestLogSizeIndex(bytes: Long): Int {
    val index = LOG_SIZE_OPTIONS.indexOfFirst { it >= bytes }
    return if (index >= 0) index else LOG_SIZE_OPTIONS.lastIndex
}

@Composable
private fun LogSizeLimitCard(
    maxBytes: Long,
    onMaxBytesChange: (Long) -> Unit,
) {
    // 拖动时只有 SliderState 在变，松手才落盘：滑过一整条轨道会写十几次磁盘
    val sliderState = remember(maxBytes) {
        SliderState(
            value = nearestLogSizeIndex(maxBytes).toFloat(),
            trackRange = 0f..LOG_SIZE_OPTIONS.lastIndex.toFloat(),
            steps = LOG_SIZE_OPTIONS.size - 2,
        )
    }
    val selected = LOG_SIZE_OPTIONS[sliderState.value.roundToInt().coerceIn(0, LOG_SIZE_OPTIONS.lastIndex)]

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Log size limit",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Oldest logs are removed once the limit is reached",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatLogSize(selected),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                state = sliderState,
                onValueChange = { sliderState.value = it },
                onValueChangeFinished = {
                    val index = sliderState.value.roundToInt().coerceIn(0, LOG_SIZE_OPTIONS.lastIndex)
                    sliderState.value = index.toFloat()
                    onMaxBytesChange(LOG_SIZE_OPTIONS[index])
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
