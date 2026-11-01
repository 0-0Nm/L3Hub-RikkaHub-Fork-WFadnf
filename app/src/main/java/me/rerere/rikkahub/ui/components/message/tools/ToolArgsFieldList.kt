package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.richtext.CodeHighlightText
import me.rerere.rikkahub.ui.components.richtext.HighlightCodeBlock

/** 内联摘要里展开后最多显示的行数, 避免一张卡片吃掉整屏 */
private const val COMPACT_EXPANDED_LINES = 8

/**
 * 工具入参的单列字段列表: 一行一个参数
 *
 * 短值跟参数名同行右对齐; 多行内容或超长单行折叠成块, 默认只显示开头几行并可展开.
 * 入参解析失败时调用方不要用这个组件, 回落到原有的 JSON 展示.
 *
 * @param compact 内联摘要用, 收紧间距与字号
 * @param maxFields 最多展示几条参数, 多出来的只显示数量
 */
@Composable
fun ToolArgsFieldList(
    fields: List<ToolArgField>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    maxFields: Int = Int.MAX_VALUE,
) {
    if (fields.isEmpty()) return
    val visible = remember(fields, maxFields) {
        if (fields.size <= maxFields) fields else fields.take(maxFields)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
    ) {
        visible.forEach { field ->
            key(field.name) {
                ToolArgFieldRow(field = field, compact = compact)
            }
        }
        if (visible.size < fields.size) {
            Text(
                text = "+${fields.size - visible.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToolArgFieldRow(field: ToolArgField, compact: Boolean) {
    if (field.shape == ToolArgValueShape.Text) {
        InlineValueField(field = field, compact = compact)
    } else {
        BlockValueField(field = field, compact = compact)
    }
}

@Composable
private fun ArgName(name: String, compact: Boolean) {
    Text(
        text = name,
        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(min = 48.dp, max = if (compact) 108.dp else 144.dp),
    )
}

/** 单行值: 短值和参数名同行, 长值另起一行折叠 */
@Composable
private fun InlineValueField(field: ToolArgField, compact: Boolean) {
    val valueStyle = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    if (!field.needsOwnLine()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArgName(name = field.name, compact = compact)
            Text(
                text = field.value,
                style = valueStyle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    var expanded by remember(field.name, field.value) { mutableStateOf(false) }
    val collapsible = field.isCollapsible()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ArgName(name = field.name, compact = compact)
        Text(
            text = field.value,
            style = valueStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = collapsible) { expanded = !expanded },
        )
        if (collapsible) {
            ExpandToggle(expanded = expanded, compact = compact) { expanded = !expanded }
        }
    }
}

/** 代码/JSON 值: 单独成块, 折叠时只渲染开头几行, 展开后交给代码块组件 (带复制/下载) */
@Composable
private fun BlockValueField(field: ToolArgField, compact: Boolean) {
    val language = field.language ?: "plaintext"
    val collapsedLines = ToolArgsPresentation.COLLAPSED_LINES
    val collapsible = field.isCollapsible(collapsedLines)
    var expanded by remember(field.name, field.value, compact) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArgName(name = field.name, compact = compact)
            if (!compact && field.language != null) {
                Text(
                    text = field.language,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
        when {
            // 内联摘要展开时也给个行数上限, 否则一条长命令会把整张卡片撑开
            compact -> CodePreview(
                code = if (expanded) field.value else ToolArgsPresentation.previewOf(field, collapsedLines),
                language = language,
                maxLines = if (expanded) COMPACT_EXPANDED_LINES else collapsedLines,
            )

            expanded -> HighlightCodeBlock(
                code = field.value,
                language = language,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
            )

            else -> CodePreview(
                code = ToolArgsPresentation.previewOf(field, collapsedLines),
                language = language,
                maxLines = collapsedLines,
            )
        }
        if (collapsible) {
            ExpandToggle(expanded = expanded, compact = compact) { expanded = !expanded }
        }
    }
}

@Composable
private fun CodePreview(code: String, language: String, maxLines: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        CodeHighlightText(
            code = code,
            language = language,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ExpandToggle(expanded: Boolean, compact: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onToggle)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(if (compact) 12.dp else 14.dp),
        )
        Text(
            text = stringResource(if (expanded) R.string.code_block_collapse else R.string.code_block_expand),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
