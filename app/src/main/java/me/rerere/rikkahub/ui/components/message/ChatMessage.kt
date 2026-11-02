package me.rerere.rikkahub.ui.components.message

import me.rerere.rikkahub.ui.components.ai.chatInputHaze
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.hugeicons.HugeIcons
import coil3.compose.AsyncImage
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.Video01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.richtext.SelectableContent
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import me.rerere.rikkahub.ui.components.richtext.buildMarkdownPreviewHtml
import me.rerere.rikkahub.ui.components.ui.ChainOfThought
import me.rerere.rikkahub.ui.components.ui.Favicon
import me.rerere.rikkahub.ui.components.ui.FaviconRow
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalWorkspaceId
import me.rerere.rikkahub.ui.theme.LocalUiSlots
import me.rerere.rikkahub.ui.theme.LocalChatFontFamily
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents
import me.rerere.rikkahub.ui.theme.TerminalPalette
import me.rerere.rikkahub.ui.theme.adaptiveCornerRadius
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPaddingValue
import me.rerere.rikkahub.ui.theme.adaptiveShape
import me.rerere.rikkahub.ui.theme.rememberChatFontFamily
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.copyMessageToClipboard
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.toDp
import me.rerere.rikkahub.utils.urlDecode
import me.rerere.ui.charts.ChartCard
import me.rerere.ui.charts.ChartSpec
import me.rerere.ui.webview.WebViewContentCache
import java.util.Locale
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

// 一条消息内部各块（思考卡、正文气泡、附件、引用）之间的垂直间距。
// 这些块直接作为父 Column 的子项发射，缺了它卡片和气泡会像素级贴死。
private val MessageBlockGap = 4.dp

@Composable
fun ChatMessage(
    node: MessageNode,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    model: Model? = null,
    assistant: Assistant? = null,
    lastMessage: Boolean = false,
    messageIndex: Int = 0,
    // 与上一条消息同组（同角色连续）：气泡上沿收紧，头像/模型名只在组首出现
    joinedToPrevious: Boolean = false,
    // 与下一条消息同组：气泡下沿收紧
    joinedToNext: Boolean = false,
    onFork: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (MessageNode) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onTranslate: ((UIMessage, Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    hazeState: HazeState? = null,
    conversationId: Uuid? = null,
) {
    val message = node.messages[node.selectIndex]
    if (message.isContextCheckpoint) {
        ChatMessageContextCheckpoint(
            message = message,
            onEdit = onEdit,
            onDelete = onDelete,
            modifier = modifier,
        )
        return
    }
    if (message.isSubAgentNotice) {
        ChatMessageSubAgentNotice(message = message, modifier = modifier)
        return
    }
    val settings = LocalSettings.current.displaySetting
    val terminalStyle = settings.terminalStyle
    val chatFontFamily = LocalChatFontFamily.current ?: rememberChatFontFamily(settings)
    val textStyle = LocalTextStyle.current.copy(
        fontSize = LocalTextStyle.current.fontSize * settings.fontSizeRatio,
        lineHeight = LocalTextStyle.current.lineHeight * settings.fontSizeRatio,
        fontFamily = chatFontFamily
    )
    var showActionsSheet by remember { mutableStateOf(false) }
    var showSelectCopySheet by remember { mutableStateOf(false) }
    val navController = LocalNavController.current
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (terminalStyle) {
            Alignment.Start
        } else if (message.role == MessageRole.USER) {
            Alignment.End
        } else {
            Alignment.Start
        },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 同组后续消息不再重复头像/模型名，否则气泡之间会被头像行顶开，接不到一起。
        // 想让每条消息都显示头像，把 joinedToPrevious 从这个判断里去掉即可。
        val showMessageHeader = !message.parts.isEmptyUIMessage() &&
            (!joinedToPrevious || terminalStyle)
        if (showMessageHeader) {
            if (terminalStyle) {
                TerminalMessageHeader(
                    message = message,
                    model = model,
                    assistant = assistant,
                    loading = loading,
                    index = messageIndex,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    ChatMessageAssistantAvatar(
                        message = message,
                        model = model,
                        assistant = assistant,
                        loading = loading,
                        modifier = Modifier.weight(1f)
                    )
                    ChatMessageUserAvatar(
                        message = message,
                        avatar = settings.userAvatar,
                        nickname = settings.userNickname,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        val appSettings = LocalSettings.current
        val translation = message.translation
        var showTranslationPage by remember(message.id) { mutableStateOf(false) }
        var translating by remember(message.id) { mutableStateOf(false) }
        // 只有本次会话里从“无译文”变成“有译文”时才自动翻到译文页；
        // 从别的页面回来时消息本身就带着译文，应该留在原文
        var previousTranslation by remember(message.id) { mutableStateOf(translation) }
        LaunchedEffect(translation) {
            if (translation != null && translation != previousTranslation) {
                translating = false
                showTranslationPage = true
            }
            previousTranslation = translation
        }
        // 只负责查看已有译文，不靠横滑触发翻译。
        // 必须用 draggable(Horizontal)：detectDragGestures 会先吃掉触摸阈值事件，
        // 纵向滑动被它抢走后列表就滚不动了（译文消息划不动的根因）。
        var swipeX by remember(message.id) { mutableFloatStateOf(0f) }
        val triggerDistance = with(LocalDensity.current) { 96.dp.toPx() }
        val swipeModifier = if (translation == null) {
            Modifier
        } else {
            Modifier.draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> swipeX += delta },
                onDragStarted = { swipeX = 0f },
                onDragStopped = {
                    if (abs(swipeX) >= triggerDistance) {
                        showTranslationPage = swipeX < 0
                    }
                },
            )
        }
        CompositionLocalProvider(
            LocalTextStyle provides textStyle,
            LocalWorkspaceId provides assistant?.primaryWorkspaceId?.toString(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(swipeModifier)
            ) {
                AnimatedContent(
                    targetState = showTranslationPage && translation != null,
                    transitionSpec = {
                        if (targetState) {
                            (slideInHorizontally { it } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()) togetherWith
                                (slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "messageTranslationPage",
                ) { translated ->
                    if (translated && translation != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        ) {
                            MarkdownBlock(
                                content = translation,
                                onClickCitation = {},
                            )
                        }
                    } else {
                        if (settings.terminalStyle) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "│",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = TerminalPalette.GreenDim,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    MessagePartsBlock(
                                        assistant = assistant,
                                        role = message.role,
                                        parts = message.parts,
                                        annotations = message.annotations,
                                        loading = loading,
                                        model = model,
                                        onToolApproval = onToolApproval,
                                        onToolAnswer = onToolAnswer,
                                        onUserMessageClick = if (message.role == MessageRole.USER) onEdit else null,
                                        hazeState = hazeState,
                                        joinedToPrevious = joinedToPrevious,
                                        joinedToNext = joinedToNext,
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(MessageBlockGap),
                                horizontalAlignment = if (message.role == MessageRole.USER) {
                                    Alignment.End
                                } else {
                                    Alignment.Start
                                },
                            ) {
                                MessagePartsBlock(
                                    assistant = assistant,
                                    role = message.role,
                                    parts = message.parts,
                                    annotations = message.annotations,
                                    loading = loading,
                                    model = model,
                                    onToolApproval = onToolApproval,
                                    onToolAnswer = onToolAnswer,
                                    onUserMessageClick = if (message.role == MessageRole.USER) onEdit else null,
                                    hazeState = hazeState,
                                    joinedToPrevious = joinedToPrevious,
                                    joinedToNext = joinedToNext,
                                )
                            }
                        }
                    }
                }
            }
            if (translation != null || translating) {
            Text(
                text = stringResource(
                    when {
                        translating -> R.string.chat_message_translation_translating
                        showTranslationPage -> R.string.chat_message_translation_show_original
                        else -> R.string.chat_message_translation_show_translation
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable {
                        when {
                            translation != null -> showTranslationPage = !showTranslationPage
                            onTranslate != null && !translating -> {
                                translating = true
                                onTranslate(
                                    message,
                                    Locale.forLanguageTag(appSettings.translateTargetLanguage),
                                )
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 2.dp),
            )
            }
        }

        if (terminalStyle && !message.parts.isEmptyUIMessage()) {
            Text(
                text = "└" + "─".repeat(160),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = TerminalPalette.Border,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
            )
        }

        val showActions = if (lastMessage) {
            !loading
        } else {
            message.parts.isEmptyUIMessage().not()
        }

        val motionScheme = MaterialTheme.motionScheme
        if (terminalStyle) {
            if (showActions) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TerminalMessageAction("copy") { context.copyMessageToClipboard(message) }
                    TerminalMessageAction("retry") { onRegenerate() }
                    if (message.role == MessageRole.USER) {
                        TerminalMessageAction("edit", onEdit)
                    }
                    TerminalMessageAction("more") { showActionsSheet = true }
                }
            }
        } else AnimatedVisibility(
            visible = showActions,
            enter = slideInVertically(motionScheme.defaultSpatialSpec()) { it / 2 } +
                fadeIn(motionScheme.defaultEffectsSpec()),
            exit = slideOutVertically(motionScheme.fastSpatialSpec()) { it / 2 } +
                fadeOut(motionScheme.fastEffectsSpec())
        ) {
            Column(
                modifier = Modifier.animateContentSize()
            ) {
                ChatMessageActionButtons(
                    message = message,
                    onRegenerate = onRegenerate,
                    node = node,
                    onUpdate = onUpdate,
                    onOpenActionSheet = {
                        showActionsSheet = true
                    },
                    onTranslate = onTranslate,
                    onClearTranslation = onClearTranslation
                )
            }
        }

        EditedFilesList(
            parts = message.parts,
            assistant = assistant,
            conversationId = conversationId,
            isLastMessage = lastMessage,
        )

        ProvideTextStyle(textStyle) {
            ChatMessageNerdLine(message = message)
        }

    }
    if (showActionsSheet) {
        ChatMessageActionsSheet(
            message = message,
            onEdit = onEdit,
            onDelete = onDelete,
            onShare = onShare,
            onFork = onFork,
            model = model,
            onSelectAndCopy = {
                showSelectCopySheet = true
            },
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onWebViewPreview = {
                val textContent = message.parts
                    .filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()
                if (textContent.isNotBlank()) {
                    val htmlContent = buildMarkdownPreviewHtml(
                        context = context,
                        markdown = textContent,
                        colorScheme = colorScheme
                    )
                    val contentId = WebViewContentCache.store(context.cacheDir, htmlContent)
                    navController.navigate(Screen.WebView(contentId = contentId))
                }
            },
            onDismissRequest = {
                showActionsSheet = false
            }
        )
    }

    if (showSelectCopySheet) {
        ChatMessageCopySheet(
            message = message,
            hazeState = hazeState,
            onDismissRequest = {
                showSelectCopySheet = false
            }
        )
    }
}

// 最后一段正文之前的步骤整体折叠时显示的开关
@Composable
private fun EarlierStepsToggle(
    collapsed: Boolean,
    count: Int,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (collapsed) HugeIcons.ArrowDown01 else HugeIcons.ArrowUp01,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                if (collapsed) {
                    R.string.chat_message_expand_earlier_steps
                } else {
                    R.string.chat_message_collapse_earlier_steps
                },
                count,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// 思考链折叠时默认保留的尾部步骤数
private const val COLLAPSED_VISIBLE_STEPS = 2

@OptIn(FlowPreview::class)
@Composable
private fun MessagePartsBlock(
    assistant: Assistant?,
    role: MessageRole,
    model: Model?,
    parts: List<UIMessagePart>,
    annotations: List<UIMessageAnnotation>,
    loading: Boolean,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    onUserMessageClick: (() -> Unit)? = null,
    hazeState: HazeState? = null,
    joinedToPrevious: Boolean = false,
    joinedToNext: Boolean = false,
) {
    val context = LocalContext.current
    // 消息输出HapticFeedback
    val hapticFeedback = LocalHapticFeedback.current
    val settings = LocalSettings.current
    val partsState by rememberUpdatedState(parts)

    val handleClickCitation: (String) -> Unit = remember {
        handler@{ citationId ->
            partsState.forEach { part ->
                if (part is UIMessagePart.Tool && part.toolName == "search_web" && part.isExecuted) {
                    val outputText = part.output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
                    val items =
                        runCatching { JsonInstant.parseToJsonElement(outputText).jsonObject["items"]?.jsonArray }.getOrNull()
                            ?: return@forEach
                    items.forEach { item ->
                        val id = item.jsonObject["id"]?.jsonPrimitive?.content ?: return@forEach
                        val url = item.jsonObject["url"]?.jsonPrimitive?.content ?: return@forEach
                        if (citationId == id) {
                            context.openUrl(url)
                            return@handler
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(settings.displaySetting) {
        snapshotFlow { partsState }
            .debounce(50.milliseconds)
            .collect { parts ->
                if (parts.isNotEmpty() && loading && settings.displaySetting.enableMessageGenerationHapticEffect) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                }
            }
    }

    // 用户消息里的图片/文件统一收进气泡里的矩形卡片，文本再长也不会把它们挤没
    if (role == MessageRole.USER && !settings.displaySetting.terminalStyle) {
        UserMessageContent(
            parts = parts,
            assistant = assistant,
            onClick = onUserMessageClick,
            onClickCitation = handleClickCitation,
            hazeState = hazeState,
            joinedToPrevious = joinedToPrevious,
            joinedToNext = joinedToNext,
        )
        return
    }

    // Render parts in original order (group thinking/tool as chain-of-thought)
    val groupedParts = remember(parts) { parts.groupMessageParts() }
    // 最后一段正文之前的内容整体再折叠一层；展开后思考和工具仍按原来的方式折叠
    val lastBodyIndex = groupedParts.indexOfLast { block ->
        block is MessagePartBlock.ContentBlock && (block.part as? UIMessagePart.Text)?.text?.isNotBlank() == true
    }
    // 流式过程中也参与折叠：正在输出的正文留在外面，之前的思考和工具先收起来
    val blocksBeforeBody = if (role == MessageRole.ASSISTANT) {
        when {
            lastBodyIndex > 0 -> groupedParts.subList(0, lastBodyIndex)
            // 还没有正文（只有一段思考或一次工具调用）时整段内容都参与折叠
            lastBodyIndex <= 0 && groupedParts.isNotEmpty() &&
                groupedParts.none { it is MessagePartBlock.ContentBlock } -> groupedParts
            else -> emptyList()
        }
    } else {
        emptyList()
    }
    // 正文之前的思考/工具内容整体再折叠一层；单个纯思考链或单张工具卡也走这层折叠
    val hiddenBlocks = blocksBeforeBody
    // 等待审批的工具不能藏起来，否则用户看不到审批按钮
    val hiddenPendingTool = hiddenBlocks.any { block ->
        block is MessagePartBlock.ThinkingBlock && block.steps.any { it is ThinkingStep.ToolStep && it.tool.isPending }
    }
    val collapsibleCount = if (hiddenPendingTool) 0 else hiddenBlocks.size
    // 不能按 parts 记住：流式时每个 chunk 都会重建列表，展开状态会被重置
    var showEarlierSteps by remember { mutableStateOf(false) }

    if (collapsibleCount > 0) {
        EarlierStepsToggle(
            collapsed = !showEarlierSteps,
            count = collapsibleCount,
            onToggle = { showEarlierSteps = !showEarlierSteps },
        )
    }
    groupedParts.fastForEachIndexed { index, block ->
        if (collapsibleCount > 0 && !showEarlierSteps && index < collapsibleCount) {
            return@fastForEachIndexed
        }
        when (block) {
            is MessagePartBlock.ThinkingBlock -> {
                if (block.steps.isNotEmpty()) {
                    // 等待审批的工具不能折叠起来，否则用户看不到审批按钮，这一轮会一直卡在等待审批
                    val firstPendingIndex = block.steps.indexOfFirst { it is ThinkingStep.ToolStep && it.tool.isPending }
                    val collapsedVisibleCount = if (firstPendingIndex == -1) {
                        COLLAPSED_VISIBLE_STEPS
                    } else {
                        maxOf(COLLAPSED_VISIBLE_STEPS, block.steps.size - firstPendingIndex)
                    }
                    val chainEffect = settings.displaySetting.effectiveBackgroundEffect
                    val chainShape = adaptiveShape(20.dp)
                    val chainBaseColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ChainOfThought(
                        modifier = Modifier
                            .animateContentSize()
                            .then(
                                if (chainEffect != BackgroundEffectType.OFF && hazeState != null) {
                                    Modifier
                                        .clip(chainShape)
                                        .chatInputHaze(
                                            hazeState = hazeState,
                                            displaySetting = settings.displaySetting,
                                            containerColor = chainBaseColor,
                                            shape = chainShape,
                                        )
                                } else {
                                    Modifier
                                }
                            ),
                        steps = block.steps,
                        collapsedVisibleCount = collapsedVisibleCount,
                        border = if (chainEffect == BackgroundEffectType.BLUR) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        } else {
                            null
                        },
                        cardColors = CardDefaults.cardColors(
                            containerColor = when (chainEffect) {
                                BackgroundEffectType.OFF -> chainBaseColor.copy(alpha = settings.displaySetting.bubbleOpacity)
                                BackgroundEffectType.BLUR -> chainBaseColor.copy(alpha = settings.displaySetting.blurOpacity)
                                BackgroundEffectType.GLASS -> Color.Transparent
                            },
                        ),
                    ) { step ->
                        when (step) {
                            is ThinkingStep.ReasoningStep -> {
                                key(step.reasoning.createdAt) {
                                    ChatMessageReasoningStep(
                                        reasoning = step.reasoning,
                                        model = model,
                                        assistant = assistant,
                                    )
                                }
                            }

                            is ThinkingStep.ToolStep -> {
                                key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                                    ChatMessageToolStep(
                                        tool = step.tool,
                                        loading = loading && !step.tool.isExecuted,
                                        assistant = assistant,
                                        onToolApproval = onToolApproval,
                                        onToolAnswer = onToolAnswer,
                                    )
                                }
                            }

                            is ThinkingStep.ServerToolStep -> {
                                key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                                    ChatMessageServerToolStep(tool = step.tool)
                                }
                            }
                        }
                    }
                }
            }

            is MessagePartBlock.ChartBlock -> key(block.index) {
                val spec = remember(block.tool.input) { ChartSpec.fromJson(block.tool.inputAsJson()) }
                spec?.let { ChartCard(spec = it) }
            }

            is MessagePartBlock.ContentBlock -> key(block.index) {
                when (val part = block.part) {
                    is UIMessagePart.Text -> {
                        // 只有正在生成的最后一段文字可能停在没写完的语法上
                        val streamingText = loading && block.index == parts.lastIndex
                        // 正则替换只取决于正文和助手配置：本帧没有变化的旧消息不必再扫一遍全文
                        val renderedText = if (role == MessageRole.USER) {
                            part.text
                        } else {
                            remember(part.text, assistant?.regexes) {
                                part.text.replaceRegexes(
                                    assistant = assistant,
                                    scope = AssistantAffectScope.ASSISTANT,
                                    visual = true,
                                )
                            }
                        }
                        val textContent = @Composable {
                            if (role == MessageRole.USER) {
                                if (settings.displaySetting.terminalStyle) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateContentSize(),
                                    ) {
                                        CollapsibleUserMessageText(
                                            text = part.text,
                                            assistant = assistant,
                                            onClickCitation = handleClickCitation,
                                        )
                                    }
                                } else {
                                    ChatMessageBubble(
                                        role = role,
                                        color = LocalUiSlots.current.userBubble.copy(alpha = settings.displaySetting.bubbleOpacity),
                                        hazeState = hazeState,
                                        joinedToPrevious = joinedToPrevious,
                                        joinedToNext = joinedToNext,
                                        onClick = { onUserMessageClick?.invoke() },
                                        // 用户气泡不占满整行，和助手的回复错开；全宽模式下贴到边
                                        modifier = Modifier
                                            .padding(start = adaptiveHorizontalPaddingValue(24.dp))
                                            .animateContentSize(),
                                    ) {
                                        CollapsibleUserMessageText(
                                            text = part.text,
                                            assistant = assistant,
                                            onClickCitation = handleClickCitation,
                                        )
                                    }
                                }
                            } else {
                                if (settings.displaySetting.terminalStyle) {
                                    TerminalTypewriterMarkdown(
                                        content = renderedText,
                                        streaming = streamingText,
                                        onClickCitation = handleClickCitation,
                                    )
                                } else if (settings.displaySetting.showAssistantBubble) {
                                    ChatMessageBubble(
                                        role = role,
                                        color = LocalUiSlots.current.aiBubble.copy(alpha = settings.displaySetting.bubbleOpacity),
                                        hazeState = hazeState,
                                        joinedToPrevious = joinedToPrevious,
                                        joinedToNext = joinedToNext,
                                        modifier = if (streamingText) Modifier else Modifier.animateContentSize(),
                                    ) {
                                        MarkdownBlock(
                                            content = renderedText,
                                            streaming = streamingText,
                                            onClickCitation = handleClickCitation,
                                        )
                                    }
                                } else {
                                    MarkdownBlock(
                                        content = renderedText,
                                        streaming = streamingText,
                                        onClickCitation = handleClickCitation,
                                        modifier = if (streamingText) {
                                            Modifier
                                        } else {
                                            Modifier.animateContentSize()
                                        }
                                    )
                                }
                            }
                        }

                        // Markdown 由 BetterText 渲染，要用它自己的选择容器；生成中也可以选择
                        SelectableContent {
                            textContent()
                        }
                    }

                    is UIMessagePart.Video -> {
                        ChatMessageMediaTile(
                            icon = HugeIcons.Video01,
                            onClick = { context.openLocalFile(part.url) },
                        )
                    }

                    is UIMessagePart.Audio -> {
                        ChatMessageMediaTile(
                            icon = HugeIcons.MusicNote03,
                            onClick = { context.openLocalFile(part.url) },
                        )
                    }

                    is UIMessagePart.Image -> {
                        val isImageLoading =
                            part.url.isBlank() || part.url.matches(Regex("^data:image/[^;]*;base64,\\s*$"))
                        if (isImageLoading) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .shimmer(isLoading = true)
                            )
                        } else {
                            ZoomableAsyncImage(
                                model = part.url,
                                contentDescription = null,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.large)
                                    .height(72.dp)
                            )
                        }
                    }

                    is UIMessagePart.Document -> {
                        ChatMessageFileChip(
                            text = part.fileName,
                            onClick = { context.openLocalFile(part.url) },
                            icon = {
                                when (part.mime) {
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                                        Icon(
                                            painter = painterResource(R.drawable.docx),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    "application/pdf" -> {
                                        Icon(
                                            painter = painterResource(R.drawable.pdf),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    else -> {
                                        Icon(
                                            imageVector = HugeIcons.File02,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            },
                        )
                    }

                    else -> {
                        // Skip unknown part types (e.g., deprecated ToolCall, ToolResult, Search)
                    }
                }
            }
        }
    }

    // Annotations (always rendered at the end)
    if (annotations.isNotEmpty()) {
        ChatMessageCitations(annotations = annotations)
    }
}

private const val USER_MESSAGE_COLLAPSED_LINES = 3

@Composable
private fun CollapsibleUserMessageText(
    text: String,
    assistant: Assistant?,
    onClickCitation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val explicitLineCount = remember(text) { text.trimEnd().lineSequence().count() }
    var expanded by remember(text) { mutableStateOf(false) }
    var measuredOverflow by remember(text) { mutableStateOf(false) }
    val lineHeight = LocalTextStyle.current.lineHeight.toDp()
    val collapsedHeight = (if (lineHeight.value > 0f) lineHeight else 20.dp) *
        USER_MESSAGE_COLLAPSED_LINES.toFloat()
    val collapsedHeightPx = with(LocalDensity.current) { collapsedHeight.toPx() }
    val collapsible = explicitLineCount > USER_MESSAGE_COLLAPSED_LINES || measuredOverflow
    val clipped = collapsible && !expanded

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .then(
                    if (clipped) {
                        Modifier
                            .heightIn(max = collapsedHeight)
                            .clipToBounds()
                    } else {
                        Modifier
                    }
                )
                .onSizeChanged { size ->
                    if (!measuredOverflow && size.height > collapsedHeightPx) {
                        measuredOverflow = true
                    }
                }
        ) {
            MarkdownBlock(
                content = text.replaceRegexes(
                    assistant = assistant,
                    scope = AssistantAffectScope.USER,
                    visual = true,
                ),
                onClickCitation = onClickCitation,
            )
        }
        if (collapsible) {
            Text(
                text = stringResource(
                    if (expanded) {
                        R.string.chat_message_collapse_content
                    } else {
                        R.string.chat_message_expand_content
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun TerminalTypewriterMarkdown(
    content: String,
    streaming: Boolean,
    onClickCitation: (String) -> Unit,
) {
    val latest = rememberUpdatedState(content)
    var visible by remember { mutableIntStateOf(if (streaming) 0 else content.length) }
    LaunchedEffect(streaming) {
        if (!streaming) {
            // 流结束后把尾巴快速补完，仍然保留逐字观感
            visible = visible.coerceIn(0, latest.value.length)
            while (visible < latest.value.length) {
                delay(50)
                val remaining = latest.value.length - visible
                visible = minOf(latest.value.length, visible + maxOf(8, remaining / 3))
            }
            return@LaunchedEffect
        }
        if (visible == 0 && content.isNotEmpty()) visible = minOf(12, content.length)
        var lastNanos = System.nanoTime()
        while (true) {
            delay(50)
            val target = latest.value.length
            if (visible > target) visible = target
            val backlog = target - visible
            if (backlog <= 0) {
                lastNanos = System.nanoTime()
                continue
            }
            val now = System.nanoTime()
            val seconds = ((now - lastNanos) / 1_000_000_000.0).coerceIn(0.001, 0.25)
            lastNanos = now
            // 上游来得快时按积压加速，保证不会越拖越远；慢下来后再回到正常打字速度
            val charsPerSecond = when {
                backlog > 1200 -> 12_000.0
                backlog > 320 -> 4_200.0
                backlog > 96 -> 1_200.0
                else -> 240.0
            }
            val step = maxOf(1, (charsPerSecond * seconds).toInt())
            visible = minOf(target, visible + step)
        }
    }
    val shown = content.take(visible.coerceIn(0, content.length))
    val printing = visible < content.length
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (printing) Modifier else Modifier.animateContentSize()),
    ) {
        MarkdownBlock(
            content = if (printing) "$shown▋" else shown,
            streaming = printing && streaming,
            onClickCitation = onClickCitation,
        )
    }
}

@Composable
private fun TerminalMessageHeader(
    message: UIMessage,
    model: Model?,
    assistant: Assistant?,
    loading: Boolean,
    index: Int,
) {
    val isUser = message.role == MessageRole.USER
    val userName = if (isUser) {
        "user"
    } else {
        assistant?.name?.trim()?.replace(Regex("\\s+"), "-")?.lowercase()?.takeIf { it.isNotBlank() }
            ?: model?.modelId?.substringAfterLast('/')?.lowercase()
            ?: "assistant"
    }
    val time = "%02d:%02d:%02d".format(
        message.createdAt.hour,
        message.createdAt.minute,
        message.createdAt.second,
    )
    val prompt = if (isUser) {
        "user@l3hub:~\$"
    } else {
        "λ $userName@l3hub:~\$"
    }
    val rule = "─".repeat(160)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "┌─[%02d][%s] $time  $prompt $rule".format(index, if (isUser) "U" else "A"),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = if (isUser) TerminalPalette.Amber else TerminalPalette.Green,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.fillMaxWidth(),
        )
        if (loading) {
            Text(
                text = "│ processing... ▋",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = TerminalPalette.GreenDim,
            )
        }
    }
}

@Composable
private fun TerminalMessageAction(label: String, onClick: () -> Unit) {
    Text(
        text = "[$label]",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = TerminalPalette.GreenDim,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

private val BubbleCorner = 24.dp
private val BubbleTailCorner = 6.dp
private val BubblePressedCorner = 12.dp

// 同组相邻气泡接缝处的圆角：上一条的下圆角与下一条的上圆角都收成这个值，两条气泡看着是连着的
private val BubbleJoinedCorner = 8.dp

/**
 * 消息气泡：大圆角，靠头像一侧的顶角收紧；可点击时按下其余圆角也跟着收紧。
 * 与同组相邻气泡相接的那条边统一收成 [BubbleJoinedCorner]，看上去是一条连线。
 */
@Composable
private fun ChatMessageBubble(
    role: MessageRole,
    color: Color,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    joinedToPrevious: Boolean = false,
    joinedToNext: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressProgress by animatePressProgress(interactionSource)
    val fullWidth = LocalFullWidthComponents.current
    val radius = adaptiveCornerRadius(LocalSettings.current.displaySetting.uiCornerRadius.dp)
    val corner = lerp(radius, minOf(radius, BubblePressedCorner), pressProgress)
    val tail = minOf(BubbleTailCorner, radius * 0.25f)
    // 接缝那一侧不能再留小尾巴，否则两条气泡之间会露一个豁口
    val join = minOf(BubbleJoinedCorner, corner)
    val shape = if (role == MessageRole.USER) {
        RoundedCornerShape(
            topStart = if (joinedToPrevious) join else corner,
            topEnd = if (joinedToPrevious) join else tail,
            bottomEnd = if (joinedToNext) join else corner,
            bottomStart = if (joinedToNext) join else corner,
        )
    } else {
        RoundedCornerShape(
            topStart = if (joinedToPrevious) join else tail,
            topEnd = if (joinedToPrevious) join else corner,
            bottomEnd = if (joinedToNext) join else corner,
            bottomStart = if (joinedToNext) join else corner,
        )
    }
    val displaySetting = LocalSettings.current.displaySetting
    val effect = displaySetting.effectiveBackgroundEffect
    val useEffect = hazeState != null && effect != BackgroundEffectType.OFF
    val hazeModifier = if (useEffect && hazeState != null) {
        Modifier.chatInputHaze(
            hazeState = hazeState,
            displaySetting = displaySetting,
            containerColor = color,
            shape = shape,
        )
    } else {
        Modifier
    }
    val bubbleColor = if (useEffect) {
        color.copy(alpha = color.alpha * 0.65f)
    } else {
        color
    }
    val contentColor = contentColorFor(color)
    Box(
        modifier = modifier
            // 全宽模式下气泡不再按内容收缩，左右边缘对齐父容器
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .then(if (useEffect) Modifier.graphicsLayer(shape = shape, clip = true) else Modifier)
            .clipDrawing(shape)
            .then(hazeModifier)
            .background(bubbleColor)
            .then(
                if (effect == BackgroundEffectType.BLUR) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interactionSource, indication = ripple(), onClick = onClick)
                } else {
                    Modifier
                },
            )
            // MarkdownBlock 自带 4dp 的水平内边距
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

/**
 * 只裁剪绘制内容，不像 [clip] 那样把形状设成图层轮廓。
 *
 * 四个角半径不同时图层轮廓是 Path，Compose 判断触摸点是否落在轮廓内时用 Path 求交，
 * 坐标超过 8192px 后 float 精度不够，求交结果为空，长消息下半部分（例如展开的长代码块）就收不到触摸了。
 */
private fun Modifier.clipDrawing(shape: Shape): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    onDrawWithContent {
        clipPath(path) { this@onDrawWithContent.drawContent() }
    }
}

@Composable
private fun ChatMessageCitations(annotations: List<UIMessageAnnotation>) {
    var expand by remember { mutableStateOf(false) }
    val urls = remember(annotations) {
        annotations.map { annotation ->
            when (annotation) {
                is UIMessageAnnotation.UrlCitation -> annotation.url
            }
        }
    }
    Column(
        modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChatMessageFileChip(
            text = stringResource(R.string.citations_count, annotations.size),
            onClick = { expand = !expand },
            color = LocalUiSlots.current.aiBubble,
            icon = { FaviconRow(urls = urls, size = 18.dp) },
        )
        if (expand) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                ProvideTextStyle(
                    MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        annotations.fastForEachIndexed { index, annotation ->
                            when (annotation) {
                                is UIMessageAnnotation.UrlCitation -> {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Favicon(annotation.url, modifier = Modifier.size(20.dp))
                                        Text(
                                            text = buildAnnotatedString {
                                                append("${index + 1}. ")
                                                withLink(LinkAnnotation.Url(annotation.url)) {
                                                    append(annotation.title.urlDecode())
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 用户消息：附件和文本放在同一个气泡里，附件用和输入框预览一样的矩形卡片。
 * 超过三个时只画前三个，第三个后面标出还剩几个。
 */
@Composable
private fun UserMessageContent(
    parts: List<UIMessagePart>,
    assistant: Assistant?,
    onClick: (() -> Unit)?,
    onClickCitation: (String) -> Unit,
    hazeState: HazeState?,
    joinedToPrevious: Boolean = false,
    joinedToNext: Boolean = false,
) {
    val settings = LocalSettings.current.displaySetting
    val attachments = remember(parts) {
        parts.filter {
            it is UIMessagePart.Image ||
                it is UIMessagePart.Document ||
                it is UIMessagePart.Video ||
                it is UIMessagePart.Audio
        }
    }
    val texts = remember(parts) {
        parts.filterIsInstance<UIMessagePart.Text>().filter { it.text.isNotBlank() }
    }
    if (attachments.isEmpty() && texts.isEmpty()) return

    ChatMessageBubble(
        role = MessageRole.USER,
        color = LocalUiSlots.current.userBubble.copy(alpha = settings.bubbleOpacity),
        hazeState = hazeState,
        joinedToPrevious = joinedToPrevious,
        joinedToNext = joinedToNext,
        onClick = { onClick?.invoke() },
        modifier = Modifier
            .padding(start = adaptiveHorizontalPaddingValue(24.dp))
            .animateContentSize(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (attachments.isNotEmpty()) {
                UserAttachmentTiles(attachments = attachments)
            }
            texts.forEach { text ->
                CollapsibleUserMessageText(
                    text = text.text,
                    assistant = assistant,
                    onClickCitation = onClickCitation,
                )
            }
        }
    }
}

@Composable
private fun UserAttachmentTiles(attachments: List<UIMessagePart>) {
    val context = LocalContext.current
    val visible = attachments.take(3)
    val hiddenCount = (attachments.size - visible.size).coerceAtLeast(0)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        visible.forEachIndexed { index, part ->
            val more = if (index == visible.lastIndex) hiddenCount else 0
            UserAttachmentTile(
                part = part,
                moreCount = more,
                onClick = {
                    val url = when (part) {
                        is UIMessagePart.Image -> part.url
                        is UIMessagePart.Document -> part.url
                        is UIMessagePart.Video -> part.url
                        is UIMessagePart.Audio -> part.url
                        else -> null
                    }
                    if (!url.isNullOrBlank()) context.openLocalFile(url)
                },
            )
        }
    }
}

@Composable
private fun UserAttachmentTile(
    part: UIMessagePart,
    moreCount: Int,
    onClick: () -> Unit,
) {
    val partUrl = when (part) {
        is UIMessagePart.Image -> part.url
        is UIMessagePart.Document -> part.url
        is UIMessagePart.Video -> part.url
        is UIMessagePart.Audio -> part.url
        else -> ""
    }
    val title = when (part) {
        is UIMessagePart.Document -> part.fileName.ifBlank { partUrl.substringAfterLast('/') }
        else -> partUrl.substringAfterLast('/').ifBlank {
            when (part) {
                is UIMessagePart.Image -> "image"
                is UIMessagePart.Video -> "video"
                is UIMessagePart.Audio -> "audio"
                else -> "file"
            }
        }
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .height(44.dp)
                .padding(start = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                when (part) {
                    is UIMessagePart.Image -> AsyncImage(
                        model = part.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    is UIMessagePart.Video -> Icon(HugeIcons.Video01, null, Modifier.size(18.dp))
                    is UIMessagePart.Audio -> Icon(HugeIcons.MusicNote03, null, Modifier.size(18.dp))
                    else -> Icon(HugeIcons.Files02, null, Modifier.size(18.dp))
                }
                if (moreCount > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "+$moreCount",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                            ),
                            color = Color.White,
                        )
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
        }
    }
}
