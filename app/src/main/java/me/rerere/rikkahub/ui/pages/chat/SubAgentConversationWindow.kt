package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.uuid.Uuid
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_COMPLETED
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_FAILED
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_RUNNING
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.message.ChatMessage
import org.koin.compose.koinInject

private const val PREVIEW_REFRESH_MS = 1200L

/**
 * 从任务树点开的子代理会话小窗：从屏幕上方滑下，占半屏，只读。
 * 会话还在跑时按固定间隔刷新，跑完就停；要输入或审批可以点「打开完整会话」。
 */
@Composable
fun SubAgentConversationWindow(
    conversationId: Uuid,
    onDismiss: () -> Unit,
    onOpenFull: () -> Unit,
) {
    val chatService: ChatService = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    var conversation by remember(conversationId) { mutableStateOf<Conversation?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(conversationId) {
        visible = true
        while (true) {
            conversation = runCatching { chatService.getConversation(conversationId) }.getOrNull()
            if (conversation?.subAgentState != SUB_AGENT_STATE_RUNNING) break
            delay(PREVIEW_REFRESH_MS)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(onClick = onDismiss),
            )
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.62f),
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 3.dp,
                ) {
                    val current = conversation
                    if (current == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.task_tree_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        // 窗口从屏幕顶部滑下，内容要避开状态栏
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = current.title.ifBlank { current.subAgentTask.orEmpty() },
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = stringResource(R.string.task_tree_open_full),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable(onClick = onOpenFull)
                                            .padding(vertical = 2.dp),
                                    )
                                }
                                StateDot(current.subAgentState)
                                Icon(
                                    imageVector = HugeIcons.Cancel01,
                                    contentDescription = stringResource(R.string.close),
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(20.dp)
                                        .clickable(onClick = onDismiss),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .size(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            )
                            MessagePreview(
                                conversation = current,
                                settings = settings,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            // 高度不够时也能点空白处关闭
            if (screenHeight <= 0.dp) {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun MessagePreview(
    conversation: Conversation,
    settings: me.rerere.rikkahub.data.datastore.Settings,
    modifier: Modifier = Modifier,
) {
    val assistant = remember(conversation.assistantId, settings.assistants) {
        settings.assistants.firstOrNull { it.id == conversation.assistantId }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(conversation.messageNodes.size, conversation.subAgentState) {
        if (conversation.messageNodes.isNotEmpty()) {
            listState.scrollToItem(conversation.messageNodes.lastIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(conversation.messageNodes, key = { _, node -> node.id }) { index, node ->
            ChatMessage(
                node = node,
                loading = conversation.subAgentState == SUB_AGENT_STATE_RUNNING &&
                    index == conversation.messageNodes.lastIndex,
                assistant = assistant,
                conversationId = conversation.id,
                lastMessage = index == conversation.messageNodes.lastIndex,
                messageIndex = index,
                onFork = {},
                onRegenerate = {},
                onEdit = {},
                onShare = {},
                onDelete = {},
                onUpdate = {},
            )
        }
    }
}

@Composable
private fun StateDot(state: String?) {
    val color = when (state) {
        SUB_AGENT_STATE_RUNNING -> MaterialTheme.colorScheme.tertiary
        SUB_AGENT_STATE_COMPLETED -> MaterialTheme.colorScheme.primary
        SUB_AGENT_STATE_FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape),
    )
}
