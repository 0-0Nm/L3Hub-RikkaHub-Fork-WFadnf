package me.rerere.rikkahub.ui.floating

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
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
import com.petterp.floatingx.core.gesture.FxRegion
import com.petterp.floatingx.core.layout.FxAdsorb
import com.petterp.floatingx.core.layout.FxAnchor
import com.petterp.floatingx.core.layout.FxGravity
import com.petterp.floatingx.core.layout.FxLayoutInput
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

// 面板自己的上下留白（8+8）+ 手柄和它上面的间距（6+48）。封顶时要一起扣掉：
// 窗口比屏幕还高的话，整块屏幕都成了它的触摸区
private const val PANEL_CHROME_DP = 70f

// 顶部标题栏那一块可以整块拖动面板。y 从内容顶端算起，而窗口高度 = 内容高度，
// 所以这个值同时也等于“从窗口顶端往下多少”是拖动区。
// 标题栏默认到 64dp（8 外留白 + 16 内留白 + 40 图标），大字体时会被撑高，留到 72dp；
// 预览区从 76dp 才开始，往上一两个 dp 也不会抢它的滚动手势
private const val PANEL_DRAG_REGION_DP = 72f

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
                // 换屏（旋转 / 分屏）改了可用宽度：宽度按新屏幕换算，横向重新居中。
                // 不能拿旋转前的坐标去推——那个位置在新可用区里已经对不上了。
                // 显式读一次 LocalConfiguration：配置一变这个 lambda 就重组，下面的 effect 才会重跑
                val configuration = LocalConfiguration.current
                val screenWidthPx = remember(configuration) { panelWidthPx(1f) }
                LaunchedEffect(screenWidthPx) {
                    panelFeature?.recenter(panelWidthPx(display.floatingPanelWidthRatio))
                }
                // 设置页滑块改了比例：以原来的中轴线为中心收缩 / 展开
                LaunchedEffect(display.floatingPanelWidthRatio) {
                    panelFeature?.setWindowWidthCentered(panelWidthPx(display.floatingPanelWidthRatio))
                }
                ChatFloatingPanel(
                    state = state,
                    widthFraction = display.floatingPanelWidthRatio,
                    heightFraction = display.floatingPanelHeightRatio,
                    maxHeight = rememberPanelMaxHeight(),
                    previewMaxHeight = display.floatingPreviewMaxHeight.dp,
                    onWidthChanged = { ratio -> panelFeature?.resizeWindow(panelWidthPx(ratio)) },
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
        // 锚点贴物理左边缘（LTR 用 START，RTL 由 PanelWindowFeature 换成 END）：
        // 改宽度时只有右边那条边动，左边钉在原处。
        // 初始横向偏移取“居中”，看起来和以前一样，但不再是居中锚点
        anchor(FxGravity.TOP_START, dx = panelStartPx(initialWidthRatio()), dy = dp(PANEL_TOP_OFFSET_DP))
        // 面板可以整块拖：只有顶部标题栏那一块能起拖，预览区滚动、输入框和按钮照常收自己的手势
        gesture(
            FxGesture(
                click = false,
                longPress = false,
                drag = FxDrag.IMMEDIATE,
                dragRegion = FxRegion.rect(0f, 0f, Float.MAX_VALUE, dp(PANEL_DRAG_REGION_DP)),
            )
        )
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
                it.width = panelWidthPx(initialWidthRatio())
                // 库的默认 flags 本来就带 NOT_TOUCH_MODAL，这里再显式写一次：
                // 以后谁改 flags（比如弹键盘时清 NOT_FOCUSABLE）也别把“窗口外触摸透传”丢掉
                it.flags = it.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            }
            onBackPressed {
                tracker.collapse()
                true
            }
        }
    }

    /**
     * 面板窗口的运行期状态都收在这里：
     * - 尺寸：窗口矩形只覆盖面板本身，面板之外的触摸归下面的应用；
     * - 位置：改宽度前把锚点钉回起点边，于是只有被拖的那条边动，另一条边不动；
     * - 焦点：只在回复框按下 / 聚焦时临时放开（弹键盘用）。窗口一失焦或收起立刻让回去——
     *   系统窗口一直握着焦点的话，别的应用既收不到键盘，点一下也像被吃掉。
     */
    private class PanelWindowFeature : FxFeature {
        private var container: FxWindowContainer? = null
        private var scope: FxFeatureScope? = null
        private var windowFocusObserver: ViewTreeObserver? = null

        // 用户去点了别的应用：把可聚焦和输入法一起让回去
        private val windowFocusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (!hasFocus) releaseWindowFocus()
        }

        override fun onAttach(scope: FxFeatureScope) {
            this.scope = scope
            val c = scope.container as? FxWindowContainer ?: return
            container = c
            val observer = c.viewTreeObserver
            windowFocusObserver = observer
            observer.addOnWindowFocusChangeListener(windowFocusListener)
        }

        override fun onDetach() {
            windowFocusObserver?.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(windowFocusListener)
            windowFocusObserver = null
            container = null
            scope = null
        }

        /** 拖手柄改宽度：只换宽度，位置不动，所以起点边钉在原处 */
        fun resizeWindow(px: Int) {
            val c = container ?: return
            val width = px.coerceAtLeast(1)
            // 已经拖到上下限时不必每帧都再摆一次窗口
            if (c.windowParams.width == width) return
            val s = scope ?: return
            val input = s.layoutInput() ?: return
            val pos = c.contentPosition()
            val anchor = startAnchored(input, pos.x, pos.y)
            c.windowParams.width = width
            s.commitAnchor(anchor)
            s.requestRelayout()
        }

        /** 设置页滑块改比例：面板以原来的中轴线为中心收缩 / 展开 */
        fun setWindowWidthCentered(px: Int) {
            val c = container ?: return
            val width = px.coerceAtLeast(1)
            val old = c.windowParams.width
            if (old == width) return
            // 宽度还不是有效像素值（宿主刚重建 / 被改成了 WRAP_CONTENT）：没有旧宽度可依，按新宽度居中
            if (old <= 0) {
                recenter(width)
                return
            }
            val s = scope ?: return
            val input = s.layoutInput() ?: return
            val pos = c.contentPosition()
            val anchor = startAnchored(input, pos.x, pos.y)
            val shift = FloatingPanelSizing.centeredStartOffset(anchor.dx, old.toFloat(), width.toFloat())
            c.windowParams.width = width
            s.commitAnchor(anchor.copy(dx = shift))
            s.requestRelayout()
        }

        /** 换屏（旋转 / 分屏）之后：宽度按新屏幕换算，横向按新的可用区重新居中 */
        fun recenter(px: Int) {
            val s = scope ?: return
            val c = container ?: return
            val input = s.layoutInput() ?: return
            val width = px.coerceAtLeast(1)
            val pos = c.contentPosition()
            c.windowParams.width = width
            // 直接按新宽度居中：旋转前的坐标在新可用区里已经没有意义了
            val left = ((input.area.width - width) / 2f).coerceAtLeast(0f)
            s.commitAnchor(leftEdgeAnchored(input, left, pos.y - input.area.top))
            s.requestRelayout()
        }

        fun setWindowFocusable(focusable: Boolean) {
            container?.setWindowFocusable(focusable)
        }

        override fun onHide() {
            releaseWindowFocus()
        }

        /**
         * 当前左上角换算成“贴起点边”的锚点。
         *
         * 拖动结束时库按最近的边记锚点（横向只会是 START 或 END），面板停在右半边就会记成 END，
         * 之后一改宽度窗口就从右边长——正是“只能按中轴线调大小”的来源。
         * 每次改宽度之前统一钉回 TOP_START，宽度就只往末端长。
         */
        private fun startAnchored(input: FxLayoutInput, x: Float, y: Float): FxAnchor =
            leftEdgeAnchored(input, x - input.area.left, y - input.area.top)

        /**
         * 贴物理左边缘的锚点：LTR 用 START，RTL 下 START 会被库翻成右边缘，所以改用 END。
         *
         * 锚在物理左边，改宽度时窗口自己不动，拖右边手柄的位移才是 1:1 的
         * （锚在右边的话窗口会跟着变宽往左移，和手指的位移互相抵消，RTL 下尤其明显）。
         */
        private fun leftEdgeAnchored(input: FxLayoutInput, left: Float, top: Float): FxAnchor {
            val gravity = if (input.ltr) FxGravity.TOP_START else FxGravity.TOP_END
            return FxAnchor(gravity, left, top)
        }

        private fun releaseWindowFocus() {
            container?.setWindowFocusable(false)
            val c = container ?: return
            // 键盘跟着一起收：留在别的应用上面，会把下半屏的点击全吃掉
            c.context.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(c.windowToken, 0)
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
        // wrapContent：内容不许铺满父容器。铺满时 wrap_content 的系统窗口会被撑到整块屏幕，
        // 窗口就是触摸区，窗口外的滑动和点击全被浮窗接走（“滑哪都在拖悬浮球”就是这么来的）
        RikkahubTheme(wrapContent = true) {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                state?.let { content(it, settings.displaySetting) }
            }
        }
    }

    // 面板窗口是 wrap_content 且不受屏幕限制（FloatingX 用了 NO_LIMITS），内容多了会伸出屏幕底部。
    // 上限连面板自己的留白和下方手柄一起扣：窗口比屏幕还高，整块屏幕就都是它的触摸区了
    @Composable
    private fun rememberPanelMaxHeight(): Dp {
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        return remember(configuration, density) {
            with(density) { (safeScreenHeight() - dp(PANEL_TOP_OFFSET_DP + PANEL_CHROME_DP)).toDp() }
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

    private fun initialWidthRatio(): Float =
        settingsStore.settingsFlow.value.displaySetting.floatingPanelWidthRatio

    /** 面板的初始位置：在可用宽度里居中；之后用户拖到哪算哪 */
    private fun panelStartPx(ratio: Float): Float = (panelWidthPx(1f) - panelWidthPx(ratio)) / 2f

    // 用与 Compose 侧同一份尺寸（screenWidthDp * density）算窗口宽度，两边才能真正对齐；
    // 直接用 widthPixels 在个别机型上会和 LocalConfiguration.screenWidthDp 差几像素，
    // 窗口窄了会裁掉面板，宽了又会多出吃触摸的透明区
    private fun panelWidthPx(ratio: Float): Int {
        val configuration = context.resources.configuration
        val screenWidthPx = configuration.screenWidthDp * context.resources.displayMetrics.density
        return FloatingPanelSizing.windowWidthPx(screenWidthPx, ratio)
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
