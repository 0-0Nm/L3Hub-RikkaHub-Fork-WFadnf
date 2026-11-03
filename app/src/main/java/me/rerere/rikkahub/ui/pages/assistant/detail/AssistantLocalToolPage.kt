package me.rerere.rikkahub.ui.pages.assistant.detail

import android.Manifest
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.toast.ToastType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Calendar03
import me.rerere.hugeicons.stroke.ChartLineData01
import me.rerere.hugeicons.stroke.ClipboardPaste
import me.rerere.hugeicons.stroke.Flowchart01
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.JavaScript
import me.rerere.hugeicons.stroke.MessageQuestion
import me.rerere.hugeicons.stroke.SmartPhone01
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.subagent.subAgentLabel
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.permission.PermissionInfo
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.hasUsageStatsPermission
import me.rerere.rikkahub.utils.openUsageAccessSettings
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

@Composable
fun AssistantLocalToolPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_local_tools))
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
        AssistantLocalToolContent(
            innerPadding = innerPadding,
            assistant = assistant,
            assistants = settings.assistants,
            onUpdate = { vm.update(it) }
        )
    }
}

@Composable
private fun AssistantLocalToolContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    assistants: List<Assistant>,
    onUpdate: (Assistant) -> Unit
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    var showSubAgentPicker by remember { mutableStateOf(false) }
    val subAgentCandidates = assistants.filter { it.isSubAgent && it.id != assistant.id }
    val allowedSubAgentCount = subAgentCandidates.count { it.id in assistant.allowedSubAgentIds }
    val permissionRequiredText =
        stringResource(R.string.assistant_page_local_tools_screen_time_permission_required)

    val calendarPermissionState = rememberPermissionState(
        permissions = setOf(
            PermissionInfo(
                permission = Manifest.permission.READ_CALENDAR,
                displayName = { Text(stringResource(R.string.permission_calendar_read)) },
                usage = { Text(stringResource(R.string.permission_calendar_read_desc)) },
                required = true
            ),
            PermissionInfo(
                permission = Manifest.permission.WRITE_CALENDAR,
                displayName = { Text(stringResource(R.string.permission_calendar_write)) },
                usage = { Text(stringResource(R.string.permission_calendar_write_desc)) },
                required = true
            ),
        )
    )
    PermissionManager(permissionState = calendarPermissionState)

    fun toggleLocalTool(option: LocalToolOption, enabled: Boolean) {
        if (enabled && option == LocalToolOption.ScreenTime && !context.hasUsageStatsPermission()) {
            toaster.show(message = permissionRequiredText, type = ToastType.Warning)
            context.openUsageAccessSettings()
        }
        if (enabled && option == LocalToolOption.Calendar && !calendarPermissionState.allPermissionsGranted) {
            calendarPermissionState.requestPermissions()
            return
        }
        val newLocalTools = if (enabled) {
            assistant.localTools + option
        } else {
            assistant.localTools - option
        }
        onUpdate(assistant.copy(localTools = newLocalTools))
    }

    fun CardGroupScope.toolItem(
        option: LocalToolOption,
        icon: ImageVector,
        @StringRes title: Int,
        @StringRes description: Int,
    ) {
        val checked = assistant.localTools.contains(option)
        switchItem(
            checked = checked,
            onCheckedChange = { toggleLocalTool(option, it) },
            // 图标底色跟着开关状态变化
            leadingContent = {
                CardGroupIcon(
                    icon = icon,
                    containerColor = if (checked) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (checked) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
            supportingContent = { Text(stringResource(description)) },
            headlineContent = { Text(stringResource(title)) },
        )
    }

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
        CardGroup {
            toolItem(
                option = LocalToolOption.JavascriptEngine,
                icon = HugeIcons.JavaScript,
                title = R.string.assistant_page_local_tools_javascript_engine_title,
                description = R.string.assistant_page_local_tools_javascript_engine_desc,
            )
            toolItem(
                option = LocalToolOption.TimeInfo,
                icon = HugeIcons.Clock01,
                title = R.string.assistant_page_local_tools_time_info_title,
                description = R.string.assistant_page_local_tools_time_info_desc,
            )
            toolItem(
                option = LocalToolOption.Clipboard,
                icon = HugeIcons.ClipboardPaste,
                title = R.string.assistant_page_local_tools_clipboard_title,
                description = R.string.assistant_page_local_tools_clipboard_desc,
            )
            toolItem(
                option = LocalToolOption.Tts,
                icon = HugeIcons.VolumeHigh,
                title = R.string.assistant_page_local_tools_tts_title,
                description = R.string.assistant_page_local_tools_tts_desc,
            )
            toolItem(
                option = LocalToolOption.AskUser,
                icon = HugeIcons.MessageQuestion,
                title = R.string.assistant_page_local_tools_ask_user_title,
                description = R.string.assistant_page_local_tools_ask_user_desc,
            )
            toolItem(
                option = LocalToolOption.ScreenTime,
                icon = HugeIcons.SmartPhone01,
                title = R.string.assistant_page_local_tools_screen_time_title,
                description = R.string.assistant_page_local_tools_screen_time_desc,
            )
            toolItem(
                option = LocalToolOption.Calendar,
                icon = HugeIcons.Calendar03,
                title = R.string.assistant_page_local_tools_calendar_title,
                description = R.string.assistant_page_local_tools_calendar_desc,
            )
            toolItem(
                option = LocalToolOption.ChartDisplay,
                icon = HugeIcons.ChartLineData01,
                title = R.string.assistant_page_local_tools_chart_display_title,
                description = R.string.assistant_page_local_tools_chart_display_desc,
            )
            toolItem(
                option = LocalToolOption.SubAgent,
                icon = HugeIcons.Flowchart01,
                title = R.string.assistant_page_local_tools_sub_agent_title,
                description = R.string.assistant_page_local_tools_sub_agent_desc,
            )
            // 开了派发工具，或者它自己是子代理（子代理会话会自动拿到派发工具）时，
            // 这个范围都可能生效
            if (assistant.localTools.contains(LocalToolOption.SubAgent) || assistant.isSubAgent) {
                item(
                    onClick = if (subAgentCandidates.isNotEmpty()) {
                        { showSubAgentPicker = true }
                    } else {
                        null
                    },
                    leadingContent = {
                        CardGroupIcon(
                            icon = HugeIcons.Flowchart01,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = when {
                                subAgentCandidates.isEmpty() -> stringResource(
                                    R.string.assistant_page_allowed_sub_agents_empty
                                )
                                assistant.allowedSubAgentIds.isEmpty() -> stringResource(
                                    R.string.assistant_page_allowed_sub_agents_summary_all
                                )
                                allowedSubAgentCount == 0 -> stringResource(
                                    R.string.assistant_page_allowed_sub_agents_missing
                                )
                                else -> stringResource(
                                    R.string.assistant_page_allowed_sub_agents_summary_selected,
                                    allowedSubAgentCount,
                                )
                            }
                        )
                    },
                    headlineContent = {
                        Text(stringResource(R.string.assistant_page_allowed_sub_agents))
                    },
                    trailingContent = {
                        Icon(
                            imageVector = HugeIcons.ArrowRight01,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }

    SubAgentAllowlistDialog(
        show = showSubAgentPicker,
        assistant = assistant,
        candidates = subAgentCandidates,
        onDismiss = { showSubAgentPicker = false },
        onConfirm = { ids ->
            onUpdate(assistant.copy(allowedSubAgentIds = ids))
            showSubAgentPicker = false
        },
    )
}

/** 多选派发范围；一项都不勾表示不限制 */
@Composable
private fun SubAgentAllowlistDialog(
    show: Boolean,
    assistant: Assistant,
    candidates: List<Assistant>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Uuid>) -> Unit,
) {
    if (!show) return
    var selected by remember { mutableStateOf(assistant.allowedSubAgentIds) }

    RikkaConfirmDialog(
        show = show,
        title = stringResource(R.string.assistant_page_allowed_sub_agents),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = { onConfirm(selected) },
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.assistant_page_allowed_sub_agents_dialog_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            candidates.forEach { candidate ->
                val checked = candidate.id in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { enabled ->
                                selected = if (enabled) {
                                    selected + candidate.id
                                } else {
                                    selected - candidate.id
                                }
                            },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Text(
                        text = subAgentLabel(candidate),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
