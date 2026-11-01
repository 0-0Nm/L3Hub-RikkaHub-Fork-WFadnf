package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.LocalTerminalMode

/**
 * 玻璃底能取到的 Haze 源。
 *
 * 只有背景本身有东西可糊的层级（聊天页的渐变/图片背景）才提供；默认 null 时玻璃底退化成
 * 半透明色块 —— 背景只有一层纯色时糊一遍还是同一个颜色，白多一次 blur 开销。
 *
 * 源由页面显式提供，不给列表每一项挂 `hazeSource`：滚动时每项重糊一次会明显掉帧。
 */
val LocalGlassHazeState = compositionLocalOf<HazeState?> { null }

/** 玻璃透明度；关闭模糊特效时为 1，调用方按原来的实色画。 */
val DisplaySetting.glassSurfaceAlpha: Float
    get() = if (enableBlurEffect) glassOpacity.coerceIn(0f, 1f) else 1f

/** 实色底换成同色半透明；关闭模糊特效（或终端风格）时原样返回。 */
@Composable
fun glassColor(base: Color): Color {
    val displaySetting = LocalSettings.current.displaySetting
    if (!displaySetting.enableBlurEffect || LocalTerminalMode.current) return base
    return base.copy(alpha = base.alpha * displaySetting.glassSurfaceAlpha)
}

/**
 * 毛玻璃底：半透明色 + hazeEffect。
 *
 * - `enableBlurEffect == false`：一条 haze 都不挂，也不改透明度，直接返回原 modifier，
 *   由组件自己的实色底兜住。
 * - 没有 Haze 源：只画半透明底加一道描边。
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape,
    baseColor: Color,
    hazeState: HazeState? = LocalGlassHazeState.current,
    radius: Dp = 18.dp,
): Modifier {
    val displaySetting = LocalSettings.current.displaySetting
    // 终端风格是一套纯字符的界面，不加玻璃；关闭模糊特效时同样一条 haze 都不挂
    if (!displaySetting.enableBlurEffect || LocalTerminalMode.current) return this
    val tint = baseColor.copy(alpha = baseColor.alpha * displaySetting.glassSurfaceAlpha)
    val pane = if (hazeState == null) {
        Modifier.background(tint)
    } else {
        Modifier.hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = HazeBlurStyle.Material3(containerColor = tint) { blurRadius(radius) },
        )
    }
    return clip(shape)
        .then(pane)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), shape)
}

/**
 * 选项卡（`SingleChoiceSegmentedButtonRow` 里的 `SegmentedButton`）的配色。
 *
 * 选中项原来是一块实色 secondaryContainer，这里换成 surface 加「玻璃透明度」，
 * 选中状态靠主色文字区分；关闭模糊特效时整份交回 M3 默认配色。
 */
@Composable
fun glassSegmentedButtonColors() = if (
    !LocalSettings.current.displaySetting.enableBlurEffect || LocalTerminalMode.current
) {
    SegmentedButtonDefaults.colors()
} else {
    SegmentedButtonDefaults.colors(
        activeContainerColor = glassColor(MaterialTheme.colorScheme.surface),
        activeContentColor = MaterialTheme.colorScheme.primary,
    )
}
