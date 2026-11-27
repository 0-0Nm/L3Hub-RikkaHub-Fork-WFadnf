package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalScrollCaptureInProgress
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowDownDouble
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.ArrowUpDouble
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CursorPointer01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.archivedGuidanceByNode
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.withConversation
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.todoAnchorNode
import me.rerere.rikkahub.data.model.hasActiveTodo
import me.rerere.rikkahub.ui.components.message.ConversationTodoCard
import me.rerere.rikkahub.ui.components.message.LocalConversationStatusInSidePanel
import me.rerere.rikkahub.ui.components.message.TodoReminderUi
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.ui.components.ai.ArchivedLiveGuidanceCard
import me.rerere.rikkahub.ui.components.message.ChatMessage
import me.rerere.rikkahub.ui.components.ui.ErrorCardsDisplay
import me.rerere.rikkahub.ui.components.ui.glassOverlayColor
import me.rerere.rikkahub.ui.components.ui.shadow.FilledIconButton
import me.rerere.rikkahub.ui.hooks.ImeLazyListAutoScroller
import me.rerere.rikkahub.ui.terminal.IconButton
import me.rerere.rikkahub.ui.theme.rememberSpatialSpec
import me.rerere.rikkahub.ui.theme.ChatFontProvider
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPadding
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPaddingValue
import me.rerere.rikkahub.ui.theme.fullWidthHorizontalInset
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.components.ListSelectableItem
import me.rerere.ui.components.Tooltip
import kotlin.math.sqrt
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

private const val TAG = "ChatList"
private const val LoadingIndicatorKey = "LoadingIndicator"
private const val ScrollBottomKey = "ScrollBottomKey"

// 消息之间的纵向间距：原来由 LazyColumn 的 verticalArrangement 统一给 12dp，
// 现在改成 0，交给每个 item 按分组情况自己加，这样同组和跨组才能用不同的间距。
private val MESSAGE_GAP_JOINED = 2.dp
private val MESSAGE_GAP_ROLE_CHANGE = 16.dp

/**
 * 气泡分组用的角色：只有普通的气泡消息才参与分组。
 *
 * 压缩检查点、子代理通知在界面上是独立卡片，不是气泡，所以返回 null 打断分组；
 * SYSTEM / TOOL 这类不走气泡样式的节点同理，各自单独成组。空消息不占高度，也打断分组。
 */
private fun MessageNode.bubbleGroupRole(): MessageRole? {
    val message = messages.getOrNull(selectIndex) ?: return null
    if (message.isContextCheckpoint || message.isSubAgentNotice) return null
    if (message.parts.isEmptyUIMessage()) return null
    return when (message.role) {
        MessageRole.USER, MessageRole.ASSISTANT -> message.role
        else -> null
    }
}

@Composable
fun ChatList(
    innerPadding: PaddingValues,
    conversation: Conversation,
    state: LazyListState,
    previewListState: LazyListState,
    selectionState: ChatSelectionState,
    loading: Boolean,
    compressing: Boolean = false,
    processingStatus: String? = null,
    previewMode: Boolean,
    onPreviewModeChange: (Boolean) -> Unit,
    settings: Settings,
    bubbleHazeState: HazeState? = null,
    // 消息列表本身也注册成源：输入框、顶栏、抽屉和浮层才能糊到滚动中的正文。
    // 生成中不挂，避免每个 token 到达都重糊一次列表。
    contentSource: HazeState? = null,
    errors: List<ChatError> = emptyList(),
    onDismissError: (Uuid) -> Unit = {},
    onClearAllErrors: () -> Unit = {},
    onRegenerate: (UIMessage) -> Unit = {},
    onEdit: (UIMessage) -> Unit = {},
    // 子代理通知的编辑/删除；都不传时通知只读（预览、导出这类复用）
    onEditSubAgentNotice: ((UIMessage, String) -> Unit)? = null,
    onDeleteSubAgentNotice: ((UIMessage) -> Unit)? = null,
    onForkMessage: (UIMessage) -> Unit = {},
    onDelete: (UIMessage) -> Unit = {},
    onUpdateMessage: (MessageNode) -> Unit = {},
    onClickSuggestion: (String) -> Unit = {},
    onTranslate: ((UIMessage, java.util.Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
    onJumpToMessage: (Int) -> Unit = {},
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    onToggleFavorite: ((MessageNode) -> Unit)? = null,
    onConversationSystemPromptChange: ((String?) -> Unit)? = null,
    emptyHero: (@Composable () -> Unit)? = null,
    // 滚到最底部后继续上拉、松手时回调；不传就不启用这个手势
    onCreateNewChat: (() -> Unit)? = null,
    // 就地内嵌任务清单卡片用：手动编辑写回与提醒设置更新，都复用会话原有的单一入口
    onTodosChange: ((List<TodoItem>) -> Unit)? = null,
    onUpdateAssistant: ((Assistant) -> Unit)? = null,
) {
    // 概览页按手势进度平移：聊天页向左滑出，概览页从右侧跟进。
    // 顶栏按钮切换时进度动画到目标；手指拖动时由 previewSwipeGesture 直接驱动。
    val previewProgress = rememberPreviewSwipeProgress(previewMode)
    val scope = rememberCoroutineScope()
    val currentPreviewMode by rememberUpdatedState(previewMode)
    val currentOnPreviewModeChange by rememberUpdatedState(onPreviewModeChange)
    val currentConversation by rememberUpdatedState(conversation)
    val layoutDirection = LocalLayoutDirection.current
    val layoutDirectionSign = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
    // 概览页只在露出来的时候组合，平时不占资源
    val showPreview by remember { derivedStateOf { previewProgress.value > 0f } }
    val showNormal by remember { derivedStateOf { previewProgress.value < 1f } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .previewSwipeGesture(
                progress = previewProgress,
                scope = scope,
                layoutDirection = layoutDirection,
                canEnterPreview = { currentConversation.messageNodes.isNotEmpty() },
                isPreviewMode = { currentPreviewMode },
                onPreviewModeChange = { currentOnPreviewModeChange(it) },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = previewProgress.value
                    translationX = -p * size.width * layoutDirectionSign
                    alpha = if (showNormal) 1f - p * 0.4f else 0f
                },
        ) {
            ChatListNormal(
                innerPadding = innerPadding,
                conversation = conversation,
                state = state,
                loading = loading,
                compressing = compressing,
                processingStatus = processingStatus,
                settings = settings,
                bubbleHazeState = bubbleHazeState,
                contentSource = contentSource,
                errors = errors,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                onRegenerate = onRegenerate,
                onEdit = onEdit,
                onEditSubAgentNotice = onEditSubAgentNotice,
                onDeleteSubAgentNotice = onDeleteSubAgentNotice,
                onForkMessage = onForkMessage,
                onDelete = onDelete,
                onUpdateMessage = onUpdateMessage,
                onClickSuggestion = onClickSuggestion,
                onTranslate = onTranslate,
                onClearTranslation = onClearTranslation,
                onToolApproval = onToolApproval,
                onToolAnswer = onToolAnswer,
                onToggleFavorite = onToggleFavorite,
                onConversationSystemPromptChange = onConversationSystemPromptChange,
                emptyHero = emptyHero,
                onCreateNewChat = onCreateNewChat,
                onTodosChange = onTodosChange,
                onUpdateAssistant = onUpdateAssistant,
                selectionState = selectionState,
            )
        }

        if (showPreview) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = previewProgress.value
                        translationX = (1f - p) * size.width * layoutDirectionSign
                        alpha = 0.4f + p * 0.6f
                    },
            ) {
                ChatListPreview(
                    innerPadding = innerPadding,
                    conversation = conversation,
                    chatListState = state,
                    listState = previewListState,
                    settings = settings,
                    onJumpToMessage = onJumpToMessage,
                )
            }
        }
    }
}

// 顶部渐隐：消息滚到标题栏下面时逐渐透明，从状态栏一直淡出到标题栏下沿之外，保证标题可读。
// 让内容自己变透明而不是盖一层背景色，背景图和动态渐变背景才不会被盖住。
internal fun Modifier.topFadingEdge(height: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val fadeHeight = height.toPx()
        val brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.6f to Color.Black.copy(alpha = 0.15f),
            1f to Color.Black,
            startY = 0f,
            endY = fadeHeight,
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush, size = Size(size.width, fadeHeight), blendMode = BlendMode.DstIn)
        }
    }

@Composable
private fun ChatListNormal(
    innerPadding: PaddingValues,
    conversation: Conversation,
    state: LazyListState,
    loading: Boolean,
    compressing: Boolean,
    processingStatus: String? = null,
    settings: Settings,
    bubbleHazeState: HazeState? = null,
    contentSource: HazeState? = null,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
    onRegenerate: (UIMessage) -> Unit,
    onEdit: (UIMessage) -> Unit,
    onEditSubAgentNotice: ((UIMessage, String) -> Unit)?,
    onDeleteSubAgentNotice: ((UIMessage) -> Unit)?,
    onForkMessage: (UIMessage) -> Unit,
    onDelete: (UIMessage) -> Unit,
    onUpdateMessage: (MessageNode) -> Unit,
    onClickSuggestion: (String) -> Unit,
    onTranslate: ((UIMessage, java.util.Locale) -> Unit)?,
    onClearTranslation: (UIMessage) -> Unit,
    selectionState: ChatSelectionState,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    onToggleFavorite: ((MessageNode) -> Unit)? = null,
    onConversationSystemPromptChange: ((String?) -> Unit)? = null,
    emptyHero: (@Composable () -> Unit)? = null,
    onCreateNewChat: (() -> Unit)? = null,
    onTodosChange: ((List<TodoItem>) -> Unit)? = null,
    onUpdateAssistant: ((Assistant) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val loadingState by rememberUpdatedState(loading)
    var isRecentScroll by remember { mutableStateOf(false) }
    val conversationUpdated by rememberUpdatedState(conversation)
    // Archived steering is metadata anchored to a reply node, not an extra message in the list.
    val archivedByNode = remember(conversation.config) { conversation.config.archivedGuidanceByNode() }
    val density = LocalDensity.current
    val activity = LocalContext.current as? me.rerere.rikkahub.RouteActivity

    DisposableEffect(Unit) {
        val listener: (Boolean) -> Boolean = { isVolumeUp ->
            if (settings.displaySetting.enableVolumeKeyScroll) {
                // 列表视口包含标题栏和输入区遮住的部分，翻页时只按可见区域计算
                val coveredPx = with(density) {
                    (innerPadding.calculateTopPadding() + 32.dp + innerPadding.calculateBottomPadding()).toPx()
                }
                val scrollAmount = (state.layoutInfo.viewportSize.height - coveredPx) *
                    settings.displaySetting.volumeKeyScrollRatio
                scope.launch { state.scrollBy(if (isVolumeUp) -scrollAmount else scrollAmount) }
                true
            } else false
        }
        activity?.volumeKeyListeners?.add(listener)
        onDispose {
            activity?.volumeKeyListeners?.remove(listener)
        }
    }

    fun List<LazyListItemInfo>.isAtBottom(): Boolean {
        // 还没布局出任何 item（新会话第一条消息刚进来）也算在底部
        val lastItem = lastOrNull() ?: return true
        val inputBarHeight = with(density) { innerPadding.calculateBottomPadding().toPx() }
        val lastPos = lastItem.offset + lastItem.size
        val inputPos = (state.layoutInfo.viewportEndOffset - inputBarHeight.roundToInt())
        // println("lastPos = $lastPos, inputPos = $inputPos  | ${lastPos <= inputPos - 8}")
        return lastPos <= inputPos - 8
    }

    // 聊天选择（状态放在外层，顶栏菜单也能直接进入多选）
    val selectedItems = selectionState.selectedItems
    var showExportSheet by remember { mutableStateOf(false) }

    // 自动跟随键盘滚动
    ImeLazyListAutoScroller(lazyListState = state)

    val assistant = remember(
        settings.assistants,
        conversation.assistantId,
        conversation.config,
        conversation.modeInjectionIds,
        conversation.lorebookIds,
    ) {
        // 工作区等配置在会话开始后以会话上固定的为准
        settings.getAssistantById(conversation.assistantId)?.withConversation(conversation)
    }
    val modelById = remember(settings.providers) {
        settings.providers
            .flatMap { it.models }
            .associateBy { it.id }
    }
    val lastMessageIndex = conversation.messageNodes.lastIndex
    // 捕获这个值而不是整份会话，消息条的 lambda 才能跨 chunk 复用
    val conversationId = conversation.id
    // 内嵌任务清单挂在哪条消息：清单为空时 null；就地卡片只在这一条消息下面出现一次，
    // 因此「旧清单不当成当前」由 todoAnchorNode 的归属优先保证
    val todoAnchorNodeId = remember(
        conversation.messageNodes,
        conversation.todos,
        conversation.todosOwnerNodeId,
    ) { conversation.todoAnchorNode()?.id }

    // 新消息入场：只有"尾部刚追加"的那几条会从屏幕下方滑进来，
    // 打开会话、加载历史、切换分支时列表尾部不会多出 id，所以都不会播。
    val enterTracker = remember(conversation.id) { MessageEnterTracker() }
    // 流式输出时每帧都会重组，条数和尾条没变就不用重扫一遍 id
    val messageIds = remember(
        conversation.id,
        conversation.messageNodes.size,
        conversation.messageNodes.lastOrNull()?.id,
    ) {
        conversation.messageNodes.map { it.id }
    }
    val enteringMessageIds = enterTracker.track(messageIds)
    // 方法引用每次重组都是新实例，缓存一份，动画中的 item 才不会被父级重组带着跑
    val consumeEnter = remember(enterTracker) { enterTracker::consume }

    // 滚到最底部以后继续上拉：拉满才认。
    // available 沿用原始手势符号（手指上滑是负值），到底以后列表吃不下，多出来的这段就是拉伸量。
    // 300dp 接近半屏，而且进度按开方衰减：前面涨得快、越往后越慢，最后那一段要实打实拉上去。
    val createPullMax = with(LocalDensity.current) { 300.dp.toPx() }
    var createPull by remember { mutableFloatStateOf(0f) }
    var createPullArmed by remember { mutableStateOf(false) }
    // 回调每次重组都是新实例，当 remember 的 key 用会让连接对象每帧重建
    val createNewChat = rememberUpdatedState(onCreateNewChat)
    // 松手没拉满时，让进度条顺着原路退回去，而不是凭空消失
    val createPullSettleSpec = remember { tween<Float>(durationMillis = 240, easing = FastOutSlowInEasing) }
    val createPullConnection = remember(state, createPullMax) {
        object : NestedScrollConnection {
            private var settleJob: Job? = null

            // 手势重新接管：正在进行的回退立刻停手，交给下面的手写数值
            private fun takeOverPull() {
                settleJob?.cancel()
                settleJob = null
            }

            // 顺着原路退回 0。带一段短动画，松手时胶囊是缩回去的，不是一下消失
            private fun settlePullBack() {
                createPullArmed = false
                if (createPull <= 0f) {
                    createPull = 0f
                    return
                }
                if (settleJob?.isActive == true) return
                val start = createPull
                settleJob = scope.launch {
                    animate(
                        initialValue = start,
                        targetValue = 0f,
                        animationSpec = createPullSettleSpec,
                    ) { value, _ ->
                        createPull = value
                    }
                    createPull = 0f
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) {
                    return Offset.Zero
                }
                // 手指往反方向走（往下）：先把已经拉开的那段按位移收回去，列表先不动。
                // 收完了才轮到列表往下滚，所以进度条是一点点退回去的，不会一碰就清零。
                if (available.y > 0f && createPull > 0f) {
                    // 这里没有上拉新建、或者已经离开底部：进度条不该再挂着，
                    // 直接作废，别让它静静吃掉一段向下滚动
                    if (createNewChat.value == null || state.canScrollForward) {
                        takeOverPull()
                        createPull = 0f
                        createPullArmed = false
                        return Offset.Zero
                    }
                    val retract = minOf(createPull, available.y)
                    takeOverPull()
                    createPull -= retract
                    createPullArmed = createPull >= createPullMax
                    return Offset(0f, retract)
                }
                // 内容不足一屏时列表两个方向都滚不动，onPostScroll 那条路不一定轮得到，
                // 这里先把上拉这段收下来
                if (available.y < 0f &&
                    createNewChat.value != null &&
                    conversationUpdated.messageNodes.isNotEmpty() &&
                    !state.canScrollForward &&
                    !state.canScrollBackward
                ) {
                    takeOverPull()
                    createPull = (createPull - available.y).coerceAtMost(createPullMax)
                    createPullArmed = createPull >= createPullMax
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (createNewChat.value == null || source != NestedScrollSource.UserInput) {
                    return Offset.Zero
                }
                // 到底了就算：能往下滚说明有内容且滚到了末尾；两个方向都滚不动说明内容不足一屏，
                // 这种短会话以前被排除在外，上拉一直没反应
                if (available.y < 0f &&
                    !state.canScrollForward &&
                    conversationUpdated.messageNodes.isNotEmpty()
                ) {
                    takeOverPull()
                    createPull = (createPull - available.y).coerceAtMost(createPullMax)
                    createPullArmed = createPull >= createPullMax
                    return Offset(0f, available.y)
                }
                // 已经离开底部（往回滚了，或者内容变长），残留的拉伸量顺着原路退回去
                if (createPull > 0f && state.canScrollForward) {
                    settlePullBack()
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (createPullArmed) {
                    createNewChat.value?.invoke()
                    takeOverPull()
                    createPull = 0f
                    createPullArmed = false
                } else {
                    // 没拉满就松手：胶囊原路弹回，而不是直接消失
                    settlePullBack()
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        // 新消息追加：用短动画滚过去，不要一瞬间闪现。
        // 用户自己发的消息由 ChatPage 在发送后滚动，这里只认助手新开的那条，
        // 否则同一次发送会排出两条动画，滚到位以后再补一小段。
        // 流式 token 的小步增长不算追加，仍走下面那条每帧跟随。
        var lastNodeCount by remember(conversation.id) {
            mutableIntStateOf(conversation.messageNodes.size)
        }
        LaunchedEffect(conversation.messageNodes.size) {
            val grew = conversation.messageNodes.size > lastNodeCount
            lastNodeCount = conversation.messageNodes.size
            if (!grew || !loadingState || state.isScrollInProgress) return@LaunchedEffect
            if (!settings.displaySetting.enableAutoScroll) return@LaunchedEffect
            if (conversation.messageNodes.lastOrNull()?.role != MessageRole.ASSISTANT) return@LaunchedEffect
            if (!state.layoutInfo.visibleItemsInfo.isAtBottom()) return@LaunchedEffect
            // 等新 item 完成布局再算目标，否则拿到的还是追加前的位置
            withFrameNanos { }
            state.animateScrollToItem((state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            // 动画期间内容还在长的话，收尾再追一次，
            // 免得动画一停就把增长的那一截交给每帧跟随去瞬跳
            withFrameNanos { }
            if (state.canScrollForward) {
                state.animateScrollToItem((state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            }
        }

        // 自动滚动到底部
        if (settings.displaySetting.enableAutoScroll) {
            LaunchedEffect(state) {
                snapshotFlow { state.layoutInfo.visibleItemsInfo }.collect { visibleItemsInfo ->
                    // println("is bottom = ${visibleItemsInfo.isAtBottom()}, scroll = ${state.isScrollInProgress}, can_scroll = ${state.canScrollForward}, loading = $loading")
                    if (!state.isScrollInProgress && loadingState) {
                        if (visibleItemsInfo.isAtBottom()) {
                            state.requestScrollToItem(conversationUpdated.messageNodes.lastIndex + 10)
                            // Log.i(TAG, "ChatList: scroll to ${conversationUpdated.messageNodes.lastIndex}")
                        }
                    }
                }
            }
        }

        // 判断最近是否滚动
        LaunchedEffect(state.isScrollInProgress) {
            if (state.isScrollInProgress) {
                isRecentScroll = true
                delay(1500)
                isRecentScroll = false
            } else {
                delay(1500)
                isRecentScroll = false
            }
        }

        ChatFontProvider(displaySetting = settings.displaySetting) {
            LazyColumn(
                state = state,
                // 顶部留白放进 contentPadding，让消息可以滚动到透明标题栏下面
                // 全宽组件：左右不再留白，气泡/卡片的边缘直接对齐屏幕；
                // 刘海和侧边系统栏的安全区照样让出来
                contentPadding = PaddingValues(
                    horizontal = adaptiveHorizontalPaddingValue(16.dp) + fullWidthHorizontalInset(),
                    vertical = 16.dp,
                ) + PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = 32.dp + innerPadding.calculateBottomPadding(),
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier
                    .fillMaxSize()
                    // 详情面板把 topBar 撑高后，topPadding 会跟着涨到几百 dp；
                    // 渐隐带按它算会把上半屏消息整片吃掉，所以封顶到顶栏本体的量级
                    .topFadingEdge(minOf(innerPadding.calculateTopPadding() + 24.dp, 160.dp))
                    .nestedScroll(createPullConnection)
                    // 静息时把列表也交给 haze：输入框与顶栏能糊到正文；
                    // 生成中撤掉，省掉「每个 token 重糊整块列表」的开销
                    .then(
                        if (contentSource != null && !loading && !compressing) {
                            Modifier.hazeSource(contentSource)
                        } else {
                            Modifier
                        }
                    ),
            ) {
            itemsIndexed(
                items = conversation.messageNodes,
                key = { index, item -> item.id },
            ) { index, node ->
                // 同角色连续的两条消息算一组：气泡接缝两侧的圆角收小、间距压到最小
                val groupRole = node.bubbleGroupRole()
                val joinedToPrevious = groupRole != null &&
                    conversation.messageNodes.getOrNull(index - 1)?.bubbleGroupRole() == groupRole
                val joinedToNext = groupRole != null &&
                    conversation.messageNodes.getOrNull(index + 1)?.bubbleGroupRole() == groupRole
                val itemGap = if (joinedToPrevious) MESSAGE_GAP_JOINED else MESSAGE_GAP_ROLE_CHANGE
                MessageEnterSlide(
                    messageId = node.id,
                    animateEnter = node.id in enteringMessageIds,
                    onEnterFinished = consumeEnter,
                ) {
                    Column {
                        if (itemGap > 0.dp) {
                            Spacer(Modifier.height(itemGap))
                        }
                        ListSelectableItem(
                            key = node.id,
                            onSelectChange = {
                                if (!selectedItems.contains(node.id)) {
                                    selectedItems.add(node.id)
                                } else {
                                    selectedItems.remove(node.id)
                                }
                            },
                            selectedKeys = selectedItems,
                            enabled = selectionState.selecting,
                        ) {
                            ChatMessage(
                                node = node,
                                // 每帧重新 snapshotModel 会造出一个新对象，ChatMessage 就没法跳过
                                model = remember(node) {
                                    node.currentMessage.modelId?.let(modelById::get)
                                        ?: node.currentMessage.snapshotModel()
                                },
                                assistant = assistant,
                                loading = loading && !compressing && index == lastMessageIndex,
                                onRegenerate = {
                                    onRegenerate(node.currentMessage)
                                },
                                onEdit = {
                                    onEdit(node.currentMessage)
                                },
                                onEditSubAgentNotice = onEditSubAgentNotice,
                                onDeleteSubAgentNotice = onDeleteSubAgentNotice?.let { delete ->
                                    { delete(node.currentMessage) }
                                },
                                onFork = {
                                    onForkMessage(node.currentMessage)
                                },
                                onDelete = {
                                    onDelete(node.currentMessage)
                                },
                                // 读 conversationUpdated 而不是捕获当前的会话对象：
                                // 否则每来一个 token 这里都会换一个新 lambda，旧消息也跟着整条重组
                                onShare = {
                                    val nodes = conversationUpdated.messageNodes
                                    val index = nodes.indexOfFirst { it.id == node.id }
                                    if (index >= 0) {
                                        selectionState.start(nodes.take(index + 1).map { it.id })
                                    }
                                },
                                onUpdate = {
                                    onUpdateMessage(it)
                                },
                                isFavorite = node.isFavorite,
                                onToggleFavorite = {
                                    onToggleFavorite?.invoke(node)
                                },
                                onTranslate = onTranslate,
                                onClearTranslation = onClearTranslation,
                                onToolApproval = onToolApproval,
                                onToolAnswer = onToolAnswer,
                                hazeState = bubbleHazeState,
                                conversationId = conversationId,
                                lastMessage = index == lastMessageIndex,
                                joinedToPrevious = joinedToPrevious,
                                joinedToNext = joinedToNext,
                            )
                        }
                        if (todoAnchorNodeId == node.id && !LocalConversationStatusInSidePanel.current) {
                            // 进行中计划归右上角当前计划下拉；结束后只在原回复下保留历史清单。
                            // 这里不再按 hideCompletedTodoCard 隐藏，否则完成后的历史快照会消失。
                            if (conversation.todos.isNotEmpty() && !conversation.todos.hasActiveTodo()) {
                                Spacer(Modifier.height(4.dp))
                                ConversationTodoCard(
                                    todos = conversation.todos,
                                    onTodosChange = onTodosChange,
                                    reminder = TodoReminderUi(
                                        enabled = assistant?.todoReminderEnabled ?: true,
                                        interval = assistant?.todoReminderInterval ?: 1,
                                    ),
                                    onReminderChange = { next ->
                                        val a = assistant
                                        if (a != null && onUpdateAssistant != null) {
                                            onUpdateAssistant(
                                                a.copy(
                                                    todoReminderEnabled = next.enabled,
                                                    todoReminderInterval = next.interval,
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                )
                            }
                        }
                        archivedByNode[node.id]?.forEach { entry ->
                            Spacer(Modifier.height(4.dp))
                            ArchivedLiveGuidanceCard(
                                entry = entry,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
            }

            if (!loading && assistant?.allowConversationSystemPrompt == true && onConversationSystemPromptChange != null) {
                item(key = "ConversationSystemPrompt") {
                    Column {
                        Spacer(Modifier.height(MESSAGE_GAP_ROLE_CHANGE))
                        ConversationSystemPromptButton(
                            customSystemPrompt = conversation.customSystemPrompt,
                            onSystemPromptChange = onConversationSystemPromptChange,
                        )
                    }
                }
            }

            // 压缩时也用这一行，processingStatus 显示的是压缩中
            if (loading) {
                item(LoadingIndicatorKey) {
                    Row(
                        modifier = Modifier.padding(
                            start = 8.dp,
                            end = 8.dp,
                            bottom = 8.dp,
                            top = MESSAGE_GAP_ROLE_CHANGE,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ContainedLoadingIndicator(
                            modifier = Modifier.size(28.dp)
                        )
                        AnimatedVisibility(
                            visible = processingStatus != null,
                        ) {
                            Text(
                                text = processingStatus ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // 为了能正确滚动到这
            item(ScrollBottomKey) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                )
            }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 错误消息卡片
            ErrorCardsDisplay(
                errors = errors,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(5f)
            )

            // 完成选择
            ChatSelectionToolbar(
                state = selectionState,
                allIds = conversation.messageNodes.map { it.id },
                onConfirm = {
                    selectionState.selecting = false
                    if (conversation.messageNodes.any { it.id in selectedItems }) {
                        showExportSheet = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -(48).dp),
            )

            // 导出对话框
            ChatExportSheet(
                visible = showExportSheet,
                onDismissRequest = {
                    showExportSheet = false
                    selectedItems.clear()
                },
                conversation = conversation,
                selectedMessages = conversation.messageNodes.filter { it.id in selectedItems }
                    .map { it.currentMessage }
            )

            val captureProgress = LocalScrollCaptureInProgress.current

            MessageNavBars(
                messageCount = conversation.messageNodes.size,
                firstMessageItemIndex = 0,
                state = state,
                onLeft = settings.displaySetting.messageNavOnLeft,
                modifier = Modifier.align(
                    if (settings.displaySetting.messageNavOnLeft) Alignment.CenterStart else Alignment.CenterEnd
                ),
            )

            // 消息快速跳转
            MessageJumper(
                show = isRecentScroll && !state.isScrollInProgress && settings.displaySetting.showMessageJumper && !captureProgress,
                onLeft = settings.displaySetting.messageJumperOnLeft,
                scope = scope,
                state = state
            )

            if (conversation.currentMessages.isEmpty() && emptyHero != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 32.dp)
                ) {
                    emptyHero()
                }
            }

            // Suggestion
            if (conversation.chatSuggestions.isNotEmpty() && !captureProgress) {
                ChatSuggestionsRow(
                    conversation = conversation,
                    onClickSuggestion = onClickSuggestion,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
        if (onCreateNewChat != null) {
            val trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.24f)
            val fillColor = MaterialTheme.colorScheme.onSecondaryContainer
            Surface(
                shape = CircleShape,
                // 拉满换色，明确告诉用户"现在松手就会新建"；
                // 底色跟「玻璃透明度」走，0.7 的下限保证压着消息正文时还认得出是一块胶囊
                color = glassOverlayColor(
                    base = if (createPullArmed) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    floor = 0.7f,
                ),
                tonalElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = innerPadding.calculateBottomPadding() + 24.dp)
                    .graphicsLayer {
                        // 在图层相位读拉伸量：只重绘这一层，不重组整个列表
                        val progress = sqrt((createPull / createPullMax).coerceIn(0f, 1f))
                        alpha = progress
                        translationY = (1f - progress) * 24.dp.toPx()
                    },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (createPullArmed) stringResource(R.string.chat_page_pull_release_new_chat) else stringResource(R.string.chat_page_pull_keep_pulling),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Canvas(
                        modifier = Modifier
                            .width(140.dp)
                            .height(4.dp),
                    ) {
                        // 同样在绘制阶段读 state，拖动时只重画这条进度条
                        val p = sqrt((createPull / createPullMax).coerceIn(0f, 1f))
                        val corner = CornerRadius(size.height / 2f)
                        drawRoundRect(color = trackColor, cornerRadius = corner)
                        drawRoundRect(
                            color = fillColor,
                            size = Size(size.width * p, size.height),
                            cornerRadius = corner,
                        )
                    }
                }
            }
        }

    }
}
