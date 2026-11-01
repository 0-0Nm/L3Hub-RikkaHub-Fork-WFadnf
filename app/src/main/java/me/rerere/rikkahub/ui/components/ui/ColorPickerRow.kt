package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** HSV / RGB 双模式的取色行：色块 + 三个通道滑块 + 文本输入（支持 #RRGGBB）。 */
@Composable
fun ColorPickerRow(
    color: Color,
    onColorChange: (Color) -> Unit,
) {
    var mode by remember { mutableStateOf(ColorPickerMode.HSV) }
    val argb = color.toArgb()
    val hsv = remember(argb) { FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) } }
    val hue = hsv[0]
    val saturation = hsv[1] * 100f
    val value = hsv[2] * 100f
    val red = android.graphics.Color.red(argb).toFloat()
    val green = android.graphics.Color.green(argb).toFloat()
    val blue = android.graphics.Color.blue(argb).toFloat()

    var code by remember(argb, mode) {
        mutableStateOf(
            when (mode) {
                ColorPickerMode.HSV -> formatHsvCode(hue, saturation, value)
                ColorPickerMode.RGB -> formatRgbCode(red, green, blue)
            }
        )
    }
    var codeError by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                drawCircle(color = color)
            }
            Column(modifier = Modifier.weight(1f)) {
                when (mode) {
                    ColorPickerMode.HSV -> {
                        ChannelSlider("H", hue, 0f..360f) {
                            onColorChange(Color(android.graphics.Color.HSVToColor(floatArrayOf(it, hsv[1], hsv[2]))))
                        }
                        ChannelSlider("S", saturation, 0f..100f) {
                            onColorChange(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, it / 100f, hsv[2]))))
                        }
                        ChannelSlider("V", value, 0f..100f) {
                            onColorChange(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, hsv[1], it / 100f))))
                        }
                    }

                    ColorPickerMode.RGB -> {
                        ChannelSlider("R", red, 0f..255f) {
                            onColorChange(
                                Color(
                                    android.graphics.Color.rgb(it.roundToInt(), green.roundToInt(), blue.roundToInt())
                                )
                            )
                        }
                        ChannelSlider("G", green, 0f..255f) {
                            onColorChange(
                                Color(
                                    android.graphics.Color.rgb(red.roundToInt(), it.roundToInt(), blue.roundToInt())
                                )
                            )
                        }
                        ChannelSlider("B", blue, 0f..255f) {
                            onColorChange(
                                Color(
                                    android.graphics.Color.rgb(red.roundToInt(), green.roundToInt(), it.roundToInt())
                                )
                            )
                        }
                    }
                }
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

        OutlinedTextField(
            value = code,
            onValueChange = { input ->
                code = input
                val parsed = parseColorCode(input, mode)
                codeError = parsed == null
                parsed?.let(onColorChange)
            },
            label = { Text(mode.name) },
            placeholder = {
                Text(
                    when (mode) {
                        ColorPickerMode.HSV -> "hsv(200 43% 48%)"
                        ColorPickerMode.RGB -> "rgb(70 110 122)"
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = codeError,
            supportingText = if (codeError) {
                {
                    Text(
                        when (mode) {
                            ColorPickerMode.HSV -> "Use hsv(200 43% 48%) or #466E7A"
                            ColorPickerMode.RGB -> "Use rgb(70 110 122) or #466E7A"
                        }
                    )
                }
            } else {
                null
            },
        )
    }
}

private enum class ColorPickerMode { HSV, RGB }

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
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
    }
}

private val colorNumberRegex = Regex("""[-+]?\d*\.?\d+""")

/** 支持 rgb()/hsv() 三个数值，也支持 #RRGGBB */
private fun parseColorCode(value: String, mode: ColorPickerMode): Color? {
    val hex = value.trim().removePrefix("#")
    if (hex.length == 6 && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
        return hex.toLongOrNull(16)?.let { Color(0xFF000000.toInt() or it.toInt()) }
    }

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

private fun formatHsvCode(hue: Float, saturation: Float, value: Float): String =
    "hsv(${hue.roundToInt()} ${saturation.roundToInt()}% ${value.roundToInt()}%)"

private fun formatRgbCode(red: Float, green: Float, blue: Float): String =
    "rgb(${red.roundToInt()} ${green.roundToInt()} ${blue.roundToInt()})"
