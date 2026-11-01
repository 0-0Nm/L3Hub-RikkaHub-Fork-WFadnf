package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SwitchDefaults
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
 * 浮层（抽屉 / BottomSheet）能不能真做成玻璃。
 *
 * 想糊出东西就得手上有一个 Haze 源，外加模糊特效开着、不是终端风格。缺任意一条时
 * [glassSurface] 只剩一层半透明 —— 浮层半透明却不模糊，底下的正文会直接透上来，
 * 比实色更难读，所以调用方按这个返回 false 保持原来的实色底。
 */
@Composable
fun canGlassPane(hazeState: HazeState? = LocalGlassHazeState.current): Boolean {
    val displaySetting = LocalSettings.current.displaySetting
    return hazeState != null && displaySetting.enableBlurEffect && !LocalTerminalMode.current
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
    // 大块浮层（抽屉、BottomSheet）必须给个不透明下限：haze 只糊注册过的源，
    // 正文本身不在源里，35% 透明时文字会原样透上来，看着像没底板
    minAlpha: Float = 0f,
): Modifier {
    val displaySetting = LocalSettings.current.displaySetting
    // 终端风格是一套纯字符的界面，不加玻璃；关闭模糊特效时同样一条 haze 都不挂
    if (!displaySetting.enableBlurEffect || LocalTerminalMode.current) return this
    val tint = baseColor.copy(
        alpha = maxOf(baseColor.alpha * displaySetting.glassSurfaceAlpha, minAlpha),
    )
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

/** BottomSheet 底板圆角：跟 M3 默认的 28dp 顶角对齐，玻璃底和内容裁切才不会错位。 */
val GlassSheetShape: Shape =
    RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp)

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

/**
 * 开关配色：未选中的轨道（关着的时候它就是开关的「容器」）走玻璃底。
 *
 * 轨道淡下去以后形状全靠描边认，所以描边留 0.5 不透明度；选中态保持主色实心，
 * 「开着」得一眼看得出来。关闭模糊特效 / 终端风格时整份交回 M3 默认配色。
 */
@Composable
fun glassSwitchColors() = if (
    !LocalSettings.current.displaySetting.enableBlurEffect || LocalTerminalMode.current
) {
    SwitchDefaults.colors()
} else {
    SwitchDefaults.colors(
        uncheckedTrackColor = glassColor(MaterialTheme.colorScheme.surfaceContainerHighest),
        uncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    )
}

/** FilterChip 配色：未选中是玻璃底，选中态是玻璃底的 secondaryContainer。 */
@Composable
fun glassFilterChipColors() = if (
    !LocalSettings.current.displaySetting.enableBlurEffect || LocalTerminalMode.current
) {
    FilterChipDefaults.filterChipColors()
} else {
    FilterChipDefaults.filterChipColors(
        containerColor = glassColor(MaterialTheme.colorScheme.surfaceContainerLow),
        selectedContainerColor = glassColor(MaterialTheme.colorScheme.secondaryContainer),
    )
}

/** AssistChip 配色：同上，只有一层玻璃底。 */
@Composable
fun glassAssistChipColors() = if (
    !LocalSettings.current.displaySetting.enableBlurEffect || LocalTerminalMode.current
) {
    AssistChipDefaults.assistChipColors()
} else {
    AssistChipDefaults.assistChipColors(
        containerColor = glassColor(MaterialTheme.colorScheme.surfaceContainerLow),
    )
}
