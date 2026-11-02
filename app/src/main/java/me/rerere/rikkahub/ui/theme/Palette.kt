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
        // 正文用的是 onSurfaceVariant（216 处），它不跟 onSurface 走的话，
        // 改「表面上的文字颜色」只在标题那 40 处生效，看着就像没生效。
        // 音调按 M3 的关系错开一档，默认值下几乎看不出差别。
        onSurfaceVariant = Color(onSurface.toInt()).shiftTone(if (dark) -10.0 else 20.0),
    ).withSurfaceFamily(surface, dark)

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
            // 色卡本身是不透明的一套，alpha 在这里丢掉：带透明度的 primary 会让
            // 按钮、顶栏透出下层，而 onPrimary 是派生出来的不透明色，对比没保证
            val opaque = (seed and 0x00FFFFFFL) or 0xFF000000L
            val hct = Hct.fromInt(opaque.toInt())
            // 中音调取色相/彩度：靠近 0 或 100 时色域只放得下很小的彩度，
            // 种子是近白/近黑的话中性族会退化成纯灰，色相整个丢掉
            val neutral = Hct.from(hct.hue, 4.0, 50.0)
            val neutralVariant = Hct.from(hct.hue, 8.0, 50.0)
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

/** 保持色相和彩度，只把音调挪一档——用来从正文色推出次要文字色。 */
private fun Color.shiftTone(delta: Double): Color {
    val hct = Hct.fromInt(toArgb())
    return Color(Hct.from(hct.hue, hct.chroma, (hct.tone + delta).coerceIn(0.0, 100.0)).toInt())
}

/**
 * 表面族允许的最大彩度。
 *
 * M3 的 surfaceContainer* 来自中性色板（彩度一般不超过 6）。这里放到 12：
 * 够看出用户选的是哪个色相，又不至于让输入框底、卡片底变成一块饱和色，
 * 把 onSurface 的对比压垮。
 */
private const val SURFACE_CHROMA_MAX = 12.0

/**
 * M3 里「默认表面」的音调：容器阶梯 94/96/98/100（深色 12/17/22/24）都是相对它定的。
 * 拿它当基准，才能把整条阶梯按用户选的表面色平移。
 */
private const val LIGHT_SURFACE_BASE_TONE = 98.0
private const val DARK_SURFACE_BASE_TONE = 6.0

/**
 * 把整族表面色换成「表层色」的色相、彩度，并把深浅按它的音调整体平移。
 *
 * 音调不能只换不改：M3 的容器阶梯 94/96/98/100（深色 12/17/22/24）是相对默认表面
 * （浅色 tone 98、深色 tone 6）定的。只换色相的话，把表面色设成 #000000，
 * 容器算出来仍是 tone 94 的近白——而界面上绝大多数容器用的就是 surfaceContainer*，
 * 看上去就是「改了没反应」。平移之后，选的表面色越深，整片界面越深。
 *
 * 反色那两个（Snackbar 底）要往反方向平移：表面变黑时它们得变亮，否则一坨黑叠黑。
 */
internal fun ColorScheme.withSurfaceFamily(surface: Long, dark: Boolean): ColorScheme {
    val seed = Hct.fromInt(surface.toInt())
    val chroma = seed.chroma.coerceAtMost(SURFACE_CHROMA_MAX)
    val delta = seed.tone - if (dark) DARK_SURFACE_BASE_TONE else LIGHT_SURFACE_BASE_TONE

    fun shade(color: Color, offset: Double): Color {
        val tone = (Hct.fromInt(color.toArgb()).tone + offset).coerceIn(0.0, 100.0)
        return Color(Hct.from(seed.hue, chroma, tone).toInt())
    }

    return copy(
        surface = Color(surface.toInt()),
        surfaceDim = shade(surfaceDim, delta),
        surfaceBright = shade(surfaceBright, delta),
        surfaceContainerLowest = shade(surfaceContainerLowest, delta),
        surfaceContainerLow = shade(surfaceContainerLow, delta),
        surfaceContainer = shade(surfaceContainer, delta),
        surfaceContainerHigh = shade(surfaceContainerHigh, delta),
        surfaceContainerHighest = shade(surfaceContainerHighest, delta),
        // Snackbar、Tooltip 这类反色底也来自中性族，不一起换的话把 surface 设成红，
        // 弹出来的提示条还是原来那块灰
        inverseSurface = shade(inverseSurface, -delta),
        inverseOnSurface = shade(inverseOnSurface, -delta),
    )
}
