package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.HeartbeatSetting
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.modelSettingItem
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.NumberSlider
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

@Composable
fun AssistantHeartbeatPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val heartbeat = assistant.heartbeat
    val modelState = rememberModelListState(
        modelId = heartbeat.modelId,
        providers = settings.providers,
        type = ModelType.CHAT,
    )

    fun update(block: (HeartbeatSetting) -> HeartbeatSetting) {
        vm.update(assistant.copy(heartbeat = block(heartbeat)))
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_heartbeat))
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardGroup {
                switchItem(
                    checked = heartbeat.enabled,
                    onCheckedChange = { enabled -> update { it.copy(enabled = enabled) } },
                    headlineContent = { Text(stringResource(R.string.heartbeat_page_enable_title)) },
                    supportingContent = { Text(stringResource(R.string.heartbeat_page_enable_desc)) },
                )
            }

            CardGroup {
                formItem(
                    headlineContent = { Text(stringResource(R.string.heartbeat_page_interval_title)) },
                    supportingContent = { Text(stringResource(R.string.heartbeat_page_interval_desc)) },
                    trailingContent = {
                        CardGroupValue(text = "${heartbeat.intervalMinutes} min")
                    },
                ) {
                    NumberSlider(
                        value = heartbeat.intervalMinutes,
                        onValueChange = { value ->
                            update { it.copy(intervalMinutes = value) }
                        },
                        valueRange = 5..720,
                        suffix = "min",
                    )
                }
                formItem(
                    headlineContent = { Text(stringResource(R.string.heartbeat_page_hours_title)) },
                    supportingContent = { Text(stringResource(R.string.heartbeat_page_hours_desc)) },
                    trailingContent = {
                        CardGroupValue(
                            text = "%02d:00 - %02d:00".format(heartbeat.startHour, heartbeat.endHour)
                        )
                    },
                ) {
                    Text(
                        text = stringResource(R.string.heartbeat_page_hours_start),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = heartbeat.startHour.toFloat(),
                        onValueChange = { value ->
                            update { it.copy(startHour = value.roundToInt().coerceIn(0, 23)) }
                        },
                        valueRange = 0f..23f,
                        steps = 22,
                    )
                    Text(
                        text = stringResource(R.string.heartbeat_page_hours_end),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = heartbeat.endHour.toFloat(),
                        onValueChange = { value ->
                            update { it.copy(endHour = value.roundToInt().coerceIn(0, 23)) }
                        },
                        valueRange = 0f..23f,
                        steps = 22,
                    )
                }
            }

            CardGroup {
                formItem(
                    headlineContent = { Text(stringResource(R.string.heartbeat_page_prompt_title)) },
                    supportingContent = { Text(stringResource(R.string.heartbeat_page_prompt_desc)) },
                ) {
                    OutlinedTextField(
                        value = heartbeat.prompt,
                        onValueChange = { text -> update { it.copy(prompt = text) } },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.heartbeat_page_prompt_placeholder)) },
                        minLines = 2,
                        maxLines = 6,
                    )
                }
            }

            CardGroup {
                modelSettingItem(
                    icon = HugeIcons.Clock01,
                    title = R.string.heartbeat_page_model_title,
                    state = modelState,
                    iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    placeholder = R.string.heartbeat_page_model_desc,
                    onClear = { update { it.copy(modelId = null) } },
                    clearContentDescription = R.string.heartbeat_page_model_clear,
                )
            }
        }
    }

    ModelListSheet(
        state = modelState,
        onSelect = { model ->
            update { it.copy(modelId = model.id) }
        },
    )
}
