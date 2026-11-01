package me.rerere.rikkahub.ui.pages.setting

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.ColorPickerRow
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.DEFAULT_PALETTE_ID
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.PaletteColors
import me.rerere.rikkahub.ui.theme.PalettePresets
import me.rerere.rikkahub.ui.theme.PaletteRole
import me.rerere.rikkahub.ui.theme.PaletteSetting
import me.rerere.rikkahub.ui.theme.argbLong
import me.rerere.rikkahub.ui.theme.findPalettePreset
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.androidx.compose.koinViewModel

/**
 * 调色板：内置预设 + 逐个角色自定义，深浅两套分开存。
 *
 * 动态取色开着的时候这里的一切都不生效（沿用上游的行为），页面上会提示并可直接关掉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingPalettePage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val palette = settings.palette
    val darkMode = LocalDarkMode.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var editingDarkMode by remember { mutableStateOf<Boolean?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    fun updatePalette(fn: (PaletteSetting) -> PaletteSetting) {
        vm.updateSettings { it.copy(palette = fn(it.palette)) }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_page_palette)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("dynamicColor") {
                CardGroup {
                    switchItem(
                        checked = settings.dynamicColor,
                        onCheckedChange = { enabled ->
                            vm.updateSettings { it.copy(dynamicColor = enabled) }
                        },
                        headlineContent = { Text(stringResource(R.string.setting_palette_page_follow_dynamic)) },
                        supportingContent = { Text(stringResource(R.string.setting_palette_page_follow_dynamic_desc)) },
                    )
                }
            }

            if (settings.dynamicColor) {
                item("dynamicHint") {
                    Text(
                        text = stringResource(R.string.setting_palette_page_dynamic_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }

            item("enablePalette") {
                CardGroup {
                    switchItem(
                        checked = palette.enabled,
                        onCheckedChange = { enabled ->
                            updatePalette {
                                it.copy(presetId = if (enabled) it.presetId ?: DEFAULT_PALETTE_ID else null)
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.setting_palette_page_enable)) },
                        supportingContent = { Text(stringResource(R.string.setting_palette_page_enable_desc)) },
                    )
                }
            }

            item("presetsTitle") {
                PaletteSectionTitle(stringResource(R.string.setting_palette_page_presets))
            }

            item("presets") {
                CardGroup {
                    PalettePresets.forEach { preset ->
                        item(
                            onClick = { updatePalette { it.copy(presetId = preset.id) } },
                            leadingContent = {
                                val colors = remember(preset.id, darkMode) { preset.colors(darkMode) }
                                PaletteStrip(colors)
                            },
                            headlineContent = { Text(stringResource(preset.nameRes)) },
                            trailingContent = {
                                if (palette.presetId == preset.id) {
                                    Icon(
                                        HugeIcons.Tick01,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                        )
                    }
                }
            }

            item("customTitle") {
                PaletteSectionTitle(stringResource(R.string.setting_palette_page_custom))
            }

            item("customSides") {
                val lightColors = remember(palette) { palette.effectiveColors(darkMode = false) }
                val darkColors = remember(palette) { palette.effectiveColors(darkMode = true) }
                // CardGroup 的 content 不是 composable 作用域，文案要在这里先取出来
                val lightTitle = stringResource(R.string.setting_palette_page_light_palette)
                val darkTitle = stringResource(R.string.setting_palette_page_dark_palette)
                CardGroup {
                    paletteSideItem(
                        colors = lightColors,
                        headline = lightTitle,
                        onClick = { editingDarkMode = false },
                    )
                    paletteSideItem(
                        colors = darkColors,
                        headline = darkTitle,
                        onClick = { editingDarkMode = true },
                    )
                }
            }

            item("reset") {
                CardGroup {
                    item(
                        onClick = { showResetDialog = true },
                        leadingContent = { Icon(HugeIcons.Refresh01, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.setting_palette_page_reset)) },
                        supportingContent = { Text(stringResource(R.string.setting_palette_page_reset_desc)) },
                    )
                }
            }
        }
    }

    editingDarkMode?.let { dark ->
        val preset = palette.preset()
        PaletteEditSheet(
            darkMode = dark,
            initial = palette.effectiveColors(dark),
            presetColors = (preset ?: findPalettePreset(DEFAULT_PALETTE_ID)).colors(dark),
            onDismiss = { editingDarkMode = null },
            onSave = { colors ->
                updatePalette {
                    it.copy(
                        presetId = it.presetId ?: DEFAULT_PALETTE_ID,
                        light = if (dark) it.light else colors,
                        dark = if (dark) colors else it.dark,
                    )
                }
                editingDarkMode = null
            },
        )
    }

    RikkaConfirmDialog(
        show = showResetDialog,
        title = stringResource(R.string.setting_palette_page_reset),
        confirmText = stringResource(android.R.string.ok),
        dismissText = stringResource(android.R.string.cancel),
        onConfirm = {
            updatePalette { PaletteSetting() }
            showResetDialog = false
        },
        onDismiss = { showResetDialog = false },
        text = { Text(stringResource(R.string.setting_palette_page_reset_message)) },
    )
}

/** 没启用调色板时也给出当前预设的颜色，方便直接打开编辑器改。 */
private fun PaletteSetting.effectiveColors(darkMode: Boolean): PaletteColors =
    colors(darkMode) ?: findPalettePreset(DEFAULT_PALETTE_ID).colors(darkMode)

private fun CardGroupScope.paletteSideItem(
    colors: PaletteColors,
    headline: String,
    onClick: () -> Unit,
) = item(
    onClick = onClick,
    leadingContent = { PaletteStrip(colors) },
    headlineContent = { Text(headline) },
    supportingContent = { Text(stringResource(R.string.setting_palette_page_custom_desc)) },
    trailingContent = { Icon(HugeIcons.PencilEdit01, contentDescription = stringResource(R.string.edit)) },
)

@Composable
private fun PaletteSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp),
    )
}

/** 五段色带：主色、辅色、第三色、错误色、表面变体色。 */
@Composable
private fun PaletteStrip(colors: PaletteColors, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val slices = listOf(
        colors.primary,
        colors.secondary,
        colors.tertiary,
        colors.error,
        colors.surfaceVariant,
    )
    Canvas(
        modifier = modifier
            .size(width = 56.dp, height = 28.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        val sliceWidth = size.width / slices.size
        slices.forEachIndexed { index, argb ->
            drawRect(
                color = Color(argb.toInt()),
                topLeft = Offset(x = index * sliceWidth, y = 0f),
                size = Size(sliceWidth, size.height),
            )
        }
    }
}

/** 展开后的完整配色预览：背景上摆一圈主要角色。 */
@Composable
private fun PalettePreview(colors: PaletteColors, darkMode: Boolean) {
    val scheme = remember(colors, darkMode) { colors.generateColorScheme(darkMode) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Aa", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Spacer(Modifier.weight(1f))
        listOf(
            scheme.primary,
            scheme.secondary,
            scheme.tertiary,
            scheme.error,
            scheme.surfaceVariant,
        ).forEach { color ->
            Canvas(modifier = Modifier.size(24.dp).clip(CircleShape)) {
                drawCircle(color = color)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteEditSheet(
    darkMode: Boolean,
    initial: PaletteColors,
    presetColors: PaletteColors,
    onDismiss: () -> Unit,
    onSave: (PaletteColors) -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var colors by remember(initial) { mutableStateOf(initial) }
    var editingRole by remember { mutableStateOf<PaletteRole?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (darkMode) R.string.setting_palette_page_dark_palette
                        else R.string.setting_palette_page_light_palette
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { colors = presetColors }) {
                    Text(stringResource(R.string.setting_palette_page_reset_side))
                }
            }

            Spacer(Modifier.height(12.dp))

            PalettePreview(colors = colors, darkMode = darkMode)

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                PaletteRole.entries.forEach { role ->
                    PaletteColorRow(
                        role = role,
                        argb = colors[role],
                        onClick = { editingRole = role },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(colors) }) {
                    Text(stringResource(R.string.setting_theme_page_save))
                }
            }
        }
    }

    editingRole?.let { role ->
        PaletteColorDialog(
            role = role,
            initial = colors[role],
            onDismiss = { editingRole = null },
            onConfirm = { argb ->
                colors = colors.with(role, argb)
                editingRole = null
            },
        )
    }
}

@Composable
private fun PaletteColorRow(
    role: PaletteRole,
    argb: Long,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(modifier = Modifier.size(28.dp).clip(CircleShape)) {
            drawCircle(color = Color(argb.toInt()))
        }
        Text(
            text = stringResource(role.labelRes()),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = argb.toHexCode(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PaletteColorDialog(
    role: PaletteRole,
    initial: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var color by remember(initial) { mutableStateOf(Color(initial.toInt())) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(role.labelRes())) },
        text = {
            ColorPickerRow(
                color = color,
                onColorChange = { color = it },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(color.argbLong()) }) {
                Text(stringResource(R.string.setting_theme_page_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@StringRes
private fun PaletteRole.labelRes(): Int = when (this) {
    PaletteRole.PRIMARY -> R.string.setting_theme_page_primary_color
    PaletteRole.ON_PRIMARY -> R.string.palette_role_on_primary
    PaletteRole.SECONDARY -> R.string.setting_theme_page_secondary_color
    PaletteRole.TERTIARY -> R.string.setting_theme_page_tertiary_color
    PaletteRole.ERROR -> R.string.palette_role_error
    PaletteRole.BACKGROUND -> R.string.palette_role_background
    PaletteRole.SURFACE -> R.string.palette_role_surface
    PaletteRole.SURFACE_VARIANT -> R.string.palette_role_surface_variant
    PaletteRole.ON_SURFACE -> R.string.palette_role_on_surface
}

private fun Long.toHexCode(): String = "#%06X".format(this and 0xFFFFFF)
