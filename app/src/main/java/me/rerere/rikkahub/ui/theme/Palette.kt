package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dynamiccolor.ColorSpec
import dynamiccolor.ColorSpecs
import dynamiccolor.DynamicScheme
import dynamiccolor.Variant
import hct.Hct
import kotlinx.serialization.Serializable
import me.rerere.material3.toColorScheme
import palettes.TonalPalette

/** 调色板里允许逐个指定的颜色角色。 */
enum class PaletteRole {
    PRIMARY,
    ON_PRIMARY,
    SECONDARY,
    TERTIARY,
    ERROR,
    BACKGROUND,
    SURFACE,
    SURFACE_VARIANT,
    ON_SURFACE,
}

/**
 * 一份调色板里用户能直接改的九个颜色，其余角色由 M3 从这九个颜色派生。
 *
 * 存的是 ARGB（0xAARRGGBB），和 [CustomTheme] 里存色的方式一致。
 */
@Serializable
data class PaletteColors(
    val primary: Long = 0xFF6750A4,
    val onPrimary: Long = 0xFFFFFFFF,
    val secondary: Long = 0xFF625B71,
    val tertiary: Long = 0xFF7D5260,
    val error: Long = 0xFFB3261E,
    val background: Long = 0xFFFEF7FF,
    val surface: Long = 0xFFFEF7FF,
    val surfaceVariant: Long = 0xFFE7E0EC,
    val onSurface: Long = 0xFF1D1B20,
) {
    operator fun get(role: PaletteRole): Long = when (role) {
        PaletteRole.PRIMARY -> primary
        PaletteRole.ON_PRIMARY -> onPrimary
        PaletteRole.SECONDARY -> secondary
        PaletteRole.TERTIARY -> tertiary
        PaletteRole.ERROR -> error
        PaletteRole.BACKGROUND -> background
        PaletteRole.SURFACE -> surface
        PaletteRole.SURFACE_VARIANT -> surfaceVariant
        PaletteRole.ON_SURFACE -> onSurface
    }

    fun with(role: PaletteRole, color: Long): PaletteColors = when (role) {
        PaletteRole.PRIMARY -> copy(primary = color)
        PaletteRole.ON_PRIMARY -> copy(onPrimary = color)
        PaletteRole.SECONDARY -> copy(secondary = color)
        PaletteRole.TERTIARY -> copy(tertiary = color)
        PaletteRole.ERROR -> copy(error = color)
        PaletteRole.BACKGROUND -> copy(background = color)
        PaletteRole.SURFACE -> copy(surface = color)
        PaletteRole.SURFACE_VARIANT -> copy(surfaceVariant = color)
        PaletteRole.ON_SURFACE -> copy(onSurface = color)
    }

    /**
     * 生成完整配色：先按 M3 的音调规则把九个颜色展开成全部角色，
     * 再把用户指定的九个角色原样盖回去，保证选中的颜色所见即所得。
     */
    fun generateColorScheme(dark: Boolean): ColorScheme = paletteColorScheme(
        dark = dark,
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        error = error,
        neutral = background,
        neutralVariant = surfaceVariant,
    ).copy(
        primary = Color(primary.toInt()),
        onPrimary = Color(onPrimary.toInt()),
        secondary = Color(secondary.toInt()),
        tertiary = Color(tertiary.toInt()),
        error = Color(error.toInt()),
        background = Color(background.toInt()),
        onBackground = Color(onSurface.toInt()),
        surface = Color(surface.toInt()),
        surfaceVariant = Color(surfaceVariant.toInt()),
        onSurface = Color(onSurface.toInt()),
    )
}

/**
 * 调色板设置。
 *
 * - [presetId] 为 null：不使用调色板，继续走 [me.rerere.rikkahub.data.datastore.Settings.themeId]
 * - [light] / [dark] 为 null：该侧用预设的四个种子色生成；非 null：该侧逐个改过
 */
@Serializable
data class PaletteSetting(
    val presetId: String? = null,
    val light: PaletteColors? = null,
    val dark: PaletteColors? = null,
) {
    val enabled: Boolean get() = presetId != null

    fun preset(): PalettePreset? = presetId?.let { findPalettePreset(it) }

    /** 当前生效的九个颜色；没启用调色板时返回 null。 */
    fun colors(darkMode: Boolean): PaletteColors? {
        val preset = preset() ?: return null
        return (if (darkMode) dark else light) ?: preset.colors(darkMode)
    }

    fun generateColorScheme(darkMode: Boolean): ColorScheme? =
        colors(darkMode)?.generateColorScheme(darkMode)
}

/**
 * 用五个种子色走一遍 material-color-utilities，得到全量 M3 角色。
 * 不手写容器色和状态色，避免和 M3 的音调规则对不上。
 */
internal fun paletteColorScheme(
    dark: Boolean,
    primary: Long,
    secondary: Long,
    tertiary: Long,
    error: Long,
    neutral: Long,
    neutralVariant: Long,
): ColorScheme {
    val specVersion = ColorSpec.SpecVersion.SPEC_2025
    val platform = DynamicScheme.DEFAULT_PLATFORM
    val contrastLevel = 0.0
    val variant = Variant.TONAL_SPOT
    val scheme = DynamicScheme(
        Hct.fromInt(primary.toInt()),
        variant,
        dark,
        contrastLevel,
        platform,
        specVersion,
        TonalPalette.fromInt(primary.toInt()),
        TonalPalette.fromInt(secondary.toInt()),
        TonalPalette.fromInt(tertiary.toInt()),
        TonalPalette.fromInt(neutral.toInt()),
        TonalPalette.fromInt(neutralVariant.toInt()),
        TonalPalette.fromInt(error.toInt()),
    )
    return scheme.toColorScheme()
}

internal fun Color.argbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
