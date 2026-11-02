package me.rerere.rikkahub.ui.theme

import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.hooks.rememberCurrentColorMode
import me.rerere.rikkahub.ui.hooks.rememberUserSettingsState
import me.rerere.rikkahub.utils.getActivity

private val ExtendLightColors = lightExtendColors()
private val ExtendDarkColors = darkExtendColors()
val LocalExtendColors = compositionLocalOf { ExtendLightColors }

val LocalDarkMode = compositionLocalOf { false }

private val AMOLED_DARK_BACKGROUND = Color(0xFF000000)

/** 关闭动效时用的 MotionScheme：所有 spec 都是 snap，动画一帧到位 */
private val SnapMotionScheme = object : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

@Serializable
enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun RikkahubTheme(
    colorMode: ColorMode = rememberCurrentColorMode(),
    content: @Composable () -> Unit
) {
    val settings by rememberUserSettingsState()

    val darkTheme = when (colorMode) {
        ColorMode.SYSTEM -> isSystemInDarkTheme()
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }
    val amoledDarkMode by rememberAmoledDarkMode()

    // 调色板：深浅两套各自生成；没启用时返回 null，继续用主题设置里的配色
    val paletteScheme = remember(settings.palette, darkTheme) {
        settings.palette.generateColorScheme(darkTheme)
    }

    val colorScheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        paletteScheme != null -> paletteScheme
        else -> {
            val theme = findThemeById(settings.themeId, settings.customThemes)
                ?: findPresetTheme(settings.themeId)
            theme.getColorScheme(dark = darkTheme)
        }
    }
    val terminal = settings.displaySetting.terminalStyle
    // 全宽组件：直角 + 组件贴父容器边，由下面的 CompositionLocal 和 shapes 一起提供
    val fullWidthComponents = settings.displaySetting.fullWidthComponents
    val colorSchemeConverted = remember(darkTheme, amoledDarkMode, colorScheme, terminal) {
        if (terminal) {
            TerminalColorScheme
        } else if (darkTheme && amoledDarkMode) {
            colorScheme.copy(
                background = AMOLED_DARK_BACKGROUND,
                surface = AMOLED_DARK_BACKGROUND,
            )
        } else {
            colorScheme
        }
    }
    // 位置层：终端模式下整体走终端配色，调色板的位置覆盖不参与
    val uiSlots = remember(settings.palette, terminal, darkTheme, colorSchemeConverted) {
        if (terminal) uiSlotsFrom(colorSchemeConverted)
        else settings.palette.uiSlots(darkTheme, colorSchemeConverted)
    }
    val extendColors = if (darkTheme || terminal) ExtendDarkColors else ExtendLightColors
    val effectiveDark = darkTheme || terminal

    // 更新状态栏图标颜色
    val view = LocalView.current
    val activity = view.context.getActivity()
    // 浮窗可能使用 Application Context，没有可更新系统栏的 Activity。
    if (!view.isInEditMode && activity != null) {
        DisposableEffect(view, activity, effectiveDark) {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, view)
            val previousLightStatusBars = controller.isAppearanceLightStatusBars
            val previousLightNavigationBars = controller.isAppearanceLightNavigationBars
            controller.apply {
                isAppearanceLightStatusBars = !effectiveDark
                isAppearanceLightNavigationBars = !effectiveDark
            }
            onDispose {
                // 嵌套主题（如终端的深色主题）退出时恢复原有系统栏图标颜色。
                controller.isAppearanceLightStatusBars = previousLightStatusBars
                controller.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }

    CompositionLocalProvider(
        LocalUiSlots provides uiSlots,
        LocalDarkMode provides effectiveDark,
        LocalExtendColors provides extendColors,
        LocalOverscrollFactory provides null,
        LocalTerminalMode provides terminal,
        LocalFullWidthComponents provides fullWidthComponents,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorSchemeConverted,
            typography = if (terminal) terminalTypography(Typography) else Typography,
            shapes = when {
                terminal -> TerminalShapes
                fullWidthComponents -> SquareShapes
                else -> MaterialTheme.shapes
            },
            content = content,
            motionScheme = if (settings.displaySetting.disableAnimations) {
                SnapMotionScheme
            } else {
                MotionScheme.expressive()
            }
        )
    }
}

val MaterialTheme.extendColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendColors.current
