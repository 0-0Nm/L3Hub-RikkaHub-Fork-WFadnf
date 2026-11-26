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
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
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
import me.rerere.rikkahub.data.datastore.FloatingBubbleAnimation
import me.rerere.rikkahub.data.model.FloatingBubbleIcon
import me.rerere.rikkahub.data.model.FloatingCounterPalette
import me.rerere.rikkahub.data.ai.tools.local.PHONE_USE_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.local.phoneUseActionOf
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
fun ChatFloatingBubble(
    state: FloatingChatUiState,
    icon: FloatingBubbleIcon,
    onClick: () -> Unit,
    /** 整体（球+角标）缩放；只缩视觉，触控区由窗口层保证独立可用 */
    scale: Float = 1f,
    /** 外壳动画：花形（旋转+花瓣形变）或圆形呼吸，与图案解耦 */
    animation: FloatingBubbleAnimation = FloatingBubbleAnimation.FLOWER,
    /** 贴物理边缘时把角标镜像到贴边一侧；false = 贴边反侧，true = 贴边同侧 */
    mirrorBadges: Boolean = false,
    /** 主完成角标（对勾）：null = 本根没有子任务范围，沿用旧角标逻辑 */
    mainBadgeVisible: Boolean? = null,
    /** 子任务数字角标：null = 本根没有子任务范围 */
    childBadge: ChildBadgeUi? = null,
    counterFontPath: String = "",
    counterOffsetX: Float = 0f,
    counterOffsetY: Float = 0f,
    /** The live-bubble window is clipping the number, so a non-touchable companion draws it. */
    counterInOverlay: Boolean = false,
    counterPalette: FloatingCounterPalette = FloatingCounterPalette.Default,
) {
    val colors = MaterialTheme.colorScheme
    // 收起到面板时窗口隐藏，帧还在跑，停下动画
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animating = state.generating && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    // All three visual elements live in one scaled layer. Touch target stays usable.
    Box(modifier = Modifier.absolutePadding(
        left = if (mirrorBadges) {
            FloatingCounterGeometry.HOST_TRAILING_PADDING_DP.dp
        } else {
            FloatingCounterGeometry.HOST_LEADING_PADDING_DP.dp
        },
        right = if (mirrorBadges) {
            FloatingCounterGeometry.HOST_LEADING_PADDING_DP.dp
        } else {
            FloatingCounterGeometry.HOST_TRAILING_PADDING_DP.dp
        },
        top = FloatingCounterGeometry.HOST_VERTICAL_PADDING_DP.dp,
        bottom = FloatingCounterGeometry.HOST_VERTICAL_PADDING_DP.dp,
    )) {
        BubbleShell(
            animation = animation,
            animating = animating,
            scale = scale,
            onClick = onClick,
            overlays = {
                when {
                    state.error != null && !state.generating ->
                        StatusBadge(HugeIcons.Alert01, colors.error, colors.onError, mirror = mirrorBadges)
                    state.pendingTools.isNotEmpty() && !state.generating ->
                        StatusBadge(HugeIcons.Wrench01, colors.error, colors.onError, mirror = mirrorBadges)
                    else -> TaskBadges(
                        mainVisible = !state.generating &&
                            (mainBadgeVisible ?: state.hasReply),
                        child = childBadge,
                        scale = 1f,
                        mirror = mirrorBadges,
                        counterFontPath = counterFontPath,
                        counterOffsetX = counterOffsetX,
                        counterOffsetY = counterOffsetY,
                        counterPalette = counterPalette,
                        drawCounter = !counterInOverlay,
                    )
                }
            },
        ) { shellShape ->
            if (icon is FloatingBubbleIcon.AppLogo) LogoBubbleContent(shellShape)
            else BubbleIconPattern(shellShape, icon)
        }
    }
}

/** 子任务数字角标的渲染输入；颜色/计数都来自 SubAgentBadgeState.Snapshot 同一份快照 */
data class ChildBadgeUi(
    val count: Int,
    val green: Boolean,
)

/** 详情文字缩放只乘字号/行高；字重、触控区、图标一律不动 */
private fun TextStyle.scaledBy(scale: Float): TextStyle =
    if (scale == 1f) this else copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)

/**
 * 悬浮球外壳：图案与外壳解耦（E335）。
 *
 * 外壳负责动画与触控：
 * - [FloatingBubbleAnimation.FLOWER] 复用 LogoBubble 的花形算法：旋转 + 花瓣形变（同一组参数）；
 * - [FloatingBubbleAnimation.PULSE] 复用 BubbleWithIcon 的呼吸缩放。
 * 任意图案（logo / 内置 / Emoji / 图片）都可以配任一动画：logo 在花形里按原样画在变形轮廓上，
 * 在呼吸里画进圆形；其他图案在花形里画进花瓣形容器，在呼吸里画进圆形容器。
 * 触控区固定 52dp 不随缩放（点击仍由这一层接），视觉整体按 [scale] 缩放。
 */
@Composable
private fun BubbleShell(
    animation: FloatingBubbleAnimation,
    animating: Boolean,
    scale: Float,
    onClick: () -> Unit,
    overlays: @Composable BoxScope.() -> Unit = {},
    content: @Composable (Shape) -> Unit,
) {
    val flowerShape = remember { Animatable(0f) }
    val flowerSpin = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(animating, animation) {
        if (animation == FloatingBubbleAnimation.FLOWER) {
            pulse.snapTo(1f)
            if (!animating) {
                // 停在一个完整的形状上
                flowerShape.animateTo(ceil(flowerShape.value), MorphSpring)
                return@LaunchedEffect
            }
            launch {
                flowerSpin.animateTo(
                    targetValue = flowerSpin.value + 360f,
                    animationSpec = infiniteRepeatable(tween(SPIN_DURATION_MILLIS, easing = LinearEasing)),
                )
            }
            while (true) {
                val morph = launch { flowerShape.animateTo(floor(flowerShape.value) + 1f, MorphSpring) }
                delay(MORPH_INTERVAL_MILLIS)
                morph.join()
            }
        } else {
            // 从花形切过来时先停回完整形状，再走圆形呼吸
            flowerShape.animateTo(ceil(flowerShape.value), MorphSpring)
            if (!animating) {
                pulse.animateTo(1f, tween(BUBBLE_SETTLE_MILLIS))
                return@LaunchedEffect
            }
            while (true) {
                pulse.animateTo(BUBBLE_PULSE_MIN, tween(BUBBLE_PULSE_MILLIS, easing = FastOutSlowInEasing))
                pulse.animateTo(1f, tween(BUBBLE_PULSE_MILLIS, easing = FastOutSlowInEasing))
            }
        }
    }
    val shellShape = if (animation == FloatingBubbleAnimation.FLOWER) {
        logoMorphShape(flowerShape.value, extraRotation = flowerSpin.value)
    } else {
        CircleShape
    }
    Box(
        modifier = Modifier
            .size(BUBBLE_SIZE)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 视觉层单独缩放：触控区保持 52dp，缩到 30% 也按得住
        Box(
            modifier = Modifier
                .size(BUBBLE_SIZE)
                .graphicsLayer {
                    val visual = scale * if (animation == FloatingBubbleAnimation.PULSE) pulse.value else 1f
                    scaleX = visual
                    scaleY = visual
                    // The counter may intentionally leave the ball; do not add a Compose clip here.
                    // The system overlay can still clip at its own window/screen bounds.
                    clip = false
                },
            contentAlignment = Alignment.Center,
        ) {
            content(shellShape)
            overlays()
        }
    }
}

/** logo 图案：画在传入的外壳形状上（花形=变形轮廓，呼吸=圆形），不加背景、不接手势 */
@Composable
private fun LogoBubbleContent(logoShape: Shape) {
    val logoLayers = rememberAppLogoLayers()
    Box(
        modifier = Modifier
            .size(BUBBLE_SIZE)
            // 不能用 elevation 阴影：轮廓是凹多边形，每帧都要重新三角剖分，变形时会把 RenderThread 卡住几十秒
            .dropShadow(logoShape, Shadow(radius = 6.dp, offset = DpOffset(0.dp, 2.dp), alpha = 0.3f))
            .clip(logoShape)
            .drawAppLogo(logoLayers)
    )
}

/** logo 以外的图案：画进传入的外壳形状的容器里，不加手势 */
@Composable
private fun BubbleIconPattern(logoShape: Shape, icon: FloatingBubbleIcon) {
    Box(
        modifier = Modifier
            .size(BUBBLE_SIZE)
            .dropShadow(logoShape, Shadow(radius = 6.dp, offset = DpOffset(0.dp, 2.dp), alpha = 0.3f))
            .clip(logoShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
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
private fun BoxScope.StatusBadge(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    scale: Float = 1f,
    mirror: Boolean = false,
) {
    Box(
        modifier = Modifier
            .align(if (mirror) AbsoluteAlignment.TopLeft else AbsoluteAlignment.TopRight)
            .size(20.dp * scale)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            .padding(2.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(10.dp * scale))
    }
}

/**
 * 主/子任务角标（E335 需求 4/5）：主对勾 + 子数字，直径分别为球的 D/4 和 D/8，与球同一比例缩放，
 * 不设视觉最小值破坏比例。左右贴边时整体镜像到贴边一侧，子标放在主标同侧下方。
 * 角标只画不接手势：不拦球的点击。
 */
@Composable
private fun BoxScope.TaskBadges(
    mainVisible: Boolean,
    child: ChildBadgeUi?,
    scale: Float,
    mirror: Boolean,
    counterFontPath: String = "",
    counterOffsetX: Float = 0f,
    counterOffsetY: Float = 0f,
    counterPalette: FloatingCounterPalette,
    drawCounter: Boolean = true,
) {
    val align = if (mirror) AbsoluteAlignment.TopLeft else AbsoluteAlignment.TopRight
    val crossAlign = if (mirror) AbsoluteAlignment.Left else AbsoluteAlignment.Right
    Column(
        modifier = Modifier.align(align),
        horizontalAlignment = crossAlign,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (mainVisible) MainDoneBadge(scale = scale)
        else if (child != null) Box(Modifier.size(BUBBLE_SIZE / 4 * scale))
        if (child != null) ChildStatusBadge(green = child.green, scale = scale)
    }
    if (child != null && drawCounter) {
        val displacement = FloatingCounterGeometry.displacement(
            horizontal = counterOffsetX,
            vertical = counterOffsetY,
            mirror = mirror,
            bubbleSize = BUBBLE_SIZE.value,
        )
        ArcTaskCount(
            count = child.count,
            palette = counterPalette,
            fontPath = counterFontPath,
            modifier = Modifier
                // Keep the legacy alignment/base offset so 0/0 is pixel-identical at every density.
                // BubbleShell scales the displacement with the ball; do not apply bubbleScale twice.
                .align(if (mirror) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft)
                .absoluteOffset(
                    x = if (mirror) {
                        BUBBLE_SIZE / 4 * scale + displacement.x.dp
                    } else {
                        -BUBBLE_SIZE / 4 * scale + displacement.x.dp
                    },
                    y = displacement.y.dp,
                )
                .size(width = BUBBLE_SIZE / 2 * scale, height = BUBBLE_SIZE / 4 * scale),
        )
    }
}

/** 主完成角标：根主任务及其任务范围全部结束才显示的对勾（D/4） */
@Composable
private fun MainDoneBadge(scale: Float) {
    val size = BUBBLE_SIZE / 4 * scale
    Box(
        modifier = Modifier
            .size(size)
            .border(size / 8, MaterialTheme.colorScheme.surface, CircleShape)
            .padding(size / 10)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            HugeIcons.Tick01,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(size / 2),
        )
    }
}

/** Small child status circle; only the opposite-side number carries the count. */
@Composable
private fun ChildStatusBadge(green: Boolean, scale: Float) {
    val diameter = BUBBLE_SIZE / 8 * scale
    Box(
        Modifier.size(diameter)
            .border(diameter / 8, MaterialTheme.colorScheme.surface, CircleShape)
            .clip(CircleShape)
            .background(if (green) SubAgentBadgeGreen else SubAgentBadgeOrange)
    )
}

/** Counter font affects only this number; malformed or removed fonts fall back to serif. */
@Composable
private fun ArcTaskCount(
    count: Int,
    fontPath: String,
    palette: FloatingCounterPalette,
    modifier: Modifier = Modifier,
) {
    val fillArgb = remember(count, palette) { FloatingCounterVisuals.colorArgb(count, palette) }
    val context = LocalContext.current
    val typeface = remember(fontPath, context.filesDir) {
        loadFloatingCounterTypeface(context, fontPath)
    }
    val label = count.coerceAtLeast(0).toString()
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true } }
    val glyphBounds = remember { android.graphics.Rect() }
    Canvas(modifier.semantics { contentDescription = label }) {
        drawIntoCanvas { canvas ->
            drawFloatingCounter(
                canvas = canvas.nativeCanvas,
                width = size.width,
                height = size.height,
                count = count,
                fillArgb = fillArgb,
                typeface = typeface,
                paint = paint,
                bounds = glyphBounds,
            )
        }
    }
}

// 参考色仅 FF9800 橙 / 43A047 绿
private val SubAgentBadgeOrange = Color(0xFFFF9800)
private val SubAgentBadgeGreen = Color(0xFF43A047)

/**
 * 展开后的面板：看进度、审批工具、停止生成和快速回复。
 * [widthFraction] 是面板宽度占屏幕宽度的比例；[maxHeight] 是可用满高，乘 [heightFraction] 之后才是面板上限；
 * [previewMaxHeight] 是预览区能拖到的最大高度。
 * 面板下方的手柄左右拖改宽度（只动这一条边）、上下拖改预览区高度，松手才通过 [onResize] 写回设置。
 */
@Composable
fun ChatFloatingPanel(
    state: FloatingChatUiState,
    widthFraction: Float,
    heightFraction: Float,
    maxHeight: Dp,
    previewMaxHeight: Dp,
    /** 详情文字缩放（E335 需求 3）：只作用在标题/状态/预览正文/工具卡/队列文案/输入占位，不碰按钮图标与触控区 */
    detailTextScale: Float = 1f,
    onResize: (widthFraction: Float, heightFraction: Float, previewHeightDp: Int) -> Unit,
    /** 拖动中实时通知窗口层改宽度；窗口只换宽度、不动位置，所以另一条边钉在原地 */
    onWidthChanged: (Float) -> Unit,
    /** 输入框按下/失焦时通知窗口层切换可聚焦（弹键盘用） */
    onReplyFocusChanged: (Boolean) -> Unit,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit,
    onOpenConversation: () -> Unit,
    onToolApproval: (toolCallId: String, approved: Boolean) -> Unit,
    onStop: () -> Unit,
    /** 返回 false 表示没被受理（关闭消息队列后又碰上等审批、压缩上下文），输入框内容要留着 */
    onSend: (String) -> Boolean,
    onResumeQueue: () -> Unit,
    // 消息队列开关：只影响「本轮结束后发送」的占位文字；已经压着的旧消息照常显示
    queueEnabled: Boolean = true,
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

    // 手柄的拖动位移只能按窗口坐标算：手柄自己跟着手指走（面板变宽、预览变高），
    // 用节点局部坐标量出来的位移会互相抵消，拖到第二帧就不动了。
    // 外框铺满窗口、原点不动，把起手判定和拖动都挂在外框上，坐标就是窗口坐标。
    val handleGeometry = remember { PanelHandleGeometry() }
    val latestOnWidthChanged by rememberUpdatedState(onWidthChanged)
    val latestOnResize by rememberUpdatedState(onResize)

    fun dragHandleBy(delta: Offset) {
        // 手柄在面板的物理右侧、窗口钉住物理左边缘：横向走了多少宽度就变多少。
        // 窗口那条边不动，所以位移不会被窗口自己的移动吃掉；也不会像以前那样
        // 两条边一起从中轴线往两边长
        width = FloatingPanelSizing.draggedPanelWidthRatio(
            current = width,
            deltaDp = delta.x / density.density,
            extentDp = screenWidthDp,
        )
        latestOnWidthChanged(width)
        previewHeight = FloatingPanelSizing.draggedPreviewHeight(
            currentDp = previewHeight,
            deltaDp = delta.y / density.density,
        )
    }

    fun finishHandleDrag() = latestOnResize(width, height, previewHeight.toInt())

    // 窗口宽度已经收窄到面板本身，这里的 fillMaxWidth 不会超出可见区域
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { handleGeometry.boxOrigin = it.positionInWindow() }
            // 这些值都是按值捕获进手势协程的：换会话、转屏、改字体时都要重启
            .pointerInput(state.conversationId, screenWidthDp, density.density) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 手势坐标是外框局部坐标，加上外框在窗口里的原点才是窗口坐标，
                    // 才能和手柄那份窗口矩形比。起手点不在手柄上就原样放过去：
                    // 标题栏按钮、预览区滚动都还要用它
                    val downInWindow = down.position + handleGeometry.boxOrigin
                    if (!handleGeometry.handle.contains(downInWindow)) return@awaitEachGesture
                    var last = down.position
                    var dragged = false
                    drag(down.id) { change ->
                        val delta = change.position - last
                        last = change.position
                        if (delta != Offset.Zero) {
                            dragged = true
                            change.consume()
                            dragHandleBy(delta)
                        }
                    }
                    // 只点了一下手柄、没真拖动时不用写一次设置
                    if (dragged) finishHandleDrag()
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
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
                        detailTextScale = detailTextScale,
                        onOpenConversation = onOpenConversation,
                        onCollapse = onCollapse,
                        onDismiss = onDismiss,
                    )
                    // 多个审批、横屏或大字体时放不下，中间这段滚动，标题和输入栏始终可见
                    // 队列开关只决定新消息收不收；已经压着的旧消息仍要显示（暂停时还要能继续）
                    val showQueue = state.queuedCount > 0
                    if (state.preview.isNotEmpty() || state.pendingTools.isNotEmpty() || showQueue) {
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
                                    detailTextScale = detailTextScale,
                                )
                            }
                            state.pendingTools.forEach { tool ->
                                PendingToolCard(
                                    tool = tool,
                                    detailTextScale = detailTextScale,
                                    onApproval = { approved -> onToolApproval(tool.toolCallId, approved) },
                                    onOpenConversation = onOpenConversation,
                                )
                            }
                            if (showQueue) {
                                QueueRow(
                                    count = state.queuedCount,
                                    paused = state.queuePaused,
                                    detailTextScale = detailTextScale,
                                    onResume = onResumeQueue,
                                )
                            }
                        }
                    }
                    ReplyBar(
                        conversationId = state.conversationId,
                        generating = state.generating,
                        queueEnabled = queueEnabled,
                        detailTextScale = detailTextScale,
                        onSend = onSend,
                        onStop = onStop,
                        onReplyFocusChanged = onReplyFocusChanged,
                    )
                }
            }
            // 手柄固定在面板的物理右下角。窗口锚点钉的是物理左边缘，拖右边这一侧才不会
            // 因为窗口自己也在跟着动而把位移抵消掉（RTL 下会变成只长一半）。
            // 这里的 LTR 只用来把 Row 的子项摆到物理右边，手柄本身没有文字，不受方向影响
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PanelResizeHandle(modifier = Modifier.padding(top = 6.dp), geometry = handleGeometry)
                }
            }
        }
    }
}

/**
 * 手柄和外框在窗口里的位置。手势挂在外框上，比之前先把起手点换算到窗口坐标。
 *
 * 默认值给一个空矩形：首帧还没量出来时正好按在左上角，不能被当成“按住手柄”。
 */
private class PanelHandleGeometry {
    var handle: Rect = Rect(0f, 0f, -1f, -1f)
    var boxOrigin: Offset = Offset.Zero
}

/** 面板下方的拖拽手柄：左右改宽度，上下改预览区高度，松手才写回设置。这里只负责画，手势由面板外框接 */
@Composable
private fun PanelResizeHandle(modifier: Modifier, geometry: PanelHandleGeometry) {
    val colors = MaterialTheme.colorScheme
    val resizeLabel = stringResource(R.string.floating_panel_resize)
    // 可见的圆保持 34dp，外面套 48dp 接手势：34dp 低于最小触控尺寸，手指不好按
    Box(
        modifier = modifier
            .size(48.dp)
            .onGloballyPositioned { coordinates ->
                val origin = coordinates.positionInWindow()
                val size = coordinates.size
                geometry.handle = Rect(
                    origin.x,
                    origin.y,
                    origin.x + size.width.toFloat(),
                    origin.y + size.height.toFloat(),
                )
            }
            .semantics { contentDescription = resizeLabel },
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
    detailTextScale: Float,
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
                style = MaterialTheme.typography.titleSmallEmphasized.scaledBy(detailTextScale),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.statusText(),
                style = MaterialTheme.typography.labelMedium.scaledBy(detailTextScale),
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
private fun MessagePreview(
    text: String,
    isReasoning: Boolean,
    generating: Boolean,
    maxHeight: Dp,
    detailTextScale: Float,
) {
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
            // 显式字号随正文一起缩；Markdown 主题里的 em 相对字号按合并后的 style 推导
            style = MaterialTheme.typography.bodyMedium.scaledBy(detailTextScale),
            explicitTextScale = detailTextScale,
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
    detailTextScale: Float,
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
                style = MaterialTheme.typography.labelLarge.scaledBy(detailTextScale),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (!isAskUser && tool.input.isNotBlank()) {
                Text(
                    text = tool.input.take(300),
                    style = MaterialTheme.typography.bodySmall.scaledBy(detailTextScale),
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
private fun QueueRow(
    count: Int,
    paused: Boolean,
    detailTextScale: Float,
    onResume: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(
                if (paused) R.string.chat_page_queue_paused_count else R.string.chat_page_queue_pending_count,
                count,
            ),
            style = MaterialTheme.typography.labelMedium.scaledBy(detailTextScale),
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
    queueEnabled: Boolean,
    detailTextScale: Float,
    onSend: (String) -> Boolean,
    onStop: () -> Unit,
    onReplyFocusChanged: (Boolean) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var text by rememberSaveable(conversationId) { mutableStateOf("") }

    fun send() {
        val content = text.trim()
        if (content.isEmpty()) return
        // 被拒绝（关闭队列后正在压缩上下文）时留着原文，别让用户重打
        if (!onSend(content)) return
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
            textStyle = MaterialTheme.typography.bodyLarge.scaledBy(detailTextScale),
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
            // 生成中发送的消息会排队，等这一轮结束后再发；队列关掉时就是打断重发
            placeholder = {
                Text(
                    stringResource(
                        if (generating && queueEnabled) {
                            R.string.floating_chat_reply_queued_placeholder
                        } else {
                            R.string.floating_chat_reply_placeholder
                        }
                    ),
                    // 占位文字跟详情比例缩；输入框本体和发送按钮不动
                    style = LocalTextStyle.current.scaledBy(detailTextScale),
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
        is GenerationPhase.RunningTool -> stringResource(R.string.notification_live_update_tool, phase.displayName())
        is GenerationPhase.Thinking -> stringResource(R.string.notification_live_update_thinking)
        is GenerationPhase.Writing -> stringResource(R.string.notification_live_update_writing)
        GenerationPhase.Waiting -> stringResource(R.string.notification_live_update_title)
    }
}

// Phone Use 一轮要调用很多次同一个工具，带上动作名才看得出进行到哪一步
private fun GenerationPhase.RunningTool.displayName(): String {
    if (toolName != PHONE_USE_TOOL_NAME) return toolName
    return phoneUseActionOf(input)?.let { "$toolName · $it" } ?: toolName
}

/**
 * 悬浮球预览（设置页用）：同一套外壳/图案组件，无任务副作用。
 * [animating] = true 时模拟运行中（花形持续形变/呼吸持续脉动），预览里不会出现完成角标；
 * 左右贴边（[mirrorBadges]）由真实悬浮窗层传入，预览里也可显式传来看看镜像效果。
 */
@Composable
fun FloatingBubblePreview(
    icon: FloatingBubbleIcon,
    scale: Float,
    animation: FloatingBubbleAnimation,
    animating: Boolean = false,
    mirrorBadges: Boolean = false,
    counterFontPath: String = "",
    counterOffsetX: Float = 0f,
    counterOffsetY: Float = 0f,
    previewCount: Int = 12,
    counterPalette: FloatingCounterPalette = FloatingCounterPalette.Default,
) {
    Box(modifier = Modifier.absolutePadding(
        left = if (mirrorBadges) 10.dp else 25.dp,
        right = if (mirrorBadges) 25.dp else 10.dp,
        top = 10.dp,
        bottom = 10.dp,
    )) {
        BubbleShell(
            animation = animation,
            animating = animating,
            scale = scale,
            onClick = {},
            overlays = {
                TaskBadges(
                    mainVisible = true,
                    child = ChildBadgeUi(count = previewCount, green = true),
                    scale = 1f,
                    mirror = mirrorBadges,
                    counterFontPath = counterFontPath,
                    counterOffsetX = counterOffsetX,
                    counterOffsetY = counterOffsetY,
                    counterPalette = counterPalette,
                )
            },
        ) { logoShape ->
            if (icon is FloatingBubbleIcon.AppLogo) {
                LogoBubbleContent(logoShape = logoShape)
            } else {
                BubbleIconPattern(logoShape = logoShape, icon = icon)
            }
        }
    }
}
