package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.datastore.FloatingBubbleAnimation
import me.rerere.rikkahub.data.model.FloatingBubbleIcon
import me.rerere.rikkahub.data.model.FloatingCounterPalette
import me.rerere.rikkahub.ui.components.ui.EmojiPicker
import me.rerere.rikkahub.ui.components.ui.PercentNumberSlider
import me.rerere.rikkahub.ui.floating.BubbleIconContent
import me.rerere.rikkahub.ui.floating.BubblePresets
import me.rerere.rikkahub.ui.floating.FloatingBubblePreview
import me.rerere.rikkahub.ui.floating.FloatingCounterGeometry
import me.rerere.rikkahub.ui.floating.FloatingCounterVisuals
import me.rerere.rikkahub.ui.floating.FloatingPanelSizing
import me.rerere.rikkahub.ui.floating.bubblePresetIcon
import me.rerere.rikkahub.ui.terminal.OutlinedButton
import org.koin.compose.koinInject

/**
 * 悬浮球图标选择：内置图标、Emoji 和相册图片。
 *
 * 选中后立刻回调，怎么存由调用处决定。相册图片会先复制到应用私有目录，
 * 相册里的原件被删掉或改掉都不影响已经选好的图标。
 */
@Composable
internal fun FloatingBubbleIconPicker(
    current: FloatingBubbleIcon,
    /** 拖动预览草稿：大小滑块/动画选择改的是这份本地草稿，松手才通过 [onDraftChanged] 保存 */
    initialScale: Float = 1f,
    initialAnimation: FloatingBubbleAnimation = FloatingBubbleAnimation.FLOWER,
    initialCounterOffsetX: Float = 0f,
    initialCounterOffsetY: Float = 0f,
    initialCounterScale: Float = 1f,
    onDraftChanged: (scale: Float, animation: FloatingBubbleAnimation) -> Unit = { _, _ -> },
    onCounterOffsetChanged: (x: Float, y: Float) -> Unit = { _, _ -> },
    onCounterScaleChanged: (Float) -> Unit = {},
    onSelect: (FloatingBubbleIcon) -> Unit,
    onDismiss: () -> Unit,
    counterFontPath: String = "",
    importedFontPaths: List<String> = emptyList(),
    importedFontNames: List<String> = emptyList(),
    onCounterFontSelected: (String) -> Unit = {},
    counterPalette: FloatingCounterPalette = FloatingCounterPalette.Default,
    onCounterPaletteChanged: ((FloatingCounterPalette) -> FloatingCounterPalette) -> Unit,
) {
    val filesManager = koinInject<FilesManager>()
    val scope = rememberCoroutineScope()
    var showEmojiPicker by remember { mutableStateOf(false) }
    var fontMenuExpanded by remember { mutableStateOf(false) }
    var previewCount by remember { mutableStateOf(12) }
    // Keep raw text (including partial input) only for the lifetime of this sheet.
    // Swatches and preview always follow the same last valid setting.
    val counterHexDrafts = remember { mutableStateMapOf<Int, String>() }
    var pickingImage by remember { mutableStateOf(false) }
    var pickFailed by remember { mutableStateOf(false) }
    // 预览草稿：拖滑块实时变，松手/选中动画时提交
    var draftScale by remember { mutableFloatStateOf(FloatingPanelSizing.clampBubbleScale(initialScale)) }
    var draftAnimation by remember { mutableStateOf(initialAnimation) }
    var draftCounterOffsetX by remember(initialCounterOffsetX) {
        mutableFloatStateOf(FloatingCounterGeometry.clampOffset(initialCounterOffsetX))
    }
    var draftCounterOffsetY by remember(initialCounterOffsetY) {
        mutableFloatStateOf(FloatingCounterGeometry.clampOffset(initialCounterOffsetY))
    }
    var draftCounterScale by remember(initialCounterScale) {
        mutableFloatStateOf(FloatingCounterGeometry.clampCounterScale(initialCounterScale))
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickingImage = true
            pickFailed = false
            scope.launch {
                // 复制文件是同步 IO，别放在主线程
                val local = withContext(Dispatchers.IO) {
                    filesManager.saveBubbleIcon(uri)
                }
                pickingImage = false
                if (local == null) {
                    // 复制失败要说一声，不然点了相册像是没反应
                    pickFailed = true
                    return@launch
                }
                onSelect(FloatingBubbleIcon.Image(local.toString()))
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (showEmojiPicker) {
            EmojiPicker(
                onEmojiSelected = { emoji ->
                    onSelect(FloatingBubbleIcon.Emoji(emoji.emoji))
                    showEmojiPicker = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.floating_bubble_icon_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.floating_bubble_icon_preset),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconChoice(
                        selected = current is FloatingBubbleIcon.AppLogo,
                        label = stringResource(R.string.floating_bubble_icon_app_logo),
                        onClick = {
                            onSelect(FloatingBubbleIcon.AppLogo)
                        },
                    ) {
                        BubbleIconPreview(FloatingBubbleIcon.AppLogo)
                    }
                    BubblePresets.forEach { preset ->
                        IconChoice(
                            selected = current is FloatingBubbleIcon.Preset && current.name == preset.name,
                            label = preset.name,
                            onClick = {
                                onSelect(FloatingBubbleIcon.Preset(preset.name))
                            },
                        ) {
                            BubbleIconPreview(FloatingBubbleIcon.Preset(preset.name))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.floating_bubble_icon_custom),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { showEmojiPicker = true },
                        enabled = !pickingImage,
                    ) {
                        Text(stringResource(R.string.floating_bubble_icon_emoji))
                    }
                    OutlinedButton(
                        onClick = { imagePicker.launch("image/*") },
                        enabled = !pickingImage,
                    ) {
                        Text(stringResource(R.string.floating_bubble_icon_gallery))
                    }
                }
                if (pickFailed) {
                    Text(
                        text = stringResource(R.string.floating_bubble_icon_load_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                // ---- 实时预览 + 大小 + 动画（E335 需求 1/2）----
                // Controls apply to every pattern, independently of the preset grid.
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(R.string.floating_bubble_preview_section),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                // Room for the same invisible 3D x 2.4D guide at maximum bubble scale.
                                .height(180.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            // 与悬浮球同一套外壳/图案组件渲染；无任务副作用（不模拟运行状态以外的完成角标）
                            FloatingBubblePreview(
                                icon = current,
                                scale = draftScale,
                                animation = draftAnimation,
                                animating = true,
                                counterFontPath = counterFontPath.takeIf { it in importedFontPaths }.orEmpty(),
                                counterOffsetX = draftCounterOffsetX,
                                counterOffsetY = draftCounterOffsetY,
                                counterScale = draftCounterScale,
                                previewCount = previewCount,
                                counterPalette = counterPalette,
                            )
                        }
                        Text(
                            text = stringResource(R.string.floating_counter_font),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Box {
                            val fontIndex = importedFontPaths.indexOf(counterFontPath)
                            val selectedFontName = if (fontIndex >= 0) {
                                importedFontNames.getOrNull(fontIndex)?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.floating_counter_imported_font, fontIndex + 1)
                            } else stringResource(R.string.floating_counter_serif)
                            OutlinedButton(onClick = { fontMenuExpanded = true }) { Text(selectedFontName) }
                            DropdownMenu(
                                expanded = fontMenuExpanded,
                                onDismissRequest = { fontMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.floating_counter_serif)) },
                                    onClick = { onCounterFontSelected(""); fontMenuExpanded = false },
                                )
                                importedFontPaths.forEachIndexed { index, path ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(importedFontNames.getOrNull(index)?.takeIf { it.isNotBlank() }
                                                ?: stringResource(R.string.floating_counter_imported_font, index + 1))
                                        },
                                        onClick = { onCounterFontSelected(path); fontMenuExpanded = false },
                                    )
                                }
                            }
                        }
                        if (importedFontPaths.isEmpty()) {
                            Text(
                                text = stringResource(R.string.floating_counter_import_hint),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(stringResource(R.string.floating_counter_sample), style = MaterialTheme.typography.labelSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0, 12, 25, 50, 75, 100, 101).forEach { sample ->
                                FilterChip(
                                    selected = previewCount == sample,
                                    onClick = { previewCount = sample },
                                    label = { Text(sample.toString()) },
                                )
                            }
                        }
                        FloatingCounterPaletteEditor(
                            palette = counterPalette,
                            drafts = counterHexDrafts,
                            onHexChanged = { stop, input ->
                                counterHexDrafts[stop] = input
                                FloatingCounterPalette.normalizeHex(input)?.let { hex ->
                                    onCounterPaletteChanged { it.withHex(stop, hex) }
                                }
                            },
                            onReset = {
                                // This also clears invalid drafts when the colors already equal Default.
                                counterHexDrafts.clear()
                                onCounterPaletteChanged { FloatingCounterPalette.Default }
                            },
                        )
                        Text(
                            text = stringResource(R.string.floating_bubble_size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PercentNumberSlider(
                            value = draftScale,
                            // 拖动实时更新预览草稿
                            onValueChange = { scale ->
                                draftScale = FloatingPanelSizing.clampBubbleScale(scale)
                            },
                            // 松手（或输入框提交）再保存
                            onValueChangeFinished = { onDraftChanged(draftScale, draftAnimation) },
                            valueRange = FloatingPanelSizing.MIN_BUBBLE_SCALE..FloatingPanelSizing.MAX_BUBBLE_SCALE,
                        )
                        Text(
                            text = stringResource(R.string.floating_counter_offset_x),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PercentNumberSlider(
                            value = draftCounterOffsetX,
                            onValueChange = { draftCounterOffsetX = FloatingCounterGeometry.clampOffset(it) },
                            onValueChangeFinished = {
                                onCounterOffsetChanged(draftCounterOffsetX, draftCounterOffsetY)
                            },
                            valueRange = FloatingCounterGeometry.MIN_OFFSET..FloatingCounterGeometry.MAX_OFFSET,
                        )
                        Text(
                            text = stringResource(R.string.floating_counter_offset_y),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PercentNumberSlider(
                            value = draftCounterOffsetY,
                            onValueChange = { draftCounterOffsetY = FloatingCounterGeometry.clampOffset(it) },
                            onValueChangeFinished = {
                                onCounterOffsetChanged(draftCounterOffsetX, draftCounterOffsetY)
                            },
                            valueRange = FloatingCounterGeometry.MIN_OFFSET..FloatingCounterGeometry.MAX_OFFSET,
                        )
                        Text(
                            text = stringResource(R.string.floating_counter_size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PercentNumberSlider(
                            value = draftCounterScale,
                            onValueChange = { draftCounterScale = FloatingCounterGeometry.clampCounterScale(it) },
                            onValueChangeFinished = { onCounterScaleChanged(draftCounterScale) },
                            valueRange = FloatingCounterGeometry.MIN_COUNTER_SCALE..FloatingCounterGeometry.MAX_COUNTER_SCALE,
                        )
                        Text(
                            text = stringResource(R.string.floating_bubble_animation),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(FloatingBubbleAnimation.FLOWER, FloatingBubbleAnimation.PULSE).forEach { animation ->
                                FilterChip(
                                    selected = draftAnimation == animation,
                                    onClick = {
                                        draftAnimation = animation
                                        onDraftChanged(draftScale, draftAnimation)
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(
                                                if (animation == FloatingBubbleAnimation.FLOWER) {
                                                    R.string.floating_bubble_animation_flower
                                                } else {
                                                    R.string.floating_bubble_animation_pulse
                                                },
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }
            }
        }
    }
}

/** Raw text stays local; invalid/intermediate HEX never reaches palette persistence. */
@Composable
private fun FloatingCounterPaletteEditor(
    palette: FloatingCounterPalette,
    drafts: Map<Int, String>,
    onHexChanged: (stop: Int, input: String) -> Unit,
    onReset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.floating_counter_palette_title),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = stringResource(R.string.floating_counter_palette_format),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FloatingCounterPalette.stops.forEach { stop ->
            val input = drafts[stop] ?: palette.hexAt(stop)
            val invalid = FloatingCounterPalette.normalizeHex(input) == null
            OutlinedTextField(
                value = input,
                onValueChange = { onHexChanged(stop, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.floating_counter_palette_stop, stop)) },
                singleLine = true,
                isError = invalid,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                ),
                supportingText = if (invalid) {
                    { Text(stringResource(R.string.floating_counter_palette_invalid)) }
                } else null,
                // Decorative: the labeled text field already exposes the editable color.
                // On invalid input this intentionally shows the last valid effective color.
                trailingIcon = {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(FloatingCounterVisuals.colorArgb(stop, palette)))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                },
            )
        }
        OutlinedButton(onClick = onReset) {
            Text(stringResource(R.string.floating_counter_palette_reset))
        }
    }
}

@Composable
private fun IconChoice(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.size(width = 64.dp, height = 84.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(colors.secondaryContainer)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) colors.primary else colors.outlineVariant,
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
        )
    }
}

/** 选择器里的大预览，尺寸和悬浮球上的一致 */
@Composable
internal fun BubbleIconPreview(icon: FloatingBubbleIcon) {
    BubbleIconContent(
        icon = icon,
        size = 52.dp,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

/** 设置页列表项里的小预览 */
@Composable
internal fun FloatingBubbleIconThumb(icon: FloatingBubbleIcon, size: Dp) {
    BubbleIconContent(
        icon = icon,
        size = size,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}
