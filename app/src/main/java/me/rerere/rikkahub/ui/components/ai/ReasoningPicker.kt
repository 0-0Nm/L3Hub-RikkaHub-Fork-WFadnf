package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.flow.drop
import me.rerere.ai.core.ReasoningLevel
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Idea
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.rikkahub.R
import me.rerere.ui.components.ToggleSurface
import me.rerere.ui.icons.ReasoningHigh
import me.rerere.ui.icons.ReasoningLow
import me.rerere.ui.icons.ReasoningMedium
import kotlin.math.roundToInt

private val levels = ReasoningLevel.entries
private val levelCount = levels.size
private val levelShapes = levels.map { it.shape() }

@Composable
fun ReasoningButton(
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    reasoningLevel: ReasoningLevel,
    onUpdateReasoningLevel: (ReasoningLevel) -> Unit,
    modelName: String? = null,
    onOpenModelList: (() -> Unit)? = null,
    // 不关面板直接换到模型列表：交给下层把内容塞进同一个 sheet
    modelContent: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
    useL3HubStyle: Boolean = false,
) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        ReasoningPicker(
            reasoningLevel = reasoningLevel,
            onDismissRequest = { showPicker = false },
            onUpdateReasoningLevel = onUpdateReasoningLevel,
            modelName = modelName,
            onOpenModelList = onOpenModelList,
            modelContent = modelContent,
            useL3HubStyle = useL3HubStyle,
        )
    }

    ToggleSurface(
        checked = reasoningLevel.isEnabled,
        onClick = { showPicker = true },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .widthIn(min = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (useL3HubStyle) {
                    // L3Hub 样式用粗体文字，颜色跟档位对应
                    Text(
                        text = reasoningLevel.badgeText(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                        ),
                        color = reasoningLevel.indicatorColor(),
                        maxLines = 1,
                    )
                } else {
                    Icon(reasoningLevel.icon(), null)
                }
            }
            if (!onlyIcon) Text(stringResource(R.string.setting_provider_page_reasoning))
        }
    }
}

@Composable
fun ReasoningPicker(
    reasoningLevel: ReasoningLevel,
    onDismissRequest: () -> Unit = {},
    onUpdateReasoningLevel: (ReasoningLevel) -> Unit,
    modelName: String? = null,
    onOpenModelList: (() -> Unit)? = null,
    // 不关面板直接换到模型列表时用这个，参数是「关掉整个面板」的回调
    modelContent: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
    useL3HubStyle: Boolean = false,
) {
    // 面板内部切页：false 是思考强度，true 是模型列表
    var showModelList by remember { mutableStateOf(false) }
    val currentIndex = levels.indexOf(reasoningLevel).coerceAtLeast(0)
    val sliderState = remember {
        SliderState(
            value = currentIndex.toFloat(),
            trackRange = 0f..(levelCount - 1).toFloat(),
            steps = levelCount - 2,
        )
    }
    val interactionSource = remember { MutableInteractionSource() }
    val hapticFeedback = LocalHapticFeedback.current
    // 拖动过程中就跟随滑块预览，松手后才真正提交
    val previewLevel = levels[sliderState.value.roundToInt().coerceIn(0, levelCount - 1)]

    LaunchedEffect(currentIndex) {
        sliderState.value = currentIndex.toFloat()
    }

    LaunchedEffect(sliderState, useL3HubStyle) {
        if (!useL3HubStyle) {
            snapshotFlow { sliderState.value.roundToInt() }
                .drop(1)
                .collect { hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick) }
        }
    }

    ModalBottomSheet(
        // 返回键和点外部都走这里：先在面板内部退一级，退无可退才真关掉。
        // 这样即使 BackHandler 被 Dialog 自己吃了，行为也是对的
        onDismissRequest = {
            if (showModelList) showModelList = false else onDismissRequest()
        },
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) sheet@{
        // 在模型列表那一页按返回，先退回思考强度，别一下把整个面板关掉
        BackHandler(enabled = showModelList) {
            showModelList = false
        }
        // 从思考强度面板切到模型列表时不关 sheet，直接换内容
        // 两页要叠在同一块区域上，所以套了 Box；Box 里是 BoxScope，直接写 AnimatedVisibility
        // 会落到 sheet 内容的 ColumnScope 那个重载上、被布局的 DSL marker 挡掉，这里显式指回去
        Box(modifier = Modifier.animateContentSize().clipToBounds()) {
            this@sheet.AnimatedVisibility(
                visible = !showModelList,
                enter = slideInHorizontally { -it } + fadeIn(),
                exit = slideOutHorizontally { -it } + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (onOpenModelList != null || modelContent != null) {
                        Surface(
                            onClick = {
                                if (modelContent != null) {
                                    showModelList = true
                                } else {
                                    onDismissRequest()
                                    onOpenModelList?.invoke()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Brain02,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = modelName ?: stringResource(R.string.model_list_select_model),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = stringResource(R.string.reasoning_picker_model),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    PickerValueHeader(
                        title = stringResource(R.string.reasoning_picker_title),
                        value = previewLevel,
                        hint = stringResource(R.string.reasoning_picker_hint),
                        label = { it.label() },
                    ) {
                        // 等级越高形状越「激烈」
                        PickerHero(
                            shapes = levelShapes,
                            index = levels.indexOf(previewLevel),
                            icon = previewLevel.icon(),
                            letter = if (useL3HubStyle) previewLevel.letter() else null,
                            containerColor = when {
                                useL3HubStyle -> previewLevel.indicatorColor()
                                !previewLevel.isEnabled -> MaterialTheme.colorScheme.surfaceContainerHighest
                                previewLevel >= ReasoningLevel.HIGH -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                            contentColor = when {
                                useL3HubStyle -> Color.White
                                !previewLevel.isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                                previewLevel >= ReasoningLevel.HIGH -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        )
                    }

                    if (useL3HubStyle) {
                        LinearReasoningSlider(
                            level = previewLevel,
                            onPreview = { sliderState.value = levels.indexOf(it).toFloat() },
                            onCommit = { onUpdateReasoningLevel(it) },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    } else {
                        Slider(
                            state = sliderState,
                            onValueChange = { sliderState.value = it },
                            onValueChangeFinished = {
                                val snappedIndex = sliderState.value.roundToInt().coerceIn(0, levelCount - 1)
                                sliderState.value = snappedIndex.toFloat()
                                onUpdateReasoningLevel(levels[snappedIndex])
                            },
                            modifier = Modifier.fillMaxWidth(),
                            interactionSource = interactionSource,
                            thumb = {
                                SliderDefaults.Thumb(
                                    interactionSource = interactionSource,
                                    isVertical = false,
                                    thumbSize = DpSize(4.dp, 52.dp),
                                )
                            },
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    trackCornerSize = 12.dp,
                                    modifier = Modifier.height(40.dp),
                                )
                            }
                        )
                    }
                }
            }
            if (modelContent != null) {
                this@sheet.AnimatedVisibility(
                    visible = showModelList,
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut(),
                ) {
                    modelContent { onDismissRequest() }
                }
            }
        }
    }
}

private fun ReasoningLevel.icon(): ImageVector = when (this) {
    ReasoningLevel.OFF -> HugeIcons.Idea
    ReasoningLevel.AUTO -> HugeIcons.Idea01
    ReasoningLevel.MINIMAL -> ReasoningLow
    ReasoningLevel.LOW -> ReasoningLow
    ReasoningLevel.MEDIUM -> ReasoningMedium
    ReasoningLevel.HIGH -> ReasoningHigh
    ReasoningLevel.XHIGH -> ReasoningHigh
    ReasoningLevel.MAX -> ReasoningHigh
    ReasoningLevel.ULTRA -> ReasoningHigh
}

private fun ReasoningLevel.shape(): RoundedPolygon = when (this) {
    ReasoningLevel.OFF -> MaterialShapes.Circle
    ReasoningLevel.AUTO -> MaterialShapes.Cookie4Sided
    ReasoningLevel.MINIMAL -> MaterialShapes.Clover4Leaf
    ReasoningLevel.LOW -> MaterialShapes.Cookie6Sided
    ReasoningLevel.MEDIUM -> MaterialShapes.Cookie7Sided
    ReasoningLevel.HIGH -> MaterialShapes.Cookie9Sided
    ReasoningLevel.XHIGH -> MaterialShapes.Cookie12Sided
    ReasoningLevel.MAX -> MaterialShapes.SoftBurst
    ReasoningLevel.ULTRA -> MaterialShapes.Sunny
}

@Composable
private fun ReasoningLevel.label(): String = when (this) {
    ReasoningLevel.OFF -> stringResource(R.string.reasoning_off)
    ReasoningLevel.AUTO -> stringResource(R.string.reasoning_auto)
    ReasoningLevel.MINIMAL -> stringResource(R.string.reasoning_minimal)
    ReasoningLevel.LOW -> stringResource(R.string.reasoning_light)
    ReasoningLevel.MEDIUM -> stringResource(R.string.reasoning_medium)
    ReasoningLevel.HIGH -> stringResource(R.string.reasoning_heavy)
    ReasoningLevel.XHIGH -> stringResource(R.string.reasoning_xhigh)
    ReasoningLevel.MAX -> stringResource(R.string.reasoning_max)
    ReasoningLevel.ULTRA -> stringResource(R.string.reasoning_ultra)
}

private fun ReasoningLevel.badgeText(): String = when (this) {
    ReasoningLevel.OFF -> "OFF"
    ReasoningLevel.AUTO -> "AUTO"
    ReasoningLevel.MINIMAL -> "MIN"
    ReasoningLevel.LOW -> "LOW"
    ReasoningLevel.MEDIUM -> "MED"
    ReasoningLevel.HIGH -> "HIGH"
    ReasoningLevel.XHIGH -> "XHIGH"
    ReasoningLevel.MAX -> "MAX"
    ReasoningLevel.ULTRA -> "ULTRA"
}

private fun ReasoningLevel.letter(): String = when (this) {
    ReasoningLevel.OFF -> "O"
    ReasoningLevel.AUTO -> "A"
    ReasoningLevel.MINIMAL -> "MIN"
    ReasoningLevel.LOW -> "L"
    ReasoningLevel.MEDIUM -> "M"
    ReasoningLevel.HIGH -> "H"
    ReasoningLevel.XHIGH -> "X"
    ReasoningLevel.MAX -> "M"
    ReasoningLevel.ULTRA -> "U"
}

private fun ReasoningLevel.indicatorColor(): Color = when (this) {
    ReasoningLevel.OFF -> Color(0xFF8A8A8A)
    ReasoningLevel.AUTO -> Color(0xFF43A047)
    ReasoningLevel.MINIMAL -> Color(0xFF66BB6A)
    ReasoningLevel.LOW -> Color(0xFF7CB342)
    ReasoningLevel.MEDIUM -> Color(0xFFC0CA33)
    ReasoningLevel.HIGH -> Color(0xFFFFB300)
    ReasoningLevel.XHIGH -> Color(0xFFFF9800)
    ReasoningLevel.MAX -> Color(0xFFF4511E)
    ReasoningLevel.ULTRA -> Color(0xFFE53935)
}

@Composable
private fun LinearReasoningSlider(
    level: ReasoningLevel,
    onPreview: (ReasoningLevel) -> Unit,
    onCommit: (ReasoningLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()
    var previewIndex by remember { mutableIntStateOf(levels.indexOf(level).coerceAtLeast(0)) }
    LaunchedEffect(level) {
        previewIndex = levels.indexOf(level).coerceAtLeast(0)
    }

    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val letterStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
    val thumbLetterStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)

    fun select(index: Int, commit: Boolean) {
        val safeIndex = index.coerceIn(0, levels.lastIndex)
        if (safeIndex != previewIndex) {
            previewIndex = safeIndex
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onPreview(levels[safeIndex])
        }
        if (commit) onCommit(levels[safeIndex])
    }

    fun indexAt(x: Float, width: Int, start: Float): Int {
        val end = width - start
        if (end <= start) return previewIndex
        val fraction = ((x - start) / (end - start)).coerceIn(0f, 1f)
        return (fraction * levels.lastIndex).roundToInt()
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    select(indexAt(offset.x, size.width, 20.dp.toPx()), commit = true)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        select(indexAt(offset.x, size.width, 20.dp.toPx()), commit = false)
                    },
                    onDrag = { change, _ ->
                        select(indexAt(change.position.x, size.width, 20.dp.toPx()), commit = false)
                    },
                    onDragEnd = { onCommit(levels[previewIndex]) },
                    onDragCancel = { onCommit(levels[previewIndex]) },
                )
            },
    ) {
        val inset = 20.dp.toPx()
        val trackHeight = 10.dp.toPx()
        val trackY = 24.dp.toPx()
        val trackLeft = inset
        val trackWidth = (size.width - inset * 2f).coerceAtLeast(0f)
        val progress = previewIndex.toFloat() / levels.lastIndex
        val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(trackLeft, trackY - trackHeight / 2f),
            size = Size(trackWidth, trackHeight),
            cornerRadius = cornerRadius,
        )
        if (progress > 0f) {
            drawRoundRect(
                color = levels[previewIndex].indicatorColor(),
                topLeft = Offset(trackLeft, trackY - trackHeight / 2f),
                size = Size(trackWidth * progress, trackHeight),
                cornerRadius = cornerRadius,
            )
        }

        levels.forEachIndexed { index, item ->
            val x = trackLeft + trackWidth * index / levels.lastIndex
            val active = index == previewIndex
            val label = textMeasurer.measure(
                text = AnnotatedString(item.letter()),
                style = letterStyle.copy(
                    color = item.indicatorColor().copy(alpha = if (active) 1f else 0.55f),
                ),
            )
            drawText(
                textLayoutResult = label,
                topLeft = Offset(
                    x = x - label.size.width / 2f,
                    y = trackY + trackHeight / 2f + 10.dp.toPx(),
                ),
            )
        }

        val thumbX = trackLeft + trackWidth * progress
        val thumbRadius = 15.dp.toPx()
        val thumbColor = levels[previewIndex].indicatorColor()
        val thumbContentColor = Color.White
        drawCircle(color = thumbColor, radius = thumbRadius, center = Offset(thumbX, trackY))
        val thumbLetter = textMeasurer.measure(
            text = AnnotatedString(levels[previewIndex].letter()),
            style = thumbLetterStyle.copy(color = thumbContentColor),
        )
        drawText(
            textLayoutResult = thumbLetter,
            topLeft = Offset(
                x = thumbX - thumbLetter.size.width / 2f,
                y = trackY - thumbLetter.size.height / 2f,
            ),
        )
    }
}

@Composable
@Preview(showBackground = true)
private fun ReasoningPickerPreview() {
    MaterialTheme {
        var level by remember { mutableStateOf(ReasoningLevel.AUTO) }
        ReasoningPicker(
            reasoningLevel = level,
            onUpdateReasoningLevel = { level = it }
        )
    }
}
