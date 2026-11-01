package me.rerere.rikkahub.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import me.rerere.rikkahub.R

/**
 * 内置调色板：只给主色/辅色/第三色/错误色四个种子，
 * 其余角色（容器色、状态色、表面色阶）由 M3 音调规则生成。
 */
data class PalettePreset(
    val id: String,
    @StringRes val nameRes: Int,
    val primary: Long,
    val secondary: Long,
    val tertiary: Long,
    val error: Long,
    /** 中性色种子，只给单色系用；为 null 时中性色跟随主色。 */
    val neutral: Long? = null,
) {
    fun generateColorScheme(dark: Boolean): ColorScheme = paletteColorScheme(
        dark = dark,
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        error = error,
        neutral = neutral ?: primary,
        neutralVariant = neutral ?: secondary,
    )

    /** 展开成九个可直接编辑的颜色，自定义时以它为起点。 */
    fun colors(dark: Boolean): PaletteColors {
        val scheme = generateColorScheme(dark)
        return PaletteColors(
            primary = scheme.primary.argbLong(),
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

val DEFAULT_PALETTE_ID = "default"

val PalettePresets: List<PalettePreset> = listOf(
    PalettePreset(
        id = DEFAULT_PALETTE_ID,
        nameRes = R.string.palette_name_default,
        primary = 0xFF6750A4,
        secondary = 0xFF625B71,
        tertiary = 0xFF7D5260,
        error = 0xFFB3261E,
    ),
    PalettePreset(
        id = "ocean",
        nameRes = R.string.palette_name_ocean,
        primary = 0xFF116682,
        secondary = 0xFF4D616C,
        tertiary = 0xFF5D5B7D,
        error = 0xFFBA1A1A,
    ),
    PalettePreset(
        id = "forest",
        nameRes = R.string.palette_name_forest,
        primary = 0xFF4C662B,
        secondary = 0xFF586249,
        tertiary = 0xFF386663,
        error = 0xFFBA1A1A,
    ),
    PalettePreset(
        id = "sunset",
        nameRes = R.string.palette_name_sunset,
        primary = 0xFF8B5000,
        secondary = 0xFF755843,
        tertiary = 0xFF8C4A60,
        error = 0xFFBA1A1A,
    ),
    PalettePreset(
        id = "rose",
        nameRes = R.string.palette_name_rose,
        primary = 0xFF8E4955,
        secondary = 0xFF76565A,
        tertiary = 0xFF785831,
        error = 0xFFBA1A1A,
    ),
    PalettePreset(
        id = "monochrome",
        nameRes = R.string.palette_name_monochrome,
        primary = 0xFF5C5C5C,
        secondary = 0xFF5C5C5C,
        tertiary = 0xFF5C5C5C,
        error = 0xFFBA1A1A,
        neutral = 0xFF5C5C5C,
    ),
)

fun findPalettePreset(id: String): PalettePreset =
    PalettePresets.find { it.id == id } ?: PalettePresets.first()
