package me.rerere.rikkahub.ui.pages.assistant.detail

import android.text.format.DateUtils
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.MemoryConsolidationStatus
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AutoCompressTarget
import me.rerere.rikkahub.data.model.AutoCompressThreshold
import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.model.groupByDirectory
import me.rerere.rikkahub.data.model.memoryTitleOf
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.rikkahub.ui.components.ui.glassSegmentedButtonColors
import me.rerere.rikkahub.ui.components.ui.shadow.FilledTonalButton
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.memory.resultText
import me.rerere.rikkahub.ui.terminal.IconButton
import me.rerere.rikkahub.ui.terminal.RadioButton
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// 记忆文件放第一页：从侧边栏的整理入口进来时能直接看到整理状态
private enum class MemoryTab(
    override val icon: ImageVector,
    @param:StringRes private val labelRes: Int,
) : PagerTab {
    FILES(HugeIcons.Brain02, R.string.assistant_page_manage_memory_title),
    SETTINGS(HugeIcons.Settings03, R.string.settings);

    override val label: String
        @Composable get() = stringResource(labelRes)
}

@Composable
fun AssistantMemoryPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val consolidation by vm.memoryConsolidation.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pagerState = rememberPagerState { MemoryTab.entries.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val memoryRepository: MemoryRepository = koinInject()

    // 导出整个记忆库为 JSON；导入时按 path 覆盖同名文件、其余新增
    val exportMemory = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val files = memoryRepository.getFiles(vm.memoryId)
                val json = buildJsonObject {
                    put("version", 1)
                    put("memoryId", vm.memoryId)
                    putJsonArray("files") {
                        files.forEach { file ->
                            addJsonObject {
                                put("path", file.path)
                                put("content", file.content)
                            }
                        }
                    }
                }
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toString().toByteArray())
                } ?: error("cannot open output stream")
            }.onSuccess {
                Toast.makeText(
                    context,
                    context.getString(R.string.memory_export_done),
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.memory_export_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val importMemory = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("cannot open input stream")
                val files = Json.parseToJsonElement(text).jsonObject["files"]?.jsonArray
                    ?: error("missing files array")
                files.forEach { element ->
                    val obj = element.jsonObject
                    val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    memoryRepository.writeFile(vm.memoryId, path, content)
                }
            }.onSuccess {
                Toast.makeText(
                    context,
                    context.getString(R.string.memory_import_done),
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.memory_import_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_memory))
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    if (MemoryTab.entries[pagerState.currentPage] == MemoryTab.FILES) {
                        IconButton(onClick = {
                            exportMemory.launch(
                                "memory-${assistant.name.ifBlank { "assistant" }}-${System.currentTimeMillis()}.json"
                            )
                        }) {
                            Icon(HugeIcons.ArrowDown01, contentDescription = stringResource(R.string.memory_export))
                        }
                        IconButton(onClick = { importMemory.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                            Icon(HugeIcons.ArrowUp01, contentDescription = stringResource(R.string.memory_import))
                        }
                        IconButton(onClick = { navController.navigate(Screen.MemoryFile(vm.memoryId, null)) }) {
                            Icon(HugeIcons.Add01, contentDescription = stringResource(R.string.add))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PagerTabs(
                pagerState = pagerState,
                tabs = MemoryTab.entries,
                modifier = Modifier.padding(bottom = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (MemoryTab.entries[page]) {
                    MemoryTab.FILES -> MemoryFilesTab(
                        memories = memories,
                        consolidation = consolidation.takeIf { assistant.enableMemory },
                        onOrganizeMemory = { vm.organizeMemory() },
                        onOpenMemory = { path -> navController.navigate(Screen.MemoryFile(vm.memoryId, path)) },
                        onDeleteMemory = { vm.deleteMemory(it) },
                        onOpenSettings = {
                            scope.launch { pagerState.animateScrollToPage(MemoryTab.SETTINGS.ordinal) }
                        },
                    )

                    MemoryTab.SETTINGS -> MemorySettingsTab(
                        assistant = assistant,
                        onUpdateAssistant = { vm.update(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MemorySettingsTab(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
) {
    var showTimeReminderIntervalDialog by remember(assistant.id) { mutableStateOf(false) }
    var timeReminderIntervalInput by remember(assistant.id) { mutableStateOf("") }
    var showAutoCompressThresholdDialog by remember(assistant.id) { mutableStateOf(false) }
    var showAutoCompressTargetDialog by remember(assistant.id) { mutableStateOf(false) }
    var autoCompressTargetInput by remember(assistant.id) { mutableStateOf("") }
    var autoCompressTargetMode by remember(assistant.id) { mutableStateOf(assistant.autoCompressTarget) }
    val settings = LocalSettings.current
    val dreamingModelState = rememberModelListState(
        modelId = assistant.dreamingModelId,
        providers = settings.providers,
        type = ModelType.CHAT,
    )
    ModelListSheet(
        state = dreamingModelState,
        onSelect = { onUpdateAssistant(assistant.copy(dreamingModelId = it.id)) },
    )

    if (showTimeReminderIntervalDialog) {
        val interval = timeReminderIntervalInput.toIntOrNull()?.takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { showTimeReminderIntervalDialog = false },
            title = { Text(stringResource(R.string.assistant_page_time_reminder_interval)) },
            text = {
                TextField(
                    value = timeReminderIntervalInput,
                    onValueChange = { timeReminderIntervalInput = it },
                    label = { Text(stringResource(R.string.assistant_page_time_reminder_interval_label)) },
                    supportingText = { Text(stringResource(R.string.assistant_page_time_reminder_interval_hint)) },
                    isError = interval == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = interval != null,
                    onClick = {
                        interval?.let {
                            onUpdateAssistant(assistant.copy(timeReminderIntervalMinutes = it))
                        }
                        showTimeReminderIntervalDialog = false
                    },
                ) {
                    Text(stringResource(R.string.assistant_page_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimeReminderIntervalDialog = false }) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            },
        )
    }

    if (showAutoCompressThresholdDialog) {
        AlertDialog(
            onDismissRequest = { showAutoCompressThresholdDialog = false },
            title = { Text(stringResource(R.string.assistant_page_memory_auto_compress_threshold)) },
            text = {
                Column {
                    AutoCompressThreshold.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateAssistant(assistant.copy(autoCompressThreshold = option))
                                    showAutoCompressThresholdDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = assistant.autoCompressThreshold == option,
                                onClick = null,
                            )
                            Text(
                                text = option.name,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAutoCompressThresholdDialog = false }) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            },
        )
    }

    if (showAutoCompressTargetDialog) {
        val parsed = autoCompressTargetInput.toIntOrNull()
        val minValue = if (autoCompressTargetMode == AutoCompressTarget.MESSAGES) 1 else 100
        val valid = parsed != null && parsed >= minValue
        AlertDialog(
            onDismissRequest = { showAutoCompressTargetDialog = false },
            title = { Text(stringResource(R.string.assistant_page_memory_auto_compress_target)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AutoCompressTarget.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                colors = glassSegmentedButtonColors(),
                                selected = autoCompressTargetMode == mode,
                                onClick = {
                                    autoCompressTargetMode = mode
                                    val current = autoCompressTargetInput.toIntOrNull() ?: 0
                                    autoCompressTargetInput = when (mode) {
                                        AutoCompressTarget.MESSAGES ->
                                            if (current in 1..200) current.toString() else "8"
                                        AutoCompressTarget.TOKENS ->
                                            if (current >= 1000) current.toString() else "16000"
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, AutoCompressTarget.entries.size),
                            ) {
                                Text(
                                    stringResource(
                                        if (mode == AutoCompressTarget.MESSAGES) {
                                            R.string.assistant_page_memory_auto_compress_mode_messages
                                        } else {
                                            R.string.assistant_page_memory_auto_compress_mode_tokens
                                        }
                                    )
                                )
                            }
                        }
                    }
                    TextField(
                        value = autoCompressTargetInput,
                        onValueChange = { autoCompressTargetInput = it },
                        label = { Text(stringResource(R.string.assistant_page_memory_auto_compress_target_label)) },
                        isError = !valid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        parsed?.let {
                            onUpdateAssistant(
                                assistant.copy(
                                    autoCompressTarget = autoCompressTargetMode,
                                    autoCompressTargetValue = it,
                                )
                            )
                        }
                        showAutoCompressTargetDialog = false
                    },
                ) {
                    Text(stringResource(R.string.assistant_page_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutoCompressTargetDialog = false }) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CardGroup {
            switchItem(
                checked = assistant.enableMemory,
                onCheckedChange = {
                    onUpdateAssistant(
                        assistant.copy(
                            enableMemory = it
                        )
                    )
                },
                supportingContent = { Text(stringResource(R.string.assistant_page_memory_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_memory)) },
            )
            switchItem(
                checked = assistant.useGlobalMemory,
                onCheckedChange = {
                    onUpdateAssistant(
                        assistant.copy(
                            useGlobalMemory = it
                        )
                    )
                },
                enabled = assistant.enableMemory,
                supportingContent = { Text(stringResource(R.string.assistant_page_global_memory_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_global_memory)) },
            )
            switchItem(
                checked = assistant.autoConsolidateMemory,
                onCheckedChange = {
                    onUpdateAssistant(
                        assistant.copy(
                            autoConsolidateMemory = it
                        )
                    )
                },
                enabled = assistant.enableMemory,
                supportingContent = { Text(stringResource(R.string.assistant_page_memory_auto_dream_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_memory_auto_dream)) },
            )
            item(
                onClick = { dreamingModelState.open() },
                headlineContent = { Text(stringResource(R.string.assistant_page_dreaming_model)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_dreaming_model_desc)) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = dreamingModelState.currentModel?.displayName
                                ?: stringResource(R.string.assistant_page_dreaming_model_follow_global),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (assistant.dreamingModelId != null) {
                            IconButton(
                                onClick = { onUpdateAssistant(assistant.copy(dreamingModelId = null)) },
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Cancel01,
                                    contentDescription = stringResource(R.string.assistant_page_dreaming_model_follow_global),
                                )
                            }
                        }
                    }
                },
            )
            switchItem(
                checked = assistant.enableRecentChatsReference,
                onCheckedChange = {
                    onUpdateAssistant(
                        assistant.copy(
                            enableRecentChatsReference = it
                        )
                    )
                },
                supportingContent = { Text(stringResource(R.string.assistant_page_recent_chats_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_recent_chats)) },
            )
        }

        CardGroup {
            switchItem(
                checked = assistant.autoCompress,
                onCheckedChange = {
                    onUpdateAssistant(
                        assistant.copy(
                            autoCompress = it
                        )
                    )
                },
                supportingContent = { Text(stringResource(R.string.assistant_page_memory_auto_compress_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_memory_auto_compress)) },
            )
            if (assistant.autoCompress) {
                switchItem(
                    checked = assistant.autoCompressLoop,
                    onCheckedChange = {
                        onUpdateAssistant(assistant.copy(autoCompressLoop = it))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.assistant_page_memory_auto_compress_loop_desc))
                    },
                    headlineContent = { Text(stringResource(R.string.assistant_page_memory_auto_compress_loop)) },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.assistant_page_memory_auto_compress_threshold)) },
                    supportingContent = { Text(stringResource(R.string.assistant_page_memory_auto_compress_threshold_desc)) },
                    trailingContent = {
                        Text(
                            text = assistant.autoCompressThreshold.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    onClick = { showAutoCompressThresholdDialog = true },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.assistant_page_memory_auto_compress_target)) },
                    supportingContent = { Text(stringResource(R.string.assistant_page_memory_auto_compress_target_desc)) },
                    trailingContent = {
                        Text(
                            text = when (assistant.autoCompressTarget) {
                                AutoCompressTarget.MESSAGES -> stringResource(
                                    R.string.assistant_page_memory_auto_compress_messages,
                                    assistant.autoCompressTargetValue,
                                )
                                AutoCompressTarget.TOKENS -> stringResource(
                                    R.string.assistant_page_memory_auto_compress_tokens,
                                    assistant.autoCompressTargetValue,
                                )
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    onClick = {
                        autoCompressTargetMode = assistant.autoCompressTarget
                        autoCompressTargetInput = assistant.autoCompressTargetValue.toString()
                        showAutoCompressTargetDialog = true
                    },
                )
            }
        }

        CardGroup {
            switchItem(
                checked = assistant.enableTimeReminder,
                onCheckedChange = {
                    onUpdateAssistant(
                        assistant.copy(
                            enableTimeReminder = it
                        )
                    )
                },
                supportingContent = { Text(stringResource(R.string.assistant_page_time_reminder_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_time_reminder)) },
            )
            if (assistant.enableTimeReminder) {
                item(
                    headlineContent = { Text(stringResource(R.string.assistant_page_time_reminder_interval)) },
                    supportingContent = { Text(stringResource(R.string.assistant_page_time_reminder_interval_desc)) },
                    trailingContent = {
                        Text(
                            text = stringResource(
                                R.string.assistant_page_time_reminder_interval_value,
                                assistant.timeReminderIntervalMinutes
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    onClick = {
                        timeReminderIntervalInput = assistant.timeReminderIntervalMinutes.toString()
                        showTimeReminderIntervalDialog = true
                    },
                )
            }
        }
    }
}

@Composable
private fun MemoryFilesTab(
    memories: List<MemoryFile>,
    // 为 null 时不显示整理入口
    consolidation: MemoryConsolidationStatus?,
    onOrganizeMemory: () -> Unit,
    onOpenMemory: (path: String) -> Unit,
    onDeleteMemory: (MemoryFile) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var pendingDeleteMemory by remember { mutableStateOf<MemoryFile?>(null) }
    val memoryGroups = remember(memories) { memories.groupByDirectory() }

    // 空列表不滚动，占位才能在剩余空间里居中
    if (memoryGroups.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (consolidation != null) {
                MemoryConsolidationSection(
                    status = consolidation,
                    onOrganize = onOrganizeMemory,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                ExtensionEmptyState(
                    message = stringResource(R.string.assistant_page_memory_empty),
                    description = stringResource(
                        if (consolidation != null) {
                            R.string.assistant_page_memory_empty_hint
                        } else {
                            R.string.assistant_page_memory_empty_disabled_hint
                        }
                    ),
                    buttonText = stringResource(R.string.settings).takeIf { consolidation == null },
                    onAction = onOpenSettings,
                    icon = HugeIcons.Brain02,
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (consolidation != null) {
            MemoryConsolidationSection(
                status = consolidation,
                onOrganize = onOrganizeMemory,
            )
        }

        memoryGroups.fastForEach { (directory, files) ->
            CardGroup(
                title = { Text(memorySectionTitle(directory)) },
            ) {
                files.fastForEach { memory ->
                    item(
                        onClick = { onOpenMemory(memory.path) },
                        trailingContent = {
                            ItemActionMenu(
                                actions = listOf(
                                    ItemAction(
                                        text = stringResource(R.string.delete),
                                        icon = HugeIcons.Delete01,
                                        destructive = true,
                                        onClick = { pendingDeleteMemory = memory },
                                    ),
                                )
                            )
                        },
                        headlineContent = {
                            Text(
                                text = memory.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (memory.description.isNotEmpty()) {
                                    Text(
                                        text = memory.description,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = stringResource(
                                        R.string.assistant_page_memory_updated_at,
                                        relativeTimeOf(memory.updatedAt),
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    RikkaConfirmDialog(
        show = pendingDeleteMemory != null,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            pendingDeleteMemory?.let(onDeleteMemory)
            pendingDeleteMemory = null
        },
        onDismiss = { pendingDeleteMemory = null },
        text = {
            Text(
                text = pendingDeleteMemory?.path.orEmpty(),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/** 后台整理的状态和手动入口 */
@Composable
private fun MemoryConsolidationSection(
    status: MemoryConsolidationStatus,
    onOrganize: () -> Unit,
) {
    val lastRun = status.lastRun
    CardGroup {
        item(
            headlineContent = { Text(stringResource(R.string.memory_dream_title)) },
            supportingContent = { Text(memoryConsolidationSummary(status)) },
            trailingContent = {
                if (status.running) {
                    ContainedLoadingIndicator(modifier = Modifier.size(32.dp))
                } else {
                    FilledTonalButton(onClick = onOrganize) {
                        Text(
                            stringResource(
                                if (lastRun?.error != null) {
                                    R.string.assistant_page_memory_dream_retry
                                } else {
                                    R.string.assistant_page_memory_dream_now
                                }
                            )
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun memoryConsolidationSummary(status: MemoryConsolidationStatus): String {
    val lastRun = status.lastRun
    return when {
        status.running -> stringResource(R.string.memory_dreaming)
        lastRun?.error != null -> lastRun.resultText()
        else -> listOfNotNull(
            status.pendingTurns.takeIf { it > 0 }?.let {
                stringResource(R.string.assistant_page_memory_dream_pending, it)
            },
            lastRun?.let { "${relativeTimeOf(it.finishedAt)}: ${it.resultText()} (${durationTextOf(it.duration)})" },
        ).joinToString("\n").ifEmpty { stringResource(R.string.assistant_page_memory_dream_hint) }
    }
}

// 按整秒显示成 "42s"、"1m 5s"
private fun durationTextOf(duration: Duration): String = duration.inWholeSeconds.coerceAtLeast(1).seconds.toString()

// 根目录下是 profile 和 preferences，都是关于用户本人的；记忆格式约定的几个目录有译名，其余的从目录名推
@Composable
private fun memorySectionTitle(directory: String): String = when (directory) {
    "/" -> stringResource(R.string.assistant_page_memory_section_you)
    "/topics" -> stringResource(R.string.assistant_page_memory_section_topics)
    "/areas" -> stringResource(R.string.assistant_page_memory_section_areas)
    "/people" -> stringResource(R.string.assistant_page_memory_section_people)
    else -> memoryTitleOf(directory.removePrefix("/"))
}

// 一周内显示"41 分钟前"这样的相对时间，更早的显示日期
private fun relativeTimeOf(time: Long): String = DateUtils.getRelativeTimeSpanString(
    time,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
    DateUtils.FORMAT_ABBREV_MONTH,
).toString()
