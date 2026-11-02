package me.rerere.rikkahub.ui.components.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.TodoStatus
import me.rerere.rikkahub.ui.theme.adaptiveShape

/**
 * 会话顶部的任务清单卡片。清单存在会话上, 压缩上下文不会丢。
 * 折叠时只显示当前进行中的一项, 点开看全部。
 */
@Composable
internal fun ConversationTodoCard(
    todos: List<TodoItem>,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    if (todos.isEmpty()) return
    var expanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    val completed = todos.count { it.status == TodoStatus.COMPLETED }
    val current = todos.firstOrNull { it.status == TodoStatus.IN_PROGRESS }
        ?: todos.firstOrNull { it.status == TodoStatus.PENDING }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = adaptiveShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "任务",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$completed/${todos.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (expanded) "收起" else (current?.content ?: "全部完成"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // current 只可能是进行中或未开始（全部完成时是 null 走「全部完成」文案），
                    // 这里原来判断 COMPLETED 的分支永远不会成立
                    modifier = Modifier.weight(1f),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    todos.forEach { item -> TodoRow(item) }
                }
            }
        }
    }
}

@Composable
private fun TodoRow(item: TodoItem) {
    val done = item.status == TodoStatus.COMPLETED
    val running = item.status == TodoStatus.IN_PROGRESS
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = when {
                done -> "✓"
                running -> "▶"
                else -> "○"
            },
            style = MaterialTheme.typography.bodySmall,
            color = when {
                done -> MaterialTheme.colorScheme.primary
                running -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .size(16.dp)
                .background(Color.Transparent, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = item.content,
            style = MaterialTheme.typography.bodySmall,
            color = if (done) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
            fontWeight = if (running) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}
