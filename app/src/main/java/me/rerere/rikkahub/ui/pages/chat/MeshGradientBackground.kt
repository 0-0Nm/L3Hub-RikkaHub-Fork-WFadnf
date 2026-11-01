package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val MESH_FRAME_MILLIS = 50L
private const val TWO_PI = 2f * PI.toFloat()

/**
 * 四个光斑的相位。回绕点必须落在「自身一圈的整数倍」上，否则 cos(k·p) 在回绕处会跳一下：
 * p1 的 y 用 cos(p1·1.15)，所以 p1 要按 20 圈回绕；p3/p4 同理按 10 圈，p2 按 1 圈。
 *
 * [hue] 是色相专用的一圈慢时钟（31 秒），只取整数倍的频率，回绕处不会跳色。
 */
private data class MeshPhases(
    val p1: Float = 0f,
    val p2: Float = 0f,
    val p3: Float = 0f,
    val p4: Float = 0f,
    val hue: Float = 0f,
) {
    fun advance(elapsedMillis: Float): MeshPhases = MeshPhases(
        p1 = advancePhase(p1, elapsedMillis, 5_500f, TWO_PI * 20f),
        p2 = advancePhase(p2, elapsedMillis, 7_000f, TWO_PI),
        p3 = advancePhase(p3, elapsedMillis, 8_500f, TWO_PI * 10f),
        p4 = advancePhase(p4, elapsedMillis, 6_200f, TWO_PI * 10f),
        hue = advancePhase(hue, elapsedMillis, 31_000f, TWO_PI),
    )
}

private fun advancePhase(current: Float, elapsedMillis: Float, periodMillis: Float, wrap: Float): Float =
    (current + (elapsedMillis / periodMillis) * TWO_PI) % wrap

/**
 * 一个光斑的配色。
 *
 * 只让色相摆动，饱和度、明度和不透明度写死：漂移时亮度不变，深浅主题都不会忽明忽暗。
 * [hueOffset] 让几个光斑不同步地来回换色，避免整屏一起「呼吸」。
 */
private data class MeshBlob(
    val hue: Float,
    val hueOffset: Float,
    val hueSwing: Float,
    val saturation: Float,
    val value: Float,
    val alpha: Float,
)

private fun MeshBlob.color(huePhase: Float): Color = Color.hsv(
    hue = hue + hueSwing * sin(huePhase + hueOffset),
    saturation = saturation,
    value = value,
    alpha = alpha,
)

/**
 * Gemini 风格动态渐变背景，多色版本。
 *
 * 原理:
 *  1. 底层一个线性渐变(顶部偏靛紫/藕荷、底部收到近黑/纯白)。
 *  2. 上面叠几个 radialGradient 光斑(中心有色 → 边缘透明),天生就是柔的。
 *  3. 每个光斑用一个独立周期的漂移,沿正弦/余弦轨迹缓慢移动;色相另走一圈 31 秒的慢时钟。
 *
 * 不依赖 Modifier.blur,全 API 级别可用,性能也更好。
 */
@Composable
fun MeshGradientBackground(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    var phases by remember { mutableStateOf(MeshPhases()) }

    // 窗口不在前台时连定时器一起停掉：delay() 不受帧时钟约束，
    // 不这么写的话应用切后台仍会每 50ms 醒一次并触发一次 draw。
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 背景是几十秒周期的缓慢漂移，不需要跟屏幕刷新率同步：20fps 看不出差别，
    // 而生成中、滚动中、切后台时整段停下来，省掉的正是最缺主线程的那段时间。
    LaunchedEffect(animate, resumed) {
        if (!animate || !resumed) return@LaunchedEffect
        var lastNanos = System.nanoTime()
        while (true) {
            delay(MESH_FRAME_MILLIS)
            val now = System.nanoTime()
            val elapsedMillis = ((now - lastNanos) / 1_000_000L).coerceIn(1L, 500L)
            lastNanos = now
            phases = phases.advance(elapsedMillis.toFloat())
        }
    }

    val dark = LocalDarkMode.current
    val baseGradient = if (dark) {
        // 暗色：顶部一层很浅的靛紫，向下收到接近纯黑，给光斑留出对比
        arrayOf(
            0.0f to Color(0xFF191428),
            0.22f to Color(0xFF141020),
            0.45f to Color(0xFF0D0A15),
            0.65f to Color(0xFF09070E),
            1.0f to Color(0xFF07060A),
        )
    } else {
        // 亮色：顶部淡藕荷，向下渐隐到白
        arrayOf(
            0.0f to Color(0xFFEAE4F8),
            0.22f to Color(0xFFF4F0FC),
            0.45f to Color(0xFFFBF9FF),
            0.65f to Color(0xFFFFFFFF),
            1.0f to Color(0xFFFFFFFF),
        )
    }

    // 蓝 / 青 / 紫品红 / 橙琥珀 四色。暗色把明度压到 0.6 附近、alpha 不超过 0.44，
    // 叠在近黑的底上不会糊成一片；亮色明度给满但饱和度不超过 0.6，是淡彩不是荧光。
    val blobs = if (dark) {
        arrayOf(
            MeshBlob(hue = 214f, hueOffset = 0f, hueSwing = 16f, saturation = 0.58f, value = 0.64f, alpha = 0.44f),
            MeshBlob(hue = 176f, hueOffset = 2.1f, hueSwing = 14f, saturation = 0.52f, value = 0.58f, alpha = 0.34f),
            MeshBlob(hue = 286f, hueOffset = 4.0f, hueSwing = 18f, saturation = 0.55f, value = 0.60f, alpha = 0.38f),
            MeshBlob(hue = 32f, hueOffset = 1.2f, hueSwing = 12f, saturation = 0.62f, value = 0.66f, alpha = 0.22f),
        )
    } else {
        arrayOf(
            MeshBlob(hue = 210f, hueOffset = 0f, hueSwing = 14f, saturation = 0.52f, value = 1f, alpha = 0.55f),
            MeshBlob(hue = 172f, hueOffset = 2.1f, hueSwing = 12f, saturation = 0.44f, value = 1f, alpha = 0.45f),
            MeshBlob(hue = 288f, hueOffset = 4.0f, hueSwing = 16f, saturation = 0.40f, value = 1f, alpha = 0.45f),
            MeshBlob(hue = 34f, hueOffset = 1.2f, hueSwing = 10f, saturation = 0.60f, value = 1f, alpha = 0.34f),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colorStops = baseGradient)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // 在绘制阶段读取，相位变化只让 Canvas 重绘，不会重组整棵子树
            val phase = phases
            val p1 = phase.p1
            val p2 = phase.p2
            val p3 = phase.p3
            val p4 = phase.p4
            val hue = phase.hue
            val w = size.width
            val h = size.height
            // 光斑半径取屏幕较长边的比例,够大才柔
            val r = maxOf(w, h)

            // 光斑全部聚在顶部,向下渐隐,保留下半屏留白
            // 顶部蓝(主色,横向缓慢漂移)
            drawBlob(
                center = Offset(
                    w * 0.48f + sin(p1) * w * 0.38f,
                    h * 0.08f + cos(p1 * 1.15f) * h * 0.18f,
                ),
                radius = r * 0.36f,
                color = blobs[0].color(hue),
            )
            // 左上青绿点缀
            drawBlob(
                center = Offset(
                    w * 0.18f + sin(p2 + PI.toFloat() * 0.55f) * w * 0.30f,
                    h * 0.24f + cos(p2) * h * 0.20f,
                ),
                radius = r * 0.28f,
                color = blobs[1].color(hue),
            )
            // 右上紫 / 品红
            drawBlob(
                center = Offset(
                    w * 0.82f + sin(p3 + PI.toFloat() * 0.9f) * w * -0.34f,
                    h * 0.12f + cos(p3 * 0.9f) * h * 0.18f,
                ),
                radius = r * 0.30f,
                color = blobs[2].color(hue),
            )
            // 暖色光斑给运动提供更明显的色彩参照
            drawBlob(
                center = Offset(
                    w * 0.58f + sin(p4 + PI.toFloat() * 1.25f) * w * 0.28f,
                    h * 0.34f + cos(p4 * 1.1f) * h * 0.16f,
                ),
                radius = r * 0.26f,
                color = blobs[3].color(hue),
            )
        }

        content()
    }
}

/** 画一个柔光斑:中心有色,向外渐隐到透明。颜色自带 alpha,这里不再乘一遍。 */
private fun DrawScope.drawBlob(
    center: Offset,
    radius: Float,
    color: Color,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
