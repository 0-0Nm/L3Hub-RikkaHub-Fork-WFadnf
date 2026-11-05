package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Flowchart01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.PencilEdit01
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
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.effectiveContextWindow
import me.rerere.rikkahub.data.model.isContextLimitReached
import me.rerere.rikkahub.data.model.lastContextTokens
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.ui.components.ai.ChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.FilesPicker
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.SearchMode
import me.rerere.rikkahub.ui.components.ai.chatInputHaze
import me.rerere.rikkahub.ui.components.ai.completion.WorkspaceCompletionProvider
import me.rerere.rikkahub.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.message.ConversationUsageDetails
import me.rerere.rikkahub.ui.components.message.usageTokenSpeed
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.glassSurfaceAlpha
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.pages.chat.terminal.TerminalChatInput
import me.rerere.rikkahub.ui.pages.chat.terminal.TerminalChatTopBar
import me.rerere.rikkahub.ui.pages.chat.terminal.TerminalModelPickerDialog
import me.rerere.rikkahub.ui.pages.chat.terminal.TerminalOutputDialog
import me.rerere.rikkahub.ui.pages.chat.terminal.parseTerminalCommand
import me.rerere.rikkahub.ui.pages.chat.terminal.terminalHelpText
import me.rerere.rikkahub.ui.terminal.IconButton
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.rikkahub.ui.terminal.TopAppBar
import me.rerere.rikkahub.ui.theme.LocalUiSlots
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents
import me.rerere.rikkahub.ui.theme.TerminalPalette
import me.rerere.rikkahub.ui.theme.TerminalTheme
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPaddingValue
import me.rerere.rikkahub.ui.theme.appButtonShadow
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.copyMessageToClipboard
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.writeClipboardText
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import java.io.File
import java.util.Locale
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
    val conversation by vm.conversationUi.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()

    // 新会话发出第一条消息前，跟随抽屉当前选中的文件夹（#2133）
    val drawerVm: ChatDrawerVM = koinViewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
    val selectedFolderId by drawerVm.selectedFolderId.collectAsStateWithLifecycle()
    LaunchedEffect(selectedFolderId) {
        vm.updateDraftFolder(selectedFolderId)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
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

    // Handle back press when drawer is open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    // Clear input focus so popup transitions cannot reopen the keyboard.
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            focusManager.clearFocus(force = true)
            softwareKeyboardController?.hide()
        }
    }

    val windowAdaptiveInfo = currentWindowDpSize()
    val isBigScreen =
        windowAdaptiveInfo.width > windowAdaptiveInfo.height && windowAdaptiveInfo.width >= 1100.dp

    // 进入大屏（永久抽屉）模式时重置抽屉状态为关闭，
    // 避免从横屏旋转回竖屏后，模态抽屉残留为打开状态且无法关闭（#1304）
    LaunchedEffect(isBigScreen) {
        if (isBigScreen && drawerState.isOpen) {
            drawerState.close()
        }
    }

    val openSettingsFromDrawer: () -> Unit = {
        if (!isBigScreen) scope.launch { drawerState.close() }
        navController.navigate(Screen.Setting)
    }
    val startNewChat: () -> Unit = {
        if (!isBigScreen) scope.launch { drawerState.close() }
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

    Box(modifier = Modifier.fillMaxSize()) {
    when {
        isBigScreen -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    // 大屏的抽屉是和正文并排的一栏，背后没有背景层可取样，保持实色底板
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting,
                        onNewChat = startNewChat,
                        onOpenSettings = openSettingsFromDrawer,
                    )
                }
            ) {
                ChatPageContent(
                    onStartVoiceMode = startVoiceMode,
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    assistant = assistant,
                    currentChatModel = currentChatModel,
                    bigScreen = true,
                    backgroundHazeState = backgroundHazeState,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
        }

        else -> {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting,
                        onNewChat = startNewChat,
                        onOpenSettings = openSettingsFromDrawer,
                        hazeState = backgroundHazeState,
                    )
                }
            ) {
                ChatPageContent(
                    onStartVoiceMode = startVoiceMode,
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    assistant = assistant,
                    currentChatModel = currentChatModel,
                    bigScreen = false,
                    backgroundHazeState = backgroundHazeState,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
            BackHandler(drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }
        }
    }

    }
}

@Composable
private fun ChatPageContent(
    onStartVoiceMode: () -> Unit,
    inputState: ChatInputState,
    loadingJob: Job?,
    processingStatus: String? = null,
    setting: Settings,
    bigScreen: Boolean,
    conversation: Conversation,
    drawerState: DrawerState,
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
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    // currentMessages 是 getter，每次访问都新建一份列表；同一帧里有多处要用，算一次就够。
    // 它只依赖 messageNodes，按节点列表做键不会漏掉分支切换。
    val currentMessages = remember(conversation.messageNodes) { conversation.currentMessages }

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
    val lorebookInjectionCount = remember(conversation.messageNodes, assistant.lorebookIds, setting.lorebooks) {
        collectLorebookInjections(currentMessages, assistant, setting.lorebooks).size
    }

    // 上下文已经爆了且开了自动压缩：点发送先确认，确认后由 ChatService 先压缩再发送
    var showCompressAndSendDialog by remember { mutableStateOf(false) }
    val submitInput: () -> Unit = {
        if (inputState.isEditing()) {
            vm.handleMessageEdit(
                parts = inputState.getContents(),
                messageId = inputState.editingMessage!!,
            )
        } else {
            vm.handleMessageSend(inputState.getContents())
            scope.launch {
                delay(100.milliseconds)
                chatListState.requestScrollToItem(currentMessages.size + 5)
            }
        }
        inputState.clearInput()
    }

    var previewMode by rememberSaveable { mutableStateOf(false) }
    val hazeState = rememberHazeState()
    var showFilesSheet by remember { mutableStateOf(false) }
    var showTaskTree by remember { mutableStateOf(false) }
    var showUsageSheet by remember { mutableStateOf(false) }
    var showTerminalModelPicker by remember { mutableStateOf(false) }
    val chatModelListState = rememberModelListState(
        modelId = assistant.chatModelId,
        providers = setting.providers,
        type = ModelType.CHAT,
    )
    var terminalOutput by remember { mutableStateOf<String?>(null) }
    val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()
    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = inputState,
        setting = setting,
        onAttachmentAdded = { showFilesSheet = false },
    )
    val allowAudioVideoAttachments =
        currentChatModel?.findProvider(setting.providers) is ProviderSetting.Google

    // 终端与普通输入共用的发送入口：模型、上下文门限检查都走这里
    val sendInput: () -> Unit = {
        if (currentChatModel == null) {
            toaster.show("请先选择模型", type = ToastType.Error)
        } else if (contextLimitBlocksSend && assistant.autoCompress) {
            showCompressAndSendDialog = true
        } else if (contextLimitReached) {
            toaster.show(contextLimitMessage, type = ToastType.Error)
            if (contextLimitBlocksSend) Unit else submitInput()
        } else {
            submitInput()
        }
    }

    val sendInterject: () -> Unit = interject@{
        if (contextLimitReached) {
            toaster.show(contextLimitMessage, type = ToastType.Error)
            if (contextLimitBlocksSend) return@interject
        }
        vm.handleMessageSend(inputState.getContents(), interject = true)
        inputState.clearInput()
        scope.launch {
            delay(100.milliseconds)
            chatListState.requestScrollToItem(currentMessages.size + 5)
        }
    }

    fun terminalMessages(): List<me.rerere.ai.ui.UIMessage> = currentMessages

    fun terminalMessageAt(arg: String, defaultAssistant: Boolean = false): me.rerere.ai.ui.UIMessage? {
        val index = arg.trim().toIntOrNull()
        return if (index != null) {
            terminalMessages().getOrNull(index)
        } else if (defaultAssistant) {
            terminalMessages().lastOrNull { it.role == MessageRole.ASSISTANT }
        } else {
            terminalMessages().lastOrNull()
        }
    }

    fun terminalConversationMarkdown(): String = buildString {
        appendLine("# ${conversation.title.ifBlank { "conversation" }}")
        appendLine()
        terminalMessages().forEach { message ->
            val role = when (message.role) {
                MessageRole.USER -> "User"
                MessageRole.ASSISTANT -> "Assistant"
                else -> "System"
            }
            appendLine("## $role")
            appendLine()
            appendLine(message.toText())
            appendLine()
        }
    }

    fun runTerminalCommand(raw: String) {
        val command = parseTerminalCommand(raw) ?: return
        var clearInput = true
        when (command.name) {
            "help" -> terminalOutput = terminalHelpText()
            "status" -> terminalOutput = buildString {
                appendLine("assistant : ${assistant.name.ifBlank { "assistant" }}")
                appendLine("model     : ${currentChatModel?.displayName ?: "no-model"}")
                appendLine("context   : ${contextTokens ?: 0} / ${currentChatModel?.maxContextTokens ?: "-"}")
                appendLine("messages  : ${currentMessages.size}")
                appendLine("speed     : ${usageTokenSpeed?.let { "%.1f tok/s".format(it) } ?: "-"}")
                append("queue     : ${messageQueue.messages.size}")
            }
            "model" -> {
                if (command.args.isBlank()) {
                    showTerminalModelPicker = true
                } else {
                    val models = setting.providers.flatMap { it.models }
                        .filter { it.type == ModelType.CHAT }
                    val query = command.args.lowercase()
                    val matches = models.filter {
                        it.displayName.lowercase().contains(query) ||
                            it.modelId.lowercase().contains(query)
                    }
                    when {
                        matches.size == 1 -> {
                            vm.setChatModel(matches.first())
                            val picked = matches.first()
                            terminalOutput = "model -> ${picked.displayName.ifBlank { picked.modelId }}"
                        }
                        matches.isEmpty() -> terminalOutput = "no model matched: ${command.args}"
                        else -> terminalOutput = buildString {
                            appendLine("multiple matches, use a longer name:")
                            matches.take(20).forEach {
                                appendLine("  ${it.displayName.ifBlank { it.modelId }}  [${it.modelId}]")
                            }
                        }
                    }
                }
            }
            "new" -> navigateToChatPage(navController)
            "messages" -> terminalOutput = buildString {
                terminalMessages().forEachIndexed { index, message ->
                    val role = if (message.role == MessageRole.USER) "U" else "A"
                    val preview = message.toText().lineSequence().firstOrNull().orEmpty().take(72)
                    appendLine("[%02d] %s %s".format(index, role, preview))
                }
            }
            "send" -> {
                if (command.args.isBlank()) {
                    terminalOutput = "usage: /send <text>"
                } else {
                    vm.handleMessageSend(listOf(UIMessagePart.Text(command.args)))
                    scope.launch {
                        delay(100.milliseconds)
                        chatListState.requestScrollToItem(currentMessages.size + 5)
                    }
                }
            }
            "retry" -> {
                val message = terminalMessageAt(command.args, defaultAssistant = true)
                if (message == null) {
                    terminalOutput = "no assistant message to regenerate"
                } else {
                    vm.regenerateAtMessage(message)
                }
            }
            "edit" -> {
                val message = terminalMessageAt(command.args)
                if (message == null) {
                    terminalOutput = "message not found: ${command.args}"
                } else {
                    inputState.editingMessage = message.id
                    inputState.setContents(message.parts)
                    clearInput = false
                }
            }
            "delete" -> {
                val message = terminalMessageAt(command.args)
                when {
                    message == null -> terminalOutput = "message not found: ${command.args}"
                    loadingJob != null -> vm.showDeleteBlockedWhileGeneratingError()
                    else -> vm.deleteMessage(message)
                }
            }
            "copy" -> {
                val message = terminalMessageAt(command.args)
                if (message == null) {
                    terminalOutput = "message not found: ${command.args}"
                } else {
                    context.copyMessageToClipboard(message)
                    toaster.show("已复制消息", type = ToastType.Success)
                }
            }
            "branch" -> {
                val message = terminalMessageAt(command.args)
                if (message == null) {
                    terminalOutput = "message not found: ${command.args}"
                } else {
                    scope.launch {
                        val fork = vm.forkMessage(message)
                        navigateToChatPage(navController, chatId = fork.id)
                    }
                }
            }
            "compress" -> vm.handleCompressContext(command.args, keepRecentMessages = 0)
            "usage" -> showUsageSheet = true
            "tree" -> showTaskTree = true
            "files" -> showFilesSheet = true
            "voice" -> onStartVoiceMode()
            "stop" -> vm.stopGeneration()
            "title" -> {
                if (command.args.isBlank()) {
                    terminalOutput = "usage: /title <text>"
                } else {
                    vm.updateTitle(command.args)
                    terminalOutput = "title -> ${command.args}"
                }
            }
            "pin" -> {
                vm.updatePinnedStatus(conversation)
                terminalOutput = if (conversation.isPinned) "unpinned" else "pinned"
            }
            "search" -> {
                val enabled = when (command.args.lowercase()) {
                    "on", "1", "true", "yes" -> true
                    "off", "0", "false", "no" -> false
                    else -> null
                }
                if (enabled == null) {
                    terminalOutput = "usage: /search on|off"
                } else {
                    vm.updateSearch(enableWebSearch = enabled, builtInSearch = enabled)
                    terminalOutput = "web search -> ${if (enabled) "on" else "off"}"
                }
            }
            "export" -> {
                val markdown = terminalConversationMarkdown()
                context.writeClipboardText(markdown)
                terminalOutput = "conversation copied as markdown (${markdown.length} chars)"
            }
            "save" -> {
                val markdown = terminalConversationMarkdown()
                val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
                val file = File(dir, "conversation-${conversation.id}-${System.currentTimeMillis()}.md")
                runCatching { file.writeText(markdown) }
                    .onSuccess { terminalOutput = "saved -> ${file.absolutePath}" }
                    .onFailure { terminalOutput = "save failed: ${it.message}" }
            }
            "assistant" -> {
                if (command.args.isBlank()) {
                    terminalOutput = buildString {
                        appendLine("assistants:")
                        setting.assistants.forEach { item ->
                            val mark = if (item.id == conversation.assistantId) "*" else " "
                            appendLine("$mark ${item.name.ifBlank { "assistant" }}  [${item.id}]")
                        }
                    }
                } else {
                    val query = command.args.lowercase()
                    val matches = setting.assistants.filter { it.name.lowercase().contains(query) }
                    when {
                        matches.size == 1 -> {
                            vm.moveConversationToAssistant(conversation, matches.first().id)
                            terminalOutput = "assistant -> ${matches.first().name}"
                        }
                        matches.isEmpty() -> terminalOutput = "no assistant matched: ${command.args}"
                        else -> terminalOutput = buildString {
                            appendLine("multiple matches:")
                            matches.take(20).forEach { appendLine("  ${it.name}") }
                        }
                    }
                }
            }
            "chats" -> navController.navigate(Screen.History)
            "favorite" -> navController.navigate(Screen.Favorite)
            "open" -> {
                val id = runCatching { Uuid.parse(command.args.trim()) }.getOrNull()
                if (id == null) {
                    terminalOutput = "usage: /open <conversation-id>\ncurrent: ${conversation.id}"
                } else {
                    navigateToChatPage(navController, chatId = id)
                }
            }
            "close" -> {
                scope.launch {
                    vm.deleteConversation(conversation)
                    navController.popBackStack()
                }
            }
            "fav" -> {
                val message = terminalMessageAt(command.args)
                val node = message?.let { target ->
                    conversation.messageNodes.firstOrNull { node -> node.messages.any { it.id == target.id } }
                }
                if (node == null) {
                    terminalOutput = "message not found: ${command.args}"
                } else {
                    val wasFavorite = node.isFavorite
                    vm.toggleMessageFavorite(node)
                    val label = if (wasFavorite) "unfavorited" else "favorited"
                    terminalOutput = "$label -> [${node.messages.first().id}]"
                }
            }
            "switch" -> {
                val node = conversation.messageNodes.lastOrNull { it.messages.size > 1 }
                val index = command.args.trim().toIntOrNull()
                if (node == null || index == null) {
                    terminalOutput = "usage: /switch <0..${node?.messages?.lastIndex ?: 0}>"
                } else {
                    vm.selectMessageNode(node.id, index.coerceIn(0, node.messages.lastIndex))
                    terminalOutput = "branch -> $index"
                }
            }
            "translate" -> {
                val message = terminalMessageAt(command.args, defaultAssistant = true)
                if (message == null) {
                    terminalOutput = "no assistant message to translate"
                } else {
                    vm.translateMessage(message, Locale.forLanguageTag(setting.translateTargetLanguage))
                }
            }
            "mcp" -> navController.navigate(Screen.SettingMcp)
            "workspace" -> showFilesSheet = true
            "settings" -> navController.navigate(Screen.Setting)
            "terminal" -> {
                when (command.args.lowercase()) {
                    "on", "1", "true" -> vm.updateSettings {
                        it.copy(displaySetting = it.displaySetting.copy(terminalStyle = true))
                    }
                    "off", "0", "false" -> vm.updateSettings {
                        it.copy(displaySetting = it.displaySetting.copy(terminalStyle = false))
                    }
                    else -> terminalOutput = "usage: /terminal on|off"
                }
            }
            "clear" -> inputState.clearInput()
            else -> terminalOutput = "unknown command: /${command.name}\n输入 /help 查看可用命令"
        }
        if (clearInput && command.name != "clear") {
            inputState.clearInput()
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

    @Composable
    fun ChatInputContent(
        todos: List<TodoItem> = emptyList(),
        modifier: Modifier = Modifier,
        liftedBy: Dp = 0.dp,
        applyWindowInsets: Boolean = true,
    ) {
        val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()
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
            loading = loadingJob != null,
            settings = setting,
            assistant = assistant,
            chatModel = currentChatModel,
            todos = todos,
            contextTokens = contextTokens,
            usageTokenSpeed = usageTokenSpeed,
            onUpdateContextMeterStyle = { style ->
                vm.updateSettings { latest ->
                    latest.copy(displaySetting = latest.displaySetting.copy(contextMeterStyle = style))
                }
            },
            injectedLorebookCount = lorebookInjectionCount,
            onShowUsage = { showUsageSheet = true },
            hazeState = hazeState,
            completionProviders = completionProviders,
            onCancelClick = {
                vm.stopGeneration()
            },
            onSendClick = sendInput,
            onInterjectClick = {
                if (contextLimitReached) {
                    toaster.show(contextLimitMessage, type = ToastType.Error)
                    if (contextLimitBlocksSend) return@ChatInput
                }
                vm.handleMessageSend(inputState.getContents(), interject = true)
                inputState.clearInput()
                scope.launch {
                    delay(100.milliseconds)
                    chatListState.requestScrollToItem(currentMessages.size + 5)
                }
            },
            onLongSendClick = {
                if (contextLimitReached) {
                    toaster.show(contextLimitMessage, type = ToastType.Error)
                    if (contextLimitBlocksSend) return@ChatInput
                }
                if (inputState.isEditing()) {
                    vm.handleMessageEdit(
                        parts = inputState.getContents(),
                        messageId = inputState.editingMessage!!,
                    )
                } else {
                    vm.handleMessageSend(content = inputState.getContents(), answer = false)
                    scope.launch {
                        chatListState.requestScrollToItem(currentMessages.size + 5)
                    }
                }
                inputState.clearInput()
            },
            onUpdateChatModel = vm::setChatModel,
            onUpdateAssistant = vm::updateAssistant,
            onMoreClick = {
                showFilesSheet = true
            },
            modifier = modifier,
            liftedBy = liftedBy,
            applyWindowInsets = applyWindowInsets,
        )
    }

    TerminalTheme(enabled = setting.displaySetting.terminalStyle) {
    Surface(
        color = if (setting.displaySetting.terminalStyle) {
            TerminalPalette.Background
        } else {
            LocalUiSlots.current.pageBackground
        },
        modifier = Modifier.fillMaxSize()
    ) {
        if (!setting.displaySetting.terminalStyle) {
            AssistantBackground(
                setting = setting,
                // 生成中和滚动中背景完全不用动：这两段时间最缺主线程，62~110 秒周期的漂移停一会儿看不出来
                animate = loadingJob == null && !chatListState.isScrollInProgress,
                modifier = Modifier
                    .hazeSource(hazeState)
                    .hazeSource(backgroundHazeState),
            )
        }
        Scaffold(
            topBar = {
                if (setting.displaySetting.terminalStyle) {
                    TerminalChatTopBar(
                        title = conversation.title,
                        assistantName = assistant.name,
                        modelName = currentChatModel?.displayName ?: "no-model",
                        contextTokens = contextTokens,
                        maxContextTokens = currentChatModel?.maxContextTokens,
                        bigScreen = bigScreen,
                        onMenu = { scope.launch { drawerState.open() } },
                        onNewChat = { navigateToChatPage(navController) },
                        onTaskTree = { showTaskTree = true },
                        onUsage = { showUsageSheet = true },
                        onHelp = { terminalOutput = terminalHelpText() },
                    )
                } else {
                    TopBar(
                        hazeState = backgroundHazeState,
                        settings = setting,
                        assistant = assistant,
                        chatModel = currentChatModel,
                        conversation = conversation,
                        bigScreen = bigScreen,
                        drawerState = drawerState,
                        previewMode = previewMode,
                        onNewChat = {
                            navigateToChatPage(navController)
                        },
                        onClickMenu = {
                            previewMode = !previewMode
                        },
                        onShowTaskTree = { showTaskTree = true },
                        onShowModelPicker = { chatModelListState.open() },
                    )
                }
            },
            bottomBar = {
                if (currentMessages.isNotEmpty()) {
                    if (setting.displaySetting.terminalStyle) {
                        TerminalChatInput(
                            state = inputState,
                            settings = setting,
                            chatModel = currentChatModel,
                            todos = conversation.todos,
                            contextTokens = contextTokens,
                            tokenSpeed = usageTokenSpeed,
                            loading = loadingJob != null,
                            queuedCount = messageQueue.messages.size,
                            onSubmit = sendInput,
                            onStop = vm::stopGeneration,
                            onFiles = { showFilesSheet = true },
                            onVoice = onStartVoiceMode,
                            onModel = { showTerminalModelPicker = true },
                            onUsage = { showUsageSheet = true },
                            onCommand = { runTerminalCommand(it) },
                            assistant = assistant,
                            onUpdateAssistant = vm::updateAssistant,
                            onInterject = sendInterject,
                        )
                    } else {
                        ChatInputContent(todos = conversation.todos)
                    }
                }
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            if (currentMessages.isEmpty()) {
                EmptyChatContent(
                    innerPadding = innerPadding,
                    assistant = assistant,
                    slogan = setting.displaySetting.emptyChatSlogan,
                    hazeState = backgroundHazeState,
                    input = {
                        if (setting.displaySetting.terminalStyle) {
                            TerminalChatInput(
                                modifier = Modifier.fillMaxWidth(),
                                state = inputState,
                                settings = setting,
                                chatModel = currentChatModel,
                                todos = conversation.todos,
                                contextTokens = contextTokens,
                                tokenSpeed = usageTokenSpeed,
                                loading = loadingJob != null,
                                queuedCount = messageQueue.messages.size,
                                onSubmit = sendInput,
                                onStop = vm::stopGeneration,
                                onFiles = { showFilesSheet = true },
                                onVoice = onStartVoiceMode,
                                onModel = { showTerminalModelPicker = true },
                                onUsage = { showUsageSheet = true },
                                onCommand = { runTerminalCommand(it) },
                                assistant = assistant,
                                onUpdateAssistant = vm::updateAssistant,
                                onInterject = sendInterject,
                            )
                        } else {
                            ChatInputContent(
                                todos = conversation.todos,
                                modifier = Modifier.fillMaxWidth(),
                                applyWindowInsets = false,
                            )
                        }
                    },
                )
            } else {
                ChatList(
                    innerPadding = innerPadding,
                    conversation = conversation,
                    state = chatListState,
                    loading = loadingJob != null,
                    processingStatus = processingStatus,
                    previewMode = previewMode,
                    settings = setting,
                    hazeState = hazeState,
                    bubbleHazeState = backgroundHazeState,
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
                    onForkMessage = {
                        scope.launch {
                            val fork = vm.forkMessage(message = it)
                            navigateToChatPage(navController, chatId = fork.id)
                        }
                    },
                    onDelete = {
                        if (loadingJob != null) {
                            vm.showDeleteBlockedWhileGeneratingError()
                        } else {
                            vm.deleteMessage(it)
                        }
                    },
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
        }

        if (showCompressAndSendDialog) {
            AlertDialog(
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

        if (showUsageSheet) {
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

        if (showTaskTree) {
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

        if (showFilesSheet) {
            ChatFilesPickerSheet(
                inputState = inputState,
                setting = setting,
                conversation = conversation,
                assistant = assistant,
                chatModel = currentChatModel,
                vm = vm,
                attachmentPickerActions = attachmentPickerActions,
                onStartVoiceMode = onStartVoiceMode,
                onDismiss = { showFilesSheet = false },
            )
        }

        terminalOutput?.let { output ->
            TerminalOutputDialog(
                title = "OUTPUT",
                content = output,
                onDismiss = { terminalOutput = null },
            )
        }

        if (showTerminalModelPicker) {
            TerminalModelPickerDialog(
                settings = setting,
                current = currentChatModel,
                onSelect = { model ->
                    vm.setChatModel(model)
                    showTerminalModelPicker = false
                    terminalOutput = "model -> ${model.displayName.ifBlank { model.modelId }}"
                },
                onDismiss = { showTerminalModelPicker = false },
            )
        }

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
    assistant: Assistant,
    chatModel: Model?,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showCompressDialog by remember { mutableStateOf(false) }

    fun dismissAll() {
        showCompressDialog = false
        onDismiss()
    }

    val filesSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        sheetState = filesSheetState,
        onDismissRequest = { dismissAll() },
    ) {
        FilesPicker(
            conversation = conversation,
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

@Composable
private fun TopBar(
    hazeState: HazeState,
    settings: Settings,
    assistant: Assistant,
    chatModel: Model?,
    conversation: Conversation,
    drawerState: DrawerState,
    bigScreen: Boolean,
    previewMode: Boolean,
    onClickMenu: () -> Unit,
    onNewChat: () -> Unit,
    onShowTaskTree: () -> Unit,
    onShowModelPicker: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val effect = settings.displaySetting.effectiveBackgroundEffect
    val terminal = settings.displaySetting.terminalStyle
    // 顶栏按钮是「玻璃底」，透明度读玻璃透明度，而不是气泡用的模糊不透明度
    val glassAlpha = settings.displaySetting.glassSurfaceAlpha
    fun effectColor(base: Color): Color = if (terminal) {
        base
    } else {
        when (effect) {
            BackgroundEffectType.OFF -> base
            BackgroundEffectType.BLUR -> base.copy(alpha = glassAlpha)
            BackgroundEffectType.GLASS -> Color.Transparent
        }
    }
    val effectBorder = if (!terminal && effect == BackgroundEffectType.BLUR) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    } else {
        null
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = {},
        navigationIcon = {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Row(
                    // 全宽模式下顶栏两侧不再留白，仍然保留状态栏与刘海的 inset
                    modifier = Modifier.padding(start = adaptiveHorizontalPaddingValue(12.dp)),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!bigScreen) {
                        val navShape = when {
                            terminal -> RoundedCornerShape(2.dp)
                            LocalFullWidthComponents.current -> RoundedCornerShape(0.dp)
                            else -> CircleShape
                        }
                        val navColor = MaterialTheme.colorScheme.surfaceContainerLow
                        Surface(
                            onClick = {
                                scope.launch { drawerState.open() }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .appButtonShadow(navShape, 2.dp)
                                .clip(navShape)
                                .background(effectColor(navColor))
                                .then(
                                    if (effectBorder != null) {
                                        Modifier.border(effectBorder, navShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .chatInputHaze(
                                    hazeState = hazeState,
                                    displaySetting = settings.displaySetting,
                                    containerColor = navColor,
                                    alpha = glassAlpha,
                                    shape = navShape,
                                ),
                            shape = navShape,
                            color = Color.Transparent,
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(HugeIcons.Menu03, "Messages", modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    if (chatModel != null) {
                        // 模型 + 供应商两个图标并成一个胶囊，高度和右侧按钮组一致
                        val modelPillShape = if (terminal) {
                            RoundedCornerShape(2.dp)
                        } else if (LocalFullWidthComponents.current) {
                            RoundedCornerShape(0.dp)
                        } else {
                            RoundedCornerShape(50)
                        }
                        val modelPillColor = MaterialTheme.colorScheme.surfaceContainerLow
                        val provider = chatModel.findProvider(settings.providers)
                        Row(
                            modifier = Modifier
                                .height(40.dp)
                                .appButtonShadow(modelPillShape, 2.dp)
                                .clip(modelPillShape)
                                .background(effectColor(modelPillColor))
                                .then(
                                    if (effectBorder != null) {
                                        Modifier.border(effectBorder, modelPillShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .chatInputHaze(
                                    hazeState = hazeState,
                                    displaySetting = settings.displaySetting,
                                    containerColor = modelPillColor,
                                    alpha = glassAlpha,
                                    shape = modelPillShape,
                                )
                                .clickable(onClick = onShowModelPicker)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AutoAIIcon(name = chatModel.modelId, color = Color.Transparent)
                            if (provider != null) {
                                AutoAIIcon(name = provider.name, color = Color.Transparent)
                            }
                        }
                    }
                }
            }
        },
        actions = {
            val pillShape = if (terminal) {
                RoundedCornerShape(2.dp)
            } else if (LocalFullWidthComponents.current) {
                RoundedCornerShape(0.dp)
            } else {
                RoundedCornerShape(50)
            }
            val pillColor = MaterialTheme.colorScheme.surfaceContainerLow
            Row(
                modifier = Modifier.padding(end = adaptiveHorizontalPaddingValue(8.dp)),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .appButtonShadow(pillShape, 2.dp)
                        .clip(pillShape)
                        .background(effectColor(pillColor))
                        .then(
                            if (effectBorder != null) {
                                Modifier.border(effectBorder, pillShape)
                            } else {
                                Modifier
                            }
                        )
                        .chatInputHaze(
                            hazeState = hazeState,
                            displaySetting = settings.displaySetting,
                            containerColor = pillColor,
                            alpha = glassAlpha,
                            shape = pillShape,
                        )
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onShowTaskTree, modifier = Modifier.size(40.dp)) {
                        Icon(
                            HugeIcons.Flowchart01,
                            stringResource(R.string.chat_task_tree_button),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    IconButton(onClick = { onClickMenu() }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (previewMode) HugeIcons.Cancel01 else HugeIcons.LeftToRightListBullet,
                            "Chat Options",
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    IconButton(onClick = { onNewChat() }, modifier = Modifier.size(40.dp)) {
                        Icon(HugeIcons.PencilEdit01, "New Message", modifier = Modifier.size(20.dp))
                    }

                }
            }
        },
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
private fun TerminalEmptyChatHero(assistant: Assistant) {
    val rule = "─".repeat(160)
    val logo = listOf(
        "  _     _____ _   _ _   _ ____  ",
        " | |   |___ /| | | | | | | __ ) ",
        " | |     |_ \\| |_| | |_| |  _ \\ ",
        " | |___ ___) |  _  |  _  | |_) |",
        " |_____|____/|_| |_|_| |_|____/ ",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "┌─[ L3HUB // TERMINAL ]$rule",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TerminalPalette.Green,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        logo.forEach { line ->
            Text(
                text = "│ $line",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                color = TerminalPalette.Green,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(
            text = "├$rule",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TerminalPalette.Border,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = "│ [ OK ] session ready · ${assistant.name.ifBlank { "assistant" }}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TerminalPalette.GreenDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "│ > waiting for input_",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TerminalPalette.Green,
            maxLines = 1,
        )
        Text(
            text = "│   /help   列出全部命令    /model  切换模型",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TerminalPalette.GreenDim,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = "│   /usage  查看上下文    /tree   任务树",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TerminalPalette.GreenDim,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = "└$rule",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TerminalPalette.Border,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun EmptyChatHero(
    assistant: Assistant,
    slogan: String,
    hazeState: HazeState,
) {
    val settings = LocalSettings.current
    if (settings.displaySetting.terminalStyle) {
        TerminalEmptyChatHero(assistant = assistant)
        return
    }
    val effect = settings.displaySetting.effectiveBackgroundEffect
    val shape = RoundedCornerShape(28.dp)
    val baseColor = MaterialTheme.colorScheme.surfaceContainerLow
    val containerColor = when (effect) {
        BackgroundEffectType.OFF -> Color.Transparent
        BackgroundEffectType.BLUR -> baseColor.copy(alpha = settings.displaySetting.blurOpacity)
        BackgroundEffectType.GLASS -> Color.Transparent
    }
    Column(
        modifier = Modifier
            .padding(horizontal = 40.dp)
            .clip(shape)
            .background(containerColor)
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
