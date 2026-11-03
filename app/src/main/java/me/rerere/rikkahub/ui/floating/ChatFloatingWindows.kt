package me.rerere.rikkahub.ui.floating

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petterp.floatingx.compose.compose
import com.petterp.floatingx.core.FloatingX
import com.petterp.floatingx.core.FxControl
import com.petterp.floatingx.core.animation.FxAnimations
import com.petterp.floatingx.core.feature.FxFeature
import com.petterp.floatingx.core.feature.FxFeatureScope
import com.petterp.floatingx.core.gesture.FxDrag
import com.petterp.floatingx.core.gesture.FxGesture
import com.petterp.floatingx.core.layout.FxAdsorb
import com.petterp.floatingx.core.layout.FxGravity
import com.petterp.floatingx.core.storage.FxSpStorage
import com.petterp.floatingx.system.container.FxWindowContainer
import com.petterp.floatingx.system.permission.FxPermission
import com.petterp.floatingx.system.permission.FxPermissionStrategy
import com.petterp.floatingx.system.systemHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.conversationIntent
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.GenerationPhase
import me.rerere.rikkahub.data.model.generationPhase
import me.rerere.rikkahub.data.model.pendingToolCalls
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.ChatFloatingTracker
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.MessageQueueState
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import kotlin.uuid.Uuid

private const val TAG = "ChatFloatingWindows"
private const val BUBBLE_TAG = "chat_floating_bubble"
private const val PANEL_TAG = "chat_floating_panel"
private const val PANEL_TOP_OFFSET_DP = 8f

/**
 * 把 [ChatFloatingTracker] 的结果显示成系统悬浮窗：收起时是贴边的小图标，展开是固定在顶部的面板。
 * 两个窗口共用这里汇总的一份 [FloatingChatUiState]。FloatingX 只能在主线程操作，协程都跑在 AppScope 上。
 */
class ChatFloatingWindows(
    private val context: Application,
    private val appScope: AppScope,
    private val tracker: ChatFloatingTracker,
    private val settingsStore: SettingsStore,
    lazyChatService: Lazy<ChatService>,
) {
    private val chatService by lazyChatService
    private var bubble: FxControl? = null
    private var panel: FxControl? = null
    private var panelFeature: PanelWindowFeature? = null

    // 悬浮窗显示期间一直汇总，气泡和面板切换时直接拿到当前状态，不会先空一帧
    private val uiState: StateFlow<FloatingChatUiState?> = tracker.window
        .map { it?.conversationId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else chatState(id) }
        .stateIn(appScope, SharingStarted.Eagerly, null)

    // 悬浮窗的 Context 不是 Activity，默认的 UriHandler 打开链接时缺少 NEW_TASK 会崩溃
    private val uriHandler = object : UriHandler {
        override fun openUri(uri: String) {
            val intent = Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "openUri: no activity for $uri", e)
            }
        }
    }

    init {
        appScope.launch {
            tracker.window.map { it?.expanded }.distinctUntilChanged().collect(::render)
        }
    }

    private fun chatState(id: Uuid): Flow<FloatingChatUiState> {
        // 只显示这一轮的错误。开始时间要用启动任务时记下的，等这里观察到任务再取会晚于快速失败时报的错
        val error = combine(chatService.errors, chatService.getGenerationStartedAtFlow(id)) { errors, since ->
            since?.let { errors.lastOrNull { it.conversationId == id && it.timestamp >= since } }
        }
        val conversation = combine(chatService.getConversationFlow(id), chatService.getReplyMessageIdFlow(id), ::Pair)
        return combine(
            conversation,
            chatService.getGenerationJobStateFlow(id).map { it != null },
            chatService.getProcessingStatusFlow(id),
            chatService.getMessageQueueFlow(id),
            error,
        ) { (conversation, replyMessageId), generating, processingStatus, queue, error ->
            buildFloatingChatUiState(conversation, replyMessageId, generating, processingStatus, queue, error)
        }.flowOn(Dispatchers.Default)
    }

    private fun render(expanded: Boolean?) {
        // 后台弹不出权限申请页，没有权限就不显示
        if (expanded == null || !FxPermission.isGranted(context)) {
            bubble?.cancel()
            panel?.cancel()
            bubble = null
            panel = null
            return
        }
        val bubble = bubble ?: installBubble().also { bubble = it }
        if (expanded) {
            bubble.hide()
            (panel ?: installPanel().also { panel = it }).show()
        } else {
            panel?.hide()
            bubble.show()
        }
    }

    private fun installBubble(): FxControl = FloatingX.install(BUBBLE_TAG) {
        compose {
            WindowContent { state, display ->
                ChatFloatingBubble(
                    state = state,
                    icon = display.floatingBubbleIcon,
                    onClick = tracker::expand,
                )
            }
        }
        anchor(FxGravity.CENTER_END, dy = -dp(96f))
        margin(left = dp(8f), right = dp(8f))
        adsorb(FxAdsorb.horizontal())
        persist(FxSpStorage(context))
        animation(FxAnimations.scale())
        systemHost(context) { permission(FxPermissionStrategy.skip()) }
    }

    private fun installPanel(): FxControl = FloatingX.install(PANEL_TAG) {
        compose {
            WindowContent { state, display ->
                val conversationId = state.conversationId
                // 面板宽度变化（拖动、设置页滑块、旋转）时同步窗口宽度
                val screenWidthPx = panelWidthPx(1f)
                LaunchedEffect(display.floatingPanelWidthRatio, screenWidthPx) {
                    panelFeature?.setWindowWidth(panelWidthPx(display.floatingPanelWidthRatio))
                }
                ChatFloatingPanel(
                    state = state,
                    widthFraction = display.floatingPanelWidthRatio,
                    heightFraction = display.floatingPanelHeightRatio,
                    maxHeight = rememberPanelMaxHeight(),
                    previewMaxHeight = display.floatingPreviewMaxHeight.dp,
                    onWidthChanged = { ratio -> panelFeature?.setWindowWidth(panelWidthPx(ratio)) },
                    onReplyFocusChanged = { focused -> panelFeature?.setWindowFocusable(focused) },
                    onResize = { width, height, previewHeight ->
                        appScope.launch {
                            settingsStore.update { settings ->
                                settings.copy(
                                    displaySetting = settings.displaySetting.copy(
                                        floatingPanelWidthRatio = width,
                                        floatingPanelHeightRatio = height,
                                        floatingPreviewMaxHeight = previewHeight,
                                    )
                                )
                            }
                        }
                    },
                    onCollapse = tracker::collapse,
                    onDismiss = tracker::dismiss,
                    onOpenConversation = { openConversation(conversationId) },
                    onToolApproval = { toolCallId, approved ->
                        chatService.handleToolApproval(conversationId, toolCallId, approved)
                    },
                    onStop = { appScope.launch { chatService.stopGeneration(conversationId) } },
                    onSend = { text -> chatService.sendMessage(conversationId, listOf(UIMessagePart.Text(text))) },
                    onResumeQueue = { chatService.resumeMessageQueue(conversationId) },
                )
            }
        }
        anchor(FxGravity.TOP_CENTER, dy = dp(PANEL_TOP_OFFSET_DP))
        gesture(FxGesture(click = false, longPress = false, drag = FxDrag.DISABLED))
        animation(FxAnimations.fade())
        PanelWindowFeature().also { feature ->
            panelFeature = feature
            addFeature(feature)
        }
        systemHost(context) {
            permission(FxPermissionStrategy.skip())
            layoutParams {
                // 窗口宽度 = 面板可见宽度，不是全屏宽：WindowManager 按窗口矩形分发触摸，
                // 全屏宽会把面板两侧的透明区也变成吃触摸的区域，缩窄后窗口外的触摸才归下面的窗口。
                // 这里只给首帧一个初值（wrap_content 的窗口会先按对话框宽度约 320dp 测量，
                // 面板会被压窄），之后宽度由 PanelWindowFeature 随设置/拖动实时同步。
                it.width = panelWidthPx(settingsStore.settingsFlow.value.displaySetting.floatingPanelWidthRatio)
            }
            onBackPressed {
                tracker.collapse()
                true
            }
        }
    }

    /**
     * 面板窗口的两个运行期状态都在这里改：
     * - 宽度：窗口矩形只覆盖面板可见部分，面板两侧的透明区不再拦截触摸；
     * - 焦点：只在输入框按下/聚焦时临时放开（弹键盘用），失焦或收起立即恢复，
     *   面板展开本身不抢前台应用的按键/输入法焦点。
     * 系统窗口默认 flags 里始终带着 NOT_TOUCH_MODAL，窗口外的触摸本来就透传。
     */
    private class PanelWindowFeature : FxFeature {
        private var container: FxWindowContainer? = null

        override fun onAttach(scope: FxFeatureScope) {
            container = scope.container as? FxWindowContainer
        }

        override fun onDetach() {
            container = null
        }

        fun setWindowWidth(px: Int) {
            val c = container ?: return
            val width = px.coerceAtLeast(1)
            if (c.windowParams.width == width) return
            c.windowParams.width = width
            // 改宽度要走 updateViewLayout，applyLayout 顺带按锚点(TOP_CENTER)重算坐标；
            // 内容重测后 core 还会按新尺寸再校正一次位置
            c.applyLayout(c.contentPosition().x, c.contentPosition().y, FxGravity.TOP_CENTER, c.isLtr)
        }

        fun setWindowFocusable(focusable: Boolean) {
            container?.setWindowFocusable(focusable)
        }

        override fun onHide() {
            // 收起后窗口不再接收输入，把按键/输入法焦点让回去
            container?.setWindowFocusable(false)
        }
    }

    // 窗口隐藏时生命周期退回 STARTED，只在显示时订阅，流式输出不会让隐藏的窗口跟着重组。
    // 设置也走这条路：面板尺寸和图标只在窗口真的显示时才需要。
    @Composable
    private fun WindowContent(content: @Composable (FloatingChatUiState, DisplaySetting) -> Unit) {
        val state by uiState.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
        val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle(
            minActiveState = Lifecycle.State.RESUMED,
        )
        RikkahubTheme {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                state?.let { content(it, settings.displaySetting) }
            }
        }
    }

    // 面板窗口是 wrap_content 且不受屏幕限制（FloatingX 用了 NO_LIMITS），内容多了会伸出屏幕底部，按可用高度封顶
    @Composable
    private fun rememberPanelMaxHeight(): Dp {
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        return remember(configuration, density) {
            with(density) { (safeScreenHeight() - dp(PANEL_TOP_OFFSET_DP)).toDp() }
        }
    }

    // 和 FloatingX 摆放窗口用的安全区一致：R 及以上去掉屏幕级的系统栏和刘海，R 以下没有公开入口，用不含导航栏的高度
    private fun safeScreenHeight(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return context.resources.displayMetrics.heightPixels
        val metrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics
        val insets = WindowInsetsCompat.toWindowInsetsCompat(metrics.windowInsets)
            .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        return metrics.bounds.height() - insets.top - insets.bottom
    }

    private fun openConversation(conversationId: Uuid) {
        // 有悬浮窗权限的应用允许从后台启动 Activity
        context.startActivity(
            conversationIntent(context, conversationId.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density

    // 用与 Compose 侧同一份尺寸（screenWidthDp * density）算窗口宽度，两边才能真正对齐；
    // 直接用 widthPixels 在个别机型上会和 LocalConfiguration.screenWidthDp 差几像素，
    // 窗口窄了会裁掉面板，宽了又会多出吃触摸的透明区
    private fun panelWidthPx(ratio: Float): Int {
        val configuration = context.resources.configuration
        val screenWidthPx = configuration.screenWidthDp * context.resources.displayMetrics.density
        return (screenWidthPx * FloatingPanelSizing.clampWidthRatio(ratio)).toInt().coerceAtLeast(1)
    }
}

/** [replyMessageId] 是本轮正在写的助手消息，这一轮还没有输出时为空 */
internal fun buildFloatingChatUiState(
    conversation: Conversation,
    replyMessageId: Uuid?,
    generating: Boolean,
    processingStatus: String?,
    queue: MessageQueueState,
    error: ChatError?,
): FloatingChatUiState {
    // 不看末尾也不看节点选中的分支：重新生成较早的消息时，后面的节点还在，用户也可能切回了旧回复
    val reply = replyMessageId?.let { id ->
        conversation.messageNodes.firstNotNullOfOrNull { node -> node.messages.firstOrNull { it.id == id } }
    }
    val text = reply?.parts?.filterIsInstance<UIMessagePart.Text>()?.lastOrNull()?.text?.trim().orEmpty()
    val reasoning = reply?.parts?.filterIsInstance<UIMessagePart.Reasoning>()?.lastOrNull()?.reasoning?.trim().orEmpty()
    return FloatingChatUiState(
        conversationId = conversation.id,
        title = conversation.title,
        generating = generating,
        processingStatus = processingStatus,
        phase = reply?.generationPhase() ?: GenerationPhase.Waiting,
        hasReply = reply != null,
        // 还没写正文时显示思考内容。按 Markdown 渲染，不能截断，切在代码块中间后面会整段错乱
        preview = text.ifEmpty { reasoning },
        previewIsReasoning = text.isEmpty(),
        // 和 ChatService 判断是否在等审批用同一个条件，否则快速回复会被审批挡住却看不到原因
        pendingTools = conversation.pendingToolCalls,
        queuedCount = queue.messages.size,
        queuePaused = queue.paused,
        error = error,
    )
}
