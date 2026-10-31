package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
 */
private data class MeshPhases(
    val p1: Float = 0f,
    val p2: Float = 0f,
    val p3: Float = 0f,
    val p4: Float = 0f,
) {
    fun advance(elapsedMillis: Float): MeshPhases = MeshPhases(
        p1 = advancePhase(p1, elapsedMillis, 5_500f, TWO_PI * 20f),
        p2 = advancePhase(p2, elapsedMillis, 7_000f, TWO_PI),
        p3 = advancePhase(p3, elapsedMillis, 8_500f, TWO_PI * 10f),
        p4 = advancePhase(p4, elapsedMillis, 6_200f, TWO_PI * 10f),
    )
}

private fun advancePhase(current: Float, elapsedMillis: Float, periodMillis: Float, wrap: Float): Float =
    (current + (elapsedMillis / periodMillis) * TWO_PI) % wrap

/**
 * Gemini 风格动态渐变背景。
 *
 * 原理:
 *  1. 底层一个线性渐变(顶部偏蓝、底部偏白)。
 *  2. 上面叠几个 radialGradient 光斑(中心有色 → 边缘透明),天生就是柔的。
 *  3. 每个光斑用一个独立周期的漂移,沿正弦/余弦轨迹缓慢移动。
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

    // 背景是几十秒周期的缓慢漂移，不需要跟屏幕刷新率同步：20fps 看不出差别，
    // 而生成中和滚动中整段停下来，省掉的正是最缺主线程的那段时间。
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
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
        // 暗色:顶部深蓝,向下渐隐到接近纯黑
        arrayOf(
            0.0f to Color(0xFF1B2A45),
            0.22f to Color(0xFF15223A),
            0.45f to Color(0xFF0D1626),
            0.65f to Color(0xFF0A0F18),
            1.0f to Color(0xFF080B12),
        )
    } else {
        // 亮色:顶部偏蓝,向下渐隐到白
        arrayOf(
            0.0f to Color(0xFFAFD0F2),
            0.22f to Color(0xFFCBE0F6),
            0.45f to Color(0xFFF1F7FD),
            0.65f to Color(0xFFFFFFFF),
            1.0f to Color(0xFFFFFFFF),
        )
    }

    // 光斑配色(蓝 / 青 / 淡蓝 / 暖色)及浓度,亮暗各一套
    val blobBlue = if (dark) Color(0xFF3E6FB0) else Color(0xFF9EC5F0)
    val blobTeal = if (dark) Color(0xFF2E7D74) else Color(0xFFA8E6E0)
    val blobLightBlue = if (dark) Color(0xFF4A6E96) else Color(0xFFB6D7F2)
    val blobWarm = if (dark) Color(0xFF7C5F9E) else Color(0xFFFFC8D2)
    val alphaBlue = if (dark) 0.56f else 0.72f
    val alphaTeal = if (dark) 0.44f else 0.56f
    val alphaLightBlue = if (dark) 0.48f else 0.62f
    val alphaWarm = if (dark) 0.32f else 0.42f

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
                color = blobBlue,
                centerAlpha = alphaBlue,
            )
            // 左上青绿点缀
            drawBlob(
                center = Offset(
                    w * 0.18f + sin(p2 + PI.toFloat() * 0.55f) * w * 0.30f,
                    h * 0.24f + cos(p2) * h * 0.20f,
                ),
                radius = r * 0.28f,
                color = blobTeal,
                centerAlpha = alphaTeal,
            )
            // 右上淡蓝
            drawBlob(
                center = Offset(
                    w * 0.82f + sin(p3 + PI.toFloat() * 0.9f) * w * -0.34f,
                    h * 0.12f + cos(p3 * 0.9f) * h * 0.18f,
                ),
                radius = r * 0.30f,
                color = blobLightBlue,
                centerAlpha = alphaLightBlue,
            )
            // 暖色光斑给运动提供更明显的色彩参照
            drawBlob(
                center = Offset(
                    w * 0.58f + sin(p4 + PI.toFloat() * 1.25f) * w * 0.28f,
                    h * 0.34f + cos(p4 * 1.1f) * h * 0.16f,
                ),
                radius = r * 0.26f,
                color = blobWarm,
                centerAlpha = alphaWarm,
            )
        }

        content()
    }
}

/** 画一个柔光斑:中心有色,向外渐隐到透明。 */
private fun DrawScope.drawBlob(
    center: Offset,
    radius: Float,
    color: Color,
    centerAlpha: Float = 0.75f,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = centerAlpha), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
