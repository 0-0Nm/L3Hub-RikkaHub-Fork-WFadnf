package me.rerere.rikkahub.ui.theme

import androidx.annotation.StringRes
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
import me.rerere.rikkahub.R
import palettes.TonalPalette

/** 调色板的分组。面板按这个顺序渲染，[ADVANCED] 默认折叠。 */
enum class PaletteGroup { MAIN, ACCENT, SURFACE, STATUS, ADVANCED }

/**
 * 调色板里允许逐个指定的颜色角色，覆盖 ColorScheme 的全部 48 个。
 *
 * 前四组是日常会碰到的；[PaletteGroup.ADVANCED] 里那批（fixed 系列、scrim、surfaceTint）
 * 在本项目里引用点都在 1 上下，收起来免得面板变成一堵墙。
 */
enum class PaletteRole(val group: PaletteGroup, @StringRes val labelRes: Int) {
    PRIMARY(PaletteGroup.MAIN, R.string.palette_role_primary),
    ON_PRIMARY(PaletteGroup.MAIN, R.string.palette_role_on_primary),
    PRIMARY_CONTAINER(PaletteGroup.MAIN, R.string.palette_role_primary_container),
    ON_PRIMARY_CONTAINER(PaletteGroup.MAIN, R.string.palette_role_on_primary_container),
    INVERSE_PRIMARY(PaletteGroup.MAIN, R.string.palette_role_inverse_primary),
    SECONDARY(PaletteGroup.ACCENT, R.string.palette_role_secondary),
    ON_SECONDARY(PaletteGroup.ACCENT, R.string.palette_role_on_secondary),
    SECONDARY_CONTAINER(PaletteGroup.ACCENT, R.string.palette_role_secondary_container),
    ON_SECONDARY_CONTAINER(PaletteGroup.ACCENT, R.string.palette_role_on_secondary_container),
    TERTIARY(PaletteGroup.ACCENT, R.string.palette_role_tertiary),
    ON_TERTIARY(PaletteGroup.ACCENT, R.string.palette_role_on_tertiary),
    TERTIARY_CONTAINER(PaletteGroup.ACCENT, R.string.palette_role_tertiary_container),
    ON_TERTIARY_CONTAINER(PaletteGroup.ACCENT, R.string.palette_role_on_tertiary_container),
    BACKGROUND(PaletteGroup.SURFACE, R.string.palette_role_background),
    ON_BACKGROUND(PaletteGroup.SURFACE, R.string.palette_role_on_background),
    SURFACE(PaletteGroup.SURFACE, R.string.palette_role_surface),
    ON_SURFACE(PaletteGroup.SURFACE, R.string.palette_role_on_surface),
    SURFACE_VARIANT(PaletteGroup.SURFACE, R.string.palette_role_surface_variant),
    ON_SURFACE_VARIANT(PaletteGroup.SURFACE, R.string.palette_role_on_surface_variant),
    SURFACE_DIM(PaletteGroup.SURFACE, R.string.palette_role_surface_dim),
    SURFACE_BRIGHT(PaletteGroup.SURFACE, R.string.palette_role_surface_bright),
    SURFACE_CONTAINER_LOWEST(PaletteGroup.SURFACE, R.string.palette_role_surface_container_lowest),
    SURFACE_CONTAINER_LOW(PaletteGroup.SURFACE, R.string.palette_role_surface_container_low),
    SURFACE_CONTAINER(PaletteGroup.SURFACE, R.string.palette_role_surface_container),
    SURFACE_CONTAINER_HIGH(PaletteGroup.SURFACE, R.string.palette_role_surface_container_high),
    SURFACE_CONTAINER_HIGHEST(PaletteGroup.SURFACE, R.string.palette_role_surface_container_highest),
    INVERSE_SURFACE(PaletteGroup.SURFACE, R.string.palette_role_inverse_surface),
    INVERSE_ON_SURFACE(PaletteGroup.SURFACE, R.string.palette_role_inverse_on_surface),
    SURFACE_TINT(PaletteGroup.SURFACE, R.string.palette_role_surface_tint),
    ERROR(PaletteGroup.STATUS, R.string.palette_role_error),
    ON_ERROR(PaletteGroup.STATUS, R.string.palette_role_on_error),
    ERROR_CONTAINER(PaletteGroup.STATUS, R.string.palette_role_error_container),
    ON_ERROR_CONTAINER(PaletteGroup.STATUS, R.string.palette_role_on_error_container),
    OUTLINE(PaletteGroup.STATUS, R.string.palette_role_outline),
    OUTLINE_VARIANT(PaletteGroup.STATUS, R.string.palette_role_outline_variant),
    SCRIM(PaletteGroup.ADVANCED, R.string.palette_role_scrim),
    PRIMARY_FIXED(PaletteGroup.ADVANCED, R.string.palette_role_primary_fixed),
    PRIMARY_FIXED_DIM(PaletteGroup.ADVANCED, R.string.palette_role_primary_fixed_dim),
    ON_PRIMARY_FIXED(PaletteGroup.ADVANCED, R.string.palette_role_on_primary_fixed),
    ON_PRIMARY_FIXED_VARIANT(PaletteGroup.ADVANCED, R.string.palette_role_on_primary_fixed_variant),
    SECONDARY_FIXED(PaletteGroup.ADVANCED, R.string.palette_role_secondary_fixed),
    SECONDARY_FIXED_DIM(PaletteGroup.ADVANCED, R.string.palette_role_secondary_fixed_dim),
    ON_SECONDARY_FIXED(PaletteGroup.ADVANCED, R.string.palette_role_on_secondary_fixed),
    ON_SECONDARY_FIXED_VARIANT(PaletteGroup.ADVANCED, R.string.palette_role_on_secondary_fixed_variant),
    TERTIARY_FIXED(PaletteGroup.ADVANCED, R.string.palette_role_tertiary_fixed),
    TERTIARY_FIXED_DIM(PaletteGroup.ADVANCED, R.string.palette_role_tertiary_fixed_dim),
    ON_TERTIARY_FIXED(PaletteGroup.ADVANCED, R.string.palette_role_on_tertiary_fixed),
    ON_TERTIARY_FIXED_VARIANT(PaletteGroup.ADVANCED, R.string.palette_role_on_tertiary_fixed_variant),
}

// 种子色：用户没指定时用这套，和默认预设一致
private const val DEFAULT_PRIMARY = 0xFF6750A4L
private const val DEFAULT_SECONDARY = 0xFF625B71L
private const val DEFAULT_TERTIARY = 0xFF7D5260L
private const val DEFAULT_SURFACE = 0xFFFEF7FFL
private const val DEFAULT_SURFACE_VARIANT = 0xFFE7E0ECL

/**
 * 一份调色板里用户改过的颜色。
 *
 * 每个字段都可空：null 表示「没动过，跟着种子派生」。这样旧版本存的九色配置还能用，
 * 新加的三十九个角色在没被指定时也不会破坏原有的派生关系。
 */
@Serializable
data class PaletteColors(
    val primary: Long? = null,
    val onPrimary: Long? = null,
    val primaryContainer: Long? = null,
    val onPrimaryContainer: Long? = null,
    val inversePrimary: Long? = null,
    val secondary: Long? = null,
    val onSecondary: Long? = null,
    val secondaryContainer: Long? = null,
    val onSecondaryContainer: Long? = null,
    val tertiary: Long? = null,
    val onTertiary: Long? = null,
    val tertiaryContainer: Long? = null,
    val onTertiaryContainer: Long? = null,
    val background: Long? = null,
    val onBackground: Long? = null,
    val surface: Long? = null,
    val onSurface: Long? = null,
    val surfaceVariant: Long? = null,
    val onSurfaceVariant: Long? = null,
    val surfaceDim: Long? = null,
    val surfaceBright: Long? = null,
    val surfaceContainerLowest: Long? = null,
    val surfaceContainerLow: Long? = null,
    val surfaceContainer: Long? = null,
    val surfaceContainerHigh: Long? = null,
    val surfaceContainerHighest: Long? = null,
    val inverseSurface: Long? = null,
    val inverseOnSurface: Long? = null,
    val surfaceTint: Long? = null,
    val error: Long? = null,
    val onError: Long? = null,
    val errorContainer: Long? = null,
    val onErrorContainer: Long? = null,
    val outline: Long? = null,
    val outlineVariant: Long? = null,
    val scrim: Long? = null,
    val primaryFixed: Long? = null,
    val primaryFixedDim: Long? = null,
    val onPrimaryFixed: Long? = null,
    val onPrimaryFixedVariant: Long? = null,
    val secondaryFixed: Long? = null,
    val secondaryFixedDim: Long? = null,
    val onSecondaryFixed: Long? = null,
    val onSecondaryFixedVariant: Long? = null,
    val tertiaryFixed: Long? = null,
    val tertiaryFixedDim: Long? = null,
    val onTertiaryFixed: Long? = null,
    val onTertiaryFixedVariant: Long? = null,
) {
    operator fun get(role: PaletteRole): Long? = when (role) {
        PaletteRole.PRIMARY -> primary
        PaletteRole.ON_PRIMARY -> onPrimary
        PaletteRole.PRIMARY_CONTAINER -> primaryContainer
        PaletteRole.ON_PRIMARY_CONTAINER -> onPrimaryContainer
        PaletteRole.INVERSE_PRIMARY -> inversePrimary
        PaletteRole.SECONDARY -> secondary
        PaletteRole.ON_SECONDARY -> onSecondary
        PaletteRole.SECONDARY_CONTAINER -> secondaryContainer
        PaletteRole.ON_SECONDARY_CONTAINER -> onSecondaryContainer
        PaletteRole.TERTIARY -> tertiary
        PaletteRole.ON_TERTIARY -> onTertiary
        PaletteRole.TERTIARY_CONTAINER -> tertiaryContainer
        PaletteRole.ON_TERTIARY_CONTAINER -> onTertiaryContainer
        PaletteRole.BACKGROUND -> background
        PaletteRole.ON_BACKGROUND -> onBackground
        PaletteRole.SURFACE -> surface
        PaletteRole.ON_SURFACE -> onSurface
        PaletteRole.SURFACE_VARIANT -> surfaceVariant
        PaletteRole.ON_SURFACE_VARIANT -> onSurfaceVariant
        PaletteRole.SURFACE_DIM -> surfaceDim
        PaletteRole.SURFACE_BRIGHT -> surfaceBright
        PaletteRole.SURFACE_CONTAINER_LOWEST -> surfaceContainerLowest
        PaletteRole.SURFACE_CONTAINER_LOW -> surfaceContainerLow
        PaletteRole.SURFACE_CONTAINER -> surfaceContainer
        PaletteRole.SURFACE_CONTAINER_HIGH -> surfaceContainerHigh
        PaletteRole.SURFACE_CONTAINER_HIGHEST -> surfaceContainerHighest
        PaletteRole.INVERSE_SURFACE -> inverseSurface
        PaletteRole.INVERSE_ON_SURFACE -> inverseOnSurface
        PaletteRole.SURFACE_TINT -> surfaceTint
        PaletteRole.ERROR -> error
        PaletteRole.ON_ERROR -> onError
        PaletteRole.ERROR_CONTAINER -> errorContainer
        PaletteRole.ON_ERROR_CONTAINER -> onErrorContainer
        PaletteRole.OUTLINE -> outline
        PaletteRole.OUTLINE_VARIANT -> outlineVariant
        PaletteRole.SCRIM -> scrim
        PaletteRole.PRIMARY_FIXED -> primaryFixed
        PaletteRole.PRIMARY_FIXED_DIM -> primaryFixedDim
        PaletteRole.ON_PRIMARY_FIXED -> onPrimaryFixed
        PaletteRole.ON_PRIMARY_FIXED_VARIANT -> onPrimaryFixedVariant
        PaletteRole.SECONDARY_FIXED -> secondaryFixed
        PaletteRole.SECONDARY_FIXED_DIM -> secondaryFixedDim
        PaletteRole.ON_SECONDARY_FIXED -> onSecondaryFixed
        PaletteRole.ON_SECONDARY_FIXED_VARIANT -> onSecondaryFixedVariant
        PaletteRole.TERTIARY_FIXED -> tertiaryFixed
        PaletteRole.TERTIARY_FIXED_DIM -> tertiaryFixedDim
        PaletteRole.ON_TERTIARY_FIXED -> onTertiaryFixed
        PaletteRole.ON_TERTIARY_FIXED_VARIANT -> onTertiaryFixedVariant
    }

    fun with(role: PaletteRole, color: Long): PaletteColors = when (role) {
        PaletteRole.PRIMARY -> copy(primary = color)
        PaletteRole.ON_PRIMARY -> copy(onPrimary = color)
        PaletteRole.PRIMARY_CONTAINER -> copy(primaryContainer = color)
        PaletteRole.ON_PRIMARY_CONTAINER -> copy(onPrimaryContainer = color)
        PaletteRole.INVERSE_PRIMARY -> copy(inversePrimary = color)
        PaletteRole.SECONDARY -> copy(secondary = color)
        PaletteRole.ON_SECONDARY -> copy(onSecondary = color)
        PaletteRole.SECONDARY_CONTAINER -> copy(secondaryContainer = color)
        PaletteRole.ON_SECONDARY_CONTAINER -> copy(onSecondaryContainer = color)
        PaletteRole.TERTIARY -> copy(tertiary = color)
        PaletteRole.ON_TERTIARY -> copy(onTertiary = color)
        PaletteRole.TERTIARY_CONTAINER -> copy(tertiaryContainer = color)
        PaletteRole.ON_TERTIARY_CONTAINER -> copy(onTertiaryContainer = color)
        PaletteRole.BACKGROUND -> copy(background = color)
        PaletteRole.ON_BACKGROUND -> copy(onBackground = color)
        PaletteRole.SURFACE -> copy(surface = color)
        PaletteRole.ON_SURFACE -> copy(onSurface = color)
        PaletteRole.SURFACE_VARIANT -> copy(surfaceVariant = color)
        PaletteRole.ON_SURFACE_VARIANT -> copy(onSurfaceVariant = color)
        PaletteRole.SURFACE_DIM -> copy(surfaceDim = color)
        PaletteRole.SURFACE_BRIGHT -> copy(surfaceBright = color)
        PaletteRole.SURFACE_CONTAINER_LOWEST -> copy(surfaceContainerLowest = color)
        PaletteRole.SURFACE_CONTAINER_LOW -> copy(surfaceContainerLow = color)
        PaletteRole.SURFACE_CONTAINER -> copy(surfaceContainer = color)
        PaletteRole.SURFACE_CONTAINER_HIGH -> copy(surfaceContainerHigh = color)
        PaletteRole.SURFACE_CONTAINER_HIGHEST -> copy(surfaceContainerHighest = color)
        PaletteRole.INVERSE_SURFACE -> copy(inverseSurface = color)
        PaletteRole.INVERSE_ON_SURFACE -> copy(inverseOnSurface = color)
        PaletteRole.SURFACE_TINT -> copy(surfaceTint = color)
        PaletteRole.ERROR -> copy(error = color)
        PaletteRole.ON_ERROR -> copy(onError = color)
        PaletteRole.ERROR_CONTAINER -> copy(errorContainer = color)
        PaletteRole.ON_ERROR_CONTAINER -> copy(onErrorContainer = color)
        PaletteRole.OUTLINE -> copy(outline = color)
        PaletteRole.OUTLINE_VARIANT -> copy(outlineVariant = color)
        PaletteRole.SCRIM -> copy(scrim = color)
        PaletteRole.PRIMARY_FIXED -> copy(primaryFixed = color)
        PaletteRole.PRIMARY_FIXED_DIM -> copy(primaryFixedDim = color)
        PaletteRole.ON_PRIMARY_FIXED -> copy(onPrimaryFixed = color)
        PaletteRole.ON_PRIMARY_FIXED_VARIANT -> copy(onPrimaryFixedVariant = color)
        PaletteRole.SECONDARY_FIXED -> copy(secondaryFixed = color)
        PaletteRole.SECONDARY_FIXED_DIM -> copy(secondaryFixedDim = color)
        PaletteRole.ON_SECONDARY_FIXED -> copy(onSecondaryFixed = color)
        PaletteRole.ON_SECONDARY_FIXED_VARIANT -> copy(onSecondaryFixedVariant = color)
        PaletteRole.TERTIARY_FIXED -> copy(tertiaryFixed = color)
        PaletteRole.TERTIARY_FIXED_DIM -> copy(tertiaryFixedDim = color)
        PaletteRole.ON_TERTIARY_FIXED -> copy(onTertiaryFixed = color)
        PaletteRole.ON_TERTIARY_FIXED_VARIANT -> copy(onTertiaryFixedVariant = color)
    }

    /** 用 [base] 里本对象没覆盖的角色补齐。预设色和用户改动就是这么合的。 */
    fun overlay(base: PaletteColors): PaletteColors = PaletteColors(
        primary = primary ?: base.primary,
        onPrimary = onPrimary ?: base.onPrimary,
        primaryContainer = primaryContainer ?: base.primaryContainer,
        onPrimaryContainer = onPrimaryContainer ?: base.onPrimaryContainer,
        inversePrimary = inversePrimary ?: base.inversePrimary,
        secondary = secondary ?: base.secondary,
        onSecondary = onSecondary ?: base.onSecondary,
        secondaryContainer = secondaryContainer ?: base.secondaryContainer,
        onSecondaryContainer = onSecondaryContainer ?: base.onSecondaryContainer,
        tertiary = tertiary ?: base.tertiary,
        onTertiary = onTertiary ?: base.onTertiary,
        tertiaryContainer = tertiaryContainer ?: base.tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer ?: base.onTertiaryContainer,
        background = background ?: base.background,
        onBackground = onBackground ?: base.onBackground,
        surface = surface ?: base.surface,
        onSurface = onSurface ?: base.onSurface,
        surfaceVariant = surfaceVariant ?: base.surfaceVariant,
        onSurfaceVariant = onSurfaceVariant ?: base.onSurfaceVariant,
        surfaceDim = surfaceDim ?: base.surfaceDim,
        surfaceBright = surfaceBright ?: base.surfaceBright,
        surfaceContainerLowest = surfaceContainerLowest ?: base.surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow ?: base.surfaceContainerLow,
        surfaceContainer = surfaceContainer ?: base.surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh ?: base.surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest ?: base.surfaceContainerHighest,
        inverseSurface = inverseSurface ?: base.inverseSurface,
        inverseOnSurface = inverseOnSurface ?: base.inverseOnSurface,
        surfaceTint = surfaceTint ?: base.surfaceTint,
        error = error ?: base.error,
        onError = onError ?: base.onError,
        errorContainer = errorContainer ?: base.errorContainer,
        onErrorContainer = onErrorContainer ?: base.onErrorContainer,
        outline = outline ?: base.outline,
        outlineVariant = outlineVariant ?: base.outlineVariant,
        scrim = scrim ?: base.scrim,
        primaryFixed = primaryFixed ?: base.primaryFixed,
        primaryFixedDim = primaryFixedDim ?: base.primaryFixedDim,
        onPrimaryFixed = onPrimaryFixed ?: base.onPrimaryFixed,
        onPrimaryFixedVariant = onPrimaryFixedVariant ?: base.onPrimaryFixedVariant,
        secondaryFixed = secondaryFixed ?: base.secondaryFixed,
        secondaryFixedDim = secondaryFixedDim ?: base.secondaryFixedDim,
        onSecondaryFixed = onSecondaryFixed ?: base.onSecondaryFixed,
        onSecondaryFixedVariant = onSecondaryFixedVariant ?: base.onSecondaryFixedVariant,
        tertiaryFixed = tertiaryFixed ?: base.tertiaryFixed,
        tertiaryFixedDim = tertiaryFixedDim ?: base.tertiaryFixedDim,
        onTertiaryFixed = onTertiaryFixed ?: base.onTertiaryFixed,
        onTertiaryFixedVariant = onTertiaryFixedVariant ?: base.onTertiaryFixedVariant,
    )

    /**
     * 把 48 个角色一次性刷成同一个颜色。
     *
     * 诊断用：整片界面刷成洋红之后，哪一块还是原来的颜色，就是那个角色没接到界面上。
     */
    fun filledWith(argb: Long): PaletteColors = PaletteColors(
        primary = argb,
        onPrimary = argb,
        primaryContainer = argb,
        onPrimaryContainer = argb,
        inversePrimary = argb,
        secondary = argb,
        onSecondary = argb,
        secondaryContainer = argb,
        onSecondaryContainer = argb,
        tertiary = argb,
        onTertiary = argb,
        tertiaryContainer = argb,
        onTertiaryContainer = argb,
        background = argb,
        onBackground = argb,
        surface = argb,
        onSurface = argb,
        surfaceVariant = argb,
        onSurfaceVariant = argb,
        surfaceDim = argb,
        surfaceBright = argb,
        surfaceContainerLowest = argb,
        surfaceContainerLow = argb,
        surfaceContainer = argb,
        surfaceContainerHigh = argb,
        surfaceContainerHighest = argb,
        inverseSurface = argb,
        inverseOnSurface = argb,
        surfaceTint = argb,
        error = argb,
        onError = argb,
        errorContainer = argb,
        onErrorContainer = argb,
        outline = argb,
        outlineVariant = argb,
        scrim = argb,
        primaryFixed = argb,
        primaryFixedDim = argb,
        onPrimaryFixed = argb,
        onPrimaryFixedVariant = argb,
        secondaryFixed = argb,
        secondaryFixedDim = argb,
        onSecondaryFixed = argb,
        onSecondaryFixedVariant = argb,
        tertiaryFixed = argb,
        tertiaryFixedDim = argb,
        onTertiaryFixed = argb,
        onTertiaryFixedVariant = argb,
    )

    /**
     * 48 个角色各给一个专属颜色，把界面刷成一张「着色地图」。
     *
     * 色相每隔 7.5° 一个、明度在 62/38 交替，相邻两块一定能分开：
     * 界面上看到一块颜色，回列表里一比就知道是哪个角色；
     * 哪块颜色没变，就是那个角色还没接到界面上。
     */
    fun probe(): PaletteColors {
        val roles = PaletteRole.entries
        return roles.fold(PaletteColors()) { acc, role ->
            val i = role.ordinal
            val hue = i * 360.0 / roles.size
            val tone = if (i % 2 == 0) 62.0 else 38.0
            acc.with(role, Hct.from(hue, 85.0, tone).toInt().toLong() and 0xFFFFFFFFL)
        }
    }

    /** 展开成全部 48 个确定的色值，给编辑面板显示用。 */
    fun full(dark: Boolean): PaletteColors {
        val scheme = generateColorScheme(dark)
        return PaletteColors(
            primary = scheme.primary.argbLong(),
            onPrimary = scheme.onPrimary.argbLong(),
            primaryContainer = scheme.primaryContainer.argbLong(),
            onPrimaryContainer = scheme.onPrimaryContainer.argbLong(),
            inversePrimary = scheme.inversePrimary.argbLong(),
            secondary = scheme.secondary.argbLong(),
            onSecondary = scheme.onSecondary.argbLong(),
            secondaryContainer = scheme.secondaryContainer.argbLong(),
            onSecondaryContainer = scheme.onSecondaryContainer.argbLong(),
            tertiary = scheme.tertiary.argbLong(),
            onTertiary = scheme.onTertiary.argbLong(),
            tertiaryContainer = scheme.tertiaryContainer.argbLong(),
            onTertiaryContainer = scheme.onTertiaryContainer.argbLong(),
            background = scheme.background.argbLong(),
            onBackground = scheme.onBackground.argbLong(),
            surface = scheme.surface.argbLong(),
            onSurface = scheme.onSurface.argbLong(),
            surfaceVariant = scheme.surfaceVariant.argbLong(),
            onSurfaceVariant = scheme.onSurfaceVariant.argbLong(),
            surfaceDim = scheme.surfaceDim.argbLong(),
            surfaceBright = scheme.surfaceBright.argbLong(),
            surfaceContainerLowest = scheme.surfaceContainerLowest.argbLong(),
            surfaceContainerLow = scheme.surfaceContainerLow.argbLong(),
            surfaceContainer = scheme.surfaceContainer.argbLong(),
            surfaceContainerHigh = scheme.surfaceContainerHigh.argbLong(),
            surfaceContainerHighest = scheme.surfaceContainerHighest.argbLong(),
            inverseSurface = scheme.inverseSurface.argbLong(),
            inverseOnSurface = scheme.inverseOnSurface.argbLong(),
            surfaceTint = scheme.surfaceTint.argbLong(),
            error = scheme.error.argbLong(),
            onError = scheme.onError.argbLong(),
            errorContainer = scheme.errorContainer.argbLong(),
            onErrorContainer = scheme.onErrorContainer.argbLong(),
            outline = scheme.outline.argbLong(),
            outlineVariant = scheme.outlineVariant.argbLong(),
            scrim = scheme.scrim.argbLong(),
            primaryFixed = scheme.primaryFixed.argbLong(),
            primaryFixedDim = scheme.primaryFixedDim.argbLong(),
            onPrimaryFixed = scheme.onPrimaryFixed.argbLong(),
            onPrimaryFixedVariant = scheme.onPrimaryFixedVariant.argbLong(),
            secondaryFixed = scheme.secondaryFixed.argbLong(),
            secondaryFixedDim = scheme.secondaryFixedDim.argbLong(),
            onSecondaryFixed = scheme.onSecondaryFixed.argbLong(),
            onSecondaryFixedVariant = scheme.onSecondaryFixedVariant.argbLong(),
            tertiaryFixed = scheme.tertiaryFixed.argbLong(),
            tertiaryFixedDim = scheme.tertiaryFixedDim.argbLong(),
            onTertiaryFixed = scheme.onTertiaryFixed.argbLong(),
            onTertiaryFixedVariant = scheme.onTertiaryFixedVariant.argbLong(),
        )
    }

    /**
     * 生成完整配色：先按 M3 的音调规则把种子展开，再把用户指定的角色原样盖回去。
     *
     * 表面族先交给 [withSurfaceFamily] 按表面色的色相/彩度/深浅重算一遍，
     * 之后才应用用户对容器色的直接指定——高级区里逐格调过的值优先。
     */
    fun generateColorScheme(dark: Boolean): ColorScheme {
        val seedSurface = surface ?: DEFAULT_SURFACE
        val base = paletteColorScheme(
            dark = dark,
            primary = primary ?: DEFAULT_PRIMARY,
            secondary = secondary ?: DEFAULT_SECONDARY,
            tertiary = tertiary ?: DEFAULT_TERTIARY,
            error = error ?: DEFAULT_ERROR,
            neutral = seedSurface,
            neutralVariant = surfaceVariant ?: DEFAULT_SURFACE_VARIANT,
        ).withSurfaceFamily(seedSurface, dark)

        // 正文用的 onSurfaceVariant 没被指定时由 onSurface 推一档，
        // 否则改「表面上的文字」只有标题会动
        val bodyText = Color((onSurface ?: base.onSurface.argbLong()).toInt())
            .shiftTone(if (dark) -10.0 else 20.0)

        return base.copy(
            primary = primary?.let { Color(it) } ?: base.primary,
            onPrimary = onPrimary?.let { Color(it) } ?: base.onPrimary,
            primaryContainer = primaryContainer?.let { Color(it) } ?: base.primaryContainer,
            onPrimaryContainer = onPrimaryContainer?.let { Color(it) } ?: base.onPrimaryContainer,
            inversePrimary = inversePrimary?.let { Color(it) } ?: base.inversePrimary,
            secondary = secondary?.let { Color(it) } ?: base.secondary,
            onSecondary = onSecondary?.let { Color(it) } ?: base.onSecondary,
            secondaryContainer = secondaryContainer?.let { Color(it) } ?: base.secondaryContainer,
            onSecondaryContainer = onSecondaryContainer?.let { Color(it) } ?: base.onSecondaryContainer,
            tertiary = tertiary?.let { Color(it) } ?: base.tertiary,
            onTertiary = onTertiary?.let { Color(it) } ?: base.onTertiary,
            tertiaryContainer = tertiaryContainer?.let { Color(it) } ?: base.tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer?.let { Color(it) } ?: base.onTertiaryContainer,
            background = background?.let { Color(it) } ?: base.background,
            onBackground = onBackground?.let { Color(it) } ?: base.onBackground,
            surface = surface?.let { Color(it) } ?: base.surface,
            onSurface = onSurface?.let { Color(it) } ?: base.onSurface,
            surfaceVariant = surfaceVariant?.let { Color(it) } ?: base.surfaceVariant,
            onSurfaceVariant = onSurfaceVariant?.let { Color(it) } ?: bodyText,
            surfaceDim = surfaceDim?.let { Color(it) } ?: base.surfaceDim,
            surfaceBright = surfaceBright?.let { Color(it) } ?: base.surfaceBright,
            surfaceContainerLowest = surfaceContainerLowest?.let { Color(it) } ?: base.surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow?.let { Color(it) } ?: base.surfaceContainerLow,
            surfaceContainer = surfaceContainer?.let { Color(it) } ?: base.surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh?.let { Color(it) } ?: base.surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest?.let { Color(it) } ?: base.surfaceContainerHighest,
            inverseSurface = inverseSurface?.let { Color(it) } ?: base.inverseSurface,
            inverseOnSurface = inverseOnSurface?.let { Color(it) } ?: base.inverseOnSurface,
            surfaceTint = surfaceTint?.let { Color(it) } ?: base.surfaceTint,
            error = error?.let { Color(it) } ?: base.error,
            onError = onError?.let { Color(it) } ?: base.onError,
            errorContainer = errorContainer?.let { Color(it) } ?: base.errorContainer,
            onErrorContainer = onErrorContainer?.let { Color(it) } ?: base.onErrorContainer,
            outline = outline?.let { Color(it) } ?: base.outline,
            outlineVariant = outlineVariant?.let { Color(it) } ?: base.outlineVariant,
            scrim = scrim?.let { Color(it) } ?: base.scrim,
            primaryFixed = primaryFixed?.let { Color(it) } ?: base.primaryFixed,
            primaryFixedDim = primaryFixedDim?.let { Color(it) } ?: base.primaryFixedDim,
            onPrimaryFixed = onPrimaryFixed?.let { Color(it) } ?: base.onPrimaryFixed,
            onPrimaryFixedVariant = onPrimaryFixedVariant?.let { Color(it) } ?: base.onPrimaryFixedVariant,
            secondaryFixed = secondaryFixed?.let { Color(it) } ?: base.secondaryFixed,
            secondaryFixedDim = secondaryFixedDim?.let { Color(it) } ?: base.secondaryFixedDim,
            onSecondaryFixed = onSecondaryFixed?.let { Color(it) } ?: base.onSecondaryFixed,
            onSecondaryFixedVariant = onSecondaryFixedVariant?.let { Color(it) } ?: base.onSecondaryFixedVariant,
            tertiaryFixed = tertiaryFixed?.let { Color(it) } ?: base.tertiaryFixed,
            tertiaryFixedDim = tertiaryFixedDim?.let { Color(it) } ?: base.tertiaryFixedDim,
            onTertiaryFixed = onTertiaryFixed?.let { Color(it) } ?: base.onTertiaryFixed,
            onTertiaryFixedVariant = onTertiaryFixedVariant?.let { Color(it) } ?: base.onTertiaryFixedVariant,
        )
    }

    companion object {
        /** 展开用的默认错误色。种子里没有红，出错提示就会和正文混在一起。 */
        const val DEFAULT_ERROR = 0xFFB3261EL

        /**
         * 用一个色号当种子，展开成一张能直接用的色卡。
         *
         * 只填九个常用角色，其余三十九个留空——它们跟着这九个派生，
         * 用户真去高级区改了再覆盖。
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
 * - [light] / [dark] 为 null：该侧用预设的种子色生成；非 null：该侧有角色被逐个改过
 */
@Serializable
data class PaletteSetting(
    val presetId: String? = null,
    val light: PaletteColors? = null,
    val dark: PaletteColors? = null,
) {
    val enabled: Boolean get() = presetId != null

    fun preset(): PalettePreset? = presetId?.let { findPalettePreset(it) }

    /** 当前生效的颜色；没启用调色板时返回 null。改过的角色盖在预设之上。 */
    fun colors(darkMode: Boolean): PaletteColors? {
        val preset = preset() ?: return null
        val base = preset.colors(darkMode)
        return (if (darkMode) dark else light)?.overlay(base) ?: base
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
