package me.rerere.rikkahub.ui.components.ai

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.Job
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Camera01
import me.rerere.hugeicons.stroke.Codesandbox
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.Package
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.PaintBoard
import me.rerere.hugeicons.stroke.Settings02
import me.rerere.hugeicons.stroke.Video01
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.Voice
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.WorkspaceCwd
import me.rerere.rikkahub.data.model.effectiveContextWindow
import me.rerere.rikkahub.data.model.lastContextTokens
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.components.ui.ExtensionSelector
import me.rerere.rikkahub.ui.components.ui.shadow.FilledTonalButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.ui.terminal.IconButton
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.workspace.WorkspaceShellStatus
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

// 顺序决定切换方向：往后是进入下一级，往前是返回
private enum class FilesPickerPage {
    MAIN,
    MCP,
    EXTENSIONS,
    SEARCH,
    SEARCH_PROVIDER,
}

private val FilesPickerPage.parent: FilesPickerPage
    get() = when (this) {
        FilesPickerPage.SEARCH_PROVIDER -> FilesPickerPage.SEARCH
        else -> FilesPickerPage.MAIN
    }

@Composable
internal fun FilesPicker(
    conversation: Conversation,
    // 会话视角下的助手和模型：会话开始后以会话上固定的配置为准
    assistant: Assistant,
    chatModel: Model?,
    state: ChatInputState,
    mcpManager: McpManager,
    onCompressContext: (additionalPrompt: String, keepRecentMessages: Int) -> Job,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateWorkspaceCwd: (Uuid, String?) -> Unit,
    onUpdateSearchMode: (SearchMode) -> Unit,
    onToggleSearchService: (String) -> Unit,
    showCompressDialog: Boolean,
    onShowCompressDialogChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onTakePic: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPickFile: () -> Unit,
    onSketch: () -> Unit,
    onStartVoiceMode: (() -> Unit)? = null,
) {
    val settings = LocalSettings.current
    val provider = chatModel?.findProvider(providers = settings.providers)
    val navController = LocalNavController.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    val sshStates by workspaceRepository.sshStates.collectAsState()
    val workspaces by workspaceRepository.listFlow().collectAsState(initial = emptyList())

    // 每个已绑定且 shell 就绪的工作区都有自己的工作目录入口
    val cwdWorkspaces = remember(workspaces, assistant.allWorkspaceIds) {
        val boundIds = assistant.allWorkspaceIds.map { it.toString() }.toSet()
        workspaces.filter { workspace ->
            workspace.id in boundIds && workspace.shellStatus == WorkspaceShellStatus.READY.name
        }
    }
    var cwdWorkspaceId by remember { mutableStateOf<String?>(null) }

    var page by remember { mutableStateOf(FilesPickerPage.MAIN) }
    // 在子页面时，返回键回到上一级而不是关闭 sheet
    BackHandler(enabled = page != FilesPickerPage.MAIN) {
        page = page.parent
    }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            if (targetState > initialState) {
                slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it } + fadeOut()
            } else {
                slideInHorizontally { -it } + fadeIn() togetherWith
                    slideOutHorizontally { it } + fadeOut()
            }
        },
        label = "FilesPickerPage"
    ) { currentPage ->
        when (currentPage) {
            FilesPickerPage.MCP -> McpPickerPage(
                assistant = assistant,
                servers = settings.mcpServers,
                mcpManager = mcpManager,
                onUpdateAssistant = onUpdateAssistant,
                onBack = { page = FilesPickerPage.MAIN },
            )

            FilesPickerPage.EXTENSIONS -> ExtensionPickerPage(
                assistant = assistant,
                settings = settings,
                onUpdateAssistant = onUpdateAssistant,
                onBack = { page = FilesPickerPage.MAIN },
                onDismissAll = onDismiss,
            )

            FilesPickerPage.SEARCH -> SearchPickerPage(
                enableSearch = assistant.enableWebSearch,
                settings = settings,
                model = chatModel,
                onUpdateSearchMode = onUpdateSearchMode,
                onSelectProvider = { page = FilesPickerPage.SEARCH_PROVIDER },
                onBack = { page = FilesPickerPage.MAIN },
                onDismissAll = onDismiss,
            )

            FilesPickerPage.SEARCH_PROVIDER -> SearchProviderPicker(
                settings = settings,
                onToggleSearchService = onToggleSearchService,
                onBack = { page = FilesPickerPage.SEARCH },
            )

            // 矮屏上放不下时可以滚动，避免底部的压缩对话等入口被挤出屏幕
            FilesPickerPage.MAIN -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AttachmentGrid(
                    actions = buildList {
                        add(AttachmentAction(HugeIcons.Camera01, R.string.take_picture, onTakePic))
                        add(AttachmentAction(HugeIcons.Image02, R.string.photo, onPickImage))
                        add(AttachmentAction(HugeIcons.PaintBoard, R.string.sketch, onSketch))
                        if (provider != null && provider is ProviderSetting.Google) {
                            add(AttachmentAction(HugeIcons.Video01, R.string.video, onPickVideo))
                            add(AttachmentAction(HugeIcons.MusicNote03, R.string.audio, onPickAudio))
                        }
                        add(AttachmentAction(HugeIcons.Files02, R.string.upload_file, onPickFile))
                        onStartVoiceMode?.let { start ->
                            add(AttachmentAction(HugeIcons.Voice, R.string.chat_page_voice_title, start))
                        }
                    }
                )

                // 分段列表：各项按显示条件依次占位，首尾项才有大圆角
                val showWorkspace = workspaces.isNotEmpty()
                val showMcp = settings.mcpServers.isNotEmpty()
                // 限制了上下文消息数就是滚动上下文，旧消息会自动移出，不需要压缩
                val showCompress = assistant.contextMessageLimit <= 0
                // 收起的项不占位置，后面的往前挪一格；圆角和设置页的卡片组同一套算法
                val itemCount = 3 +
                    (if (showWorkspace) cwdWorkspaces.size + 1 else 0) +
                    (if (showMcp) 1 else 0) +
                    (if (showCompress) 1 else 0)
                val pickerExpandSpec = spring<IntSize>(dampingRatio = 0.72f, stiffness = 480f)
                var itemIndex = 0
                // 收起的项在退出动画期间还在渲染，给它末项形状，免得拿到首项的大圆角
                val hiddenShapeIndex = (itemCount - 1).coerceAtLeast(0)
                val searchIndex = itemIndex++
                val workspaceIndex = if (showWorkspace) itemIndex++ else hiddenShapeIndex
                val cwdIndexes = cwdWorkspaces.map { if (showWorkspace) itemIndex++ else hiddenShapeIndex }
                val mcpIndex = if (showMcp) itemIndex++ else hiddenShapeIndex
                val extensionsIndex = itemIndex++
                val planModeIndex = itemIndex++
                val compressIndex = if (showCompress) itemIndex++ else hiddenShapeIndex
                Column(
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    SearchPickerListItem(
                        enableSearch = assistant.enableWebSearch,
                        settings = settings,
                        model = chatModel,
                        shapes = ListItemDefaults.segmentedShapes(index = searchIndex, count = itemCount),
                        onClick = { page = FilesPickerPage.SEARCH },
                    )

                    AnimatedVisibility(
                        visible = showWorkspace,
                        enter = expandVertically(pickerExpandSpec) + fadeIn(),
                        exit = shrinkVertically(pickerExpandSpec) + fadeOut(),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        ) {
                            WorkspacePickerListItem(
                                assistant = assistant,
                                workspaces = workspaces,
                                shapes = ListItemDefaults.segmentedShapes(index = workspaceIndex, count = itemCount),
                                onUpdateAssistant = onUpdateAssistant,
                                onNavigateToDetail = { id ->
                                    onDismiss()
                                    navController.navigate(Screen.WorkspaceDetail(id))
                                },
                                onNavigateToTerminal = { id ->
                                    onDismiss()
                                    navController.navigate(Screen.WorkspaceTerminal(id))
                                },
                                onNavigateToManage = {
                                    onDismiss()
                                    navController.navigate(Screen.Workspaces)
                                },
                            )

                            // 每个绑定工作区各一个 CWD 入口
                            cwdWorkspaces.forEachIndexed { i, workspace ->
                                SegmentedListItem(
                                    onClick = { cwdWorkspaceId = workspace.id },
                                    shapes = ListItemDefaults.segmentedShapes(index = cwdIndexes[i], count = itemCount),
                                    leadingContent = {
                                        Icon(
                                            imageVector = HugeIcons.Folder01,
                                            contentDescription = stringResource(R.string.workspace_cwd_select_directory),
                                        )
                                    },
                                    trailingContent = {
                                        Icon(HugeIcons.ArrowRight01, contentDescription = null)
                                    },
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        WorkspaceConnectionIndicator(
                                            workspace = workspace,
                                            state = sshStates[workspace.id],
                                        )
                                        Text(
                                            text = workspace.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        Text(
                                            text = WorkspaceCwd.absolute(
                                                workspace.workspaceDir(),
                                                WorkspaceCwd.normalize(
                                                    workspace.workspaceDir(),
                                                    conversation.workspaceCwds[workspace.id],
                                                ),
                                            ),
                                            // 路径的末尾更有辨识度，放不下时省略开头
                                            maxLines = 1,
                                            overflow = TextOverflow.StartEllipsis,
                                            modifier = Modifier.weight(2f),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showMcp,
                        enter = expandVertically(pickerExpandSpec) + fadeIn(),
                        exit = shrinkVertically(pickerExpandSpec) + fadeOut(),
                    ) {
                        McpPickerListItem(
                            assistant = assistant,
                            servers = settings.mcpServers,
                            mcpManager = mcpManager,
                            shapes = ListItemDefaults.segmentedShapes(index = mcpIndex, count = itemCount),
                            onClick = { page = FilesPickerPage.MCP },
                        )
                    }

                    // Extensions (Quick Messages + Prompt Injections + Skills)
                    val activeCount =
                        assistant.quickMessageIds.size +
                            assistant.modeInjectionIds.size +
                            assistant.lorebookIds.size +
                            assistant.enabledSkills.size
                    SegmentedListItem(
                        onClick = { page = FilesPickerPage.EXTENSIONS },
                        shapes = ListItemDefaults.segmentedShapes(index = extensionsIndex, count = itemCount),
                        leadingContent = {
                            Icon(
                                imageVector = HugeIcons.Package,
                                contentDescription = null,
                            )
                        },
                        trailingContent = {
                            if (activeCount > 0) {
                                CountBadge(count = activeCount)
                            }
                        },
                    ) {
                        Text(stringResource(R.string.assistant_page_tab_extensions))
                    }

                    // Plan Mode Toggle
                    SegmentedListItem(
                        onClick = { onUpdateAssistant(assistant.copy(planMode = !assistant.planMode)) },
                        shapes = ListItemDefaults.segmentedShapes(index = planModeIndex, count = itemCount),
                        leadingContent = {
                            Icon(
                                imageVector = HugeIcons.View,
                                contentDescription = null,
                                tint = if (assistant.planMode) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalContentColor.current
                                },
                            )
                        },
                    ) {
                        Text(stringResource(R.string.assistant_page_plan_mode))
                    }

                    // Compress History Button
                    AnimatedVisibility(
                        visible = showCompress,
                        enter = expandVertically(pickerExpandSpec) + fadeIn(),
                        exit = shrinkVertically(pickerExpandSpec) + fadeOut(),
                    ) {
                        SegmentedListItem(
                            onClick = { onShowCompressDialogChange(true) },
                            shapes = ListItemDefaults.segmentedShapes(index = compressIndex, count = itemCount),
                            leadingContent = {
                                Icon(
                                    imageVector = HugeIcons.Package01,
                                    contentDescription = null,
                                )
                            },
                            trailingContent = {
                                val contextTokens = remember(conversation.messageNodes) {
                                    conversation.lastContextTokens()
                                }
                                val contextLength = chatModel?.effectiveContextWindow
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (conversation.messageNodes.isNotEmpty()) {
                                            Text(
                                                text = stringResource(R.string.chat_page_message_count, conversation.messageNodes.size),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (contextTokens != null && contextLength != null) {
                                            Text(
                                                text = "${contextTokens.formatNumber()} / ${contextLength.formatNumber()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (contextTokens != null && contextLength != null) {
                                        ContextUsageRing(fraction = contextTokens.toFloat() / contextLength)
                                    }
                                }
                            },
                        ) {
                            Text(stringResource(R.string.chat_page_compress_context))
                        }
                    }
                }
            }
        }
    }

    val cwdWorkspace = cwdWorkspaceId?.let { id -> cwdWorkspaces.find { it.id == id } }
    if (cwdWorkspace != null) {
        WorkspaceCwdPickerSheet(
            workspaceId = cwdWorkspace.id,
            workspaceDir = cwdWorkspace.workspaceDir(),
            currentCwd = conversation.workspaceCwds[cwdWorkspace.id],
            onSelectCwd = { newCwd ->
                onUpdateWorkspaceCwd(Uuid.parse(cwdWorkspace.id), newCwd)
            },
            onDismiss = { cwdWorkspaceId = null },
        )
    }

    // Compress Context Dialog
    if (showCompressDialog) {
        CompressContextDialog(onDismiss = {
            onShowCompressDialogChange(false)
            onDismiss()
        }, onConfirm = onCompressContext)
    }
}

@Composable
private fun WorkspaceActionButton(
    icon: ImageVector,
    contentDescription: String,
    workspaces: List<WorkspaceEntity>,
    onNavigate: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = {
                if (workspaces.size == 1) {
                    onNavigate(workspaces.first().id)
                } else {
                    expanded = true
                }
            },
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            workspaces.forEach { workspace ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = workspace.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onNavigate(workspace.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun WorkspacePickerListItem(
    assistant: Assistant,
    workspaces: List<WorkspaceEntity>,
    shapes: ListItemShapes,
    onUpdateAssistant: (Assistant) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToTerminal: (String) -> Unit,
    onNavigateToManage: () -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    val selectedIds = assistant.allWorkspaceIds.map { it.toString() }.toSet()
    val boundWorkspaces = remember(workspaces, selectedIds) {
        workspaces.filter { it.id in selectedIds }
    }

    SegmentedListItem(
        onClick = { showSheet = true },
        shapes = shapes,
        leadingContent = {
            Icon(
                imageVector = HugeIcons.Codesandbox,
                contentDescription = null,
            )
        },
        supportingContent = {
            Text(
                text = when {
                    boundWorkspaces.isEmpty() -> stringResource(R.string.assistant_page_workspace_unbound)
                    boundWorkspaces.size == 1 -> boundWorkspaces.first().name
                    else -> stringResource(R.string.workspace_selected_count, boundWorkspaces.size)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (boundWorkspaces.isNotEmpty()) {
                    WorkspaceActionButton(
                        icon = HugeIcons.Settings02,
                        contentDescription = stringResource(R.string.workspace_detail),
                        workspaces = boundWorkspaces,
                        onNavigate = onNavigateToDetail,
                    )
                    val terminalWorkspaces = boundWorkspaces.filter { it.supportsTerminal() }
                    if (terminalWorkspaces.isNotEmpty()) {
                        WorkspaceActionButton(
                            icon = HugeIcons.ComputerTerminal01,
                            contentDescription = stringResource(R.string.workspace_terminal),
                            workspaces = terminalWorkspaces,
                            onNavigate = onNavigateToTerminal,
                        )
                    }
                }
            }
        },
    ) {
        Text(stringResource(R.string.assistant_page_workspace))
    }

    if (showSheet) {
        WorkspaceSelectSheet(
            assistant = assistant,
            workspaces = workspaces,
            onSelect = { workspaceIds ->
                val newIds = workspaceIds.mapNotNull { id ->
                    runCatching { Uuid.parse(id) }.getOrNull()
                }.toSet()
                val newPrimary = assistant.primaryWorkspaceId?.takeIf { it in newIds }
                    ?: newIds.firstOrNull()
                onUpdateAssistant(
                    assistant.copy(
                        workspaceIds = newIds,
                        workspaceId = newPrimary,
                    )
                )
            },
            onManage = {
                showSheet = false
                onNavigateToManage()
            },
            onDismiss = { showSheet = false },
        )
    }
}

// 扩展选择页，作为子页面嵌在加号 sheet 里
@Composable
private fun ExtensionPickerPage(
    assistant: Assistant,
    settings: Settings,
    onUpdateAssistant: (Assistant) -> Unit,
    onBack: () -> Unit,
    onDismissAll: () -> Unit,
) {
    val navController = LocalNavController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .padding(horizontal = 16.dp),
    ) {
        SheetHeader(
            title = stringResource(R.string.assistant_page_tab_extensions),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(HugeIcons.ArrowLeft01, contentDescription = stringResource(R.string.back))
                }
            },
        )
        ExtensionSelector(
            assistant = assistant,
            settings = settings,
            onUpdate = onUpdateAssistant,
            modifier = Modifier.weight(1f),
            onNavigateToQuickMessages = {
                onDismissAll()
                navController.navigate(Screen.QuickMessages)
            },
            onNavigateToPrompts = {
                onDismissAll()
                navController.navigate(Screen.Prompts)
            },
            onNavigateToSkills = {
                onDismissAll()
                navController.navigate(Screen.Skills)
            })

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private class AttachmentAction(
    val icon: ImageVector,
    @param:StringRes val label: Int,
    val onClick: () -> Unit,
)

// 附件入口：一行最多四个，再多就均分成两行
@Composable
private fun AttachmentGrid(
    actions: List<AttachmentAction>,
    modifier: Modifier = Modifier,
) {
    val perRow = if (actions.size <= 4) actions.size else (actions.size + 1) / 2
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.chunked(perRow).fastForEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.fastForEach { action ->
                    AttachmentTile(
                        action = action,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentTile(
    action: AttachmentAction,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = action.onClick,
        // 按下时圆角收紧
        shapes = ButtonDefaults.shapes(
            shape = MaterialTheme.shapes.extraLarge,
            pressedShape = MaterialTheme.shapes.medium,
        ),
        modifier = modifier.heightIn(min = 88.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = stringResource(action.label),
                // 四个并排时较长的译文放不下，自动缩小字号
                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 14.sp),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// 列表项尾部的已启用数量
@Composable
internal fun CountBadge(count: Int) {
    Badge(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(count.toString())
    }
}

// 上下文使用量进度环，接近上限时变色提醒压缩
@Composable
private fun ContextUsageRing(fraction: Float) {
    val color = when {
        fraction >= 0.9f -> MaterialTheme.colorScheme.error
        fraction >= 0.7f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    CircularProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.size(24.dp),
        color = color,
        strokeWidth = 3.dp,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

@Preview(showBackground = true)
@Composable
private fun AttachmentGridPreview() {
    AttachmentGrid(
        actions = listOf(
            AttachmentAction(HugeIcons.Camera01, R.string.take_picture) {},
            AttachmentAction(HugeIcons.Image02, R.string.photo) {},
            AttachmentAction(HugeIcons.Video01, R.string.video) {},
            AttachmentAction(HugeIcons.MusicNote03, R.string.audio) {},
            AttachmentAction(HugeIcons.Files02, R.string.upload_file) {},
        ),
        modifier = Modifier.padding(16.dp),
    )
}

/** 工作区连接状态：建连/重试转圈，SSH 连上绿灯、断开红灯，本地工作区就绪即绿灯 */
@Composable
private fun WorkspaceConnectionIndicator(
    workspace: WorkspaceEntity,
    state: me.rerere.workspace.ssh.SshPoolState?,
) {
    if (workspace.isSsh()) {
        when (state) {
            me.rerere.workspace.ssh.SshPoolState.CONNECTING,
            me.rerere.workspace.ssh.SshPoolState.RETRYING -> CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
            )

            me.rerere.workspace.ssh.SshPoolState.CONNECTED -> Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.extendColors.green6, CircleShape)
            )

            else -> Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (workspace.shellStatus == me.rerere.workspace.WorkspaceShellStatus.READY.name) {
                        MaterialTheme.extendColors.green6
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = CircleShape,
                )
        )
    }
}
