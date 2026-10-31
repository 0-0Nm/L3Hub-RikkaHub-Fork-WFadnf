package me.rerere.rikkahub.ui.pages.chat.terminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.TodoStatus
import me.rerere.rikkahub.ui.components.ai.ProviderQuotaText
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.theme.TerminalPalette
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.rikkahub.utils.toFixed
import kotlin.math.roundToInt

private val TerminalTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 17.sp,
    color = TerminalPalette.Green,
)

internal fun terminalRule(length: Int = 200): String = "─".repeat(length)

@Composable
fun TerminalChatTopBar(
    title: String,
    assistantName: String,
    modelName: String,
    contextTokens: Int?,
    maxContextTokens: Int?,
    bigScreen: Boolean,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onTaskTree: () -> Unit,
    onUsage: () -> Unit,
    onHelp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalPalette.Background)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "L3HUB",
                style = TerminalTextStyle,
                fontWeight = FontWeight.Bold,
                color = TerminalPalette.Amber,
            )
            Text(
                text = " v${BuildConfig.VERSION_NAME}",
                style = TerminalTextStyle,
                color = TerminalPalette.GreenDim,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = contextText(contextTokens, maxContextTokens),
                style = TerminalTextStyle,
                color = TerminalPalette.GreenDim,
                maxLines = 1,
            )
        }
        Text(
            text = "├─ ${title.ifBlank { "untitled" }}",
            style = TerminalTextStyle,
            color = TerminalPalette.Green,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!bigScreen) TerminalTextButton("MENU", onMenu)
                TerminalTextButton("NEW", onNewChat)
                TerminalTextButton("TREE", onTaskTree)
                TerminalTextButton("USAGE", onUsage)
                TerminalTextButton("HELP", onHelp)
            }
            Text(
                text = "${assistantName.ifBlank { "assistant" }} :: $modelName",
                style = TerminalTextStyle,
                color = TerminalPalette.GreenDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .widthIn(max = 140.dp)
                    .padding(start = 6.dp),
            )
        }
        Text(
            text = terminalRule(),
            style = TerminalTextStyle,
            color = TerminalPalette.Border,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

private fun contextText(tokens: Int?, maxTokens: Int?): String {
    if (maxTokens == null || maxTokens <= 0) return "CTX --"
    val used = tokens ?: 0
    val percent = (used * 100f / maxTokens).roundToInt()
    return "CTX ${used.formatNumber()}/${maxTokens.formatNumber()} $percent%"
}

@Composable
fun TerminalChatInput(
    state: ChatInputState,
    settings: Settings,
    chatModel: Model?,
    contextTokens: Int?,
    tokenSpeed: Float?,
    loading: Boolean,
    queuedCount: Int,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onFiles: () -> Unit,
    onVoice: () -> Unit,
    onModel: () -> Unit,
    onUsage: () -> Unit,
    onCommand: (String) -> Unit,
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    onInterject: () -> Unit,
    todos: List<TodoItem> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val text = state.textContent.text.toString()
    var showCommandPalette by remember { mutableStateOf(false) }
    val suggestions = terminalCommandSuggestions(text)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TerminalPalette.Background)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = terminalRule(),
            style = TerminalTextStyle,
            color = TerminalPalette.Border,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        if (todos.isNotEmpty()) {
            val completed = todos.count { it.status == TodoStatus.COMPLETED }
            val current = todos.firstOrNull { it.status == TodoStatus.IN_PROGRESS }?.content
            Text(
                text = "todos $completed/${todos.size}" + if (current != null) " · $current" else "",
                style = TerminalTextStyle,
                color = TerminalPalette.GreenDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TerminalContextLine(
            contextTokens = contextTokens,
            warnTokens = chatModel?.warnContextTokens,
            maxTokens = chatModel?.maxContextTokens,
            tokenSpeed = tokenSpeed,
            settings = settings,
            chatModel = chatModel,
            onUsage = onUsage,
        )
        if (state.isEditing()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "── EDIT MODE [${state.editingMessage.toString().take(8)}] ",
                    style = TerminalTextStyle,
                    color = TerminalPalette.Amber,
                )
                TerminalTextButton("CANCEL", onClick = { state.clearInput() })
            }
        }
        if (queuedCount > 0) {
            Text(
                text = "queue: $queuedCount pending · /stop 清空当前生成",
                style = TerminalTextStyle,
                color = TerminalPalette.GreenDim,
            )
        }
        if (text.startsWith("/") && suggestions.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                suggestions.forEach { command ->
                    Text(
                        text = "  ${command.usage}  ${command.description}",
                        style = TerminalTextStyle,
                        color = TerminalPalette.GreenDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.setMessageText("${command.usage.substringBefore(' ')} ") }
                            .padding(vertical = 1.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = ">",
                style = TerminalTextStyle,
                color = TerminalPalette.Amber,
                modifier = Modifier.padding(end = 6.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(
                        text = "输入消息，或 /help 查看命令",
                        style = TerminalTextStyle.copy(color = TerminalPalette.GreenDim),
                    )
                }
                BasicTextField(
                    state = state.textContent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 22.dp, max = 132.dp),
                    textStyle = TerminalTextStyle,
                    cursorBrush = SolidColor(TerminalPalette.Amber),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 6),
                    onKeyboardAction = {
                        if (!loading) {
                            val raw = state.textContent.text.toString().trim()
                            if (raw.startsWith("/")) onCommand(raw) else onSubmit()
                        }
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (loading) {
                    TerminalTextButton("STOP", onClick = onStop, color = TerminalPalette.Error)
                } else {
                    TerminalTextButton("SEND", onClick = onSubmit, color = TerminalPalette.Amber)
                }
                TerminalTextButton("FILES", onFiles)
                TerminalTextButton("MODEL", onModel)
                if (chatModel?.abilities?.contains(ModelAbility.REASONING) == true) {
                    TerminalTextButton(
                        text = "THINK:${assistant.reasoningLevel.name}",
                        onClick = {
                            val levels = ReasoningLevel.entries
                            val next = levels[(levels.indexOf(assistant.reasoningLevel) + 1) % levels.size]
                            onUpdateAssistant(assistant.copy(reasoningLevel = next))
                        },
                    )
                }
                if (loading && text.isNotBlank() && settings.enableMessageQueue) {
                    TerminalTextButton("QUEUE", onClick = onInterject)
                }
                TerminalTextButton("VOICE", onVoice)
                TerminalTextButton("CMD", onClick = { showCommandPalette = true })
                TerminalTextButton("HELP", onClick = { onCommand("/help") })
            }
            Text(
                text = "${state.messageContent.size} att · ${text.length} ch",
                style = TerminalTextStyle,
                color = TerminalPalette.GreenDim,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }

    if (showCommandPalette) {
        TerminalCommandPaletteDialog(
            onPick = { spec ->
                showCommandPalette = false
                if (spec.usage.contains(' ')) {
                    state.setMessageText("/${spec.name} ")
                } else {
                    onCommand("/${spec.name}")
                }
            },
            onDismiss = { showCommandPalette = false },
        )
    }
}

@Composable
private fun TerminalContextLine(
    contextTokens: Int?,
    warnTokens: Int?,
    maxTokens: Int?,
    tokenSpeed: Float?,
    settings: Settings,
    chatModel: Model?,
    onUsage: () -> Unit,
) {
    val used = (contextTokens ?: 0).coerceAtLeast(0)
    val limit = maxTokens ?: warnTokens
    val fraction = if (limit != null && limit > 0) (used.toFloat() / limit).coerceIn(0f, 1f) else 0f
    val cells = 16
    val filled = (fraction * cells).roundToInt()
    val color = when {
        fraction >= 1f -> TerminalPalette.Error
        fraction >= 0.8f -> TerminalPalette.Amber
        else -> TerminalPalette.Green
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUsage),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("CTX", style = TerminalTextStyle, color = TerminalPalette.GreenDim)
        Text(
            text = "[" + "█".repeat(filled) + "░".repeat(cells - filled) + "]",
            style = TerminalTextStyle,
            color = color,
        )
        Text(
            text = if (limit != null) {
                "${(fraction * 100).roundToInt()}% ${used.formatNumber()}/${limit.formatNumber()}"
            } else {
                used.formatNumber()
            },
            style = TerminalTextStyle,
            color = color,
            maxLines = 1,
        )
        if (tokenSpeed != null) {
            Text(
                text = "${tokenSpeed.toFixed(1)} tok/s",
                style = TerminalTextStyle,
                color = TerminalPalette.Green,
            )
        }
        Spacer(Modifier.weight(1f))
        val provider = chatModel?.findProvider(settings.providers)
        if (provider != null) {
            ProviderQuotaText(
                providerSetting = provider,
                style = TerminalTextStyle.copy(color = TerminalPalette.GreenDim),
                refreshIntervalMillis = 30_000,
            )
        }
    }
}

@Composable
fun TerminalTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = TerminalPalette.Green,
) {
    Text(
        text = "[$text]",
        style = TerminalTextStyle,
        color = color,
        maxLines = 1,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 3.dp),
    )
}

@Composable
fun TerminalOutputDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = TerminalPalette.Background,
            border = BorderStroke(1.dp, TerminalPalette.Border),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "┌─[ $title ]" + terminalRule(40),
                    style = TerminalTextStyle,
                    color = TerminalPalette.Amber,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                )
                Text(
                    text = content,
                    style = TerminalTextStyle,
                    color = TerminalPalette.Green,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                )
                Text(
                    text = terminalRule(60),
                    style = TerminalTextStyle,
                    color = TerminalPalette.Border,
                    maxLines = 1,
                    softWrap = false,
                )
                TerminalTextButton("CLOSE", onDismiss, color = TerminalPalette.Amber)
            }
        }
    }
}

@Composable
private fun TerminalCommandPaletteDialog(
    onPick: (TerminalCommandSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = TerminalPalette.Background,
            border = BorderStroke(1.dp, TerminalPalette.Border),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "┌─[ COMMANDS ]" + terminalRule(40),
                    style = TerminalTextStyle,
                    color = TerminalPalette.Amber,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(TERMINAL_COMMANDS, key = { it.name }) { spec ->
                        Text(
                            text = spec.usage.padEnd(22) + "  " + spec.description,
                            style = TerminalTextStyle,
                            color = TerminalPalette.Green,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(spec) }
                                .padding(vertical = 3.dp),
                        )
                    }
                }
                Text(
                    text = terminalRule(60),
                    style = TerminalTextStyle,
                    color = TerminalPalette.Border,
                    maxLines = 1,
                    softWrap = false,
                )
                TerminalTextButton("CLOSE", onDismiss, color = TerminalPalette.Amber)
            }
        }
    }
}

@Composable
fun TerminalModelPickerDialog(
    settings: Settings,
    current: Model?,
    onSelect: (Model) -> Unit,
    onDismiss: () -> Unit,
) {
    val grouped = settings.providers.mapNotNull { provider ->
        val models = provider.models.filter { it.type == me.rerere.ai.provider.ModelType.CHAT }
        if (models.isEmpty()) null else provider to models
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = TerminalPalette.Background,
            border = BorderStroke(1.dp, TerminalPalette.Border),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "┌─[ SELECT MODEL ]" + terminalRule(40),
                    style = TerminalTextStyle,
                    color = TerminalPalette.Amber,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    grouped.forEach { (provider, models) ->
                        item(key = provider.id.toString()) {
                            Text(
                                text = "# ${provider.name}",
                                style = TerminalTextStyle,
                                color = TerminalPalette.GreenDim,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        items(models, key = { it.id.toString() }) { model ->
                            val selected = model.id == current?.id
                            Text(
                                text = "${if (selected) "*" else " "} ${model.displayName.ifBlank { model.modelId }}" +
                                    "  [${model.modelId}]",
                                style = TerminalTextStyle.copy(
                                    color = if (selected) TerminalPalette.Amber else TerminalPalette.Green,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(model) }
                                    .padding(vertical = 3.dp),
                            )
                        }
                    }
                }
                Text(
                    text = terminalRule(60),
                    style = TerminalTextStyle,
                    color = TerminalPalette.Border,
                    maxLines = 1,
                    softWrap = false,
                )
                TerminalTextButton("CLOSE", onDismiss, color = TerminalPalette.Amber)
            }
        }
    }
}
