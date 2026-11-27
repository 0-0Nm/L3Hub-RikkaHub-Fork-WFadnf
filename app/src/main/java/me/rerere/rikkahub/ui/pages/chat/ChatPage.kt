package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ui.components.AnimatedAlertDialog
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.toast.ToastType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Flowchart01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.FolderAdd
import me.rerere.hugeicons.stroke.Layers01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Share04
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.transformers.collectLorebookInjections
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.data.model.GuidanceMode
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.effectiveContextWindow
import me.rerere.rikkahub.data.model.isAwaitingToolApproval
import me.rerere.rikkahub.data.model.isContextLimitReached
import me.rerere.rikkahub.data.model.lastContextTokens
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.BackgroundShellStatus
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.SessionActivity
import me.rerere.rikkahub.ui.components.ai.BackgroundTasksContent
import me.rerere.rikkahub.ui.components.ai.BackgroundTasksSheet
import me.rerere.rikkahub.ui.components.ai.BusySendDialog
import me.rerere.rikkahub.ui.components.ai.ChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.ChatTopBarCapsule
import me.rerere.rikkahub.ui.components.ai.FilesPicker
import me.rerere.rikkahub.ui.components.ai.ModelListContent
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.SearchMode
import me.rerere.rikkahub.ui.components.ai.chatInputHaze
import me.rerere.rikkahub.ui.components.ui.ColumnResizeHandle
import me.rerere.rikkahub.ui.components.ai.completion.WorkspaceCompletionProvider
import me.rerere.rikkahub.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.message.ConversationUsageDetails
import me.rerere.rikkahub.ui.components.message.tools.LocalToolDetailHost
import me.rerere.rikkahub.ui.components.message.tools.ToolDetailHostState
import me.rerere.rikkahub.ui.components.message.EditedFilesList
import me.rerere.rikkahub.ui.components.message.LocalTurnChangesInSidePanel
import me.rerere.rikkahub.ui.components.message.LocalConversationStatusInSidePanel
import me.rerere.rikkahub.ui.components.message.editedFilesIn
import me.rerere.rikkahub.ui.components.message.usageTokenSpeed
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.SlideOverDrawer
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.BusyComposerAction
import me.rerere.rikkahub.ui.hooks.busyComposerAction
import me.rerere.rikkahub.ui.hooks.ComposerSendSnapshot
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.terminal.IconButton
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.rikkahub.ui.terminal.TopAppBar
import me.rerere.rikkahub.ui.theme.LocalUiSlots
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPaddingValue
import me.rerere.rikkahub.ui.theme.appButtonShadow
import me.rerere.rikkahub.ui.theme.rememberSlideSpec
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

@Composable
fun ChatPage(id: Uuid, text: String?, files: List<Uri>, nodeId: Uuid? = null) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(id.toString())
        }
    )
    val filesManager: FilesManager = koinInject()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val conversationId = conversation.id
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()

    val folders by vm.folders.collectAsStateWithLifecycle()
    val folderBinding by vm.folderWorkspaceBinding.collectAsStateWithLifecycle()
    // 文件夹绑了工作区时，工作区入口只展示这份绑定，改要去抽屉的文件夹菜单里改。
    // 子代理会话自己没有分组，工作区来自父会话的文件夹，那个文件夹可能属于别的助手、这里查不到名字，
    // 所以空串表示「被文件夹接管，但名字未知」
    val folderWorkspaceName = remember(conversation.folderId, folders, folderBinding) {
        val ownFolder = folders.firstOrNull { it.id == conversation.folderId }
        when {
            ownFolder != null && ownFolder.allWorkspaceIds.isNotEmpty() -> ownFolder.name
            folderBinding.isNotEmpty -> ""
            else -> null
        }
    }
    // 竖屏的侧边栏：自己画的滑入抽屉（见 SlideOverDrawer），不用 Material 那个会回弹的弹簧
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    val drawerSlideSpec = rememberSlideSpec<Float>(animated = !setting.displaySetting.disableAnimations)
    // 背景层（AssistantBackground）单独一份 Haze 源：抽屉和浮层的玻璃都取它，
    // 取聊天列表会把正文一起糊进来。提到这一层是为了抽屉也拿得到
    val backgroundHazeState = rememberHazeState()
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(imeVisible) {
        if (!imeVisible) {
            focusManager.clearFocus(force = true)
        }
    }

    // 抽屉开着时接管返回键
    BackHandler(enabled = drawerOpen) {
        drawerOpen = false
    }

    // Clear input focus so popup transitions cannot reopen the keyboard.
    LaunchedEffect(drawerOpen) {
        if (drawerOpen) {
            focusManager.clearFocus(force = true)
            softwareKeyboardController?.hide()
        }
    }

    val windowDpSize = currentWindowDpSize()
    // 横屏三栏：宽度够同时放下聊天区和左右两列才启用（见 ChatSidePanel.kt）
    val threePaneLayout = windowDpSize.width > windowDpSize.height && windowDpSize.width >= 600.dp
    // 左栏在横屏里是一列，不是抽屉：默认展开，顶栏的菜单按钮负责收合
    var sidebarOpen by rememberSaveable { mutableStateOf(true) }
    // 横屏三栏的列宽：拖分隔条改，关掉页面重进还在
    var sidebarWidthDp by rememberSaveable { mutableStateOf(LANDSCAPE_SIDEBAR_WIDTH.value) }
    var chatWidthDp by rememberSaveable { mutableStateOf(0f) }
    var sidebarResizing by remember { mutableStateOf(false) }
    // 转进横屏时把模态抽屉收掉，免得转回竖屏后它还开着（#1304）
    LaunchedEffect(threePaneLayout) {
        if (threePaneLayout && drawerOpen) {
            drawerOpen = false
        }
    }

    val openSettingsFromDrawer: () -> Unit = {
        // 不先收抽屉：设置页整屏盖上来时它跟着一起退场，
        // 先滑回去再推设置页等于两段动画，反而拖
        navController.navigate(Screen.Setting)
    }
    val startNewChat: () -> Unit = {
        // 横屏三栏没有模态抽屉可收；竖屏收一下，免得新会话页还被挡着
        if (!threePaneLayout) drawerOpen = false
        navigateToChatPage(navController, Uuid.random())
    }

    val startVoiceMode = rememberVoiceModeStarter(vm, setting)

    val inputState = vm.inputState

    // 初始化输入状态（处理传入的 files 和 text 参数）
    LaunchedEffect(files, text) {
        if (files.isNotEmpty()) {
            val localFiles = filesManager.createChatFilesByContents(files)
            val contentTypes = files.mapNotNull { file ->
                filesManager.getFileMimeType(file)
            }
            val parts = buildList {
                localFiles.forEachIndexed { index, file ->
                    val type = contentTypes.getOrNull(index)
                    if (type?.startsWith("image/") == true) {
                        add(UIMessagePart.Image(url = file.toString()))
                    } else if (type?.startsWith("video/") == true) {
                        add(UIMessagePart.Video(url = file.toString()))
                    } else if (type?.startsWith("audio/") == true) {
                        add(UIMessagePart.Audio(url = file.toString()))
                    }
                }
            }
            inputState.messageContent = parts
        }
        text?.base64Decode()?.let { decodedText ->
            if (decodedText.isNotEmpty()) {
                inputState.setMessageText(decodedText)
            }
        }
    }

    val chatListState = rememberLazyListState()
    LaunchedEffect(nodeId, conversation.messageNodes.size) {
        if (!vm.chatListInitialized && conversation.messageNodes.isNotEmpty()) {
            if (nodeId != null) {
                val index = conversation.messageNodes.indexOfFirst { it.id == nodeId }
                if (index >= 0) {
                    chatListState.scrollToItem(index)
                }
            } else {
                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
            }
            vm.chatListInitialized = true
        }
    }

    // 横屏的工具调用详情：挂在页面最外层，从右侧滑入，不另开窗口
    val toolDetailHost = remember { ToolDetailHostState() }
    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalToolDetailHost provides toolDetailHost) {
        if (threePaneLayout) {
            val baseDensity = LocalDensity.current
            // 横屏三栏：整块缩到 75%。三列并排后每列都只剩竖屏的三分之一宽，
            // 字号、行高、控件一起缩，信息量才塞得回去
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = baseDensity.density * LANDSCAPE_UI_SCALE,
                    fontScale = baseDensity.fontScale,
                ),
            ) {
                // 三列在缩放后的坐标系里量：整块窗口先除回 0.75
                val totalWidth = windowDpSize.width / LANDSCAPE_UI_SCALE
                val maxChatWidth = (totalWidth - (if (sidebarOpen) sidebarWidthDp else 0f).dp - 260.dp)
                    .coerceAtLeast(320.dp)
                // 中列默认按窗口比例，拖过以后按拖出来的宽度
                val chatWidth = (if (chatWidthDp > 0f) chatWidthDp.dp else (totalWidth * 0.4f))
                    .coerceIn(320.dp, maxChatWidth)
                Row(modifier = Modifier.fillMaxSize()) {
                    // 左栏：原来的抽屉内容，横屏里并排放，宽度动画负责开合
                    val sidebarSlideSpec = rememberSlideSpec<Dp>(
                        animated = !setting.displaySetting.disableAnimations,
                    )
                    val sidebarWidth by animateDpAsState(
                        targetValue = if (sidebarOpen) sidebarWidthDp.dp else 0.dp,
                        // 拖动时不要弹簧，手柄和边缘之间不能有一帧的滞后
                        animationSpec = if (sidebarResizing) {
                            snap()
                        } else {
                            sidebarSlideSpec
                        },
                        label = "chatSidebarWidth",
                    )
                    if (sidebarWidth > 0.dp) {
                        Box(
                            modifier = Modifier
                                .width(sidebarWidth)
                                .fillMaxHeight()
                                .clipToBounds(),
                        ) {
                            ChatDrawerContent(
                                navController = navController,
                                currentConversationId = conversationId,
                                vm = vm,
                                settings = setting,
                                onNewChat = startNewChat,
                                onOpenSettings = openSettingsFromDrawer,
                                // 不传 onCloseDrawer：点会话时左栏要留着，它是常驻列不是抽屉
                                avatarHero = sidebarOpen,
                                hazeState = backgroundHazeState,
                                widthOverride = sidebarWidthDp.dp,
                            )
                        }
                        ColumnResizeHandle(
                            onDrag = { delta ->
                                sidebarWidthDp = (sidebarWidthDp + delta.value).coerceIn(200f, 440f)
                            },
                            onDragStart = { sidebarResizing = true },
                            onDragEnd = { sidebarResizing = false },
                        )
                    }
                    ChatPageContent(
                        onStartVoiceMode = startVoiceMode,
                        onCreateNewChat = startNewChat,
                        inputState = inputState,
                        loadingJob = loadingJob,
                        processingStatus = processingStatus,
                        setting = setting,
                        conversation = conversation,
                        folderWorkspaceName = folderWorkspaceName,
                        navController = navController,
                        vm = vm,
                        chatListState = chatListState,
                        assistant = assistant,
                        currentChatModel = currentChatModel,
                        threePane = true,
                        sidebarOpen = sidebarOpen,
                        onOpenSidebar = { sidebarOpen = !sidebarOpen },
                        chatWidth = chatWidth,
                        onChatResize = { delta ->
                            val maxChat = (totalWidth - (if (sidebarOpen) sidebarWidthDp else 0f).dp - 260.dp)
                                .coerceAtLeast(320.dp)
                            // 读状态本身，不读这一帧的 chatWidth：一次手势里可能连来好几个事件
                            val current = if (chatWidthDp > 0f) chatWidthDp.dp else (totalWidth * 0.4f)
                            chatWidthDp = (current + delta).coerceIn(320.dp, maxChat).value
                        },
                        backgroundHazeState = backgroundHazeState,
                        errors = errors,
                        onDismissError = { vm.dismissError(it) },
                        onClearAllErrors = { vm.clearAllErrors() },
                    )
                }
            }
        } else {
            SlideOverDrawer(
                open = drawerOpen,
                onOpenChange = { drawerOpen = it },
                animationSpec = drawerSlideSpec,
                sheet = {
                    ChatDrawerContent(
                        navController = navController,
                        currentConversationId = conversationId,
                        vm = vm,
                        settings = setting,
                        onNewChat = startNewChat,
                        onOpenSettings = openSettingsFromDrawer,
                        onCloseDrawer = { drawerOpen = false },
                        // 收起状态的抽屉在屏幕外，这时别让头像参与转场
                        avatarHero = drawerOpen,
                        hazeState = backgroundHazeState,
                    )
                },
            ) {
                ChatPageContent(
                    onStartVoiceMode = startVoiceMode,
                    onCreateNewChat = startNewChat,
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    folderWorkspaceName = folderWorkspaceName,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    assistant = assistant,
                    currentChatModel = currentChatModel,
                    threePane = false,
                    sidebarOpen = drawerOpen,
                    onOpenSidebar = { drawerOpen = true },
                    backgroundHazeState = backgroundHazeState,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
        }
        }
        ToolDetailSidePanel(host = toolDetailHost)
    }
}

@Composable
private fun ChatPageContent(
    onStartVoiceMode: () -> Unit,
    onCreateNewChat: () -> Unit,
    inputState: ChatInputState,
    loadingJob: Job?,
    processingStatus: String? = null,
    setting: Settings,
    conversation: Conversation,
    // 文件夹绑了工作区时传文件夹名：工作区入口只展示这份绑定（见 ChatPage 顶部）
    folderWorkspaceName: String?,
    // 横屏三栏：中间是聊天区，右栏是标签页式的看板（见 ChatSidePanel.kt）
    threePane: Boolean,
    sidebarOpen: Boolean,
    onOpenSidebar: () -> Unit,
    // 中列宽度：横屏三栏时定宽，收起左栏时中列左移、右栏变宽；null 表示没有右栏，中列吃满全宽
    chatWidth: Dp? = null,
    // 拖动中列与右栏之间的分隔条
    onChatResize: (Dp) -> Unit = {},
    navController: Navigator,
    vm: ChatVM,
    chatListState: LazyListState,
    assistant: Assistant,
    currentChatModel: Model?,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
    // 背景层的 Haze 源：抽屉和浮层共用，见 ChatPage 里的注释
    backgroundHazeState: HazeState,
) {
    val scope = rememberCoroutineScope()
    val sessionActivity by vm.sessionActivity.collectAsStateWithLifecycle()
    // 顶栏胶囊的文件夹选单：按会话自己的助手取，不看抽屉里当前选中的助手
    val conversationFolders by vm.folders.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    // currentMessages 是 getter，每次访问都新建一份列表；同一帧里有多处要用，算一次就够。
    // 它只依赖 messageNodes，按节点列表做键不会漏掉分支切换。
    val currentMessages = remember(conversation.messageNodes) { conversation.currentMessages }
    // 回调里读最新节点，但不在组合期订阅会话，否则每来一个 token 都会换一个新 lambda
    val latestConversation by rememberUpdatedState(conversation)
    val latestInputState by rememberUpdatedState(inputState)
    var busySendSnapshot by remember(conversation.id) { mutableStateOf<ComposerSendSnapshot?>(null) }
    var busySendSubmitting by remember(conversation.id) { mutableStateOf(false) }
    val composerOwnerActive = remember(conversation.id, inputState) { mutableStateOf(true) }
    DisposableEffect(conversation.id, inputState) {
        onDispose { composerOwnerActive.value = false }
    }

    fun clearAcceptedComposerSnapshot(snapshot: ComposerSendSnapshot, textOnly: Boolean = false) {
        if (composerOwnerActive.value && latestInputState === inputState) {
            inputState.clearAcceptedSnapshot(snapshot, latestConversation.id, accepted = true, textOnly = textOnly)
        }
    }

    // 上下文占用取最近一次回复的真实用量, 到达模型的 MAX 门限后禁止再发送
    val contextTokens = remember(conversation.messageNodes) { conversation.lastContextTokens() }
    val contextLimitReached = isContextLimitReached(contextTokens, currentChatModel?.maxContextTokens)
    // 模型可以关掉强制拦截，此时只在发送时提醒一次
    val contextLimitBlocksSend = contextLimitReached && currentChatModel?.enforceMaxContext != false
    val contextLimitMessage = stringResource(R.string.chat_page_context_limit_reached)

    // Usage 看板里的最近一次请求速度
    val lastUsageMessage = remember(conversation.messageNodes) {
        currentMessages.lastOrNull { it.usage != null }
    }
    val usageTokenSpeed = remember(lastUsageMessage) { lastUsageMessage?.usageTokenSpeed() }
    // 每个 chunk 都会重建消息列表，世界书匹配要扫最近几轮的全文，不能放在组合里做
    val lorebookInjectionCount by produceState(
        0,
        conversation.messageNodes,
        assistant.lorebookIds,
        setting.lorebooks,
    ) {
        value = if (assistant.lorebookIds.isEmpty()) {
            0
        } else {
            withContext(Dispatchers.Default) {
                collectLorebookInjections(currentMessages, assistant, setting.lorebooks).size
            }
        }
    }
    // 详情面板要显示工作区名字和路径，跟着仓库的列表数据走
    val workspaces by remember(workspaceRepository) { workspaceRepository.listFlow() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // 上下文已经爆了且开了自动压缩：点发送先确认，确认后由 ChatService 先压缩再发送
    var showCompressAndSendDialog by remember { mutableStateOf(false) }
    val submitInput: () -> Unit = {
        if (inputState.isEditing()) {
            vm.handleMessageEdit(
                parts = inputState.getContents(),
                messageId = inputState.editingMessage!!,
            )
            inputState.clearInput()
        } else {
            val snapshot = inputState.captureSendSnapshot(conversation.id)
            if (vm.handleMessageSend(snapshot.parts)) {
                clearAcceptedComposerSnapshot(snapshot)
                scope.launch {
                    delay(100.milliseconds)
                    chatListState.animateScrollToItem(
                        index = (chatListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0),
                    )
                }
            }
        }
        // 没被受理（关闭队列后正在压缩上下文、或队列暂停还压着旧消息）则保留输入框内容
    }

    var previewMode by rememberSaveable { mutableStateOf(false) }
    val previewListState = rememberLazyListState()
    val selectionState = rememberChatSelectionState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    // 顶栏「详情」展开状态：面板挂在顶栏下面，展开时把聊天内容整体往下让
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showFilesSheet by remember { mutableStateOf(false) }
    var showTaskTree by remember { mutableStateOf(false) }
    var showUsageSheet by remember { mutableStateOf(false) }
    val chatModelListState = rememberModelListState(
        modelId = assistant.chatModelId,
        providers = setting.providers,
        type = ModelType.CHAT,
    )
    // 横屏三栏的右栏：一个看板一个标签页，顺序就是标签条上的顺序
    var openPaneNames by rememberSaveable { mutableStateOf(listOf(ChatSidePane.Details.name)) }
    var selectedPaneName by rememberSaveable { mutableStateOf<String?>(ChatSidePane.Details.name) }
    // 后台任务弹层从输入框里提到这一层：横屏要能把它放进右侧栏
    var showBackgroundTasks by remember { mutableStateOf(false) }
    val backgroundTasks by vm.backgroundTasks.collectAsStateWithLifecycle()
    val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()

    val openPanes = remember(openPaneNames) {
        openPaneNames.mapNotNull { name -> ChatSidePane.entries.firstOrNull { it.name == name } }
    }
    val selectedPane = remember(selectedPaneName, openPanes) {
        selectedPaneName?.let { name -> openPanes.firstOrNull { it.name == name } }
    }

    // 会话详情展开时才解析一次请求快照：工具、系统提示词和本轮注入都按当前配置重新算
    val detailsPanelVisible =
        (showDetails && !threePane) || (threePane && selectedPane == ChatSidePane.Details)
    val requestPreviewState by produceState<RequestPreviewUiState>(
        RequestPreviewUiState.Idle,
        detailsPanelVisible,
        assistant,
        currentChatModel,
        conversation.customSystemPrompt,
        conversation.messageNodes.size,
        conversation.workspaceCwds,
        setting.modeInjections,
        setting.lorebooks,
        setting.mcpServers,
        setting.assistants,
    ) {
        if (!detailsPanelVisible) {
            value = RequestPreviewUiState.Idle
            return@produceState
        }
        value = RequestPreviewUiState.Loading
        value = try {
            RequestPreviewUiState.Ready(vm.buildRequestPreview())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RequestPreviewUiState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    // 对标签的操作一律直接读状态，不读这一帧的 openPanes：连点两下时后者还是旧值
    fun openTab(pane: ChatSidePane) {
        if (openPaneNames.none { it == pane.name }) {
            openPaneNames = openPaneNames + pane.name
        }
        selectedPaneName = pane.name
    }

    fun closeTab(pane: ChatSidePane) {
        val remaining = openPaneNames.filterNot { it == pane.name }
        openPaneNames = remaining
        if (selectedPaneName == pane.name || selectedPaneName !in remaining) {
            selectedPaneName = remaining.lastOrNull()
        }
    }

    // 打开某个覆盖层：横屏进右栏的标签页，竖屏还是原来的弹层 / 对话框
    fun openPane(pane: ChatSidePane) {
        if (threePane) {
            openTab(pane)
            return
        }
        when (pane) {
            ChatSidePane.Details -> showDetails = true
            ChatSidePane.Usage -> showUsageSheet = true
            ChatSidePane.Files -> showFilesSheet = true
            // 工作区文件只在横屏右栏里出现；竖屏从输入框的附件 / 工作区入口进
            ChatSidePane.Workspace -> Unit
            ChatSidePane.Terminal -> Unit
            ChatSidePane.TaskTree -> showTaskTree = true
            ChatSidePane.ModelPicker -> chatModelListState.open()
            ChatSidePane.BackgroundTasks -> showBackgroundTasks = true
            // 本轮改动在竖屏是消息下面那一行，没有对应的弹层
            ChatSidePane.Changes -> Unit
            // 清单与引导在竖屏还是输入区上方那张卡片
            ChatSidePane.Status -> Unit
        }
    }

    // 收掉右栏当前的标签页
    fun closePane() {
        if (!threePane) return
        val name = selectedPaneName?.takeIf { it in openPaneNames } ?: openPaneNames.firstOrNull()
        name?.let { target -> ChatSidePane.entries.firstOrNull { it.name == target } }
            ?.let { closeTab(it) }
    }

    // 顶栏「详情」：横屏是右栏的标签页，竖屏还是顶栏底下那块的展开
    fun toggleDetailsPane() {
        if (threePane) {
            if (selectedPaneName == ChatSidePane.Details.name) {
                closeTab(ChatSidePane.Details)
            } else {
                openTab(ChatSidePane.Details)
            }
        } else {
            showDetails = !showDetails
        }
    }

    fun dismissFiles() {
        if (threePane) {
            if (openPaneNames.contains(ChatSidePane.Files.name)) closeTab(ChatSidePane.Files)
        } else {
            showFilesSheet = false
        }
    }

    // 本轮改动：横屏收进右栏。一轮里第一次改文件时把标签页开出来，但不抢当前看板
    val lastAssistantMessage = remember(currentMessages) {
        currentMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
    }
    val turnEditedFiles = remember(lastAssistantMessage) {
        lastAssistantMessage?.let { editedFilesIn(it.parts) }.orEmpty()
    }
    var lastAutoOpenedTurn by remember { mutableStateOf<Uuid?>(null) }
    LaunchedEffect(threePane, lastAssistantMessage?.id, turnEditedFiles) {
        if (!threePane || turnEditedFiles.isEmpty()) return@LaunchedEffect
        val turnId = lastAssistantMessage?.id ?: return@LaunchedEffect
        // 同一轮不重复开：用户手动关掉「本轮改动」以后别再弹回来
        if (lastAutoOpenedTurn == turnId) return@LaunchedEffect
        lastAutoOpenedTurn = turnId
        if (openPaneNames.none { it == ChatSidePane.Changes.name }) {
            openPaneNames = openPaneNames + ChatSidePane.Changes.name
        }
    }

    // 清单与引导：横屏收进右栏，出现时把标签页开出来，不抢当前看板
    val hasStatusContent = conversation.todos.isNotEmpty() || messageQueue.guidances.isNotEmpty() ||
        conversation.config?.liveGuidance != null
    LaunchedEffect(threePane, hasStatusContent) {
        if (!threePane || !hasStatusContent) return@LaunchedEffect
        if (openPaneNames.none { it == ChatSidePane.Status.name }) {
            openPaneNames = openPaneNames + ChatSidePane.Status.name
        }
    }

    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = inputState,
        setting = setting,
        onAttachmentAdded = { dismissFiles() },
    )
    val allowAudioVideoAttachments =
        currentChatModel?.findProvider(setting.providers) is ProviderSetting.Google

    // Busy submissions use the exact E345 snapshot/acceptance guards. No focus or IME change:
    // a rejection/new edit remains editable, and mixed-input attachments stay in the composer.
    fun submitBusyGuidance(snapshot: ComposerSendSnapshot, persistent: Boolean = false) {
        if (busySendSubmitting || !snapshot.canGuide || !composerOwnerActive.value ||
            latestInputState !== inputState || latestConversation.id != snapshot.conversationId
        ) return
        busySendSubmitting = true
        vm.submitComposerLiveGuidance(
            conversationId = snapshot.conversationId,
            content = snapshot.text,
            persistent = persistent,
        ) { accepted ->
            if (!composerOwnerActive.value || latestInputState !== inputState ||
                latestConversation.id != snapshot.conversationId
            ) return@submitComposerLiveGuidance
            busySendSubmitting = false
            if (accepted) {
                clearAcceptedComposerSnapshot(snapshot, textOnly = true)
                busySendSnapshot = null
            } else if (composerOwnerActive.value && latestConversation.id == snapshot.conversationId) {
                toaster.show(context.getString(R.string.chat_page_busy_rejected), type = ToastType.Error)
            }
        }
    }

    fun busyAction(longPress: Boolean = false) = busyComposerAction(
        busy = loadingJob != null || sessionActivity != null || conversation.isAwaitingToolApproval,
        hasText = inputState.textContent.text.isNotBlank(),
        hasAttachments = inputState.messageContent.isNotEmpty(),
        editing = inputState.isEditing(),
        longPress = longPress,
    )

    // 发送入口：模型、上下文门限检查都走这里
    val sendInput: () -> Unit = {
        if (busyAction() == BusyComposerAction.TEMPORARY) {
            submitBusyGuidance(inputState.captureSendSnapshot(conversation.id))
        } else if (busyAction() == BusyComposerAction.CHOOSER) {
            if (!busySendSubmitting) busySendSnapshot = inputState.captureSendSnapshot(conversation.id)
        } else if (currentChatModel == null) {
            toaster.show(context.getString(R.string.media_creation_page_select_model_first), type = ToastType.Error)
        } else if (contextLimitBlocksSend && assistant.autoCompress) {
            showCompressAndSendDialog = true
        } else if (contextLimitReached) {
            toaster.show(contextLimitMessage, type = ToastType.Error)
            if (contextLimitBlocksSend) Unit else submitInput()
        } else {
            submitInput()
        }
    }

    val sendInterjectSnapshot: (ComposerSendSnapshot) -> Unit = interject@{ snapshot ->
        if (snapshot.conversationId != latestConversation.id || snapshot.parts.isEmpty()) return@interject
        if (contextLimitReached) {
            toaster.show(contextLimitMessage, type = ToastType.Error)
            if (contextLimitBlocksSend) return@interject
        }
        if (vm.handleMessageSend(snapshot.parts, interject = true)) {
            clearAcceptedComposerSnapshot(snapshot)
            scope.launch {
                delay(100.milliseconds)
                chatListState.animateScrollToItem(
                    index = (chatListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0),
                )
            }
        }
    }

    // 消息滚到标题栏下面时才显示胶囊背景，空页面或在顶部时按钮直接浮在背景上
    // 概览页有自己的列表，切过去后跟着它判断
    val listScrolledUnderTopBar by remember(chatListState, previewListState) {
        derivedStateOf {
            if (previewMode) previewListState.canScrollBackward else chatListState.canScrollBackward
        }
    }

    val completionProviders = remember(assistant.allWorkspaceIds, conversation.workspaceCwds, workspaceRepository) {
        assistant.allWorkspaceIds.map { workspaceId ->
            WorkspaceCompletionProvider(
                workspaceId = workspaceId.toString(),
                repository = workspaceRepository,
                currentCwd = conversation.workspaceCwds[workspaceId.toString()],
            )
        }
    }

    TTSAutoPlay(vm = vm, setting = setting)

    // 概览页按返回键先回聊天页，不再直接退出会话
    BackHandler(previewMode) {
        previewMode = false
    }

    @Composable
    fun ChatInputContent(
        todos: List<TodoItem> = emptyList(),
        modifier: Modifier = Modifier,
        liftedBy: Dp = 0.dp,
        applyWindowInsets: Boolean = true,
    ) {
        val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
        ChatInput(
            onStartVoiceMode = onStartVoiceMode,
            voiceState = voiceState,
            onStopVoiceMode = vm.voiceSession::stop,
            state = inputState,
            messageQueue = messageQueue,
            onRemoveQueuedMessage = vm::removeQueuedMessage,
            onBeginEditQueuedMessage = vm::beginEditQueuedMessage,
            onFinishEditQueuedMessage = vm::finishEditQueuedMessage,
            onResumeMessageQueue = vm::resumeMessageQueue,
            guideMessages = setting.guideMessages,
            // 预设是长期约束，默认多轮；手输的一次性提醒默认单轮，卡片上点一下就能换
            onAddGuidance = { preset ->
                vm.addGuidance(preset.title, preset.content, GuidanceMode.REPEAT)
            },
            onSendAsGuidance = {
                val text = inputState.textContent.text.toString().trim()
                if (text.isNotEmpty()) {
                    vm.addGuidance(title = "", content = text, mode = GuidanceMode.SINGLE)
                    inputState.clearText()
                    toaster.show(context.getString(R.string.guidance_added), type = ToastType.Info)
                }
            },
            onGuidanceToggleMode = vm::toggleGuidanceMode,
            onGuidanceRemove = vm::removeGuidance,
            conversationId = conversation.id,
            liveGuidance = conversation.config?.liveGuidance,
            onLiveGuidanceModeChange = vm::setLiveGuidancePersistence,
            onLiveGuidanceNextMessage = vm::armLiveGuidanceForNextMessage,
            runningBackgroundTasks = backgroundTasks.count { it.status == BackgroundShellStatus.RUNNING },
            onShowBackgroundTasks = { openPane(ChatSidePane.BackgroundTasks) },
            loading = loadingJob != null || sessionActivity != null || conversation.isAwaitingToolApproval,
            settings = setting,
            assistant = assistant,
            chatModel = currentChatModel,
            todos = todos,
            onTodosChange = vm::updateTodos,
            contextTokens = contextTokens,
            usageTokenSpeed = usageTokenSpeed,
            onUpdateContextMeterStyle = { style ->
                vm.updateSettings { latest ->
                    latest.copy(displaySetting = latest.displaySetting.copy(contextMeterStyle = style))
                }
            },
            injectedLorebookCount = lorebookInjectionCount,
            onShowUsage = { openPane(ChatSidePane.Usage) },
            hazeState = backgroundHazeState,
            completionProviders = completionProviders,
            onCancelClick = {
                vm.stopGeneration()
            },
            onSendClick = sendInput,
            onLongSendClick = {
                if (busyAction(longPress = true) == BusyComposerAction.CHOOSER) {
                    if (!busySendSubmitting) busySendSnapshot = inputState.captureSendSnapshot(conversation.id)
                    return@ChatInput
                }
                if (contextLimitReached) {
                    toaster.show(contextLimitMessage, type = ToastType.Error)
                    if (contextLimitBlocksSend) return@ChatInput
                }
                if (inputState.isEditing()) {
                    vm.handleMessageEdit(
                        parts = inputState.getContents(),
                        messageId = inputState.editingMessage!!,
                    )
                    inputState.clearInput()
                } else if (vm.handleMessageSend(content = inputState.getContents(), answer = false)) {
                    inputState.clearInput()
                    scope.launch {
                        delay(100.milliseconds)
                        chatListState.animateScrollToItem(
                            index = (chatListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0),
                        )
                    }
                }
            },
            onUpdateChatModel = vm::setChatModel,
            onUpdateAssistant = vm::updateAssistant,
            onMoreClick = {
                openPane(ChatSidePane.Files)
            },
            modifier = modifier,
            liftedBy = liftedBy,
            applyWindowInsets = applyWindowInsets,
            // 横屏三栏去掉输入区上方的渐变遮罩
            showScrim = !threePane,
        )
    }

    Surface(
        color = LocalUiSlots.current.pageBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        AssistantBackground(
            setting = setting,
            modifier = Modifier
                .hazeSource(backgroundHazeState),
            // 横屏三栏不再盖渐变遮罩，背景图原样透出来
            scrim = !threePane,
        )
        // 横屏三栏：本轮改动那一行收进右栏，消息下面不再重复
        CompositionLocalProvider(
            LocalTurnChangesInSidePanel provides threePane,
            // 横屏时清单与引导收进右栏，输入区不再重复渲染
            LocalConversationStatusInSidePanel provides threePane,
        ) {
        Row(modifier = Modifier.fillMaxSize()) {
        // 横屏中列按定宽排，余量留给右栏；竖屏没有右栏，中列必须吃满整宽
        Box(
            modifier = if (chatWidth != null) {
                Modifier.width(chatWidth).fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            },
        ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column {
                    TopBar(
                        hazeState = backgroundHazeState,
                        settings = setting,
                        assistant = assistant,
                        chatModel = currentChatModel,
                        folderId = conversation.folderId,
                        folders = conversationFolders,
                        hasMessages = conversation.messageNodes.isNotEmpty(),
                        threePane = threePane,
                        sidebarOpen = sidebarOpen,
                        onOpenSidebar = onOpenSidebar,
                        onNewChat = {
                            navigateToChatPage(navController)
                        },
                        onShowTaskTree = { openPane(ChatSidePane.TaskTree) },
                        onShowModelPicker = { openPane(ChatSidePane.ModelPicker) },
                        onSelectFolder = vm::updateFolder,
                        onCreateFolder = { showCreateFolderDialog = true },
                        onShowOverview = {
                            selectionState.selecting = false
                            previewMode = true
                        },
                        onExport = {
                            // 跟消息上的分享一样进入多选，默认全选，用户可以再挑
                            previewMode = false
                            selectionState.start(latestConversation.messageNodes.map { it.id })
                        },
                        onDelete = { showDeleteConfirm = true },
                        showCapsuleBackground = listScrolledUnderTopBar,
                        // 横屏的详情在右侧栏里，而侧栏是模态的、整高盖住顶栏：
                        // 这个提亮只在竖屏（顶栏底下展开）时看得见
                        detailsExpanded = showDetails,
                        onToggleDetails = { toggleDetailsPane() },
                    )
                }
            },
            bottomBar = {
                if (currentMessages.isNotEmpty()) {
                    ChatInputContent(todos = conversation.todos)
                }
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (currentMessages.isEmpty()) {
                    EmptyChatContent(
                        innerPadding = innerPadding,
                        assistant = assistant,
                        slogan = setting.displaySetting.emptyChatSlogan,
                        hazeState = backgroundHazeState,
                        input = {
                            ChatInputContent(
                                todos = conversation.todos,
                                modifier = Modifier.fillMaxWidth(),
                                applyWindowInsets = false,
                            )
                        },
                    )
                } else {
                    // 普通消息和子代理通知共用同一段删除判断，生成中一律拦下
                    val deleteMessage: (UIMessage) -> Unit = { message ->
                        if (loadingJob != null) {
                            vm.showDeleteBlockedWhileGeneratingError()
                        } else {
                            vm.deleteMessage(message)
                        }
                    }
                    ChatList(
                        innerPadding = innerPadding,
                        conversation = conversation,
                        state = chatListState,
                        previewListState = previewListState,
                        selectionState = selectionState,
                        loading = loadingJob != null,
                        compressing = sessionActivity == SessionActivity.Compressing,
                        onCreateNewChat = onCreateNewChat,
                        processingStatus = processingStatus,
                        previewMode = previewMode,
                        onPreviewModeChange = { previewMode = it },
                        settings = setting,
                        bubbleHazeState = backgroundHazeState,
                        contentSource = backgroundHazeState,
                        errors = errors,
                        onDismissError = onDismissError,
                        onClearAllErrors = onClearAllErrors,
                        onRegenerate = {
                            vm.regenerateAtMessage(it)
                        },
                        onEdit = {
                            inputState.editingMessage = it.id
                            inputState.setContents(it.parts)
                        },
                        onEditSubAgentNotice = { message, text ->
                            // 子代理通知的正文改写：直接写回会话，不进输入框的编辑状态
                            vm.handleMessageEdit(
                                parts = listOf(UIMessagePart.Text(text)),
                                messageId = message.id,
                            )
                        },
                        onForkMessage = {
                            scope.launch {
                                val fork = vm.forkMessage(message = it)
                                navigateToChatPage(navController, chatId = fork.id)
                            }
                        },
                        onDelete = deleteMessage,
                        onDeleteSubAgentNotice = deleteMessage,
                        onUpdateMessage = { newNode ->
                            vm.selectMessageNode(newNode.id, newNode.selectIndex)
                        },
                        onClickSuggestion = { suggestion ->
                            inputState.editingMessage = null
                            inputState.setMessageText(suggestion)
                        },
                        onTranslate = { message, locale ->
                            vm.translateMessage(message, locale)
                        },
                        onClearTranslation = { message ->
                            vm.clearTranslationField(message.id)
                        },
                        onJumpToMessage = { index ->
                            previewMode = false
                            scope.launch {
                                // 预览页滑出、主列表重新挂载都在这一帧，等下一次测量再定位，
                                // 现在算距离用的是预览页那份还没挂上的布局
                                chatListState.requestScrollToItem(index)
                            }
                        },
                        onToolApproval = { toolCallId, approved, reason ->
                            vm.handleToolApproval(toolCallId, approved, reason)
                        },
                        onToolAnswer = { toolCallId, answer ->
                            vm.handleToolAnswer(toolCallId, answer)
                        },
                        onToggleFavorite = { node ->
                            vm.toggleMessageFavorite(node)
                        },
                        onConversationSystemPromptChange = { newPrompt ->
                            vm.updateConversationSystemPrompt(newPrompt)
                        },
                        emptyHero = null,
                        )
                }
                // 详情浮层盖在聊天内容上方：不占 Scaffold 的布局，展开和收起都不会推挤聊天区。
                // 外层有 RowScope，直接写 AnimatedVisibility 会解析到 RowScope 的扩展，这里显式用顶层版本
                androidx.compose.animation.AnimatedVisibility(
                    visible = showDetails && !threePane,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = innerPadding.calculateTopPadding())
                        .fillMaxWidth(),
                    enter = expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    ) + fadeIn(),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    ) + fadeOut(),
                ) {
                    ConversationDetailsPanel(
                        assistant = assistant,
                        model = currentChatModel,
                        settings = setting,
                        workspaces = workspaces,
                        folderWorkspaceName = folderWorkspaceName,
                        contextTokens = contextTokens,
                        messageCount = currentMessages.size,
                        previewState = requestPreviewState,
                        hazeState = backgroundHazeState,
                        onCollapse = { showDetails = false },
                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                    )
                }
            }
        }

        }
        // 横屏三栏：右栏是标签页式的看板，聊天区不再被弹层盖住
        if (threePane) {
            ColumnResizeHandle(onDrag = onChatResize)
            ChatSidePanelColumn(
                panes = openPanes,
                selected = selectedPane,
                onSelect = { selectedPaneName = it.name },
                onOpen = { openTab(it) },
                onClose = { closeTab(it) },
                // 右栏吃掉剩余宽度：左栏收起时中列左移，空出来的位置全给它
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 4.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                // 右栏背后是页面背景，取背景那份 Haze 源；聊天内容的源只覆盖左边
                hazeState = backgroundHazeState,
            ) { pane ->
                when (pane) {
                    ChatSidePane.Details -> ConversationDetailsPanel(
                        assistant = assistant,
                        model = currentChatModel,
                        settings = setting,
                        workspaces = workspaces,
                        folderWorkspaceName = folderWorkspaceName,
                        contextTokens = contextTokens,
                        messageCount = currentMessages.size,
                        previewState = requestPreviewState,
                        hazeState = backgroundHazeState,
                        onCollapse = { closePane() },
                        embedded = true,
                        // 标签条已经有标题，内容里不再重复一行
                        showHeader = false,
                    )

                    ChatSidePane.TaskTree -> SubAgentTaskTree(
                        conversationId = conversation.id,
                        onOpenConversation = { id ->
                            closePane()
                            navigateToChatPage(navController, chatId = id)
                        },
                        modifier = Modifier.fillMaxSize(),
                        showTitle = false,
                    )

                    ChatSidePane.Status -> ConversationStatusPane(
                        todos = conversation.todos,
                        guidances = messageQueue.guidances,
                        assistant = assistant,
                        hideCompletedTodos = setting.displaySetting.hideCompletedTodoCard,
                        onTodosChange = vm::updateTodos,
                        onUpdateAssistant = vm::updateAssistant,
                        onGuidanceToggleMode = vm::toggleGuidanceMode,
                        onGuidanceRemove = vm::removeGuidance,
                        conversationId = conversation.id,
                        liveGuidance = conversation.config?.liveGuidance,
                        onLiveGuidanceModeChange = vm::setLiveGuidancePersistence,
                        onLiveGuidanceNextMessage = vm::armLiveGuidanceForNextMessage,
                        modifier = Modifier.fillMaxSize(),
                    )

                    ChatSidePane.Changes -> TurnChangesPane(
                        parts = lastAssistantMessage?.parts.orEmpty(),
                        assistant = assistant,
                        conversationId = conversation.id,
                        hasChanges = turnEditedFiles.isNotEmpty(),
                        modifier = Modifier.fillMaxSize(),
                    )

                    ChatSidePane.Usage -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ConversationUsageDetails(
                            messages = currentMessages,
                            contextLength = currentChatModel?.effectiveContextWindow,
                        )
                    }

                    ChatSidePane.Files -> ChatFilesPickerContent(
                        inputState = inputState,
                        setting = setting,
                        conversation = conversation,
                        folderWorkspaceName = folderWorkspaceName,
                        assistant = assistant,
                        chatModel = currentChatModel,
                        vm = vm,
                        attachmentPickerActions = attachmentPickerActions,
                        onStartVoiceMode = onStartVoiceMode,
                        onDismiss = { closePane() },
                        modifier = Modifier.fillMaxSize(),
                    )

                    ChatSidePane.Workspace -> WorkspaceFilesPane(
                        workspaceIds = assistant.allWorkspaceIds.map { it.toString() },
                        workspaces = workspaces,
                        modifier = Modifier.fillMaxSize(),
                    )

                    ChatSidePane.Terminal -> WorkspaceTerminalPane(
                        workspaceIds = assistant.allWorkspaceIds.map { it.toString() },
                        workspaces = workspaces,
                        modifier = Modifier.fillMaxSize(),
                    )

                    ChatSidePane.ModelPicker -> ModelListContent(
                        state = chatModelListState,
                        onSelect = { model -> vm.setChatModel(model) },
                        onDismiss = { closePane() },
                        modifier = Modifier.fillMaxSize(),
                    )

                    ChatSidePane.BackgroundTasks -> BackgroundTasksContent(
                        tasks = backgroundTasks,
                        onStop = vm::stopBackgroundTask,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                    )
                }
            }
        }
        }
        }

        // 竖屏的后台任务弹层；横屏那份由上面的右侧栏接管
        if (showBackgroundTasks && !threePane) {
            BackgroundTasksSheet(
                tasks = backgroundTasks,
                onStop = vm::stopBackgroundTask,
                onDismiss = { showBackgroundTasks = false },
            )
        }

        busySendSnapshot?.let { snapshot ->
            BusySendDialog(
                snapshot = snapshot,
                submitting = busySendSubmitting,
                onDismiss = { if (!busySendSubmitting) busySendSnapshot = null },
                onQueue = queue@{
                    if (busySendSubmitting || latestConversation.id != snapshot.conversationId) return@queue
                    if (currentChatModel == null) {
                        toaster.show(context.getString(R.string.media_creation_page_select_model_first), type = ToastType.Error)
                        return@queue
                    }
                    if (contextLimitBlocksSend) {
                        toaster.show(contextLimitMessage, type = ToastType.Error)
                        return@queue
                    }
                    if (vm.sendAfterCurrent(snapshot.conversationId, snapshot.parts)) {
                        clearAcceptedComposerSnapshot(snapshot)
                        busySendSnapshot = null
                    } else {
                        toaster.show(context.getString(R.string.chat_page_busy_rejected), type = ToastType.Error)
                    }
                },
                onGuide = { persistent -> submitBusyGuidance(snapshot, persistent) },
                onLegacyInterrupt = {
                    if (!busySendSubmitting && latestConversation.id == snapshot.conversationId) {
                        busySendSnapshot = null
                        sendInterjectSnapshot(snapshot)
                    }
                },
            )
        }

        if (showCompressAndSendDialog) {
            AnimatedAlertDialog(
                onDismissRequest = { showCompressAndSendDialog = false },
                title = { Text(stringResource(R.string.chat_page_compress_before_send_title)) },
                text = { Text(stringResource(R.string.chat_page_compress_before_send_desc)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showCompressAndSendDialog = false
                            submitInput()
                        }
                    ) {
                        Text(stringResource(R.string.chat_page_compress_and_send))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCompressAndSendDialog = false }) {
                        Text(stringResource(R.string.assistant_page_cancel))
                    }
                },
            )
        }

        if (showUsageSheet && !threePane) {
            // 默认允许半展开，show() 会先停在半屏，得用手再拉一次；这里只留 Hidden / Expanded
            val usageSheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            )
            ModalBottomSheet(
                onDismissRequest = { showUsageSheet = false },
                sheetState = usageSheetState,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.chat_message_usage_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    ConversationUsageDetails(
                        messages = currentMessages,
                        contextLength = currentChatModel?.effectiveContextWindow,
                    )
                }
            }
        }

        if (showTaskTree && !threePane) {
            SubAgentTaskSheet(
                conversationId = conversation.id,
                hazeState = backgroundHazeState,
                onDismiss = { showTaskTree = false },
                onOpenConversation = { id ->
                    showTaskTree = false
                    navigateToChatPage(navController, chatId = id)
                },
            )
        }

        if (showCreateFolderDialog) {
            var folderName by remember { mutableStateOf("") }
            AnimatedAlertDialog(
                onDismissRequest = { showCreateFolderDialog = false },
                title = { Text(stringResource(R.string.chat_page_create_folder)) },
                text = {
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.chat_page_folder_name)) },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.createFolder(folderName)
                            showCreateFolderDialog = false
                        },
                        enabled = folderName.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.chat_page_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateFolderDialog = false }) {
                        Text(stringResource(R.string.chat_page_cancel))
                    }
                },
            )
        }

        RikkaConfirmDialog(
            show = showDeleteConfirm,
            title = stringResource(R.string.confirm_delete),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                showDeleteConfirm = false
                scope.launch {
                    vm.deleteConversation(conversation).join()
                    navigateToChatPage(navController)
                }
            },
            onDismiss = { showDeleteConfirm = false },
            text = { Text(stringResource(R.string.chat_page_delete_conversation_confirm)) },
        )

        if (showFilesSheet && !threePane) {
            ChatFilesPickerSheet(
                inputState = inputState,
                setting = setting,
                conversation = conversation,
                folderWorkspaceName = folderWorkspaceName,
                assistant = assistant,
                chatModel = currentChatModel,
                vm = vm,
                attachmentPickerActions = attachmentPickerActions,
                onStartVoiceMode = onStartVoiceMode,
                onDismiss = { showFilesSheet = false },
            )
        }

        // 横屏用右侧栏里的那份，这里只在竖屏组合
        if (!threePane) {
            ModelListSheet(state = chatModelListState) { model ->
                vm.setChatModel(model)
            }
        }
    }
}

@Composable
private fun ChatFilesPickerSheet(
    inputState: ChatInputState,
    setting: Settings,
    conversation: Conversation,
    folderWorkspaceName: String?,
    assistant: Assistant,
    chatModel: Model?,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    val filesSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        sheetState = filesSheetState,
        onDismissRequest = onDismiss,
    ) {
        ChatFilesPickerContent(
            inputState = inputState,
            setting = setting,
            conversation = conversation,
            folderWorkspaceName = folderWorkspaceName,
            assistant = assistant,
            chatModel = chatModel,
            vm = vm,
            attachmentPickerActions = attachmentPickerActions,
            onStartVoiceMode = onStartVoiceMode,
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 附件、工作区、搜索、扩展这些入口的本体：不含外层弹层，横屏时整块放进右侧栏 */
@Composable
private fun ChatFilesPickerContent(
    inputState: ChatInputState,
    setting: Settings,
    conversation: Conversation,
    folderWorkspaceName: String?,
    assistant: Assistant,
    chatModel: Model?,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    onDismiss: () -> Unit,
    // 弹层里保持 wrap：sheet 的高度跟着内容走，别被撑成整屏；侧栏里传 fillMaxSize
    modifier: Modifier = Modifier,
) {
    val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showCompressDialog by remember { mutableStateOf(false) }

    fun dismissAll() {
        showCompressDialog = false
        onDismiss()
    }

    Box(modifier = modifier) {
        FilesPicker(
            conversation = conversation,
            folderWorkspaceName = folderWorkspaceName,
            state = inputState,
            assistant = assistant,
            chatModel = chatModel,
            mcpManager = vm.mcpManager,
            onCompressContext = vm::handleCompressContext,
            onUpdateAssistant = vm::updateAssistant,
            onUpdateWorkspaceCwd = vm::updateWorkspaceCwd,
            onUpdateSearchMode = { mode ->
                vm.updateSearch(
                    enableWebSearch = mode == SearchMode.LOCAL,
                    builtInSearch = mode == SearchMode.BUILT_IN,
                )
            },
            onToggleSearchService = { id ->
                vm.updateSettings { latest ->
                    val defaultId = latest.searchServices
                        .getOrNull(latest.searchServiceSelected)
                        ?.id
                        ?.toString()
                    val current = latest.enabledSearchServiceIds.ifEmpty {
                        if (defaultId != null) setOf(defaultId) else emptySet()
                    }
                    val next = when {
                        id !in current -> current + id
                        current.size > 1 -> current - id
                        else -> current
                    }
                    latest.copy(enabledSearchServiceIds = next)
                }
            },
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = { dismissAll() },
            onTakePic = attachmentPickerActions.onTakePicture,
            onPickImage = attachmentPickerActions.onPickImage,
            onPickVideo = attachmentPickerActions.onPickVideo,
            onPickAudio = attachmentPickerActions.onPickAudio,
            onPickFile = attachmentPickerActions.onPickFile,
            onSketch = attachmentPickerActions.onSketch,
            onStartVoiceMode = if (
                setting.getSelectedASRProvider()?.supportsServerVadVoiceMode == true &&
                voiceState.phase == VoicePhase.Off
            ) {
                {
                    dismissAll()
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onStartVoiceMode()
                }
            } else null,
        )
    }
}

// 横屏三栏的整体缩放：0.75 就是整体缩小 25%
private const val LANDSCAPE_UI_SCALE = 0.75f

// 横屏三栏左栏的宽度（缩放后的 dp）
private val LANDSCAPE_SIDEBAR_WIDTH = 300.dp

/** 右栏的「本轮改动」标签页：和消息下面那一行同源，只是换了个位置 */
@Composable
private fun TurnChangesPane(
    parts: List<UIMessagePart>,
    assistant: Assistant,
    conversationId: Uuid,
    hasChanges: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!hasChanges) {
        Box(
            modifier = modifier.padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.chat_page_turn_changes_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 16.dp),
    ) {
        EditedFilesList(
            parts = parts,
            assistant = assistant,
            conversationId = conversationId,
            isLastMessage = true,
        )
    }
}

// 顶栏按钮统一尺寸：45dp 的按钮配 22.5dp 的图标（原来是 40dp/20dp，等比放大 12.5%），
// 按钮和图标保持 2:1，圆角、间距关系都不变。
private val TopBarButtonSize = 45.dp
private val TopBarButtonIconSize = 22.5.dp

@Composable
private fun TopBar(
    hazeState: HazeState,
    settings: Settings,
    assistant: Assistant,
    chatModel: Model?,
    folderId: Uuid?,
    folders: List<Folder>,
    hasMessages: Boolean,
    threePane: Boolean,
    sidebarOpen: Boolean,
    onOpenSidebar: () -> Unit,
    showCapsuleBackground: Boolean,
    onNewChat: () -> Unit,
    onShowTaskTree: () -> Unit,
    onShowModelPicker: () -> Unit,
    onSelectFolder: (Uuid?) -> Unit,
    onCreateFolder: () -> Unit,
    onShowOverview: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    detailsExpanded: Boolean,
    onToggleDetails: () -> Unit,
) {
    val effect = settings.displaySetting.effectiveBackgroundEffect
    // 着色交给 chatInputHaze，按当前档位读滑杆；按钮自己只在关闭特效时铺实色
    fun effectColor(base: Color): Color =
        if (effect == BackgroundEffectType.OFF) base else Color.Transparent
    val effectBorder = if (effect == BackgroundEffectType.BLUR) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    } else {
        null
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = {
            // 横屏三栏的顶栏只留菜单、新建和会话菜单：模型、详情、任务树都在右栏的标签页里。
            // 胶囊放在标题位：这里只拿到菜单与右侧按钮之间的剩余宽度，文件夹名超长时按这份宽度截断，
            // 放在导航位则会顶到右边的按钮上
            if (!threePane && chatModel != null) {
                ChatModelFolderPill(
                    settings = settings,
                    model = chatModel,
                    folderId = folderId,
                    folders = folders,
                    onSelectFolder = onSelectFolder,
                    onCreateFolder = onCreateFolder,
                    onShowModelPicker = onShowModelPicker,
                    hazeState = hazeState,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        },
        navigationIcon = {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Row(
                    // 全宽模式下顶栏两侧不再留白，仍然保留状态栏与刘海的 inset
                    modifier = Modifier.padding(start = adaptiveHorizontalPaddingValue(12.dp)),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val navShape = when {
                        LocalFullWidthComponents.current -> RoundedCornerShape(0.dp)
                        else -> CircleShape
                    }
                    val navColor = MaterialTheme.colorScheme.surfaceContainerLow
                    // 横屏三栏里这是左栏的开合，竖屏还是把抽屉拉出来
                    Surface(
                        onClick = onOpenSidebar,
                        modifier = Modifier
                            .size(TopBarButtonSize)
                            .appButtonShadow(navShape, 2.dp)
                            .clip(navShape)
                            .background(effectColor(navColor))
                            .chatInputHaze(
                                hazeState = hazeState,
                                displaySetting = settings.displaySetting,
                                containerColor = navColor,
                                shape = navShape,
                            )
                            // 描边画在模糊层之后，否则会被糊掉
                            .then(
                                if (effectBorder != null) {
                                    Modifier.border(effectBorder, navShape)
                                } else {
                                    Modifier
                                }
                            ),
                        shape = navShape,
                        color = Color.Transparent,
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                HugeIcons.Menu03,
                                "Messages",
                                modifier = Modifier.size(TopBarButtonIconSize),
                                // 左栏展开时按钮提亮，和详情按钮同一套反馈
                                tint = if (threePane && sidebarOpen) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalContentColor.current
                                },
                            )
                        }
                    }
                }
            }
        },
        actions = {
            Row(
                modifier = Modifier.padding(end = adaptiveHorizontalPaddingValue(8.dp)),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 和左侧的会话胶囊共用同一个容器，入口之间同样用一条细线隔开，
                // 顶栏两头的胶囊才是同一套东西
                TopBarPill(settings = settings, hazeState = hazeState) {
                    // 详情和任务树在横屏是右栏的标签页，顶栏不再重复两个入口
                    if (!threePane) {
                        IconButton(onClick = onToggleDetails, modifier = Modifier.size(TopBarButtonSize)) {
                            Icon(
                                HugeIcons.Layers01,
                                stringResource(R.string.chat_page_details_button),
                                modifier = Modifier.size(TopBarButtonIconSize),
                                // 展开时按钮跟着提亮，顶栏一眼能看出详情面板是开着的
                                tint = if (detailsExpanded) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalContentColor.current
                                },
                            )
                        }

                        TopBarPillDivider()

                        IconButton(onClick = onShowTaskTree, modifier = Modifier.size(TopBarButtonSize)) {
                            Icon(
                                HugeIcons.Flowchart01,
                                stringResource(R.string.chat_task_tree_button),
                                modifier = Modifier.size(TopBarButtonIconSize),
                            )
                        }

                        TopBarPillDivider()
                    }

                    IconButton(onClick = { onNewChat() }, modifier = Modifier.size(TopBarButtonSize)) {
                        Icon(HugeIcons.PencilEdit01, "New Message", modifier = Modifier.size(TopBarButtonIconSize))
                    }

                    TopBarPillDivider()

                    ChatPageMenu(
                        enabled = hasMessages,
                        onShowOverview = onShowOverview,
                        onExport = onExport,
                        onDelete = onDelete,
                    )
                }
            }
        },
    )
}


/**
 * 顶栏两侧共用的胶囊容器：同高、同圆角、同投影，模糊与描边也只有这一份实现。
 *
 * 里面的入口各自点击，段与段之间用 [TopBarPillDivider] 隔开。
 * 关闭背景特效时容器自己铺实色，开着时着色交给 chatInputHaze。
 */
@Composable
private fun TopBarPill(
    settings: Settings,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val effect = settings.displaySetting.effectiveBackgroundEffect
    val shape = if (LocalFullWidthComponents.current) {
        RoundedCornerShape(0.dp)
    } else {
        RoundedCornerShape(50)
    }
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow

    Row(
        modifier = modifier
            .height(TopBarButtonSize)
            .appButtonShadow(shape, 2.dp)
            .clip(shape)
            .background(if (effect == BackgroundEffectType.OFF) containerColor else Color.Transparent)
            .chatInputHaze(
                hazeState = hazeState,
                displaySetting = settings.displaySetting,
                containerColor = containerColor,
                shape = shape,
            )
            // 描边画在模糊层之后，否则会被糊掉
            .then(
                if (effect == BackgroundEffectType.BLUR) {
                    Modifier.border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shape,
                    )
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 入口按 45dp 铺满胶囊，不被 M3 的最小触控尺寸撑大，左右两侧才同高
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            content()
        }
    }
}

/** 胶囊里两个入口之间的细线：只切中间一段，胶囊还是一整块。 */
@Composable
private fun TopBarPillDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(18.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    )
}


/**
 * 顶栏的会话胶囊：左半边是会话文件夹，右边是当前模型。
 *
 * 两部分各点各的：文件夹打开选单，模型打开模型选择面板。整块放在 AppBar 的标题位，
 * 标题位只拿到菜单按钮与右侧按钮之间的剩余宽度，文件夹名过长时按这份宽度截断。
 * 供应商图标让位给文件名：标题位在手机上一共一百多 dp，两个图标加名字放不下，
 * 供应商在模型选择面板里仍然看得到。
 */
@Composable
private fun ChatModelFolderPill(
    settings: Settings,
    model: Model,
    folderId: Uuid?,
    folders: List<Folder>,
    onSelectFolder: (Uuid?) -> Unit,
    onCreateFolder: () -> Unit,
    onShowModelPicker: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val folder = folders.firstOrNull { it.id == folderId }
    var menuExpanded by remember { mutableStateOf(false) }

    TopBarPill(
        settings = settings,
        hazeState = hazeState,
        modifier = modifier,
    ) {
        Box {
            Row(
                modifier = Modifier
                    .height(TopBarButtonSize)
                    .clickable { menuExpanded = true }
                    .padding(start = 8.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    HugeIcons.Folder01,
                    stringResource(R.string.chat_page_folder_pill),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = folder?.name ?: stringResource(R.string.chat_page_folder_unfiled),
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ChatFolderMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                folders = folders,
                selectedFolderId = folderId,
                onSelect = { selected ->
                    menuExpanded = false
                    onSelectFolder(selected)
                },
                onCreateFolder = {
                    menuExpanded = false
                    onCreateFolder()
                },
            )
        }

        TopBarPillDivider()

        Row(
            modifier = Modifier
                .height(TopBarButtonSize)
                .clickable(onClick = onShowModelPicker)
                .padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AutoAIIcon(name = model.modelId, color = Color.Transparent)
        }
    }
}

// 胶囊里的文件夹选单：未归类排在最上面，当前归属打勾
@Composable
private fun ChatFolderMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    folders: List<Folder>,
    selectedFolderId: Uuid?,
    onSelect: (Uuid?) -> Unit,
    onCreateFolder: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        Text(
            text = stringResource(R.string.chat_page_folder_pill),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        FolderMenuItem(
            label = stringResource(R.string.chat_page_folder_unfiled),
            selected = selectedFolderId == null,
            onClick = { onSelect(null) },
        )
        folders.forEach { folder ->
            FolderMenuItem(
                label = folder.name,
                selected = folder.id == selectedFolderId,
                onClick = { onSelect(folder.id) },
            )
        }
        if (folders.isEmpty()) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_page_folder_empty)) },
                onClick = {},
                enabled = false,
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.chat_page_create_folder)) },
            leadingIcon = { Icon(HugeIcons.FolderAdd, contentDescription = null) },
            onClick = onCreateFolder,
        )
    }
}

@Composable
private fun FolderMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val trailingIcon: (@Composable () -> Unit)? = if (selected) {
        {
            Icon(
                HugeIcons.Tick01,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    } else {
        null
    }
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick,
        leadingIcon = {
            Icon(
                HugeIcons.Folder01,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = trailingIcon,
    )
}


@Composable
private fun EmptyChatContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    slogan: String,
    hazeState: HazeState,
    input: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .navigationBarsPadding()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyChatHero(
                assistant = assistant,
                slogan = slogan,
                hazeState = hazeState,
            )
            Spacer(Modifier.height(16.dp))
            input()
        }
    }
}

@Composable
private fun EmptyChatHero(
    assistant: Assistant,
    slogan: String,
    hazeState: HazeState,
) {
    val settings = LocalSettings.current
    val effect = settings.displaySetting.effectiveBackgroundEffect
    val shape = RoundedCornerShape(28.dp)
    val baseColor = MaterialTheme.colorScheme.surfaceContainerLow
    Column(
        modifier = Modifier
            .padding(horizontal = 40.dp)
            .clip(shape)
            .then(
                if (effect != BackgroundEffectType.OFF) {
                    Modifier.chatInputHaze(
                        hazeState = hazeState,
                        displaySetting = settings.displaySetting,
                        containerColor = baseColor,
                        shape = shape,
                    )
                } else {
                    Modifier
                }
            )
            // 描边画在模糊层之后，否则会被糊掉
            .then(
                if (effect == BackgroundEffectType.BLUR) {
                    Modifier.border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shape,
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UIAvatar(
            name = assistant.name,
            value = assistant.avatar,
            avatarSize = 88.dp,
            shape = CircleShape,
        )
        Text(
            text = slogan.ifBlank { stringResource(R.string.chat_empty_default_slogan) },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// 顶栏右侧的会话菜单；空对话没有内容可概览、导出或删除，菜单项全部置灰
@Composable
private fun ChatPageMenu(
    enabled: Boolean,
    onShowOverview: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    @Composable
    fun MenuItem(
        text: String,
        icon: ImageVector,
        index: Int,
        count: Int,
        destructive: Boolean = false,
        onClick: () -> Unit,
    ) {
        DropdownMenuItem(
            text = { Text(text) },
            onClick = {
                expanded = false
                onClick()
            },
            shape = MenuDefaults.itemShape(index = index, count = count).shape,
            leadingIcon = { Icon(icon, contentDescription = null) },
            enabled = enabled,
            colors = if (destructive) {
                val errorColor = MaterialTheme.colorScheme.error
                MenuDefaults.itemColors(textColor = errorColor, leadingIconColor = errorColor)
            } else {
                MenuDefaults.itemColors()
            },
        )
    }

    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(TopBarButtonSize)) {
            Icon(
                HugeIcons.MoreVertical,
                stringResource(R.string.more_options),
                modifier = Modifier.size(TopBarButtonIconSize),
            )
        }
        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
                MenuItem(
                    text = stringResource(R.string.chat_page_conversation_map),
                    icon = HugeIcons.LeftToRightListBullet,
                    index = 0,
                    count = 2,
                    onClick = onShowOverview,
                )
                MenuItem(
                    text = stringResource(R.string.export_title),
                    icon = HugeIcons.Share04,
                    index = 1,
                    count = 2,
                    onClick = onExport,
                )
            }
            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 1)) {
                MenuItem(
                    text = stringResource(R.string.delete),
                    icon = HugeIcons.Delete01,
                    index = 0,
                    count = 1,
                    destructive = true,
                    onClick = onDelete,
                )
            }
        }
    }
}
