package me.rerere.rikkahub.ui.components.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Zap
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Guidance
import me.rerere.rikkahub.data.model.GuidanceMode
import me.rerere.rikkahub.data.model.MAX_TODO_ITEMS
import me.rerere.rikkahub.data.model.MAX_TODO_TEXT_CHARS
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.TodoStatus
import me.rerere.rikkahub.data.model.addTodo
import me.rerere.rikkahub.data.model.removeTodoAt
import me.rerere.rikkahub.data.model.setTodoStatusAt
import me.rerere.rikkahub.data.model.toggleTodoCompletedAt
import me.rerere.rikkahub.ui.terminal.Switch
import me.rerere.rikkahub.ui.theme.adaptiveShape
import kotlin.math.roundToInt

/**
 * 横屏三栏时清单与引导收进右栏的看板，输入区上方那张卡片不再重复。
 */
internal val LocalConversationStatusInSidePanel = staticCompositionLocalOf { false }

/** 卡片底部那组「是否插入 / 间隔」的取值 */
internal data class TodoReminderUi(
    val enabled: Boolean,
    val interval: Int,
)

/**
 * 会话顶部的运行状态卡片：任务清单和生效中的引导合成一张，共用一次折叠。
 *
 * 清单存在会话上，压缩上下文不会丢；引导存在收件箱里，只活在这一轮会话。
 * 折叠时只留一行摘要——有清单就显示正在做的那条，没有清单就显示第一条引导。
 *
 * [onTodosChange] 非空时开启手动模式：点标记完成/取消完成, 行尾「⋮」改状态、取消或移除,
 * 展开后底部能直接添加一条。写入走和 todo_write 同一个入口, 模型下一轮看到的就是改完的结果。
 * 子代理那张只读卡片传 null。
 */
@Composable
internal fun ConversationTodoCard(
    todos: List<TodoItem>,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    onTodosChange: ((List<TodoItem>) -> Unit)? = null,
    // 非空时展开区底部多一组开关和滑块，控制清单要不要插进请求、隔几步插一次
    reminder: TodoReminderUi? = null,
    onReminderChange: ((TodoReminderUi) -> Unit)? = null,
    // 生效中的引导，和清单共用这张卡片和同一个折叠状态
    guidances: List<Guidance> = emptyList(),
    onGuidanceToggleMode: (Guidance) -> Unit = {},
    onGuidanceRemove: (Guidance) -> Unit = {},
    // 顶部计划下拉由外部统一控制展开态；普通卡片仍走自己的 remember 状态。
    expandedOverride: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
) {
    if (todos.isEmpty() && guidances.isEmpty()) return
    var localExpanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    val expanded = expandedOverride ?: localExpanded
    fun setExpanded(next: Boolean) {
        if (onExpandedChange != null) onExpandedChange(next) else localExpanded = next
    }
    // 正在输入新任务时整张卡片不折叠，否则手指一划输入框就没了
    var adding by remember { mutableStateOf(false) }
    val hasTodos = todos.isNotEmpty()
    val editable = onTodosChange != null
    val completed = todos.count { it.status == TodoStatus.COMPLETED }
    val cancelled = todos.count { it.status == TodoStatus.CANCELLED }
    // 取消掉的项不进分母：用户取消了不该做的事，进度条不该因此永远差一格
    val countable = todos.size - cancelled
    val current = todos.firstOrNull { it.status == TodoStatus.IN_PROGRESS }
        ?: todos.firstOrNull { it.status == TodoStatus.PENDING }
    // 全做完和「剩下的都取消了」是两回事，别都说成「全部完成」
    val summary = when {
        expanded -> stringResource(R.string.chat_message_collapse_content)
        current != null -> current.content
        hasTodos && cancelled > 0 -> stringResource(R.string.todo_nothing_left)
        hasTodos -> stringResource(R.string.todo_all_done)
        else -> guidances.firstOrNull()?.displayTitle.orEmpty()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = adaptiveShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .clickable(enabled = !adding) { setExpanded(!expanded) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (hasTodos) R.string.todo_title else R.string.guidance_panel_title
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (countable > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "$completed/$countable",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 折叠时这一行就是摘要：还有活就显示正在做的那条，
                    // 一条不剩了才说「全部完成」或「已无可做项」
                    modifier = Modifier.weight(1f),
                )
                // 清单和引导同时在时，右边标一下引导有几条，省得展开才知道
                if (hasTodos && guidances.isNotEmpty()) {
                    Icon(
                        imageVector = HugeIcons.Zap,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = guidances.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    if (hasTodos) {
                        todos.forEachIndexed { index, item ->
                            TodoRow(
                                item = item,
                                editable = editable,
                                onToggle = { onTodosChange?.invoke(todos.toggleTodoCompletedAt(index)) },
                                onSetStatus = { status ->
                                    onTodosChange?.invoke(todos.setTodoStatusAt(index, status))
                                },
                                onRemove = { onTodosChange?.invoke(todos.removeTodoAt(index)) },
                            )
                        }
                        if (editable) {
                            TodoAddRow(
                                adding = adding,
                                onAddingChange = { adding = it },
                                full = todos.size >= MAX_TODO_ITEMS,
                                onAdd = { text -> onTodosChange?.invoke(todos.addTodo(text)) },
                            )
                        }
                        if (reminder != null) {
                            ReminderControls(
                                reminder = reminder,
                                onChange = { onReminderChange?.invoke(it) },
                            )
                        }
                    }
                    if (guidances.isNotEmpty()) {
                        if (hasTodos) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                            )
                        }
                        guidances.forEach { guidance ->
                            GuidanceRow(
                                guidance = guidance,
                                onToggleMode = { onGuidanceToggleMode(guidance) },
                                onRemove = { onGuidanceRemove(guidance) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 一条生效中的引导：整行可点切模式，右边叉取消 */
@Composable
private fun GuidanceRow(
    guidance: Guidance,
    onToggleMode: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleMode() }
            .padding(vertical = 3.dp),
    ) {
        Icon(
            imageVector = HugeIcons.Zap,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = guidance.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = guidance.content,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = stringResource(
                    if (guidance.mode == GuidanceMode.SINGLE) {
                        R.string.guidance_mode_single
                    } else {
                        R.string.guidance_mode_repeat
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        // 用卡片自己的小按钮：IconButton 自带 48dp 触摸区，会把这一行撑胖
        SmallIconAction(
            icon = HugeIcons.Cancel01,
            contentDescription = stringResource(R.string.guidance_remove),
            onClick = onRemove,
        )
    }
}

/** 清单插不插、隔几步插一次。放在展开区底部，折叠时不占地方 */
@Composable
private fun ReminderControls(
    reminder: TodoReminderUi,
    onChange: (TodoReminderUi) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.todo_reminder_enabled),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.todo_reminder_enabled_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = reminder.enabled,
                onCheckedChange = { onChange(reminder.copy(enabled = it)) },
            )
        }
        if (reminder.enabled) {
            val interval = reminder.interval.coerceIn(1, 4)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.todo_reminder_interval),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.todo_reminder_interval_value, interval),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = interval.toFloat(),
                onValueChange = { onChange(reminder.copy(interval = it.roundToInt().coerceIn(1, 4))) },
                valueRange = 1f..4f,
                steps = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TodoRow(
    item: TodoItem,
    editable: Boolean,
    onToggle: () -> Unit,
    onSetStatus: (TodoStatus) -> Unit,
    onRemove: () -> Unit,
) {
    val done = item.status == TodoStatus.COMPLETED
    val running = item.status == TodoStatus.IN_PROGRESS
    val cancelled = item.status == TodoStatus.CANCELLED
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                // 竖直 padding 不放在 editable 分支里：只读卡片（子代理那张）的行距全靠它撑开，
                // 少了它 1dp 的 spacedBy 会让整块挤成一团
                .padding(vertical = 3.dp)
                .then(
                    if (editable) {
                        // 整行都是「完成 / 撤回」的点击区：卡片很窄，只让 16dp 的标记可点会点不中
                        Modifier
                            .clip(adaptiveShape(10.dp))
                            .clickable(onClick = onToggle)
                    } else {
                        Modifier
                    }
                ),
        ) {
            Text(
                text = when {
                    done -> "✓"
                    running -> "▶"
                    cancelled -> "✕"
                    else -> "○"
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    done || running -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.content,
                style = MaterialTheme.typography.bodySmall,
                color = if (done || cancelled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (done || cancelled) TextDecoration.LineThrough else TextDecoration.None,
                fontWeight = if (running) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
        }
        if (editable) {
            TodoRowMenu(status = item.status, onSetStatus = onSetStatus, onRemove = onRemove)
        }
    }
}

@Composable
private fun TodoRowMenu(
    status: TodoStatus,
    onSetStatus: (TodoStatus) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        // 这里不用 IconButton：它自带 48dp 的最小触摸区，会把每一行都撑成 48dp，卡片立刻变胖
        SmallIconAction(
            icon = HugeIcons.MoreVertical,
            contentDescription = stringResource(R.string.todo_actions),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TodoMenuItem(
                text = stringResource(R.string.todo_mark_in_progress),
                icon = HugeIcons.Play,
                enabled = status != TodoStatus.IN_PROGRESS,
                onClick = {
                    expanded = false
                    onSetStatus(TodoStatus.IN_PROGRESS)
                },
            )
            TodoMenuItem(
                text = stringResource(R.string.todo_mark_not_started),
                icon = HugeIcons.Refresh01,
                enabled = status != TodoStatus.PENDING,
                onClick = {
                    expanded = false
                    onSetStatus(TodoStatus.PENDING)
                },
            )
            TodoMenuItem(
                text = stringResource(R.string.todo_cancel_task),
                icon = HugeIcons.Cancel01,
                enabled = status != TodoStatus.CANCELLED,
                onClick = {
                    expanded = false
                    onSetStatus(TodoStatus.CANCELLED)
                },
            )
            TodoMenuItem(
                text = stringResource(R.string.todo_remove_task),
                icon = HugeIcons.Delete02,
                destructive = true,
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
        }
    }
}

/** 卡片里的小图标按钮：比 IconButton 小一圈，触摸区还是 28dp 见方 */
@Composable
private fun SmallIconAction(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val base = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(15.dp),
            tint = if (enabled) base else base.copy(alpha = 0.38f),
        )
    }
}

@Composable
private fun TodoMenuItem(
    text: String,
    icon: ImageVector,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        colors = if (destructive) {
            val errorColor = MaterialTheme.colorScheme.error
            MenuDefaults.itemColors(textColor = errorColor, leadingIconColor = errorColor)
        } else {
            MenuDefaults.itemColors()
        },
        onClick = onClick,
    )
}

@Composable
private fun TodoAddRow(
    adding: Boolean,
    onAddingChange: (Boolean) -> Unit,
    full: Boolean,
    onAdd: (String) -> Unit,
) {
    if (full) {
        Text(
            text = stringResource(R.string.todo_limit_reached, MAX_TODO_ITEMS),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, top = 6.dp),
        )
        return
    }
    if (!adding) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(adaptiveShape(10.dp))
                .clickable { onAddingChange(true) }
                .padding(vertical = 5.dp),
        ) {
            Icon(
                imageVector = HugeIcons.Add01,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.todo_add),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }

    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    fun submit() {
        text.trim().takeIf { it.isNotEmpty() }?.let(onAdd)
        text = ""
        focusManager.clearFocus()
        onAddingChange(false)
    }
    // 点开就聚焦，少点一次
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(MAX_TODO_TEXT_CHARS) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = adaptiveShape(10.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.todo_new_task_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    innerTextField()
                }
            },
        )
        SmallIconAction(
            icon = HugeIcons.Tick02,
            contentDescription = stringResource(R.string.todo_add),
            enabled = text.isNotBlank(),
            tint = MaterialTheme.colorScheme.primary,
            onClick = { submit() },
        )
        SmallIconAction(
            icon = HugeIcons.Cancel01,
            contentDescription = stringResource(R.string.common_cancel),
            onClick = { onAddingChange(false) },
        )
    }
}
