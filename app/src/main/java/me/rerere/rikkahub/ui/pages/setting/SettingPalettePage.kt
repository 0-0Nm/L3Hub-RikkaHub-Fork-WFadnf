package me.rerere.rikkahub.ui.pages.setting

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Download04
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Upload02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.export.PaletteSerializer
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
import me.rerere.rikkahub.ui.theme.PaletteGroup
import me.rerere.rikkahub.ui.theme.PaletteRole
import me.rerere.rikkahub.ui.theme.PaletteSetting
import me.rerere.rikkahub.ui.theme.UiSlot
import me.rerere.rikkahub.ui.theme.UiSlotColors
import me.rerere.rikkahub.ui.theme.UiSlots
import me.rerere.rikkahub.ui.theme.uiSlotsFrom
import me.rerere.rikkahub.ui.theme.argbLong
import me.rerere.rikkahub.ui.theme.findPalettePreset
import me.rerere.rikkahub.ui.theme.formatHexColor
import me.rerere.rikkahub.ui.theme.hexDigitCount
import me.rerere.rikkahub.ui.theme.isCompleteHexColor
import me.rerere.rikkahub.ui.theme.parseHexColor
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

    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = PaletteSerializer.exportToJson(settings.palette)
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)
                        ?.bufferedWriter()
                        ?.use { it.write(json) }
                        ?: error("no output stream")
                }.isSuccess
            }
            notify(
                context.getString(
                    if (saved) R.string.setting_palette_page_export_done
                    else R.string.setting_palette_page_failed
                )
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching { PaletteSerializer.import(context, uri).getOrThrow() }.getOrNull()
            }
            if (imported != null) {
                updatePalette { imported }
                notify(context.getString(R.string.setting_palette_page_import_done))
            } else {
                notify(context.getString(R.string.setting_palette_page_failed))
            }
        }
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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

            item("appliedScheme") {
                AppliedSchemeCard(title = stringResource(R.string.setting_palette_page_applied))
            }

            item("seedColor") {
                SeedColorCard(
                    onApply = { colors, dark ->
                        updatePalette {
                            it.copy(
                                presetId = it.presetId ?: DEFAULT_PALETTE_ID,
                                light = if (dark) it.light else colors,
                                dark = if (dark) colors else it.dark,
                            )
                        }
                    },
                )
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

            item("transfer") {
                CardGroup {
                    item(
                        onClick = { exportLauncher.launch(PaletteSerializer.getExportFileName(settings.palette)) },
                        leadingContent = { Icon(HugeIcons.Download04, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.setting_palette_page_export)) },
                        supportingContent = { Text(stringResource(R.string.setting_palette_page_export_desc)) },
                    )
                    item(
                        onClick = {
                            val json = PaletteSerializer.exportToJson(settings.palette)
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("palette", json))
                                )
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.setting_palette_page_copied)
                                )
                            }
                        },
                        leadingContent = { Icon(HugeIcons.Copy01, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.setting_palette_page_copy)) },
                        supportingContent = { Text(stringResource(R.string.setting_palette_page_copy_desc)) },
                    )
                    item(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        leadingContent = { Icon(HugeIcons.Upload02, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.setting_palette_page_import)) },
                        supportingContent = { Text(stringResource(R.string.setting_palette_page_import_desc)) },
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
            initial = palette.effectiveColors(dark).full(dark),
            presetColors = (preset ?: findPalettePreset(DEFAULT_PALETTE_ID)).colors(dark).full(dark),
            initialSlots = palette.slotColors(dark) ?: UiSlotColors(),
            onDismiss = { editingDarkMode = null },
            onSave = { colors, slots ->
                updatePalette {
                    it.copy(
                        presetId = it.presetId ?: DEFAULT_PALETTE_ID,
                        light = if (dark) it.light else colors,
                        dark = if (dark) colors else it.dark,
                        lightSlots = if (dark) it.lightSlots else slots,
                        darkSlots = if (dark) slots else it.darkSlots,
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
        colors.primary ?: 0xFF000000L,
        colors.secondary ?: 0xFF000000L,
        colors.tertiary ?: 0xFF000000L,
        colors.error ?: 0xFF000000L,
        colors.surfaceVariant ?: 0xFF000000L,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteEditSheet(
    darkMode: Boolean,
    initial: PaletteColors,
    presetColors: PaletteColors,
    initialSlots: UiSlotColors,
    onDismiss: () -> Unit,
    onSave: (PaletteColors, UiSlotColors) -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var colors by remember(initial) { mutableStateOf(initial) }
    var slots by remember(initialSlots) { mutableStateOf(initialSlots) }
    var editingRole by remember { mutableStateOf<PaletteRole?>(null) }
    var editingSlot by remember { mutableStateOf<UiSlot?>(null) }
    // 0 = 按位置（22 个），1 = 按颜色角色（48 个）
    var mode by rememberSaveable { mutableStateOf(0) }
    val effectiveSlots = remember(colors, slots, darkMode) {
        slots.overlay(uiSlotsFrom(colors.generateColorScheme(darkMode)))
    }

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
                TextButton(onClick = { colors = presetColors; slots = UiSlotColors() }) {
                    Text(stringResource(R.string.setting_palette_page_reset_side))
                }
            }

            Spacer(Modifier.height(8.dp))

            if (mode == 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { colors = colors.probe() },
                        label = { Text(stringResource(R.string.setting_palette_page_fill_probe)) },
                    )
                    AssistChip(
                        onClick = { colors = colors.filledWith(0xFFFF00FFL) },
                        label = { Text(stringResource(R.string.setting_palette_page_fill_magenta)) },
                    )
                    AssistChip(
                        onClick = { colors = colors.filledWith(0xFF000000L) },
                        label = { Text(stringResource(R.string.setting_palette_page_fill_black)) },
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.setting_palette_page_fill_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.setting_palette_page_roles_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            PaletteScreenPreview(slots = effectiveSlots)

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == 0,
                    onClick = { mode = 0 },
                    label = { Text(stringResource(R.string.setting_palette_page_mode_slots)) },
                )
                FilterChip(
                    selected = mode == 1,
                    onClick = { mode = 1 },
                    label = { Text(stringResource(R.string.setting_palette_page_mode_roles)) },
                )
            }

            var advancedOpen by rememberSaveable { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (mode == 0) {
                    UiSlot.entries.forEach { slot ->
                        SlotColorRow(
                            slot = slot,
                            argb = effectiveSlots.get(slot).argbLong(),
                            isCustom = slots.get(slot) != null,
                            onClick = { editingSlot = slot },
                        )
                    }
                } else {
                PaletteGroup.entries.forEach { group ->
                    val roles = PaletteRole.entries.filter { it.group == group }
                    if (roles.isEmpty()) return@forEach
                    val header = stringResource(group.labelRes())
                    if (group == PaletteGroup.ADVANCED) {
                        // 这一组在本项目里几乎没人引用，收起来免得面板太长
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { advancedOpen = !advancedOpen }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = header,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(
                                imageVector = if (advancedOpen) HugeIcons.ArrowDown01 else HugeIcons.ArrowRight01,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        if (!advancedOpen) return@forEach
                    } else {
                        Text(
                            text = header,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp),
                        )
                    }
                    roles.forEach { role ->
                        PaletteColorRow(
                            role = role,
                            argb = colors[role] ?: 0xFF000000L,
                            onClick = { editingRole = role },
                        )
                    }
                }
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
                Button(onClick = { onSave(colors, slots) }) {
                    Text(stringResource(R.string.setting_theme_page_save))
                }
            }
        }
    }

    editingSlot?.let { slot ->
        SlotColorDialog(
            slot = slot,
            initial = effectiveSlots.get(slot).argbLong(),
            onDismiss = { editingSlot = null },
            onConfirm = { argb ->
                slots = slots.with(slot, argb)
                editingSlot = null
            },
        )
    }

    editingRole?.let { role ->
        PaletteColorDialog(
            role = role,
            initial = colors[role] ?: 0xFF000000L,
            onDismiss = { editingRole = null },
            onConfirm = { argb ->
                colors = colors.with(role, argb)
                editingRole = null
            },
        )
    }
}

@Composable
private fun SlotColorRow(
    slot: UiSlot,
    argb: Long,
    isCustom: Boolean,
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
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(slot.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (isCustom) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.setting_palette_page_slot_custom),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = stringResource(slot.usageRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatHexColor(argb),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SlotColorDialog(
    slot: UiSlot,
    initial: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit,
) {
    var color by remember(initial) { mutableStateOf(Color(initial.toInt())) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(slot.labelRes))
                Text(
                    text = stringResource(slot.usageRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Box(Modifier.heightIn(max = 400.dp)) {
                ColorPickerRow(
                    color = color,
                    onColorChange = { color = it },
                    showAlpha = true,
                    scrollable = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(color.argbLong()) }) {
                Text(stringResource(R.string.setting_theme_page_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onConfirm(null) }) {
                    Text(stringResource(R.string.setting_palette_page_slot_follow_role))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
    )
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(role.labelRes),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(role.usageRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatHexColor(argb),
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
        title = {
            Column {
                Text(stringResource(role.labelRes))
                Text(
                    text = stringResource(role.usageRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            // 限高再让它自己滚：M3 的 AlertDialog 不会替内容滚动，
            // 把可滚动内容直接丢进 text 槽会滚得乱七八糟
            Box(Modifier.heightIn(max = 400.dp)) {
                ColorPickerRow(
                    color = color,
                    onColorChange = { color = it },
                    showAlpha = true,
                    scrollable = true,
                )
            }
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
private fun PaletteGroup.labelRes(): Int = when (this) {
    PaletteGroup.MAIN -> R.string.palette_group_main
    PaletteGroup.ACCENT -> R.string.palette_group_accent
    PaletteGroup.SURFACE -> R.string.palette_group_surface
    PaletteGroup.STATUS -> R.string.palette_group_status
    PaletteGroup.ADVANCED -> R.string.palette_group_advanced
}

/**
 * 用编辑中的配色画一屏假界面。
 *
 * 调色板里改一格要保存、退出、看真机、再进来，一轮十几秒还容易记错；
 * 这块把最常用的十几个角色一次性摆出来——顶栏、气泡、输入框、卡片、报错条——
 * 改哪个，对应那块立刻变。
 */
@Composable
private fun PaletteScreenPreview(slots: UiSlots) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(slots.pageBackground)
            .border(1.dp, slots.divider, RoundedCornerShape(20.dp)),
    ) {
        // 顶栏：surfaceContainer + onSurface
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(slots.topBar)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("聊天", style = MaterialTheme.typography.titleSmall, color = slots.titleText)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(10.dp).clip(CircleShape).background(slots.button))
        }

        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // AI 气泡：surfaceContainerHigh + onSurfaceVariant
            Text(
                text = "好的，我看看",
                style = MaterialTheme.typography.bodySmall,
                color = slots.bodyText,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(slots.aiBubble)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            // 用户气泡：primaryContainer + onPrimaryContainer
            Text(
                text = "把这段颜色调一下",
                style = MaterialTheme.typography.bodySmall,
                color = slots.userBubbleText,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(12.dp))
                    .background(slots.userBubble)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            // 卡片：surface + outline
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(slots.card)
                    .border(1.dp, slots.border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("设置项", style = MaterialTheme.typography.bodySmall, color = slots.titleText)
                Spacer(Modifier.weight(1f))
                Text("次要说明", style = MaterialTheme.typography.labelSmall, color = slots.bodyText)
            }
            // 报错条：errorContainer + onErrorContainer
            Text(
                text = "请求失败",
                style = MaterialTheme.typography.labelSmall,
                color = slots.errorOnBackground,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(slots.errorBackground)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            // 输入框：surfaceContainerHigh + outlineVariant
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(slots.aiBubble)
                    .border(1.dp, slots.divider, RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("输入消息…", style = MaterialTheme.typography.bodySmall, color = slots.bodyText)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(18.dp).clip(CircleShape).background(slots.button))
            }
        }
    }
}

/**
 * 当前真正生效的四个角色。
 *
 * 读的是 MaterialTheme.colorScheme，不是编辑面板里的临时值：保存后回到这一页，
 * 这块跟着变就说明颜色进到主题里了，不变就说明卡在别处——省得对着聊天页猜。
 * 从外到内分别是 background、surface、surfaceVariant，两种文字色和主色。
 */
@Composable
private fun AppliedSchemeCard(title: String) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.background)
                .padding(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.surface)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Aa", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                Text("正文", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.surfaceVariant),
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
            }
        }
    }
}

/**
 * 输入一个色号，当场展开成深浅两套色卡。
 *
 * 「应用」只写其中一侧：既有的自定义色不会被顺手清掉，想只调浅色就只点浅色。
 */
@Composable
private fun SeedColorCard(onApply: (PaletteColors, Boolean) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    // 只有能解释的写法才展开：半截色号每敲一位都重算四套配色，手机上会卡。
    // 三位简写算能解释——parseHexColor 本来就吃它，不认的话用户会以为坏了。
    val usable = hexDigitCount(input).let { it == 3 || it == 6 || it == 8 }
    val seed = remember(input, usable) { if (usable) parseHexColor(input) else null }
    val invalid = isCompleteHexColor(input) && seed == null
    val lightColors = remember(seed) { seed?.let { PaletteColors.fromSeed(it, dark = false) } }
    val darkColors = remember(seed) { seed?.let { PaletteColors.fromSeed(it, dark = true) } }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.setting_palette_page_seed_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.setting_palette_page_seed_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("HEX") },
                placeholder = { Text("#6750A4") },
                singleLine = true,
                isError = invalid,
                trailingIcon = if (input.isEmpty()) {
                    null
                } else {
                    {
                        IconButton(onClick = { input = "" }) {
                            Icon(HugeIcons.Cancel01, contentDescription = null)
                        }
                    }
                },
            )

            if (lightColors != null && darkColors != null) {
                SeedPreviewRow(
                    colors = lightColors,
                    dark = false,
                    label = stringResource(R.string.setting_palette_page_light_palette),
                )
                SeedPreviewRow(
                    colors = darkColors,
                    dark = true,
                    label = stringResource(R.string.setting_palette_page_dark_palette),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onApply(lightColors, false) }) {
                        Text(stringResource(R.string.setting_palette_page_apply_light))
                    }
                    TextButton(onClick = { onApply(darkColors, true) }) {
                        Text(stringResource(R.string.setting_palette_page_apply_dark))
                    }
                }
            }
        }
    }
}

/** 一行色卡预览：底色用 surfaceContainer，和真界面用的是同一个角色。 */
@Composable
private fun SeedPreviewRow(colors: PaletteColors, dark: Boolean, label: String) {
    val scheme = remember(colors, dark) { colors.generateColorScheme(dark) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.error).forEach { swatch ->
            Canvas(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
            ) {
                drawCircle(color = swatch)
            }
        }
    }
}
