package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.AnimationSpeed
import me.rerere.rikkahub.data.datastore.ChatFontFamily
import me.rerere.rikkahub.data.datastore.ContextMeterStyle
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FileUtils
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.terminal.IconButton
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPadding
import me.rerere.rikkahub.ui.theme.rememberChatFontFamily
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun SettingPreferencesUIPage(embedded: Boolean = false, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val chatFontFamily = rememberChatFontFamily(displaySetting)

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings { it.copy(displaySetting = setting) }
    }

    // 自定义字体回退链：优先用列表；旧配置只有单字体时退化为单元素列表
    val customFontPaths = if (displaySetting.chatCustomFontPaths.isNotEmpty()) {
        displaySetting.chatCustomFontPaths
    } else {
        listOf(displaySetting.chatCustomFontPath).filter { it.isNotBlank() }
    }
    val customFontNames = if (displaySetting.chatCustomFontPaths.isNotEmpty()) {
        displaySetting.chatCustomFontNames
    } else {
        listOf(displaySetting.chatCustomFontName).filter { it.isNotBlank() }
    }

    val importSuccessMsg = stringResource(R.string.setting_display_page_custom_font_import_success)
    val importFailedMsg = stringResource(R.string.setting_display_page_custom_font_import_failed)
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    importCustomChatFontInternal(context, uri)
                }
            }.onSuccess { importedFont ->
                // 追加到回退链尾部：旧配置单字体迁移到列表首位；names 与 paths 按索引对齐（缺失补空串，UI 有兜底显示）
                val basePaths = if (displaySetting.chatCustomFontPaths.isNotEmpty()) displaySetting.chatCustomFontPaths
                    else listOf(displaySetting.chatCustomFontPath).filter { it.isNotBlank() }
                val baseNames = basePaths.mapIndexed { i, _ -> displaySetting.chatCustomFontNames.getOrNull(i) ?: "" }
                val newPaths = basePaths + importedFont.relativePath
                val newNames = baseNames + importedFont.displayName
                updateDisplaySetting(
                    displaySetting.copy(
                        chatFontFamily = ChatFontFamily.CUSTOM,
                        chatCustomFontPaths = newPaths,
                        chatCustomFontNames = newNames,
                        // 单字段同步为新首位，避免导入/删除后悬挂引用
                        chatCustomFontPath = newPaths.first(),
                        chatCustomFontName = newNames.first(),
                    )
                )
                toaster.show(importSuccessMsg, type = ToastType.Success)
            }.onFailure { error ->
                toaster.show(importFailedMsg.format(error.message.orEmpty()), type = ToastType.Error)
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .adaptiveHorizontalPadding(16.dp)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardGroup(
                title = { Text(stringResource(R.string.setting_page_message_display_settings)) },
            ) {
                switchItem(
                    checked = displaySetting.showUserAvatar,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showUserAvatar = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_show_user_avatar_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_show_user_avatar_title)) },
                )
                switchItem(
                    checked = displaySetting.showAssistantBubble,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showAssistantBubble = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_show_assistant_bubble_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_show_assistant_bubble_title)) },
                )
                formItem(
                    headlineContent = { Text(stringResource(R.string.setting_display_page_empty_slogan_title)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_empty_slogan_desc)) },
                ) {
                    OutlinedTextField(
                        value = displaySetting.emptyChatSlogan,
                        onValueChange = {
                            updateDisplaySetting(displaySetting.copy(emptyChatSlogan = it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.chat_empty_default_slogan)) },
                        singleLine = true,
                    )
                }
                formItem(
                    headlineContent = { Text(stringResource(R.string.setting_display_page_bubble_opacity_title)) },
                    trailingContent = { CardGroupValue("${(displaySetting.bubbleOpacity * 100).roundToInt()}%") },
                ) {
                    Slider(
                        value = displaySetting.bubbleOpacity,
                        onValueChange = {
                            updateDisplaySetting(displaySetting.copy(bubbleOpacity = it))
                        },
                        valueRange = 0f..1.0f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            CardGroup {
                switchItem(
                    checked = displaySetting.showModelIcon,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showModelIcon = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_chat_list_model_icon_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_chat_list_model_icon_title)) },
                )
                switchItem(
                    checked = displaySetting.showModelName,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showModelName = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_show_model_name_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_show_model_name_title)) },
                )
                switchItem(
                    checked = displaySetting.buttonShadow,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(buttonShadow = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_button_shadow_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_button_shadow_title)) },
                )
                switchItem(
                    checked = displaySetting.hideCompletedTodoCard,
                    onCheckedChange = {
                        updateDisplaySetting(displaySetting.copy(hideCompletedTodoCard = it))
                    },
                    supportingContent = { Text("任务全部完成后从输入框上方收起；随时可以从「+」菜单里回看") },
                    headlineContent = { Text("完成后收起任务清单") },
                )
                switchItem(
                    checked = displaySetting.showDateTimeInMessage,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showDateTimeInMessage = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_show_datetime_in_message_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_show_datetime_in_message_title)) },
                )
                switchItem(
                    checked = displaySetting.useL3HubReasoningStyle,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(useL3HubReasoningStyle = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_use_l3hub_reasoning_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_use_l3hub_reasoning_title)) },
                )
                switchItem(
                    checked = displaySetting.showTokenUsage,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showTokenUsage = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_show_token_usage_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_show_token_usage_title)) },
                )
                switchItem(
                    checked = displaySetting.usagePieChart,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(usagePieChart = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_usage_pie_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_usage_pie_title)) },
                )
                switchItem(
                    checked = displaySetting.contextMeterStyle != ContextMeterStyle.OFF,
                    onCheckedChange = { enabled ->
                        updateDisplaySetting(
                            displaySetting.copy(
                                contextMeterStyle = if (enabled) ContextMeterStyle.BAR else ContextMeterStyle.OFF
                            )
                        )
                    },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_context_meter_switch_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_context_meter_switch_title)) },
                )
            }

            CardGroup {
                switchItem(
                    checked = displaySetting.showToolCallOutput,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showToolCallOutput = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_show_tool_call_output_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_show_tool_call_output_title)) },
                )
                formItem(
                    headlineContent = { Text(stringResource(R.string.setting_display_page_ui_corner_radius_title)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_ui_corner_radius_desc, displaySetting.uiCornerRadius)) },
                ) {
                    Slider(
                        value = displaySetting.uiCornerRadius.toFloat(),
                        onValueChange = {
                            updateDisplaySetting(displaySetting.copy(uiCornerRadius = it.roundToInt()))
                        },
                        valueRange = 0f..32f,
                        steps = 31,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                formItem(
                    headlineContent = { Text(stringResource(R.string.setting_display_page_input_padding_title)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.setting_display_page_input_padding_desc,
                                displaySetting.inputPadding,
                            )
                        )
                    },
                ) {
                    Slider(
                        value = displaySetting.inputPadding.toFloat(),
                        onValueChange = {
                            updateDisplaySetting(displaySetting.copy(inputPadding = it.roundToInt()))
                        },
                        valueRange = 0f..16f,
                        steps = 15,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                switchItem(
                    checked = displaySetting.disableAnimations,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(disableAnimations = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_disable_animations_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_disable_animations_title)) },
                )
                // 关掉动效以后这一项没有意义，直接收起来
                if (!displaySetting.disableAnimations) {
                    AnimationSpeed.entries.forEach { speed ->
                        item(
                            onClick = {
                                updateDisplaySetting(displaySetting.copy(animationSpeed = speed))
                            },
                            supportingContent = { Text(speed.description) },
                            trailingContent = {
                                RadioButton(
                                    selected = displaySetting.animationSpeed == speed,
                                    onClick = null,
                                )
                            },
                        ) {
                            Text(speed.label)
                        }
                    }
                }
                switchItem(
                    checked = displaySetting.showThinkingContent,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showThinkingContent = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_show_thinking_content_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_show_thinking_content_title)) },
                )
                switchItem(
                    checked = displaySetting.autoCloseThinking,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(autoCloseThinking = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_auto_collapse_thinking_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_auto_collapse_thinking_title)) },
                )
                switchItem(
                    checked = displaySetting.showThinkingSummary,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showThinkingSummary = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_show_thinking_summary_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_show_thinking_summary_title)) },
                )
                switchItem(
                    checked = displaySetting.enableLatexRendering,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableLatexRendering = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_enable_latex_rendering_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_enable_latex_rendering_title)) },
                )
                switchItem(
                    checked = displaySetting.enableQuoteHighlight,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableQuoteHighlight = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_enable_quote_highlight_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_enable_quote_highlight_title)) },
                )
            }

            CardGroup {
                formItem(
                    headlineContent = { Text(stringResource(R.string.setting_display_page_chat_font_family_title)) },
                ) {
                    ConnectedChoiceButtons(
                        options = ChatFontFamily.entries,
                        selected = displaySetting.chatFontFamily,
                        onSelect = { family ->
                            if (family == ChatFontFamily.CUSTOM &&
                                displaySetting.chatCustomFontPaths.isEmpty() &&
                                displaySetting.chatCustomFontPath.isBlank()
                            ) {
                                fontPickerLauncher.launch(CustomFontMimeTypesUI)
                            } else {
                                updateDisplaySetting(displaySetting.copy(chatFontFamily = family))
                            }
                        },
                        minHeight = 64.dp,
                    ) { family, _ ->
                        // 每个按钮用对应的字体写一个样字
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Aa",
                                fontFamily = family.toFontFamilyUI(chatFontFamily),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = family.labelUI(),
                                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 11.sp),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                switchItem(
                    checked = displaySetting.fontApplyGlobal,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(fontApplyGlobal = it)) },
                    // 终端风格强制等宽、默认字体本就全局生效，这两种情况开关没有意义
                    enabled = !displaySetting.terminalStyle && displaySetting.chatFontFamily != ChatFontFamily.DEFAULT,
                    supportingContent = { Text(stringResource(R.string.setting_display_page_font_global_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_font_global_title)) },
                )
                item(
                    onClick = { fontPickerLauncher.launch(CustomFontMimeTypesUI) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_custom_font_title)) },
                    supportingContent = {
                        Text(
                            if (customFontPaths.isEmpty()) {
                                stringResource(R.string.setting_display_page_custom_font_not_imported)
                            } else {
                                stringResource(R.string.setting_display_page_custom_font_chain_desc, customFontPaths.size)
                            }
                        )
                    },
                    trailingContent = {
                        IconButton(
                            onClick = { fontPickerLauncher.launch(CustomFontMimeTypesUI) }
                        ) {
                            Icon(
                                HugeIcons.FileImport,
                                contentDescription = stringResource(
                                    R.string.setting_display_page_custom_font_import
                                )
                            )
                        }
                    }
                )
                // 已导入字体回退链：每款一行，可上移调整优先级、单独删除（删除后链自动收拢）
                if (customFontPaths.isNotEmpty()) {
                    customFontPaths.forEachIndexed { index, path ->
                        item(
                            headlineContent = {
                                Text(
                                    customFontNames.getOrNull(index)?.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.setting_display_page_custom_font_fallback_name, index + 1)
                                )
                            },
                            supportingContent = {
                                Text(stringResource(R.string.setting_display_page_custom_font_chain_position, index + 1))
                            },
                            trailingContent = {
                                Row {
                                    if (index > 0) {
                                        IconButton(
                                            onClick = { updateDisplaySetting(moveChatFontUp(displaySetting, index)) }
                                        ) {
                                            Icon(
                                                HugeIcons.ArrowUp02,
                                                contentDescription = stringResource(
                                                    R.string.setting_display_page_custom_font_move_up
                                                )
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            deleteCustomChatFontInternal(context, path)
                                            updateDisplaySetting(removeCustomChatFontAt(displaySetting, index))
                                        }
                                    ) {
                                        Icon(
                                            HugeIcons.Delete02,
                                            contentDescription = stringResource(
                                                R.string.setting_display_page_custom_font_remove
                                            )
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
                formItem(
                    headlineContent = { Text(stringResource(R.string.setting_display_page_font_size_title)) },
                    trailingContent = { CardGroupValue("${(displaySetting.fontSizeRatio * 100).toInt()}%") },
                ) {
                    Slider(
                        value = displaySetting.fontSizeRatio,
                        onValueChange = { ratio ->
                            val snapped = (ratio * 20f).roundToInt() / 20f
                            updateDisplaySetting(displaySetting.copy(fontSizeRatio = snapped))
                        },
                        valueRange = 0.5f..2f,
                        steps = 29,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // 预览放在凹下去的底色里，和设置项本身区分开
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        MarkdownBlock(
                            content = stringResource(R.string.setting_display_page_font_size_preview),
                            modifier = Modifier.padding(12.dp),
                            style = LocalTextStyle.current.copy(
                                fontSize = LocalTextStyle.current.fontSize * displaySetting.fontSizeRatio,
                                lineHeight = LocalTextStyle.current.lineHeight * displaySetting.fontSizeRatio,
                                fontFamily = chatFontFamily
                            )
                        )
                    }
                }
            }

            CardGroup(
                title = { Text(stringResource(R.string.setting_page_code_display_settings)) },
            ) {
                switchItem(
                    checked = displaySetting.codeBlockAutoWrap,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(codeBlockAutoWrap = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_wrap_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_wrap_title)) },
                )
                switchItem(
                    checked = displaySetting.codeBlockAutoCollapse,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(codeBlockAutoCollapse = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_collapse_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_collapse_title)) },
                )
                switchItem(
                    checked = displaySetting.showLineNumbers,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showLineNumbers = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_show_line_numbers_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_show_line_numbers_title)) },
                )
            }

        }
    }
    if (embedded) {
        content()
    } else {
        Scaffold(
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(stringResource(R.string.setting_page_preferences_ui))
                    },
                    navigationIcon = {
                        BackButton()
                    },
                    scrollBehavior = scrollBehavior,
                    colors = CustomColors.topBarColors
                )
            },
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = CustomColors.topBarColors.containerColor
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding),
            ) {
                content()
            }
        }
    }
}

private val CustomFontMimeTypesUI = arrayOf(
    "font/*",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/vnd.ms-opentype",
    "application/octet-stream",
    "*/*",
)

private val CustomFontExtensionsUI = setOf("ttf", "otf", "ttc")

private data class ImportedChatFontUI(
    val relativePath: String,
    val displayName: String,
)

@Composable
private fun ChatFontFamily.labelUI(): String = when (this) {
    ChatFontFamily.DEFAULT -> stringResource(R.string.setting_display_page_chat_font_family_default)
    ChatFontFamily.SERIF -> stringResource(R.string.setting_display_page_chat_font_family_serif)
    ChatFontFamily.MONOSPACE -> stringResource(R.string.setting_display_page_chat_font_family_monospace)
    ChatFontFamily.CUSTOM -> stringResource(R.string.setting_display_page_chat_font_family_custom)
}

private fun ChatFontFamily.toFontFamilyUI(customFontFamily: FontFamily): FontFamily = when (this) {
    ChatFontFamily.DEFAULT -> FontFamily.Default
    ChatFontFamily.SERIF -> FontFamily.Serif
    ChatFontFamily.MONOSPACE -> FontFamily.Monospace
    ChatFontFamily.CUSTOM -> customFontFamily
}

private fun importCustomChatFontInternal(context: Context, uri: Uri): ImportedChatFontUI {
    val displayName = FileUtils.getFileNameFromUri(context, uri)?.takeIf { it.isNotBlank() } ?: "custom_font"
    val extension = displayName.substringAfterLast('.', "")
        .lowercase()
        .takeIf { it in CustomFontExtensionsUI }
        ?: "ttf"
    val fontDir = File(context.filesDir, FileFolders.FONTS).apply { mkdirs() }
    val targetFile = File(fontDir, "chat_font.${UUID.randomUUID()}.$extension")
    val tempFile = File(fontDir, "chat_font_import.tmp")

    try {
        tempFile.delete()
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to open selected font")

        runCatching {
            Typeface.createFromFile(tempFile)
        }.onFailure { error ->
            throw IllegalArgumentException(error.message ?: "Invalid font file", error)
        }

        // 多字体回退链：文件名带时间戳天然唯一，直接落盘，不清理已有字体
        check(tempFile.renameTo(targetFile)) { "Unable to save selected font" }
    } catch (error: Throwable) {
        tempFile.delete()
        throw error
    }

    val relativePath = FileUtils.getRelativePathInFilesDir(context.filesDir, targetFile)
        ?: "${FileFolders.FONTS}/${targetFile.name}"
    return ImportedChatFontUI(relativePath = relativePath, displayName = displayName)
}

/** 从回退链中移除第 index 款字体；删光后回到默认字体并清空所有字体字段 */
private fun removeCustomChatFontAt(displaySetting: DisplaySetting, index: Int): DisplaySetting {
    // 旧配置单字体（列表为空）：删除即清空单字体字段并回到默认
    if (displaySetting.chatCustomFontPaths.isEmpty()) {
        return displaySetting.copy(
            chatFontFamily = ChatFontFamily.DEFAULT,
            chatCustomFontPath = "",
            chatCustomFontName = "",
        )
    }
    val newPaths = displaySetting.chatCustomFontPaths.filterIndexed { i, _ -> i != index }
    if (newPaths.isEmpty()) {
        return displaySetting.copy(
            chatFontFamily = ChatFontFamily.DEFAULT,
            chatCustomFontPaths = emptyList(),
            chatCustomFontNames = emptyList(),
            chatCustomFontPath = "",
            chatCustomFontName = "",
        )
    }
    // names 按原索引重映射（删除位置之后的项索引前移一位），与 paths 保持对齐
    val newNames = newPaths.mapIndexed { newIndex, _ ->
        val origIndex = if (newIndex < index) newIndex else newIndex + 1
        displaySetting.chatCustomFontNames.getOrNull(origIndex) ?: ""
    }
    return displaySetting.copy(
        chatCustomFontPaths = newPaths,
        chatCustomFontNames = newNames,
        // 单字段同步为新首位，避免悬挂引用
        chatCustomFontPath = newPaths.first(),
        chatCustomFontName = newNames.first(),
    )
}

/** 把第 index 款字体上移一位（越靠前优先级越高）；首行或非法索引时原样返回 */
private fun moveChatFontUp(displaySetting: DisplaySetting, index: Int): DisplaySetting {
    val paths = displaySetting.chatCustomFontPaths
    if (index <= 0 || index >= paths.size) return displaySetting
    val newPaths = paths.toMutableList().also { list ->
        val item = list.removeAt(index)
        list.add(index - 1, item)
    }
    val newNames = displaySetting.chatCustomFontNames.toMutableList().also { list ->
        if (index < list.size) {
            val item = list.removeAt(index)
            list.add(index - 1, item)
        }
    }
    return displaySetting.copy(
        chatCustomFontPaths = newPaths,
        chatCustomFontNames = newNames,
        // 首位变了，单字段同步
        chatCustomFontPath = newPaths.first(),
        chatCustomFontName = newNames.firstOrNull() ?: "",
    )
}

private fun deleteCustomChatFontInternal(context: Context, relativePath: String) {
    val filesDir = runCatching { context.filesDir.canonicalFile }.getOrNull() ?: return
    val fontFile = runCatching { File(filesDir, relativePath).canonicalFile }.getOrNull() ?: return
    if (fontFile.path.startsWith("${filesDir.path}${File.separator}")) {
        fontFile.delete()
    }
}

private val AnimationSpeed.label: String
    get() = when (this) {
        AnimationSpeed.SYSTEM -> "跟随系统"
        AnimationSpeed.FAST -> "轻快"
        AnimationSpeed.GENTLE -> "柔和"
    }

private val AnimationSpeed.description: String
    get() = when (this) {
        AnimationSpeed.SYSTEM -> "用 Material 自带的空间曲线"
        AnimationSpeed.FAST -> "同样的位移用更短的时间走完"
        AnimationSpeed.GENTLE -> "慢一点，尾段拖得更长"
    }
