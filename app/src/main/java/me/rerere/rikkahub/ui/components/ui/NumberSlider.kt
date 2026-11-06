package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 刻度很多的滑块旁边加一个数字输入框。
 *
 * 可选值超过 32 个时，拖动很难精确落到想要的值，直接输入更快。
 * 输入框自己保存草稿：[valueRange] 之外的数字先记在框里、不往回弹，失焦时再回到真实值。
 * 敲出一个落在区间里的数就算一次提交（会调 [onValueChangeFinished]，不必等松手），
 * 这样打完字直接退出页面也不会丢。
 */
@Composable
fun NumberSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    modifier: Modifier = Modifier,
    suffix: String = "",
    enabled: Boolean = true,
    // 默认每个整数一档；取值范围太大时传 0，免得轨道上画出一片密密麻麻的刻度
    steps: Int = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
    onValueChangeFinished: () -> Unit = {},
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    SliderWithNumberField(
        modifier = modifier,
        enabled = enabled,
        suffix = suffix,
        text = text,
        onTextChange = { input ->
            val digits = input.filter { it.isDigit() }.take(7)
            text = digits
            digits.toIntOrNull()?.let { parsed ->
                if (parsed in valueRange) {
                    onValueChange(parsed)
                    onValueChangeFinished()
                }
            }
        },
        // 失焦时回到真实值，避免留下一个不合法的草稿
        onTextFocusLost = { text = value.toString() },
    ) { sliderModifier ->
        Slider(
            value = value.coerceIn(valueRange.first, valueRange.last).toFloat(),
            onValueChange = { raw ->
                onValueChange(raw.roundToInt().coerceIn(valueRange.first, valueRange.last))
            },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = steps,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            modifier = sliderModifier,
        )
    }
}

/**
 * 比例值的版本：滑杆和输入框都按百分数走（0.45f 显示成 45）。
 *
 * 1% 算一格：40%..100% 就是 61 格，够多到需要输入框；滑杆本身仍是连续拖动的、不画刻度，手感不变。
 */
@Composable
fun PercentNumberSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: () -> Unit = {},
) {
    val percent = (value * 100).roundToInt()
    val minPercent = (valueRange.start * 100).roundToInt()
    val maxPercent = (valueRange.endInclusive * 100).roundToInt()
    var text by remember(percent) { mutableStateOf(percent.toString()) }
    SliderWithNumberField(
        modifier = modifier,
        enabled = enabled,
        suffix = "%",
        text = text,
        onTextChange = { input ->
            val digits = input.filter { it.isDigit() }.take(3)
            text = digits
            digits.toIntOrNull()?.let { parsed ->
                if (parsed in minPercent..maxPercent) {
                    onValueChange(parsed / 100f)
                    onValueChangeFinished()
                }
            }
        },
        onTextFocusLost = { text = percent.toString() },
    ) { sliderModifier ->
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            modifier = sliderModifier,
        )
    }
}

/** 滑杆占满剩下的宽度，输入框固定在最右边，两边都别抢对方的位置。 */
@Composable
private fun SliderWithNumberField(
    modifier: Modifier,
    enabled: Boolean,
    suffix: String,
    text: String,
    onTextChange: (String) -> Unit,
    onTextFocusLost: () -> Unit,
    slider: @Composable (Modifier) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        slider(Modifier.weight(1f))
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            enabled = enabled,
            modifier = Modifier
                .width(96.dp)
                .onFocusChanged { state ->
                    if (!state.isFocused) onTextFocusLost()
                },
            suffix = if (suffix.isNotBlank()) {
                { Text(suffix, style = MaterialTheme.typography.labelSmall) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}
