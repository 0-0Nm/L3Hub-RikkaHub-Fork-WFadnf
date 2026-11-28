package me.rerere.rikkahub.ui.components.message

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.TodoStatus
import me.rerere.rikkahub.data.model.hasActiveTodo
import me.rerere.rikkahub.ui.components.ai.chatInputHaze
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents
import me.rerere.rikkahub.ui.theme.appButtonShadow

/**
 * One shape, haze pass, border and shadow for the EXISTING right pill and its tail.
 * The caller leaves the pill's original button row in place, but removes its background.
 * This body overlay starts at the measured pill top, not at Scaffold's content padding.
 * No list/input height is inserted. Completed/cancelled history is never an active plan.
 */
@Composable
internal fun CurrentPlanOverlay(
    todos: List<TodoItem>,
    assistant: Assistant?,
    onTodosChange: ((List<TodoItem>) -> Unit)?,
    onUpdateAssistant: ((Assistant) -> Unit)?,
    sessionKey: String,
    anchorWidth: Dp,
    anchorHeight: Dp,
    settings: Settings,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val active = todos.hasActiveTodo()
    var expanded by rememberSaveable(sessionKey, active) { mutableStateOf(false) }
    var headerSize by remember { mutableStateOf(IntSize.Zero) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val completed = todos.count { it.status == TodoStatus.COMPLETED }
    val countable = todos.count { it.status != TodoStatus.CANCELLED }
    val density = LocalDensity.current
    val square = LocalFullWidthComponents.current
    val shape = JoinedPlanShape(
        pillWidth = with(density) { anchorWidth.toPx() },
        pillHeight = with(density) { anchorHeight.toPx() },
        headerSize = headerSize,
        panelSize = panelSize,
        radius = with(density) { if (square) 0f else 14.dp.toPx() },
        squarePill = square,
    )
    val effect = settings.displaySetting.effectiveBackgroundEffect
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val emptyTitle = stringResource(R.string.todo_plan_empty_title)
    val title = if (active) stringResource(R.string.todo_title) else emptyTitle
    val progress = if (active) "$completed/$countable" else emptyTitle
    BackHandler(expanded) { expanded = false }

    Column(
        modifier = modifier
            .appButtonShadow(shape, 2.dp)
            .clip(shape)
            .background(if (effect == BackgroundEffectType.OFF) containerColor else Color.Transparent)
            .chatInputHaze(
                hazeState = hazeState,
                displaySetting = settings.displaySetting,
                containerColor = containerColor,
                shape = shape,
            )
            .then(if (effect == BackgroundEffectType.BLUR) Modifier.border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shape,
            ) else Modifier),
        horizontalAlignment = Alignment.End,
    ) {
        // Transparent space for the original top-bar buttons (their actions are unchanged).
        Spacer(Modifier.height(anchorHeight))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .onSizeChanged { headerSize = it }
                .semantics(mergeDescendants = true) { stateDescription = progress }
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (active) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = progress,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            modifier = Modifier.onSizeChanged { panelSize = it },
        ) {
            Column(modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(0.88f)) {
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (active) {
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
                                    onUpdateAssistant(current.copy(
                                        todoReminderEnabled = next.enabled,
                                        todoReminderInterval = next.interval,
                                    ))
                                }
                            },
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(emptyTitle, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.todo_plan_empty_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clickable(role = Role.Button) { expanded = false }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.chat_message_collapse_content),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(HugeIcons.ArrowUp01, null, modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** A union outline, not two adjacent surfaces. The bridge fills the pill's lower-right arc. */
private data class JoinedPlanShape(
    val pillWidth: Float,
    val pillHeight: Float,
    val headerSize: IntSize,
    val panelSize: IntSize,
    val radius: Float,
    val squarePill: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val right = size.width
        val pillRadius = if (squarePill) 0f else minOf(pillHeight / 2f, pillWidth / 2f)
        val pill = Path().apply {
            addRoundRect(RoundRect(Rect(right - pillWidth, 0f, right, pillHeight),
                CornerRadius(pillRadius, pillRadius)))
        }
        // Starting at the pill centre (not its curved bottom) removes the triangular hole.
        val headerBottom = (pillHeight + headerSize.height).coerceAtMost(size.height)
        val hasPanel = size.height > headerBottom + 0.5f && panelSize.width > 0
        val headerRadius = if (hasPanel) 0f else minOf(radius, headerSize.height / 2f)
        val tail = Path().apply {
            addRoundRect(RoundRect(
                left = right - headerSize.width, top = pillHeight / 2f,
                right = right, bottom = headerBottom,
                bottomLeftCornerRadius = CornerRadius(headerRadius, headerRadius),
                bottomRightCornerRadius = CornerRadius(headerRadius, headerRadius),
            ))
        }
        var joined = Path.combine(PathOperation.Union, pill, tail)
        if (hasPanel) {
            val panelRadius = minOf(radius, (size.height - headerBottom) / 2f)
            val panel = Path().apply {
                addRoundRect(RoundRect(
                    left = right - panelSize.width, top = headerBottom,
                    right = right, bottom = size.height,
                    bottomLeftCornerRadius = CornerRadius(panelRadius, panelRadius),
                    bottomRightCornerRadius = CornerRadius(panelRadius, panelRadius),
                ))
            }
            joined = Path.combine(PathOperation.Union, joined, panel)
        }
        return Outline.Generic(joined)
    }
}
