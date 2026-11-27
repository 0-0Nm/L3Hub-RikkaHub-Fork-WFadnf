package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.ui.UIMessagePart
import me.rerere.asr.ASRStatus
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.Fullscreen
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.ContextMeterStyle
import me.rerere.rikkahub.data.datastore.InputLayoutStyle
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getQuickMessagesOfAssistant
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Guidance
import me.rerere.rikkahub.data.model.LiveGuidance
import me.rerere.rikkahub.data.model.LiveGuidanceActionTarget
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.TodoStatus
import me.rerere.rikkahub.service.InboxState
import me.rerere.rikkahub.service.QueuedMessage
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionContext
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionItem
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionList
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionProvider
import me.rerere.rikkahub.ui.components.message.ConversationTodoCard
import me.rerere.rikkahub.ui.components.message.LocalConversationStatusInSidePanel
import me.rerere.rikkahub.ui.components.message.TodoReminderUi
import me.rerere.rikkahub.ui.components.ui.KeepScreenOn
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.pages.chat.VoicePhase
import me.rerere.rikkahub.ui.pages.chat.VoiceSessionState
import me.rerere.rikkahub.ui.terminal.IconButton
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents
import me.rerere.rikkahub.ui.theme.LocalUiSlots
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPadding
import me.rerere.rikkahub.ui.theme.adaptiveShape
import me.rerere.rikkahub.ui.theme.appButtonShadow
import me.rerere.rikkahub.utils.SoundEffectPlayer
import me.rerere.ui.components.AnimatedBasicAlertDialog
import me.rerere.ui.toast.ToastType
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

/** 上下文预警进度条左右滑动的最小位移(px)，小于这个值当作点击 */

private const val CONTEXT_METER_SWIPE_THRESHOLD = 60f

@Composable
fun ChatInput(
    state: ChatInputState,
    loading: Boolean,
    settings: Settings,
    // 会话视角下的助手和模型：会话开始后以会话上固定的配置为准
    assistant: Assistant,
    chatModel: Model?,
    // 会话任务清单, 由 todo_write 工具维护, 显示在输入区最上方
    todos: List<TodoItem> = emptyList(),
    // 用户手动改清单（点完成、加一条、取消一条）; null 表示这张卡片只读
    onTodosChange: ((List<TodoItem>) -> Unit)? = null,
    // 当前会话已占用的上下文 token(最近一次回复的真实用量)
    contextTokens: Int? = null,
    usageTokenSpeed: Float? = null,
    onUpdateContextMeterStyle: (ContextMeterStyle) -> Unit = {},
    injectedLorebookCount: Int = 0,
    onShowUsage: () -> Unit = {},
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    completionProviders: List<ChatCompletionProvider> = emptyList(),
    onUpdateChatModel: (Model) -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onMoreClick: () -> Unit,
    onCancelClick: () -> Unit,
    onSendClick: () -> Unit,
    onLongSendClick: () -> Unit,
    messageQueue: InboxState = InboxState(),
    onRemoveQueuedMessage: (Uuid) -> Unit = {},
    onBeginEditQueuedMessage: (Uuid) -> QueuedMessage? = { null },
    onFinishEditQueuedMessage: (Uuid, List<UIMessagePart>?) -> Unit = { _, _ -> },
    onResumeMessageQueue: () -> Unit = {},
    // 引导提示词预设：点选后加进本轮；长按快捷消息按钮把输入框里的内容直接当引导发出去
    guideMessages: List<QuickMessage> = emptyList(),
    onAddGuidance: (QuickMessage) -> Unit = {},
    onSendAsGuidance: () -> Unit = {},
    onGuidanceToggleMode: (Guidance) -> Unit = {},
    onGuidanceRemove: (Guidance) -> Unit = {},
    conversationId: Uuid? = null,
    liveGuidanceEntries: List<LiveGuidance> = emptyList(),
    onLiveGuidanceModeChange: (Uuid, Boolean) -> Unit = { _, _ -> },
    onLiveGuidanceNextMessage: (LiveGuidanceActionTarget) -> Unit = {},
    onLiveGuidanceCancel: (LiveGuidanceActionTarget) -> Unit = {},
    liveGuidanceSwipeIds: Set<Uuid> = emptySet(),
    liveGuidanceActionIds: Set<Uuid> = emptySet(),
    runningBackgroundTasks: Int = 0,
    onShowBackgroundTasks: () -> Unit = {},
    onStartVoiceMode: (() -> Unit)? = null,
    voiceState: VoiceSessionState = VoiceSessionState(),
    onStopVoiceMode: () -> Unit = {},
    // 新对话界面把输入框整体上移，放到屏幕中心偏下
    liftedBy: androidx.compose.ui.unit.Dp = 0.dp,
    // 嵌在页面内容里时由外层负责键盘和导航栏 inset，避免重复留白
    applyWindowInsets: Boolean = true,
    // 输入区上方的底部渐变遮罩；横屏三栏里消息本来就有边界，不再压这一层
    showScrim: Boolean = true,
) {
    val toaster = LocalToaster.current
    val containerColor = LocalUiSlots.current.inputBar

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 全宽模式下输入框也和屏幕两边齐平，圆角收成直角
    val containerShape = adaptiveShape(settings.displaySetting.uiCornerRadius.dp)
    // 内层圆角按固定的 8dp 内缩算，左右内边距怎么调都不会影响上下观感
    val innerShape = adaptiveShape((settings.displaySetting.uiCornerRadius - 8).coerceAtLeast(2).dp)
    var isEditingReasoning by remember { mutableStateOf(false) }
    // 退出编辑态时收起思考编辑器，否则下次编辑另一条带思考的消息会自己弹出来
    LaunchedEffect(state.isEditing()) {
        if (!state.isEditing()) isEditingReasoning = false
    }
    val modelListState = rememberModelListState(
        modelId = chatModel?.id,
        providers = settings.providers,
        type = ModelType.CHAT,
    )

    fun sendMessage() {
        // Busy input may be rejected or retain attachments. Keep it focused and editable.
        if (!loading || state.isEditing() || state.isEmpty()) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
        if (loading && state.isEmpty()) onCancelClick() else onSendClick()
    }

    fun sendMessageWithoutAnswer() {
        if (!loading || state.isEditing() || state.isEmpty()) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
        if (loading && state.isEmpty()) onCancelClick() else onLongSendClick()
    }

    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (!imeVisible) focusManager.clearFocus()
    }

    // 关闭消息队列后，插话用的临时项只会停留几毫秒，别让它闪一下「待发送」。
    // 之前就排着的消息和系统通知照常显示，否则它们会变成看不见也撤不掉的残留
    val visibleQueue = remember(messageQueue, settings.enableMessageQueue) {
        if (settings.enableMessageQueue) {
            messageQueue
        } else {
            // 只有「正在过渡到派发」的这一条时不显示队列，免得闪一下「待发送」；
            // 一旦还压着别的消息（旧队列残留、通知）就照常显示，不能让任何一条变得看不见。
            // 暂停时也照常显示：卡住的消息要能继续或撤销。
            val transientOnly = !messageQueue.paused &&
                messageQueue.items.all { (it as? QueuedMessage)?.sentWithoutQueue == true }
            if (transientOnly) messageQueue.copy(items = emptyList()) else messageQueue
        }
    }

    val asr = LocalASRState.current
    val asrState by asr.state.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val soundEffectPlayer: SoundEffectPlayer = koinInject()
    LaunchedEffect(Unit) {
        soundEffectPlayer.preload(R.raw.asr_start, R.raw.asr_stop)
    }
    val asrPermission = rememberPermissionState(PermissionRecordAudio)
    PermissionManager(permissionState = asrPermission)
    var asrBaseText by remember { mutableStateOf("") }
    LaunchedEffect(asrState.status) {
        when (asrState.status) {
            ASRStatus.Listening -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                soundEffectPlayer.play(R.raw.asr_start)
            }

            ASRStatus.Stopping -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                soundEffectPlayer.play(R.raw.asr_stop)
            }

            else -> {}
        }
    }
    LaunchedEffect(asrState.errorMessage) {
        asrState.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            toaster.show(message = message, type = ToastType.Error)
        }
    }

    // 底部渐变遮罩：从输入区顶部一直过渡到屏幕底部（键盘弹出时到键盘上沿），避免消息透到系统导航栏
    val scrimColor = MaterialTheme.colorScheme.background
    val imeInsets = WindowInsets.ime
    // 嵌在空会话页面中间时不需要底部渐变遮罩
    val scrimModifier = if (applyWindowInsets && showScrim) {
        Modifier.drawWithCache {
            val brush = Brush.verticalGradient(
                colors = listOf(scrimColor.copy(alpha = 0f), scrimColor),
                startY = 0f,
                endY = (size.height - imeInsets.getBottom(this)).coerceAtLeast(1f),
            )
            onDrawBehind { drawRect(brush) }
        }
    } else {
        Modifier
    }
    Surface(
        color = Color.Transparent,
        modifier = scrimModifier,
    ) {
        Column(
            modifier = modifier
                .then(if (applyWindowInsets) Modifier.imePadding().navigationBarsPadding() else Modifier)
                // 全宽模式下输入框贴到屏幕左右边，键盘 inset 与导航栏 inset 仍然保留
                .adaptiveHorizontalPadding(8.dp)
                .padding(bottom = 8.dp + liftedBy)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 清单只在「还没做完」的时候占位置。全部完成后收起来；
            // 想再看回来（比如手动点错了、要取消完成）就关掉设置里的「完成后收起任务清单」。
            val hideCompletedTodos = LocalSettings.current.displaySetting.hideCompletedTodoCard &&
                todos.isNotEmpty() && todos.all { it.status == TodoStatus.COMPLETED }
            val showTodoSection = todos.isNotEmpty() && !hideCompletedTodos
            // 清单和引导共用一张卡片、一次折叠：两样都在时只有一颗展开箭头
            if (!LocalConversationStatusInSidePanel.current && (showTodoSection || visibleQueue.guidances.isNotEmpty())) {
                ConversationTodoCard(
                    todos = if (showTodoSection) todos else emptyList(),
                    onTodosChange = onTodosChange,
                    reminder = if (showTodoSection) {
                        TodoReminderUi(
                            enabled = assistant.todoReminderEnabled,
                            interval = assistant.todoReminderInterval,
                        )
                    } else {
                        null
                    },
                    onReminderChange = { next ->
                        onUpdateAssistant(
                            assistant.copy(
                                todoReminderEnabled = next.enabled,
                                todoReminderInterval = next.interval,
                            )
                        )
                    },
                    guidances = visibleQueue.guidances,
                    onGuidanceToggleMode = onGuidanceToggleMode,
                    onGuidanceRemove = onGuidanceRemove,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveHorizontalPadding(12.dp)
                        .padding(vertical = 4.dp),
                )
            }
            MessageQueuePanel(
                state = visibleQueue,
                onRemove = onRemoveQueuedMessage,
                onBeginEdit = onBeginEditQueuedMessage,
                onFinishEdit = onFinishEditQueuedMessage,
                onResume = onResumeMessageQueue,
            )
            if (!LocalConversationStatusInSidePanel.current) {
                LiveGuidanceRows(
                    entries = liveGuidanceEntries,
                    conversationId = conversationId,
                    onModeChange = onLiveGuidanceModeChange,
                    onNextMessage = onLiveGuidanceNextMessage,
                    onCancel = onLiveGuidanceCancel,
                    actionIds = liveGuidanceActionIds,
                    swipeIds = liveGuidanceSwipeIds,
                    modifier = Modifier.adaptiveHorizontalPadding(12.dp),
                )
            }
            if (injectedLorebookCount > 0) {
                Text(
                    text = stringResource(R.string.chat_page_worldbook_injected, injectedLorebookCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            if (chatModel != null && settings.displaySetting.contextMeterStyle != ContextMeterStyle.OFF) {
                Box(
                    modifier = Modifier.pointerInput(settings.displaySetting.contextMeterStyle) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                            onDragEnd = {
                                when {
                                    totalDrag <= -CONTEXT_METER_SWIPE_THRESHOLD ->
                                        onUpdateContextMeterStyle(ContextMeterStyle.BAR)

                                    totalDrag >= CONTEXT_METER_SWIPE_THRESHOLD ->
                                        onUpdateContextMeterStyle(ContextMeterStyle.RING)
                                }
                            },
                        )
                    }
                ) {
                    when (settings.displaySetting.contextMeterStyle) {
                        ContextMeterStyle.BAR -> ContextUsageMeter(
                            tokens = contextTokens,
                            warnTokens = chatModel.warnContextTokens,
                            maxTokens = chatModel.maxContextTokens,
                            onShowUsage = onShowUsage,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )

                        ContextMeterStyle.RING -> ContextUsageRing(
                            tokens = contextTokens,
                            warnTokens = chatModel.warnContextTokens,
                            maxTokens = chatModel.maxContextTokens,
                            onShowUsage = onShowUsage,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            provider = chatModel.findProvider(settings.providers),
                            tokenSpeed = usageTokenSpeed,
                        )

                        ContextMeterStyle.OFF -> Unit
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(containerShape)
                    .chatInputHaze(
                        hazeState = hazeState,
                        displaySetting = settings.displaySetting,
                        containerColor = containerColor,
                        shape = containerShape,
                    ),
                shape = containerShape,
                tonalElevation = 0.dp,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                ),
                // 着色由 chatInputHaze 统一叠在模糊之上，这里保持透明，避免叠成两倍浓度
                color = if (settings.displaySetting.effectiveBackgroundEffect == BackgroundEffectType.OFF) {
                    containerColor
                } else {
                    Color.Transparent
                },
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = settings.displaySetting.inputPadding.dp,
                        vertical = 4.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (assistant.planMode) {
                        Text(
                            text = stringResource(R.string.chat_input_plan_mode_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUpdateAssistant(assistant.copy(planMode = false)) }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                    if (voiceState.phase != VoicePhase.Off) {
                        VoiceModeRow(
                            state = voiceState,
                            onStop = onStopVoiceMode,
                            onRetry = { onStartVoiceMode?.invoke() },
                        )
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    if (state.messageContent.isNotEmpty()) {
                        MediaFileInputRow(state = state)
                    }
                    // 必须在 TextInputRow 外面：单行布局的按钮以 TextInputRow 整体高度居中，
                    // 提示条排在 TextField 之前会把按钮顶到输入框上部
                    if (state.isEditing()) {
                        EditingBanner(
                            state = state,
                            shape = innerShape,
                            onEditReasoning = { isEditingReasoning = true },
                        )
                    }

                    val singleLine = settings.displaySetting.inputLayoutStyle == InputLayoutStyle.SINGLE
                    val quickMessages = remember(settings.quickMessages, assistant.quickMessageIds) {
                        settings.getQuickMessagesOfAssistant(assistant)
                    }
                    val asrButton: @Composable () -> Unit = {
                        if (!voiceState.isActive && (asrState.isAvailable || asrState.isRecording)) {
                            AsrButton(
                                state = asrState,
                                onClick = {
                                    when (asrState.status) {
                                        ASRStatus.Listening -> asr.stop()
                                        ASRStatus.Idle, ASRStatus.Error -> {
                                            if (!asrPermission.allRequiredPermissionsGranted) {
                                                asrPermission.requestPermissions()
                                            } else {
                                                asrBaseText = state.textContent.text.toString()
                                                asr.start { transcript ->
                                                    val spacer =
                                                        if (asrBaseText.isBlank() || transcript.isBlank()) "" else " "
                                                    state.setMessageText(asrBaseText + spacer + transcript)
                                                }
                                            }
                                        }

                                        ASRStatus.Connecting, ASRStatus.Stopping -> {}
                                    }
                                }
                            )
                        }
                    }
                    // The main send button owns both busy gestures. Stop remains independently
                    // available while a nonempty draft is being composed (empty input uses send/stop).
                    val stopButton: @Composable () -> Unit = {
                        if (loading && !state.isEmpty()) {
                            ActionIconButton(onClick = onCancelClick) {
                                Icon(
                                    imageVector = HugeIcons.Cancel01,
                                    contentDescription = stringResource(R.string.stop),
                                )
                            }
                        }
                    }
                    val sendButton: @Composable () -> Unit = {
                        if (loading) {
                            KeepScreenOn()
                        }
                        AnimatedVisibility(
                            visible = !asrState.isRecording,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            SendButton(
                                loading = loading,
                                empty = state.isEmpty(),
                                busyInput = loading && !state.isEmpty() && !state.isEditing(),
                                onClick = { sendMessage() },
                                onLongClick = { sendMessageWithoutAnswer() },
                            )
                        }
                    }
                    val reasoningButton: @Composable () -> Unit = {
                        if (chatModel?.abilities?.contains(ModelAbility.REASONING) == true) {
                            ReasoningButton(
                                reasoningLevel = assistant.reasoningLevel,
                                onUpdateReasoningLevel = {
                                    onUpdateAssistant(assistant.copy(reasoningLevel = it))
                                },
                                onlyIcon = true,
                                modelName = if (singleLine) chatModel.displayName else null,
                                onOpenModelList = if (singleLine) {
                                    { modelListState.open() }
                                } else {
                                    null
                                },
                                // 单行模式下点模型名不关面板，直接把模型列表滑进来
                                modelContent = if (singleLine) {
                                    { dismiss ->
                                        ModelListContent(
                                            state = modelListState,
                                            onSelect = { model ->
                                                onUpdateChatModel(model)
                                                dismiss()
                                            },
                                            onDismiss = dismiss,
                                        )
                                    }
                                } else {
                                    null
                                },
                                useL3HubStyle = settings.displaySetting.useL3HubReasoningStyle,
                            )
                        }
                    }

                    if (singleLine) {
                        var fieldHeight by remember { mutableIntStateOf(0) }
                        var oneLineHeight by remember { mutableIntStateOf(0) }
                        var stacked by remember { mutableStateOf(state.textContent.text.isNotEmpty()) }
                        // 有内容时直接锁定「文字在上、按钮在下」的布局，避免测量误差来回切换
                        LaunchedEffect(state.textContent.text.isNotEmpty()) {
                            stacked = state.textContent.text.isNotEmpty()
                        }
                        val leadingActions: @Composable () -> Unit = {
                            ActionIconButton(onClick = onMoreClick) {
                                Icon(
                                    imageVector = HugeIcons.Add01,
                                    contentDescription = stringResource(R.string.more_options),
                                )
                            }
                            // 一直显示：长按它是把输入框内容当引导发出去的入口，
                            // 没有预设时也得能按，否则这个手势就藏起来了
                            QuickMessageButton(
                                quickMessages = quickMessages,
                                guideMessages = guideMessages,
                                state = state,
                                onAddGuidance = onAddGuidance,
                                onSendAsGuidance = onSendAsGuidance,
                            )
                        }
                        val trailingActions: @Composable () -> Unit = {
                            asrButton()
                            stopButton()
                            if (chatModel?.abilities?.contains(ModelAbility.REASONING) == true) {
                                reasoningButton()
                            } else {
                                ModelSelectorButton(
                                    state = modelListState,
                                    onlyIcon = true,
                                )
                            }
                            sendButton()
                        }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            TextInputRow(
                                state = state,
                                assistant = assistant,
                                completionProviders = completionProviders,
                                onSendMessage = { sendMessage() },
                                modifier = Modifier.fillMaxWidth(),
                                showQuickMessageButton = false,
                                fullscreenOnOverflow = true,
                                guideMessages = guideMessages,
                                onAddGuidance = onAddGuidance,
                                onSendAsGuidance = onSendAsGuidance,
                                contentPadding = PaddingValues(
                                    // 卡片左右内边距变大时，把输入框自己的内缩减回去，
                                    // 文字可用宽度保持不变，行数就不会因为拖滑块而变
                                    start = if (stacked) {
                                        4.dp
                                    } else {
                                        (70 - (settings.displaySetting.inputPadding - 8).coerceAtLeast(0)).dp
                                    },
                                    end = if (stacked) {
                                        4.dp
                                    } else {
                                        (130 - (settings.displaySetting.inputPadding - 8).coerceAtLeast(0)).dp
                                    },
                                    bottom = if (stacked) 44.dp else 0.dp,
                                ),
                                onMeasure = { height, oneLine ->
                                    fieldHeight = height
                                    oneLineHeight = oneLine
                                },
                            )
                            if (!stacked) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    leadingActions()
                                }
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    trailingActions()
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    leadingActions()
                                    Spacer(Modifier.weight(1f))
                                    trailingActions()
                                }
                            }
                        }
                    } else {
                        TextInputRow(
                            state = state,
                            assistant = assistant,
                            completionProviders = completionProviders,
                            onSendMessage = { sendMessage() },
                            guideMessages = guideMessages,
                            onAddGuidance = onAddGuidance,
                            onSendAsGuidance = onSendAsGuidance,
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                ModelSelectorButton(
                                    state = modelListState,
                                    onlyIcon = true,
                                    modifier = Modifier,
                                )
                                reasoningButton()
                            }

                            ActionIconButton(onClick = onMoreClick) {
                                Icon(
                                    imageVector = HugeIcons.Add01,
                                    contentDescription = stringResource(R.string.more_options),
                                )
                            }

                            asrButton()
                            stopButton()
                            sendButton()
                        }
                    }
                }
            }

        }
    }

    val editingReasoning = state.editingReasoning
    if (isEditingReasoning && editingReasoning != null) {
        FullScreenEditor(
            textState = editingReasoning,
            placeholder = stringResource(R.string.chat_input_reasoning_placeholder),
        ) {
            isEditingReasoning = false
        }
    }

    ModelListSheet(
        state = modelListState,
        onSelect = onUpdateChatModel,
    )
}

@Composable
internal fun SendButton(
    loading: Boolean,
    empty: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    busyInput: Boolean = false,
) {
    val longClickLabel = if (busyInput) stringResource(R.string.chat_page_busy_title) else null
    val showStop = loading && empty
    val shape = when {
        LocalFullWidthComponents.current -> RoundedCornerShape(0.dp)
        else -> CircleShape
    }
    val containerColor = when {
        showStop -> MaterialTheme.colorScheme.errorContainer
        empty -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when {
        showStop -> MaterialTheme.colorScheme.onErrorContainer
        empty -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(if (busyInput) 48.dp else 30.dp)
            .testTag("chat_send_button")
            .clip(shape)
            .combinedClickable(
                enabled = showStop || !empty,
                onClick = onClick,
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
            )
    ) {
        Surface(
            modifier = Modifier
                .size(30.dp)
                .appButtonShadow(shape, 2.dp),
            shape = shape,
            color = containerColor,
            content = {},
        )
        Icon(
            imageVector = if (showStop) HugeIcons.Cancel01 else HugeIcons.ArrowUp02,
            contentDescription = stringResource(when {
                showStop -> R.string.stop
                busyInput -> R.string.chat_page_busy_temporary
                else -> R.string.send
            }),
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun BackgroundTasksButton(
    running: Int,
    onClick: () -> Unit,
) {
    BadgedBox(
        modifier = Modifier.padding(horizontal = 5.dp),
        badge = {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Text(running.toString()) }
        },
    ) {
        ActionIconButton(onClick = onClick) {
            Icon(
                imageVector = HugeIcons.ComputerTerminal01,
                contentDescription = stringResource(R.string.background_tasks_running, running),
            )
        }
    }
}

@Composable
private fun ActionIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = when {
        LocalFullWidthComponents.current -> RoundedCornerShape(0.dp)
        else -> CircleShape
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(30.dp)
            .appButtonShadow(shape, 2.dp),
        shape = shape,
        tonalElevation = 0.dp,
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/**
 * 编辑旧消息时的提示条。
 *
 * 单独放在 [TextInputRow] 外面：单行布局的两组按钮是相对整个 [TextInputRow] 居中的，
 * 提示条一旦排在 TextField 之前就会被算进高度，输入内容被删空、按钮切回居中时就会偏到输入框上方。
 */
@Composable
private fun EditingBanner(
    state: ChatInputState,
    shape: Shape,
    onEditReasoning: () -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.editing))
            Spacer(Modifier.weight(1f))
            if (state.editingReasoning != null) {
                TextButton(onClick = onEditReasoning) {
                    Text(stringResource(R.string.chat_input_edit_reasoning))
                }
            }
            Icon(
                imageVector = HugeIcons.Cancel01,
                contentDescription = stringResource(R.string.cancel_edit),
                modifier = Modifier.clickable { state.clearInput() }
            )
        }
    }
}

@Composable
private fun TextInputRow(
    state: ChatInputState,
    assistant: Assistant,
    completionProviders: List<ChatCompletionProvider>,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier,
    showQuickMessageButton: Boolean = true,
    fullscreenOnOverflow: Boolean = false,
    guideMessages: List<QuickMessage> = emptyList(),
    onAddGuidance: (QuickMessage) -> Unit = {},
    onSendAsGuidance: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onMeasure: ((fieldHeight: Int, oneLineHeight: Int) -> Unit)? = null,
) {
    val settings = LocalSettings.current
    val fullWidth = LocalFullWidthComponents.current
    val filesManager: FilesManager = koinInject()
    val quickMessages = remember(settings.quickMessages, assistant.quickMessageIds) {
        settings.getQuickMessagesOfAssistant(assistant)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        var isFocused by remember { mutableStateOf(false) }
        var isFullScreen by remember { mutableStateOf(false) }
        var fieldHeight by remember { mutableIntStateOf(0) }
        var oneLineHeight by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val bodyLineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        val lineHeightPx = with(density) {
            try {
                bodyLineHeight.toPx()
            } catch (_: Exception) {
                24.dp.toPx()
            }
        }
        val inputOverflowed = oneLineHeight > 0 && fieldHeight >= oneLineHeight + lineHeightPx * 3.5f
        var completionList by remember { mutableStateOf<ChatCompletionList?>(null) }
        val receiveContentListener = remember(
            settings.displaySetting.pasteLongTextAsFile, settings.displaySetting.pasteLongTextThreshold
        ) {
            ReceiveContentListener { transferableContent ->
                when {
                    transferableContent.hasMediaType(MediaType.Image) -> {
                        transferableContent.consume { item ->
                            val uri = item.uri
                            if (uri != null) {
                                state.addImages(
                                    filesManager.createChatFilesByContents(
                                        listOf(uri)
                                    )
                                )
                            }
                            uri != null
                        }
                    }

                    settings.displaySetting.pasteLongTextAsFile && transferableContent.hasMediaType(MediaType.Text) -> {
                        transferableContent.consume { item ->
                            val text = item.text?.toString()
                            if (text != null && text.length > settings.displaySetting.pasteLongTextThreshold) {
                                val document = filesManager.createChatTextFile(text)
                                state.addFiles(listOf(document))
                                true
                            } else {
                                false
                            }
                        }
                    }

                    else -> transferableContent
                }
            }
        }

        LaunchedEffect(completionProviders, isFocused) {
            if (!isFocused || completionProviders.isEmpty()) {
                completionList = null
                return@LaunchedEffect
            }

            snapshotFlow {
                ChatCompletionContext(
                    text = state.textContent.text.toString(),
                    selection = state.textContent.selection,
                )
            }.collectLatest { context ->
                val lists = completionProviders.mapNotNull { provider ->
                    try {
                        provider.complete(context)
                            ?.takeIf { it.items.isNotEmpty() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
                val primary = lists.firstOrNull()
                completionList = primary?.let { list ->
                    val mergedItems = lists
                        .filter { it.replacementRange == list.replacementRange }
                        .flatMap { it.items }
                        .distinctBy { it.label to it.insertText }
                        .sortedWith(
                            compareByDescending<ChatCompletionItem> { it.sortScore }
                                .thenBy { it.label.length }
                                .thenBy { it.label.lowercase() }
                        )
                        .take(8)
                    list.copy(items = mergedItems)
                }
            }
        }

        completionList?.takeIf { it.items.isNotEmpty() }?.let { list ->
            CompletionPopup(
                completionList = list,
                onItemClick = { item ->
                    state.applyCompletion(list.replacementRange, item)
                    completionList = null
                },
            )
        }

        val maxFieldHeight = with(density) { (lineHeightPx * 5f).toDp() } + 24.dp
        TextField(
            state = state.textContent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
                .heightIn(max = maxFieldHeight)
                .testTag("chat_input")
                .onSizeChanged { size ->
                    fieldHeight = size.height
                    if (state.textContent.text.isEmpty()) {
                        oneLineHeight = size.height
                    }
                    onMeasure?.invoke(fieldHeight, oneLineHeight)
                }
                .contentReceiver(receiveContentListener)
                .onFocusChanged {
                    isFocused = it.isFocused
                },
            shape = if (fullWidth) {
                RoundedCornerShape(0.dp)
            } else {
                MaterialTheme.shapes.largeIncreased
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.chat_input_placeholder),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (settings.displaySetting.sendOnEnter) ImeAction.Send else ImeAction.Default
            ),
            onKeyboardAction = {
                if (settings.displaySetting.sendOnEnter && !state.isEmpty()) {
                    onSendMessage()
                }
            },
            colors = TextFieldDefaults.colors().copy(
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 只要有内容就给全屏按钮，不必等超出三行
                    if (state.textContent.text.isNotEmpty() ||
                        (if (fullscreenOnOverflow) inputOverflowed else isFocused)
                    ) {
                        IconButton(
                            onClick = {
                                isFullScreen = !isFullScreen
                            }) {
                            Icon(HugeIcons.Fullscreen, null)
                        }
                    }
                }
            },
            leadingIcon = if (showQuickMessageButton) {
                {
                    QuickMessageButton(
                        quickMessages = quickMessages,
                        guideMessages = guideMessages,
                        state = state,
                        onAddGuidance = onAddGuidance,
                        onSendAsGuidance = onSendAsGuidance,
                    )
                }
            } else null,
        )
        if (isFullScreen) {
            FullScreenEditor(
                textState = state.textContent,
                placeholder = stringResource(R.string.chat_input_placeholder),
            ) {
                isFullScreen = false
            }
        }
    }
}

@Composable
private fun CompletionPopup(
    completionList: ChatCompletionList,
    onItemClick: (ChatCompletionItem) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        shape = adaptiveShape(18.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            items(
                items = completionList.items,
                key = { item -> "${item.label}:${item.insertText}" },
            ) { item ->
                Surface(
                    onClick = { onItemClick(item) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item.icon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.detail?.let { detail ->
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ChatInputState.applyCompletion(
    replacementRange: TextRange,
    item: ChatCompletionItem,
) {
    val textLength = textContent.text.length
    val start = replacementRange.min.coerceIn(0, textLength)
    val end = replacementRange.max.coerceIn(start, textLength)
    textContent.edit {
        replace(start, end, item.insertText)
        selection = TextRange(start + item.insertText.length)
    }
}

@Composable
private fun QuickMessageButton(
    quickMessages: List<QuickMessage>,
    guideMessages: List<QuickMessage>,
    state: ChatInputState,
    onAddGuidance: (QuickMessage) -> Unit,
    onSendAsGuidance: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    Box {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .testTag("chat_quick_message_button")
                .combinedClickable(
                    onClick = { expanded = !expanded },
                    // 长按：输入框里的内容不进这一轮的消息，直接作为引导插进正在跑的工具间隙。
                    // 输入框是空的时候退回成展开选单，别让长按变成什么都没发生
                    onLongClick = {
                        // 判的是文字而不是 isEmpty：只有附件时引导没有内容可取，退回成展开选单
                        if (state.textContent.text.isBlank()) {
                            expanded = !expanded
                        } else {
                            expanded = false
                            onSendAsGuidance()
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(HugeIcons.Zap, null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 200.dp, max = 360.dp)
        ) {
            if (quickMessages.isEmpty() && guideMessages.isEmpty()) {
                Text(
                    text = stringResource(R.string.quick_messages_page_empty_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            quickMessages.forEach { quickMessage ->
                // 短按追加到输入框末尾，长按替换输入框内容
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                state.appendText(quickMessage.content)
                                expanded = false
                            },
                            onLongClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                state.setMessageText(quickMessage.content)
                                expanded = false
                            },
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = quickMessage.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = quickMessage.content,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // 引导提示词单独一区：点选后不进输入框，直接加进本轮的工具间隙
            if (guideMessages.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.quick_messages_guide_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 4.dp),
                )
                guideMessages.forEach { guide ->
                    Surface(
                        onClick = {
                            onAddGuidance(guide)
                            expanded = false
                        },
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(
                                text = guide.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = guide.content,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenEditor(
    textState: TextFieldState,
    placeholder: String,
    onDone: () -> Unit
) {
    AnimatedBasicAlertDialog(
        onDismissRequest = {
            onDone()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxHeight(0.9f),
                shape = if (LocalFullWidthComponents.current) {
                    RoundedCornerShape(0.dp)
                } else {
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                }
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row {
                        TextButton(
                            onClick = {
                                onDone()
                            }) {
                            Text(stringResource(R.string.chat_page_save))
                        }
                    }
                    TextField(
                        state = textState,
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .fillMaxSize(),
                        shape = adaptiveShape(32.dp),
                        placeholder = {
                            Text(placeholder)
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                        colors = TextFieldDefaults.colors().copy(
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}
