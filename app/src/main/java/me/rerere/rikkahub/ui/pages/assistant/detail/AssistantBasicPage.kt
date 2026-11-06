package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ExecutionPermission
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.ai.WorkspaceSelectSheet
import me.rerere.rikkahub.ui.components.ai.modelSettingItem
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.TagsInput
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.glassSegmentedButtonColors
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.heroAnimation
import me.rerere.rikkahub.ui.terminal.OutlinedButton
import me.rerere.rikkahub.ui.terminal.Switch
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.toFixed
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.model.Tag as DataTag

internal fun ExecutionPermission.labelRes(): Int = when (this) {
    ExecutionPermission.READ_ONLY -> R.string.assistant_page_execution_permission_read_only
    ExecutionPermission.READ_WRITE -> R.string.assistant_page_execution_permission_read_write
    ExecutionPermission.APPROVAL -> R.string.assistant_page_execution_permission_approval
    ExecutionPermission.FULL -> R.string.assistant_page_execution_permission_full
}

@Composable
fun AssistantBasicPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_basic))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantBasicContent(
            innerPadding = innerPadding,
            assistant = assistant,
            providers = providers,
            tags = tags,
            workspaces = workspaces,
            onUpdate = { vm.update(it) },
            vm = vm
        )
    }
}

@Composable
internal fun AssistantBasicContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    providers: List<me.rerere.ai.provider.ProviderSetting>,
    tags: List<DataTag>,
    workspaces: List<WorkspaceEntity>,
    onUpdate: (Assistant) -> Unit,
    vm: AssistantDetailVM
) {
    val modelListState = rememberModelListState(
        modelId = assistant.chatModelId,
        providers = providers,
        type = ModelType.CHAT,
    )
    var showWorkspaceSheet by remember { mutableStateOf(false) }
    val navController = LocalNavController.current
    ModelListSheet(
        state = modelListState,
        onSelect = { onUpdate(assistant.copy(chatModelId = it.id)) },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .padding(bottom = 16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            UIAvatar(
                value = assistant.avatar,
                name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                onUpdate = { avatar ->
                    onUpdate(
                        assistant.copy(
                            avatar = avatar
                        )
                    )
                },
                modifier = Modifier
                    .size(104.dp)
                    .heroAnimation("assistant_${assistant.id}")
            )
        }

        CardGroup {
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_name)) },
            ) {
                OutlinedTextField(
                    value = assistant.name,
                    onValueChange = {
                        onUpdate(
                            assistant.copy(
                                name = it
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            switchItem(
                checked = assistant.isSubAgent,
                onCheckedChange = {
                    onUpdate(
                        assistant.copy(
                            isSubAgent = it
                        )
                    )
                },
                supportingContent = { Text(stringResource(R.string.assistant_page_sub_agent_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_sub_agent)) },
            )
            // 计划模式和执行权限都只对工作区工具有意义，没绑工作区就不显示，免得纯聊天助手看到一堆无用开关
            switchItem(
                visible = assistant.allWorkspaceIds.isNotEmpty(),
                checked = assistant.planMode,
                onCheckedChange = { onUpdate(assistant.copy(planMode = it)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_plan_mode_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_plan_mode)) },
            )
            formItem(
                visible = assistant.allWorkspaceIds.isNotEmpty(),
                headlineContent = { Text(stringResource(R.string.assistant_page_execution_permission)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_execution_permission_desc)) },
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ExecutionPermission.entries.forEachIndexed { index, permission ->
                        SegmentedButton(
                            colors = glassSegmentedButtonColors(),
                            selected = assistant.executionPermission == permission,
                            onClick = { onUpdate(assistant.copy(executionPermission = permission)) },
                            shape = SegmentedButtonDefaults.itemShape(index, ExecutionPermission.entries.size),
                        ) {
                            Text(
                                text = stringResource(permission.labelRes()),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            switchItem(
                checked = assistant.enableNotification,
                onCheckedChange = { onUpdate(assistant.copy(enableNotification = it)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_enable_notification_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_enable_notification)) },
            )
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_tags)) },
            ) {
                TagsInput(
                    value = assistant.tags,
                    tags = tags,
                    onValueChange = { tagIds, tagList ->
                        vm.updateTags(tagIds, tagList)
                    },
                )
            }
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_workspace)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_workspace_desc)) },
            ) {
                val selectedWorkspaceIds = assistant.allWorkspaceIds.map { it.toString() }.toSet()
                val selectedWorkspaceCount = workspaces.count { it.id in selectedWorkspaceIds }
                OutlinedButton(
                    onClick = { showWorkspaceSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = when {
                            selectedWorkspaceCount == 0 -> stringResource(R.string.workspace_no_binding)
                            selectedWorkspaceCount == 1 -> workspaces.first {
                                it.id in selectedWorkspaceIds
                            }.name
                            else -> stringResource(R.string.workspace_selected_count, selectedWorkspaceCount)
                        }
                    )
                }
            }
            switchItem(
                checked = assistant.useAssistantAvatar,
                onCheckedChange = {
                    onUpdate(
                        assistant.copy(
                            useAssistantAvatar = it
                        )
                    )
                },
                supportingContent = { Text(stringResource(R.string.assistant_page_use_assistant_avatar_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_use_assistant_avatar)) },
            )
        }

        CardGroup {
            modelSettingItem(
                icon = HugeIcons.BubbleChat,
                title = R.string.assistant_page_chat_model,
                description = R.string.assistant_page_chat_model_desc,
                state = modelListState,
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_thinking_budget)) },
                trailingContent = {
                    ReasoningButton(
                        reasoningLevel = assistant.reasoningLevel,
                        onUpdateReasoningLevel = { level ->
                            onUpdate(assistant.copy(reasoningLevel = level))
                        },
                        useL3HubStyle = LocalSettings.current.displaySetting.useL3HubReasoningStyle,
                    )
                },
            )
            switchItem(
                checked = assistant.streamOutput,
                onCheckedChange = {
                    onUpdate(
                        assistant.copy(
                            streamOutput = it
                        )
                    )
                },
                supportingContent = { Text(stringResource(R.string.assistant_page_stream_output_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_stream_output)) },
            )
        }

        CardGroup {
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_temperature)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_temperature_warning)) },
                trailingContent = {
                    Switch(
                        checked = assistant.temperature != null,
                        onCheckedChange = { enabled ->
                            onUpdate(
                                assistant.copy(
                                    temperature = if (enabled) 1.0f else null
                                )
                            )
                        }
                    )
                }
            ) {
                AnimatedVisibility(visible = assistant.temperature != null) {
                    var temperatureInput by remember(assistant.id) {
                        mutableStateOf(assistant.temperature.toString())
                    }
                    val temperatureValue = temperatureInput.toFloatOrNull()
                    OutlinedTextField(
                        value = temperatureInput,
                        onValueChange = { value ->
                            temperatureInput = value
                            value.toFloatOrNull()?.takeIf { it in 0f..2f }?.let { temperature ->
                                onUpdate(
                                    assistant.copy(
                                        temperature = temperature
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = temperatureValue == null || temperatureValue !in 0f..2f,
                        supportingText = {
                            Text("0 - 2")
                        }
                    )
                }
            }
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_top_p)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_top_p_warning)) },
                trailingContent = {
                    Switch(
                        checked = assistant.topP != null,
                        onCheckedChange = { enabled ->
                            onUpdate(
                                assistant.copy(
                                    topP = if (enabled) 1.0f else null
                                )
                            )
                        }
                    )
                }
            ) {
                AnimatedVisibility(visible = assistant.topP != null) {
                    var topPInput by remember(assistant.id) {
                        mutableStateOf(assistant.topP.toString())
                    }
                    val topPValue = topPInput.toFloatOrNull()
                    OutlinedTextField(
                        value = topPInput,
                        onValueChange = { value ->
                            topPInput = value
                            value.toFloatOrNull()?.takeIf { it in 0f..1f }?.let { nextTopP ->
                                onUpdate(
                                    assistant.copy(
                                        topP = nextTopP
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = topPValue == null || topPValue !in 0f..1f,
                        supportingText = {
                            Text("0 - 1")
                        }
                    )
                }
            }
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_top_k)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_top_k_warning)) },
                trailingContent = {
                    Switch(
                        checked = assistant.topK != null,
                        onCheckedChange = { enabled ->
                            onUpdate(
                                assistant.copy(
                                    topK = if (enabled) 40 else null
                                )
                            )
                        }
                    )
                }
            ) {
                AnimatedVisibility(visible = assistant.topK != null) {
                    var topKInput by remember(assistant.id) {
                        mutableStateOf(assistant.topK.toString())
                    }
                    val topKValue = topKInput.toIntOrNull()
                    OutlinedTextField(
                        value = topKInput,
                        onValueChange = { value ->
                            topKInput = value
                            value.toIntOrNull()?.takeIf { it in 1..100 }?.let { nextTopK ->
                                onUpdate(
                                    assistant.copy(
                                        topK = nextTopK
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = topKValue == null || topKValue !in 1..100,
                        supportingText = {
                            Text("1 - 100")
                        }
                    )
                }
            }
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_context_message_limit)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_context_message_limit_desc)) },
            ) {
                var contextMessageLimitInput by remember(
                    assistant.id,
                    assistant.contextMessageLimit
                ) {
                    mutableStateOf(assistant.contextMessageLimit.toString())
                }
                var contextMessageLimitFocused by remember(assistant.id) {
                    mutableStateOf(false)
                }
                val focusManager = LocalFocusManager.current

                fun commitContextMessageLimit() {
                    val value = contextMessageLimitInput.toIntOrNull()
                    if (value == null) {
                        contextMessageLimitInput = assistant.contextMessageLimit.toString()
                        return
                    }

                    contextMessageLimitInput = value.toString()
                    if (value != assistant.contextMessageLimit) {
                        onUpdate(assistant.copy(contextMessageLimit = value))
                    }
                }

                OutlinedTextField(
                    value = contextMessageLimitInput,
                    onValueChange = { input ->
                        if (input.all(Char::isDigit) &&
                            (input.isEmpty() || input.toIntOrNull() != null)
                        ) {
                            contextMessageLimitInput = input
                            input.toIntOrNull()
                                ?.takeIf { it != assistant.contextMessageLimit }
                                ?.let { onUpdate(assistant.copy(contextMessageLimit = it)) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (contextMessageLimitFocused && !focusState.isFocused) {
                                commitContextMessageLimit()
                            }
                            contextMessageLimitFocused = focusState.isFocused
                        },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    singleLine = true,
                    supportingText = {
                        Text(
                            stringResource(R.string.assistant_page_context_message_limit_hint)
                        )
                    }
                )

                if (assistant.contextMessageLimit > 0) {
                    Text(
                        text = stringResource(R.string.assistant_page_context_message_limit_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_max_tokens)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_max_tokens_desc)) },
            ) {
                OutlinedTextField(
                    value = assistant.maxTokens?.toString() ?: "",
                    onValueChange = { text ->
                        val tokens = if (text.isBlank()) {
                            null
                        } else {
                            text.toIntOrNull()?.takeIf { it > 0 }
                        }
                        onUpdate(
                            assistant.copy(
                                maxTokens = tokens
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(stringResource(R.string.assistant_page_max_tokens_no_limit))
                    },
                    supportingText = {
                        if (assistant.maxTokens != null) {
                            Text(stringResource(R.string.assistant_page_max_tokens_limit, assistant.maxTokens))
                        } else {
                            Text(stringResource(R.string.assistant_page_max_tokens_no_token_limit))
                        }
                    }
                )
            }
        }

        CardGroup {
            switchItem(
                checked = assistant.useGradientBackground,
                onCheckedChange = {
                    onUpdate(
                        assistant.copy(
                            useGradientBackground = it
                        )
                    )
                },
                supportingContent = { Text(stringResource(R.string.assistant_page_gradient_background_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_gradient_background)) },
            )

            formItem(
                visible = !assistant.useGradientBackground,
                headlineContent = { Text(stringResource(R.string.assistant_page_chat_background)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_chat_background_desc)) },
            ) {
                BackgroundPicker(
                    background = assistant.background,
                    backgroundOpacity = assistant.backgroundOpacity,
                    onUpdate = { background ->
                        onUpdate(
                            assistant.copy(
                                background = background
                            )
                        )
                    }
                )
            }

            val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0f, 1f)
            formItem(
                visible = !assistant.useGradientBackground && assistant.background != null,
                headlineContent = { Text(stringResource(R.string.assistant_page_background_opacity)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_background_opacity_desc)) },
                trailingContent = {
                    Text(
                        text = stringResource(
                            R.string.assistant_page_background_opacity_value,
                            (backgroundOpacity * 100).roundToInt()
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            ) {
                Slider(
                    value = backgroundOpacity,
                    onValueChange = {
                        onUpdate(
                            assistant.copy(
                                backgroundOpacity = it.toFixed(2).toFloatOrNull()?.coerceIn(0f, 1f) ?: 1.0f
                            )
                        )
                    },
                    valueRange = 0f..1f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            formItem(
                visible = !assistant.useGradientBackground && assistant.background != null,
                headlineContent = { Text(stringResource(R.string.assistant_page_background_blur)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_background_blur_desc)) },
                trailingContent = {
                    Text(
                        text = "${(assistant.backgroundBlur.coerceIn(0f, 1f) * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            ) {
                Slider(
                    value = assistant.backgroundBlur.coerceIn(0f, 1f),
                    onValueChange = {
                        onUpdate(assistant.copy(backgroundBlur = it))
                    },
                    valueRange = 0f..1f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    if (showWorkspaceSheet) {
        WorkspaceSelectSheet(
            assistant = assistant,
            workspaces = workspaces,
            onSelect = { ids ->
                val parsed = ids.mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }.toSet()
                val primary = assistant.primaryWorkspaceId?.takeIf { it in parsed } ?: parsed.firstOrNull()
                onUpdate(assistant.copy(workspaceIds = parsed, workspaceId = primary))
            },
            onManage = {
                showWorkspaceSheet = false
                navController.navigate(Screen.Workspaces)
            },
            onDismiss = { showWorkspaceSheet = false },
        )
    }
}
