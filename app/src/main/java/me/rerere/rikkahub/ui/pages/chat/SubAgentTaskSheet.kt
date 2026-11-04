package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_COMPLETED
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_FAILED
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_RUNNING
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.message.ConversationTodoCard
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.ui.GlassSheetShape
import me.rerere.rikkahub.ui.components.ui.canGlassPane
import me.rerere.rikkahub.utils.JsonInstantPretty
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private data class TaskNode(
    val conversation: Conversation,
    val children: List<TaskNode>,
)

/** 扁平成左栏用的一列，depth 决定缩进 */
private data class FlatAgent(
    val node: TaskNode,
    val depth: Int,
)

private const val MAX_TREE_DEPTH = 6

@Composable
fun SubAgentTaskSheet(
    conversationId: Uuid,
    onDismiss: () -> Unit,
    onOpenConversation: (Uuid) -> Unit,
    // 底板玻璃的取样源；没有源时退回实色底，见 canGlassPane
    hazeState: HazeState? = null,
) {
    val repository: ConversationRepository = koinInject()
    var previewId by remember { mutableStateOf<Uuid?>(null) }
    val chatService: ChatService = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settingsFlow.collectAsState()
    val assistantNames = remember(settings.assistants) {
        settings.assistants.associate { it.id to it.name }
    }
    var tree by remember { mutableStateOf<TaskNode?>(null) }
    // 主会话还在生成时随时可能派发新的子代理；它停了我们才有理由停轮询
    val generating by chatService.getGenerationJobStateFlow(conversationId).collectAsState(initial = null)

    LaunchedEffect(conversationId) {
        var attempts = 0
        while (true) {
            val loaded = runCatching { loadTree(repository, chatService, conversationId) }.getOrNull()
            if (loaded != null) tree = loaded
            attempts++
            // 读失败先重试两次，别让一次异常把空态钉死
            if (loaded == null && attempts >= 3) break
            // 没有 running 的子代理、主会话也没在生成：树不会再变，别再每秒重读
            if (loaded != null && !loaded.hasRunning() && generating == null) break
            delay(1000)
        }
    }

    val flat = remember(tree) { tree?.let(::flattenTree).orEmpty() }
    var selectedId by remember(conversationId) { mutableStateOf(conversationId) }
    val selected = flat.firstOrNull { it.node.conversation.id == selectedId } ?: flat.firstOrNull()
    // 工具明细只为选中的会话加载：树里存的是不带消息的摘要
    val selectedConversationId = selected?.node?.conversation?.id
    var detail by remember(selectedConversationId) { mutableStateOf<Conversation?>(null) }
    LaunchedEffect(selectedConversationId) {
        val id = selectedConversationId ?: return@LaunchedEffect
        while (true) {
            val loaded = runCatching { chatService.getConversation(id) }.getOrNull()
            detail = loaded
            // 主会话的 subAgentState 恒为 null，生成期间要靠 generating 继续刷新工具明细
            val stillRunning = loaded?.subAgentState == SUB_AGENT_STATE_RUNNING ||
                (id == conversationId && generating != null)
            if (!stillRunning) break
            delay(1000)
        }
    }
    // 默认 sheetState 允许半展开，show() 会停到半屏：看起来就是「打开后往下掉一截」
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    // 高度第一帧就定死：树是异步读出来的，晚到的高度变化会让 sheet 重新测量、位置再跳一次
    val bodyHeight = minOf(560.dp, LocalConfiguration.current.screenHeightDp.dp * 0.75f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // 只改底板，sheet 自己的 scrim / 手势不变
        shape = GlassSheetShape,
        // 不要把 glassSurface 挂在 ModalBottomSheet 的 modifier 上：它带来的 clip/haze 图层
        // 会让 sheet 的锚定失效（面板跑到屏幕中间、不盖住底栏）。这里只把底色做成接近不透明的半透明。
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(
            alpha = if (canGlassPane(hazeState)) 0.96f else 1f,
        ),
        // 同抽屉：底色可能变透明，内容色显式钉住
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.task_tree_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (flat.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bodyHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.task_tree_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bodyHeight),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .width(140.dp)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(flat, key = { it.node.conversation.id }) { item ->
                            AgentRow(
                                item = item,
                                assistantName = assistantNames[item.node.conversation.assistantId].orEmpty(),
                                selected = item.node.conversation.id == selected?.node?.conversation?.id,
                                onClick = { selectedId = item.node.conversation.id },
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    )
                    if (selected != null) {
                        AgentDetail(
                            item = selected,
                            conversation = detail ?: selected.node.conversation,
                            assistantName = assistantNames[selected.node.conversation.assistantId].orEmpty(),
                            onOpen = { previewId = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
    previewId?.let { id ->
        SubAgentConversationWindow(
            conversationId = id,
            onDismiss = { previewId = null },
            onOpenFull = {
                previewId = null
                onOpenConversation(id)
            },
        )
    }
}

private fun flattenTree(
    node: TaskNode,
    depth: Int = 0,
): List<FlatAgent> = listOf(FlatAgent(node, depth)) + node.children.flatMap { child ->
    flattenTree(child, depth + 1)
}

private suspend fun loadTree(
    repository: ConversationRepository,
    chatService: ChatService,
    id: Uuid,
    depth: Int = 0,
    // 上层已经取到的摘要，避免每个子会话再查一次
    summary: Conversation? = null,
): TaskNode? = withContext(Dispatchers.IO) {
    // 摘要不读消息：树每秒刷新时不再为每个子会话 loadMessageNodes。
    // 根会话优先用内存里的那份（当前聊天页就是它），保证标题/状态最新。
    val conversation = (summary ?: if (depth == 0) {
        chatService.getConversation(id) ?: repository.getConversationSummary(id)
    } else {
        repository.getConversationSummary(id)
    })?.copy(messageNodes = emptyList()) ?: return@withContext null
    val children = if (depth >= MAX_TREE_DEPTH) {
        emptyList()
    } else {
        repository.getChildConversationSummaries(id).mapNotNull { child ->
            loadTree(repository, chatService, child.id, depth + 1, summary = child)
        }
    }
    TaskNode(conversation = conversation, children = children)
}

/** 树里还有没有在跑的任务，决定要不要继续轮询 */
private fun TaskNode.hasRunning(): Boolean =
    conversation.subAgentState == SUB_AGENT_STATE_RUNNING || children.any { it.hasRunning() }

/** 左栏：代理关系，一层缩进一级 */
@Composable
private fun AgentRow(
    item: FlatAgent,
    assistantName: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val conversation = item.node.conversation
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Column(
            modifier = Modifier.padding(
                start = (8 + item.depth * 10).dp,
                end = 6.dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(stateColor(conversation.subAgentState), CircleShape),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = assistantName.ifBlank { conversation.title }.ifBlank { "?" },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = conversation.title.ifBlank { conversation.subAgentTask.orEmpty() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 右栏：选中的代理的本体信息、任务和工具，工具新的在上 */
@Composable
private fun AgentDetail(
    item: FlatAgent,
    conversation: Conversation,
    assistantName: String,
    onOpen: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tools = remember(conversation.currentMessages) {
        conversation.currentMessages.flatMap { message ->
            message.parts.filterIsInstance<UIMessagePart.Tool>()
        }
    }
    val isSubAgent = conversation.parentConversationId != null

    LazyColumn(
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = assistantName.ifBlank { "?" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.task_tree_generation, item.depth + 1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    StateBadge(state = conversation.subAgentState)
                }
                Text(
                    text = conversation.title.ifBlank { conversation.subAgentTask.orEmpty() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isSubAgent) {
                    Text(
                        text = stringResource(R.string.task_tree_open),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onOpen(conversation.id) }
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }

        val task = conversation.subAgentTask
        if (!task.isNullOrBlank()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionTitle(stringResource(R.string.task_tree_task))
                    CodeBlock(text = task, maxLines = 10)
                }
            }
        }

        // 选中哪个会话就看哪个会话的清单：主会话和子代理各自有一份
        if (conversation.todos.isNotEmpty()) {
            item {
                // 卡片自己带「任务 x/y」标题，这里不再叠一层
                ConversationTodoCard(todos = conversation.todos, initiallyExpanded = true)
            }
        }

        item {
            SectionTitle(
                stringResource(R.string.task_tree_tools) + " · " + tools.size
            )
        }

        if (tools.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.task_tree_no_tools),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(
                items = tools.reversed(),
                key = { tool -> tool.toolCallId.ifBlank { tool.hashCode().toString() } },
            ) { tool ->
                ToolDetailRow(tool = tool)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ToolDetailRow(tool: UIMessagePart.Tool) {
    var open by remember(tool.toolCallId) { mutableStateOf(false) }
    val input = remember(tool.input) { formatToolInput(tool.input) }
    val output = remember(tool.output) { formatToolOutput(tool.output) }
    val running = !tool.isExecuted

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            onClick = { open = !open },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Tools,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = tool.toolName,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        if (running) R.string.task_tree_state_running else R.string.task_tree_state_completed
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
                Icon(
                    imageVector = if (open) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
        if (open) {
            Column(
                modifier = Modifier.padding(start = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (input.isNotBlank()) {
                    CodeBlock(text = input, maxLines = 12)
                }
                if (output.isNotBlank()) {
                    CodeBlock(text = output, maxLines = 16, emphasized = true)
                }
                if (input.isBlank() && output.isBlank()) {
                    Text(
                        text = stringResource(R.string.task_tree_no_tools),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(text: String, maxLines: Int, emphasized: Boolean = false) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflowing by remember(text) { mutableStateOf(false) }
    Surface(
        onClick = { if (overflowing) expanded = !expanded },
        color = if (emphasized) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                // 超出行数时点一下看全文，再点收起
                maxLines = if (expanded) Int.MAX_VALUE else maxLines,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                onTextLayout = { layout -> if (!expanded) overflowing = layout.hasVisualOverflow },
                modifier = Modifier.fillMaxWidth(),
            )
            if (overflowing) {
                Text(
                    text = stringResource(
                        if (expanded) R.string.task_tree_collapse else R.string.task_tree_expand
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun stateColor(state: String?): Color = when (state) {
    SUB_AGENT_STATE_RUNNING -> MaterialTheme.colorScheme.primary
    SUB_AGENT_STATE_COMPLETED -> MaterialTheme.colorScheme.tertiary
    SUB_AGENT_STATE_FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.outline
}

@Composable
private fun StateBadge(state: String?) {
    val (label, color) = when (state) {
        SUB_AGENT_STATE_RUNNING -> stringResource(R.string.task_tree_state_running) to MaterialTheme.colorScheme.primary
        SUB_AGENT_STATE_COMPLETED -> stringResource(R.string.task_tree_state_completed) to MaterialTheme.colorScheme.tertiary
        SUB_AGENT_STATE_FAILED -> stringResource(R.string.task_tree_state_failed) to MaterialTheme.colorScheme.error
        else -> return
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

private fun formatToolInput(raw: String): String {
    if (raw.isBlank()) return ""
    val element = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: return raw
    return runCatching {
        JsonInstantPretty.encodeToString(JsonElement.serializer(), element)
    }.getOrDefault(raw)
}

private fun formatToolOutput(parts: List<UIMessagePart>): String {
    val text = parts.filterIsInstance<UIMessagePart.Text>()
        .joinToString("\n") { it.text }
        .trim()
    if (text.isEmpty()) return ""
    val obj = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return text
    val exitCode = (obj["exitCode"] as? JsonPrimitive)?.contentOrNull
    val stdout = (obj["stdout"] as? JsonPrimitive)?.contentOrNull
    if (stdout != null) {
        return buildString {
            if (exitCode != null) appendLine("exit $exitCode")
            append(stdout.trim())
        }.trim()
    }
    return formatToolInput(text)
}
