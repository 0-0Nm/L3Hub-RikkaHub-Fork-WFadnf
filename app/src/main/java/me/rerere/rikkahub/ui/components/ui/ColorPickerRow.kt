package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.alphaComponent
import me.rerere.rikkahub.ui.theme.formatHexColor
import me.rerere.rikkahub.ui.theme.parseHexColor
import me.rerere.rikkahub.ui.theme.withAlpha
import kotlin.math.roundToInt

/** 色卡：常用色。前两排是预设在用的种子色，后两排是中性色。 */
private val SWATCHES: List<Long> = listOf(
    0xFF6750A4, 0xFF116682, 0xFF4C662B, 0xFF8B5000, 0xFF8E4955, 0xFF5C5C5C, 0xFFB3261E, 0xFF0061A4,
    0xFF7D5260, 0xFF386663, 0xFF625B71, 0xFF8C4A60, 0xFF146C2E, 0xFF8A5100, 0xFF4A4458, 0xFF3F5F90,
    0xFF1D1B20, 0xFF49454F, 0xFF79747E, 0xFFC4C7C5, 0xFFE7E0EC, 0xFFFEF7FF, 0xFFFFFFFF, 0xFF000000,
)

private const val SWATCHES_PER_ROW = 8

/**
 * 取色行：色卡 + 色号输入 + HSV/RGB 双模式滑杆，可选 A 通道。
 *
 * 色号输入框只在失焦时回写规范格式——以前每敲一个字符就被 `remember(argb)` 重置，
 * 粘贴 `#466E7A` 会被立刻改写成 `rgb(70 110 122)`，很难继续修改。
 */
@Composable
fun ColorPickerRow(
    color: Color,
    onColorChange: (Color) -> Unit,
    showAlpha: Boolean = false,
) {
    val argb = color.toArgb().toLong() and 0xFFFFFFFFL
    val hsv = remember(argb) { FloatArray(3).also { android.graphics.Color.colorToHSV(argb.toInt(), it) } }
    val hue = hsv[0]
    val saturation = hsv[1] * 100f
    val value = hsv[2] * 100f
    val red = android.graphics.Color.red(argb.toInt()).toFloat()
    val green = android.graphics.Color.green(argb.toInt()).toFloat()
    val blue = android.graphics.Color.blue(argb.toInt()).toFloat()
    val alpha = alphaComponent(argb)

    var mode by remember { mutableStateOf(ColorPickerMode.HSV) }
    var hexText by remember { mutableStateOf(formatHexColor(argb, forceAlpha = showAlpha)) }
    var hexEditing by remember { mutableStateOf(false) }
    var hexError by remember { mutableStateOf(false) }

    // 颜色从外面变化（拖动滑杆、点色卡）时同步输入框；正在输入时不打断。
    LaunchedEffect(argb, showAlpha) {
        if (!hexEditing) {
            hexText = formatHexColor(argb, forceAlpha = showAlpha)
            hexError = false
        }
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SWATCHES.chunked(SWATCHES_PER_ROW).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    row.forEach { swatch ->
                        SwatchDot(
                            argb = swatch,
                            selected = (swatch and 0xFFFFFFL) == (argb and 0xFFFFFFL),
                            onClick = { onColorChange(Color(swatch.toInt())) },
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ColorPreview(color)
            OutlinedTextField(
                value = hexText,
                onValueChange = { input ->
                    hexText = input
                    val parsed = parseHexColor(input)
                        ?: parseChannelCode(input, mode)?.toArgb()?.toLong()?.and(0xFFFFFFFFL)
                    hexError = parsed == null
                    if (parsed != null) onColorChange(Color(parsed.toInt()))
                },
                label = { Text("HEX") },
                placeholder = { Text(if (showAlpha) "#AARRGGBB" else "#RRGGBB") },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { state ->
                        hexEditing = state.isFocused
                        if (!state.isFocused) {
                            hexText = formatHexColor(argb, forceAlpha = showAlpha)
                            hexError = false
                        }
                    },
                singleLine = true,
                isError = hexError,
                supportingText = if (hexError) {
                    { Text("Use #RRGGBB, #AARRGGBB, rgb() or hsv()") }
                } else {
                    null
                },
            )
        }

        if (showAlpha) {
            ChannelSlider("A", alpha.toFloat(), 0f..255f) { newAlpha ->
                onColorChange(Color(withAlpha(argb, newAlpha.roundToInt()).toInt()))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == ColorPickerMode.HSV,
                onClick = { mode = ColorPickerMode.HSV },
                label = { Text("HSV") },
            )
            FilterChip(
                selected = mode == ColorPickerMode.RGB,
                onClick = { mode = ColorPickerMode.RGB },
                label = { Text("RGB") },
            )
        }

        when (mode) {
            ColorPickerMode.HSV -> {
                ChannelSlider("H", hue, 0f..360f) { h ->
                    onColorChange(Color(android.graphics.Color.HSVToColor(floatArrayOf(h, hsv[1], hsv[2]))).copy(alpha = color.alpha))
                }
                ChannelSlider("S", saturation, 0f..100f) { s ->
                    onColorChange(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, s / 100f, hsv[2]))).copy(alpha = color.alpha))
                }
                ChannelSlider("V", value, 0f..100f) { v ->
                    onColorChange(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, hsv[1], v / 100f))).copy(alpha = color.alpha))
                }
            }

            ColorPickerMode.RGB -> {
                ChannelSlider("R", red, 0f..255f) { onColorChange(color.copy(red = it / 255f)) }
                ChannelSlider("G", green, 0f..255f) { onColorChange(color.copy(green = it / 255f)) }
                ChannelSlider("B", blue, 0f..255f) { onColorChange(color.copy(blue = it / 255f)) }
            }
        }
    }
}

private enum class ColorPickerMode { HSV, RGB }

/** 一个色卡圆点，选中时描一圈深色边。 */
@Composable
private fun SwatchDot(
    argb: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(argb.toInt()))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

/** 带棋盘格的圆形预览：颜色带 alpha 时能看出透过去的底。 */
@Composable
private fun ColorPreview(color: Color) {
    Canvas(modifier = Modifier.size(44.dp).clip(CircleShape)) {
        val cell = size.minDimension / 6f
        for (row in 0..5) {
            for (column in 0..5) {
                drawRect(
                    color = if ((row + column) % 2 == 0) Color(0xFFE6E6E6) else Color(0xFFBDBDBD),
                    topLeft = Offset(column * cell, row * cell),
                    size = Size(cell, cell),
                )
            }
        }
        drawCircle(color = color)
    }
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(16.dp),
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
    }
}

private val colorNumberRegex = Regex("""[-+]?\d*\.?\d+""")

/** 兼容 rgb()/hsv() 三个数值的写法；色号解析交给 parseHexColor。 */
private fun parseChannelCode(value: String, mode: ColorPickerMode): Color? {
    val numbers = buildList {
        for (match in colorNumberRegex.findAll(value)) {
            add(match.value.toFloatOrNull() ?: return null)
            if (size == 3) break
        }
    }
    if (numbers.size != 3) return null

    return when (mode) {
        ColorPickerMode.HSV -> Color(
            android.graphics.Color.HSVToColor(
                floatArrayOf(
                    numbers[0].coerceIn(0f, 360f),
                    (numbers[1] / 100f).coerceIn(0f, 1f),
                    (numbers[2] / 100f).coerceIn(0f, 1f),
                )
            )
        )

        ColorPickerMode.RGB -> Color(
            android.graphics.Color.rgb(
                numbers[0].coerceIn(0f, 255f).roundToInt(),
                numbers[1].coerceIn(0f, 255f).roundToInt(),
                numbers[2].coerceIn(0f, 255f).roundToInt(),
            )
        )
    }
}
