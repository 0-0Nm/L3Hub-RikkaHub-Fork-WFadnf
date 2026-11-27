package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Guidance
import me.rerere.rikkahub.data.model.LiveGuidance
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.TodoStatus
import me.rerere.rikkahub.ui.components.ai.LiveGuidancePanel
import me.rerere.rikkahub.ui.components.message.ConversationTodoCard
import me.rerere.rikkahub.ui.components.message.TodoReminderUi
import kotlin.uuid.Uuid

/**
 * 右侧栏的「清单与引导」看板。
 *
 * 和输入区上方那张卡片是同一个 composable，只是换了个位置：横屏时收进右栏，
 * 输入区不再被清单压掉一截，列表也能展开看全。竖屏仍然走输入区那张。
 */
@Composable
internal fun ConversationStatusPane(
    todos: List<TodoItem>,
    guidances: List<Guidance>,
    assistant: Assistant,
    hideCompletedTodos: Boolean,
    onTodosChange: (List<TodoItem>) -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onGuidanceToggleMode: (Guidance) -> Unit,
    onGuidanceRemove: (Guidance) -> Unit,
    conversationId: Uuid,
    liveGuidance: LiveGuidance?,
    onLiveGuidanceModeChange: (Uuid, Boolean) -> Unit,
    onLiveGuidanceNextMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 和输入区那张一样：全部完成且开了开关时收起清单，引导不受影响
    val hideCompleted = hideCompletedTodos &&
        todos.isNotEmpty() && todos.all { it.status == TodoStatus.COMPLETED }
    val shownTodos = if (hideCompleted) emptyList() else todos

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (shownTodos.isEmpty() && guidances.isEmpty() && liveGuidance == null) {
            Text(
                text = stringResource(R.string.chat_page_status_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
        ConversationTodoCard(
            todos = shownTodos,
            // 看板本身就是展开的那一栏，进来直接看全，不再点一次箭头
            initiallyExpanded = true,
            onTodosChange = onTodosChange,
            reminder = if (shownTodos.isNotEmpty()) {
                TodoReminderUi(
                    enabled = assistant.todoReminderEnabled,
                    interval = assistant.todoReminderInterval,
                )
            } else {
                null
            },
            onReminderChange = { next ->
                onUpdateAssistant(
                    assistant.copy(
                        todoReminderEnabled = next.enabled,
                        todoReminderInterval = next.interval,
                    )
                )
            },
            guidances = guidances,
            onGuidanceToggleMode = onGuidanceToggleMode,
            onGuidanceRemove = onGuidanceRemove,
            modifier = Modifier.fillMaxWidth(),
        )
        LiveGuidancePanel(
            guidance = liveGuidance,
            onModeChange = onLiveGuidanceModeChange,
            onNextMessage = onLiveGuidanceNextMessage,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
