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
        // 中性族的种子改用「表层色」：容器色要跟它走，而不是跟背景色走
        neutral = surface,
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
    ).withSurfaceFamily(surface = surface, dark = dark)

    companion object {
        /** 展开用的默认错误色。种子里没有红，出错提示就会和正文混在一起。 */
        const val DEFAULT_ERROR = 0xFFB3261EL

        /**
         * 用一个色号当种子，展开成九个可编辑的角色。
         *
         * 主色/辅色/第三色都从这个色号派生，中性色压到低彩度再展开，
         * 得到的是一张能直接用的色卡，而不是把同一个颜色刷九遍。
         */
        fun fromSeed(seed: Long, dark: Boolean): PaletteColors {
            val opaque = seed and 0xFFFFFFFFL
            val hct = Hct.fromInt(opaque.toInt())
            val neutral = Hct.from(hct.hue, 4.0, hct.tone)
            val neutralVariant = Hct.from(hct.hue, 8.0, hct.tone)
            val scheme = paletteColorScheme(
                dark = dark,
                primary = opaque,
                secondary = opaque,
                tertiary = opaque,
                error = DEFAULT_ERROR,
                neutral = neutral.toInt().toLong() and 0xFFFFFFFFL,
                neutralVariant = neutralVariant.toInt().toLong() and 0xFFFFFFFFL,
            )
            return PaletteColors(
                primary = opaque,
                onPrimary = scheme.onPrimary.argbLong(),
                secondary = scheme.secondary.argbLong(),
                tertiary = scheme.tertiary.argbLong(),
                error = scheme.error.argbLong(),
                background = scheme.background.argbLong(),
                surface = scheme.surface.argbLong(),
                surfaceVariant = scheme.surfaceVariant.argbLong(),
                onSurface = scheme.onSurface.argbLong(),
            )
        }
    }
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


/**
 * M3 表面族的音调（tone），深浅各一套。
 *
 * 数值取自 M3 规范：surfaceDim / surfaceBright / surfaceContainerLowest…Highest。
 * 音调是固定的，色相和彩度由「表层色」决定——这正是改 surface 能带动整族的原因。
 */
internal data class SurfaceTones(
    val dim: Double,
    val bright: Double,
    val lowest: Double,
    val low: Double,
    val container: Double,
    val high: Double,
    val highest: Double,
)

internal val LightSurfaceTones = SurfaceTones(
    dim = 87.0,
    bright = 98.0,
    lowest = 100.0,
    low = 96.0,
    container = 94.0,
    high = 92.0,
    highest = 90.0,
)

internal val DarkSurfaceTones = SurfaceTones(
    dim = 6.0,
    bright = 24.0,
    lowest = 4.0,
    low = 10.0,
    container = 12.0,
    high = 17.0,
    highest = 22.0,
)

/**
 * 把整族表面色按「表层色」的色相重算一遍。
 *
 * 只覆盖 surface 一个字段是不够的：应用里绝大多数容器用的是 surfaceContainer*，
 * 它们原先由中性种子展开、音调钉死，所以改 surface 在浅色下几乎看不出来。
 * 重算之后改一次表层色，卡片、顶栏、列表项、弹层跟着一起走。
 */
internal fun ColorScheme.withSurfaceFamily(surface: Long, dark: Boolean): ColorScheme {
    val seed = Hct.fromInt(surface.toInt())
    val tones = if (dark) DarkSurfaceTones else LightSurfaceTones

    fun at(tone: Double): Color = Color(Hct.from(seed.hue, seed.chroma, tone).toInt())

    return copy(
        surface = Color(surface.toInt()),
        surfaceDim = at(tones.dim),
        surfaceBright = at(tones.bright),
        surfaceContainerLowest = at(tones.lowest),
        surfaceContainerLow = at(tones.low),
        surfaceContainer = at(tones.container),
        surfaceContainerHigh = at(tones.high),
        surfaceContainerHighest = at(tones.highest),
    )
}
