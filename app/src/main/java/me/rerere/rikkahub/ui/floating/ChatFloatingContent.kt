package me.rerere.rikkahub.ui.floating

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.Fullscreen
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.Remove01
import me.rerere.hugeicons.stroke.Rocket01
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Stop
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Wrench01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.local.ASK_USER_TOOL_NAME
import me.rerere.rikkahub.data.model.FloatingBubbleIcon
import me.rerere.rikkahub.data.model.GenerationPhase
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.ui.components.richtext.MaterialMarkdown
import me.rerere.rikkahub.ui.components.ui.drawAppLogo
import me.rerere.rikkahub.ui.components.ui.logoMorphShape
import me.rerere.rikkahub.ui.components.ui.rememberAppLogoLayers
import me.rerere.rikkahub.ui.components.ui.shadow.Button
import me.rerere.rikkahub.ui.components.ui.shadow.FilledIconButton
import me.rerere.rikkahub.ui.components.ui.shadow.FilledTonalIconButton
import me.rerere.rikkahub.ui.terminal.IconButton
import me.rerere.rikkahub.ui.terminal.OutlinedButton
import me.rerere.rikkahub.ui.terminal.TextButton
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.uuid.Uuid

// 和 Material3 LoadingIndicator 的参数一致；放宽 visibilityThreshold 让低阻尼弹簧在间隔内结束
private val MorphSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 200f, visibilityThreshold = 0.1f)
private const val MORPH_INTERVAL_MILLIS = 650L
private const val SPIN_DURATION_MILLIS = 4666

/** 悬浮窗展示的会话状态 */
data class FloatingChatUiState(
    val conversationId: Uuid,
    val title: String,
    val generating: Boolean,
    /** 网络重试等临时提示 */
    val processingStatus: String?,
    /** 本轮回复在做什么 */
    val phase: GenerationPhase,
    /** 本轮是否已经有回复 */
    val hasReply: Boolean,
    /** 本轮回复的正文（Markdown），还没写正文时是思考内容 */
    val preview: String,
    val previewIsReasoning: Boolean,
    val pendingTools: List<UIMessagePart.Tool>,
    val queuedCount: Int,
    val queuePaused: Boolean,
    /** 这一轮出现的错误 */
    val error: ChatError?,
) {
    val failed: Boolean get() = !generating && error != null
}

// 气泡的尺寸和呼吸节奏；logo 那套变形用的是上面 LoadingIndicator 的参数
private val BUBBLE_SIZE = 52.dp
private const val BUBBLE_PULSE_MIN = 0.9f
private const val BUBBLE_PULSE_MILLIS = 700
private const val BUBBLE_SETTLE_MILLIS = 150

/** 悬浮球可以换成的内置图标，[name] 会存进设置，改了名字等于丢了用户的选择 */
internal data class BubblePreset(val name: String, val icon: ImageVector)

/**
 * 预设的键。名字会存进设置，改名字等于丢掉用户已经选好的图标，只加不改。
 * 和图标分开一份，是为了单测能只验名字、不用构造 ImageVector。
 */
internal val BubblePresetNames = listOf("sparkles", "chat", "brain", "magic", "rocket", "heart")

internal val BubblePresets: List<BubblePreset> =
    BubblePresetNames.map { name -> BubblePreset(name, bubblePresetIcon(name)) }

/** 认不出来的名字（旧配置、手改过的设置）用 Sparkles 顶上，不留一个空白的球 */
internal fun bubblePresetIcon(name: String): ImageVector = when (name) {
    "sparkles" -> HugeIcons.Sparkles
    "chat" -> HugeIcons.BubbleChat
    "brain" -> HugeIcons.Brain02
    "magic" -> HugeIcons.MagicWand01
    "rocket" -> HugeIcons.Rocket01
    "heart" -> HugeIcons.Favourite
    else -> HugeIcons.Sparkles
}

/**
 * 图标的画法，气泡、选择器和设置页列表项共用一份。
 *
 * [size] 是外层圆的大小，内部元素按比例缩：预设图标用一半，Emoji 用 0.46 倍字号。
 * 图片读不出来时回落到应用 logo——文件被清理掉时不至于剩一个空圆。
 */
@Composable
internal fun BubbleIconContent(
    icon: FloatingBubbleIcon,
    size: Dp,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val logoLayers = rememberAppLogoLayers()
    val density = LocalDensity.current
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        when (icon) {
            is FloatingBubbleIcon.AppLogo -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .drawAppLogo(logoLayers),
            )

            is FloatingBubbleIcon.Preset -> Icon(
                imageVector = bubblePresetIcon(icon.name),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(size / 2),
            )

            is FloatingBubbleIcon.Emoji -> Text(
                text = icon.content,
                fontSize = with(density) { (size * 0.46f).toSp() },
                maxLines = 1,
            )

            is FloatingBubbleIcon.Image -> {
                var failed by remember(icon.url) { mutableStateOf(false) }
                if (failed) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .drawAppLogo(logoLayers),
                    )
                } else {
                    AsyncImage(
                        model = icon.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { failed = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                    )
                }
            }
        }
    }
}

/** 收起时的小图标：按设置画 logo、内置图标、Emoji 或图片，生成中不停变形，结束后在角上标出结果 */
@Composable
fun ChatFloatingBubble(state: FloatingChatUiState, icon: FloatingBubbleIcon, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // 收起到面板时窗口隐藏，帧还在跑，停下动画
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animating = state.generating && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    // 不用 Surface：它会吃掉触摸事件，拖动交给 FloatingX 处理
    Box(modifier = Modifier.padding(6.dp)) {
        if (icon is FloatingBubbleIcon.AppLogo) {
            LogoBubble(animating = animating, onClick = onClick)
        } else {
            BubbleWithIcon(icon = icon, animating = animating, onClick = onClick)
        }
        when {
            state.generating -> {}
            state.error != null -> StatusBadge(HugeIcons.Alert01, colors.error, colors.onError)
            state.pendingTools.isNotEmpty() -> StatusBadge(HugeIcons.Wrench01, colors.error, colors.onError)
            state.hasReply -> StatusBadge(HugeIcons.Tick01, colors.primary, colors.onPrimary)
        }
    }
}

/** 应用 logo：生成中按 LoadingIndicator 的节奏变形，另外整体匀速自转 */
@Composable
private fun LogoBubble(animating: Boolean, onClick: () -> Unit) {
    val logoLayers = rememberAppLogoLayers()
    val shapePosition = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    // 固定间隔变一个形状，弹簧和计时同时跑
    LaunchedEffect(animating) {
        if (!animating) {
            // 停在一个完整的形状上
            shapePosition.animateTo(ceil(shapePosition.value), MorphSpring)
            return@LaunchedEffect
        }
        launch {
            spin.animateTo(
                targetValue = spin.value + 360f,
                animationSpec = infiniteRepeatable(tween(SPIN_DURATION_MILLIS, easing = LinearEasing)),
            )
        }
        while (true) {
            val morph = launch { shapePosition.animateTo(floor(shapePosition.value) + 1f, MorphSpring) }
            delay(MORPH_INTERVAL_MILLIS)
            morph.join()
        }
    }

    val logoShape = logoMorphShape(shapePosition.value, extraRotation = spin.value)
    Box(
        modifier = Modifier
            .size(BUBBLE_SIZE)
            // 不能用 elevation 阴影：轮廓是凹多边形，每帧都要重新三角剖分，变形时会把 RenderThread 卡住几十秒
            .dropShadow(logoShape, Shadow(radius = 6.dp, offset = DpOffset(0.dp, 2.dp), alpha = 0.3f))
            .clip(logoShape)
            .clickable(onClick = onClick)
            .drawAppLogo(logoLayers)
    )
}

/**
 * logo 以外的图标：圆形底片，生成中做呼吸缩放。
 * 不做 logo 那套轮廓变形——任意图片都没有那个凹多边形轮廓可以依。
 */
@Composable
private fun BubbleWithIcon(icon: FloatingBubbleIcon, animating: Boolean, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(animating) {
        if (!animating) {
            scale.animateTo(1f, tween(BUBBLE_SETTLE_MILLIS))
            return@LaunchedEffect
        }
        while (true) {
            scale.animateTo(BUBBLE_PULSE_MIN, tween(BUBBLE_PULSE_MILLIS, easing = FastOutSlowInEasing))
            scale.animateTo(1f, tween(BUBBLE_PULSE_MILLIS, easing = FastOutSlowInEasing))
        }
    }
    Box(
        modifier = Modifier
            .size(BUBBLE_SIZE)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .dropShadow(CircleShape, Shadow(radius = 6.dp, offset = DpOffset(0.dp, 2.dp), alpha = 0.3f))
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BubbleIconContent(
            icon = icon,
            size = BUBBLE_SIZE,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun BoxScope.StatusBadge(icon: ImageVector, containerColor: Color, contentColor: Color) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .size(20.dp)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            .padding(2.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(10.dp))
    }
}

/**
 * 展开后的面板：看进度、审批工具、停止生成和快速回复。
 * [widthFraction] 是面板宽度占屏幕宽度的比例；[maxHeight] 是可用满高，乘 [heightFraction] 之后才是面板上限；
 * [previewMaxHeight] 是预览区能拖到的最大高度。
 * 面板下方的手柄左右拖改宽度、上下拖改预览区高度，松手才通过 [onResize] 写回设置。
 */
@Composable
fun ChatFloatingPanel(
    state: FloatingChatUiState,
    widthFraction: Float,
    heightFraction: Float,
    maxHeight: Dp,
    previewMaxHeight: Dp,
    onResize: (widthFraction: Float, heightFraction: Float, previewHeightDp: Int) -> Unit,
    /** 拖动中实时通知窗口层改宽度，不然面板会被窗口边缘裁掉 */
    onWidthChanged: (Float) -> Unit,
    /** 输入框按下/失焦时通知窗口层切换可聚焦（弹键盘用） */
    onReplyFocusChanged: (Boolean) -> Unit,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit,
    onOpenConversation: () -> Unit,
    onToolApproval: (toolCallId: String, approved: Boolean) -> Unit,
    onStop: () -> Unit,
    onSend: (String) -> Unit,
    onResumeQueue: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthDp = configuration.screenWidthDp.toFloat()
    // 拖动时先改本地，松手才落盘；否则每一帧都要写一次 DataStore
    var width by remember(state.conversationId) {
        mutableFloatStateOf(FloatingPanelSizing.clampWidthRatio(widthFraction))
    }
    var height by remember(state.conversationId) {
        mutableFloatStateOf(FloatingPanelSizing.clampHeightRatio(heightFraction))
    }
    // 垂直拖动改的是预览区高度：改最大高度比例时面板常常还没顶到上限，拖起来看不出变化
    var previewHeight by remember(state.conversationId) { mutableFloatStateOf(previewMaxHeight.value) }
    // 设置页里的滑块改了要把新值同步进来
    LaunchedEffect(widthFraction) { width = FloatingPanelSizing.clampWidthRatio(widthFraction) }
    LaunchedEffect(heightFraction) { height = FloatingPanelSizing.clampHeightRatio(heightFraction) }
    LaunchedEffect(previewMaxHeight) { previewHeight = previewMaxHeight.value }

    // 面板上限 = 可用满高 × 高度比例。预览区拖多高都行，超出面板的那部分由中间的
    // weight + verticalScroll 吸收，标题栏和回复栏不会被挤出去
    val panelMaxHeight = maxHeight * height

    // 窗口宽度已经收窄到面板本身，这里的 fillMaxWidth 不会超出可见区域
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        // 手柄放在面板下方而不是叠在角落里：叠着会盖住回复栏的发送按钮
        Column(
            modifier = Modifier
                .width((screenWidthDp * width).dp)
                .heightIn(max = panelMaxHeight)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // weight 让 Surface 在固定的手柄之后量：不然内容顶满时手柄会被挤成 0 高，既看不见也拖不动
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PanelHeader(
                        state = state,
                        onOpenConversation = onOpenConversation,
                        onCollapse = onCollapse,
                        onDismiss = onDismiss,
                    )
                    // 多个审批、横屏或大字体时放不下，中间这段滚动，标题和输入栏始终可见
                    if (state.preview.isNotEmpty() || state.pendingTools.isNotEmpty() || state.queuedCount > 0) {
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (state.preview.isNotEmpty()) {
                                MessagePreview(
                                    text = state.preview,
                                    isReasoning = state.previewIsReasoning,
                                    generating = state.generating,
                                    maxHeight = previewHeight.dp,
                                )
                            }
                            state.pendingTools.forEach { tool ->
                                PendingToolCard(
                                    tool = tool,
                                    onApproval = { approved -> onToolApproval(tool.toolCallId, approved) },
                                    onOpenConversation = onOpenConversation,
                                )
                            }
                            if (state.queuedCount > 0) {
                                QueueRow(
                                    count = state.queuedCount,
                                    paused = state.queuePaused,
                                    onResume = onResumeQueue,
                                )
                            }
                        }
                    }
                    ReplyBar(
                        conversationId = state.conversationId,
                        generating = state.generating,
                        onSend = onSend,
                        onStop = onStop,
                        onReplyFocusChanged = onReplyFocusChanged,
                    )
                }
            }
            ResizeHandle(
                modifier = Modifier.padding(top = 6.dp),
                onDragFinished = { onResize(width, height, previewHeight.toInt()) },
                onDrag = { delta ->
                    // 面板居中，宽度涨 Δ 时右边缘只走 Δ/2：拖动量乘 2，手柄才跟着手指走
                    width = FloatingPanelSizing.draggedRatio(
                        current = width,
                        deltaDp = delta.x * 2f / density.density,
                        extentDp = screenWidthDp,
                        min = FloatingPanelSizing.MIN_WIDTH_RATIO,
                        max = FloatingPanelSizing.MAX_WIDTH_RATIO,
                    )
                    onWidthChanged(width)
                    previewHeight = FloatingPanelSizing.draggedPreviewHeight(
                        currentDp = previewHeight,
                        deltaDp = delta.y / density.density,
                    )
                },
            )
        }
    }
}

/** 面板下方的拖拽手柄：左右改宽度，上下改预览区高度，松手才写回设置 */
@Composable
private fun ResizeHandle(
    modifier: Modifier,
    onDrag: (Offset) -> Unit,
    onDragFinished: () -> Unit,
) {
    // 手柄在 Box 的点击区之外还要参与布局，闭包里拿到的必须是最新的回调
    val latestOnDrag by rememberUpdatedState(onDrag)
    val latestOnDragFinished by rememberUpdatedState(onDragFinished)
    val colors = MaterialTheme.colorScheme
    val resizeLabel = stringResource(R.string.floating_panel_resize)
    // 可见的圆保持 34dp，外面套 48dp 接手势：34dp 低于最小触控尺寸，手指不好按
    Box(
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = resizeLabel }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { latestOnDragFinished() },
                    onDragCancel = { latestOnDragFinished() },
                ) { change, dragAmount ->
                    change.consume()
                    latestOnDrag(dragAmount)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest.copy(alpha = 0.92f)),
        ) {
            // 三道斜线，不依赖图标库里有没有现成的拖拽手柄
            Canvas(modifier = Modifier.fillMaxSize().padding(9.dp)) {
                val color = colors.onSurfaceVariant
                repeat(3) { index ->
                    val gap = (index + 1) * 4.dp.toPx()
                    drawLine(
                        color = color,
                        start = Offset(size.width - gap, size.height),
                        end = Offset(size.width, size.height - gap),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(
    state: FloatingChatUiState,
    onOpenConversation: () -> Unit,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.generating) {
            ContainedLoadingIndicator(modifier = Modifier.size(40.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (state.failed) colors.errorContainer else colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = state.statusIcon(),
                    contentDescription = null,
                    tint = if (state.failed) colors.onErrorContainer else colors.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
                style = MaterialTheme.typography.titleSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.statusText(),
                style = MaterialTheme.typography.labelMedium,
                color = if (state.failed) colors.error else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpenConversation) {
            Icon(HugeIcons.Fullscreen, contentDescription = stringResource(R.string.floating_chat_open_conversation))
        }
        IconButton(onClick = onCollapse) {
            Icon(HugeIcons.Remove01, contentDescription = stringResource(R.string.floating_chat_collapse))
        }
        IconButton(onClick = onDismiss) {
            Icon(HugeIcons.Cancel01, contentDescription = stringResource(R.string.floating_chat_close))
        }
    }
}

// 用不带应用功能的 MaterialMarkdown：应用里的 MarkdownBlock 依赖导航、Activity 等悬浮窗里没有的东西
@Composable
private fun MessagePreview(text: String, isReasoning: Boolean, generating: Boolean, maxHeight: Dp) {
    val scrollState = rememberScrollState()
    // 生成中跟随最新内容。Markdown 在后台解析，内容变高晚于文本变化，按可滚动距离跟随
    LaunchedEffect(generating) {
        if (generating) snapshotFlow { scrollState.maxValue }.collect { scrollState.scrollTo(it) }
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = if (isReasoning) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        MaterialMarkdown(
            content = text,
            style = MaterialTheme.typography.bodyMedium,
            streaming = generating,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(scrollState)
                .padding(12.dp),
        )
    }
}

@Composable
private fun PendingToolCard(
    tool: UIMessagePart.Tool,
    onApproval: (approved: Boolean) -> Unit,
    onOpenConversation: () -> Unit,
) {
    val isAskUser = tool.toolName == ASK_USER_TOOL_NAME
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isAskUser) {
                    stringResource(R.string.floating_chat_ask_user)
                } else {
                    stringResource(R.string.floating_chat_tool_request, tool.toolName.substringAfterLast("__"))
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (!isAskUser && tool.input.isNotBlank()) {
                Text(
                    text = tool.input.take(300),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (isAskUser) {
                    // 问答有多个问题和选项，在应用里回答
                    Button(onClick = onOpenConversation) { Text(stringResource(R.string.floating_chat_answer)) }
                } else {
                    OutlinedButton(onClick = { onApproval(false) }) { Text(stringResource(R.string.chat_message_tool_deny)) }
                    Button(onClick = { onApproval(true) }) { Text(stringResource(R.string.chat_message_tool_approve)) }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(count: Int, paused: Boolean, onResume: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(
                if (paused) R.string.chat_page_queue_paused_count else R.string.chat_page_queue_pending_count,
                count,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (paused) {
            TextButton(onClick = onResume) { Text(stringResource(R.string.chat_page_queue_resume)) }
        }
    }
}

@Composable
private fun ReplyBar(
    conversationId: Uuid,
    generating: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onReplyFocusChanged: (Boolean) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var text by rememberSaveable(conversationId) { mutableStateOf("") }

    fun send() {
        val content = text.trim()
        if (content.isEmpty()) return
        onSend(content)
        text = ""
        focusManager.clearFocus()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                // 窗口平时不可聚焦，手指按下时先临时放开，键盘才有办法弹出来
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        onReplyFocusChanged(true)
                    }
                }
                .onFocusChanged { onReplyFocusChanged(it.isFocused) },
            // 生成中发送的消息会排队，等这一轮结束后再发
            placeholder = {
                Text(
                    stringResource(
                        if (generating) R.string.floating_chat_reply_queued_placeholder else R.string.floating_chat_reply_placeholder
                    )
                )
            },
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
        )
        if (generating) {
            FilledTonalIconButton(onClick = onStop) {
                Icon(HugeIcons.Stop, contentDescription = stringResource(R.string.stop))
            }
        }
        FilledIconButton(onClick = ::send, enabled = text.isNotBlank()) {
            Icon(HugeIcons.ArrowUp02, contentDescription = stringResource(R.string.send))
        }
    }
}

private fun FloatingChatUiState.statusIcon(): ImageVector = when {
    error != null -> HugeIcons.Alert01
    pendingTools.isNotEmpty() -> HugeIcons.Wrench01
    hasReply -> HugeIcons.Tick01
    else -> HugeIcons.Sparkles
}

@Composable
private fun FloatingChatUiState.statusText(): String {
    if (!generating) {
        return when {
            error != null -> error.title ?: error.error.message ?: stringResource(R.string.floating_chat_status_failed)
            pendingTools.any { it.toolName == ASK_USER_TOOL_NAME } -> stringResource(R.string.floating_chat_status_awaiting_answer)
            pendingTools.isNotEmpty() -> stringResource(R.string.floating_chat_status_awaiting_approval)
            hasReply -> stringResource(R.string.floating_chat_status_done)
            else -> stringResource(R.string.floating_chat_status_idle)
        }
    }
    // 网络重试等提示优先
    processingStatus?.let { return it }
    return when (phase) {
        is GenerationPhase.RunningTool -> stringResource(R.string.notification_live_update_tool, phase.toolName)
        is GenerationPhase.Thinking -> stringResource(R.string.notification_live_update_thinking)
        is GenerationPhase.Writing -> stringResource(R.string.notification_live_update_writing)
        GenerationPhase.Waiting -> stringResource(R.string.notification_live_update_title)
    }
}
