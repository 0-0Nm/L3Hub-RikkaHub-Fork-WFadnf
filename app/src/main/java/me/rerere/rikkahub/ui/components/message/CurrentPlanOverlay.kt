package me.rerere.rikkahub.ui.components.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.TodoStatus
import me.rerere.rikkahub.data.model.hasActiveTodo

/**
 * 当前进行中计划：贴在聊天页右上角现有组件下缘的小尾巴，点按向下展开。
 *
 * 这是覆盖层，不往 LazyColumn 或输入区插入任何高度，所以展开/收起都不会让聊天重排。
 * 只有真正存在 pending / in_progress 项才显示；全部完成或取消后，由回复内的历史卡接管。
 */
@Composable
internal fun CurrentPlanOverlay(
    todos: List<TodoItem>,
    assistant: Assistant?,
    onTodosChange: ((List<TodoItem>) -> Unit)?,
    onUpdateAssistant: ((Assistant) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (!todos.hasActiveTodo()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val completed = todos.count { it.status == TodoStatus.COMPLETED }
    val cancelled = todos.count { it.status == TodoStatus.CANCELLED }
    val countable = todos.size - cancelled

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Surface(
            shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.todo_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$completed/$countable",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Surface(
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp, topStart = 4.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 360.dp),
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        ConversationTodoCard(
                            todos = todos,
                            initiallyExpanded = true,
                            expandedOverride = true,
                            onExpandedChange = { expanded = it },
                            onTodosChange = onTodosChange,
                            reminder = assistant?.let {
                                TodoReminderUi(
                                    enabled = it.todoReminderEnabled,
                                    interval = it.todoReminderInterval,
                                )
                            },
                            onReminderChange = { next ->
                                val current = assistant
                                if (current != null && onUpdateAssistant != null) {
                                    onUpdateAssistant(
                                        current.copy(
                                            todoReminderEnabled = next.enabled,
                                            todoReminderInterval = next.interval,
                                        )
                                    )
                                }
                            },
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = false }
                                .padding(vertical = 6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.chat_message_collapse_content),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = HugeIcons.ArrowUp01,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
