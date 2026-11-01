package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.InputLayoutStyle
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.hooks.rememberSharedPreferenceBoolean
import me.rerere.rikkahub.ui.terminal.RadioButton
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.rikkahub.ui.components.ui.NumberSlider
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.AppLanguage
import me.rerere.rikkahub.utils.getActivity
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@Composable
fun SettingPreferencesGeneralPage(embedded: Boolean = false, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    var ttsPlaybackSpeed by remember(settings.defaultTTSPlaybackSpeed) {
        mutableFloatStateOf(settings.defaultTTSPlaybackSpeed)
    }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings { it.copy(displaySetting = setting) }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            var showNicknameDialog by remember { mutableStateOf(false) }
            var nicknameDraft by remember(displaySetting.userNickname) {
                mutableStateOf(displaySetting.userNickname)
            }
            CardGroup {
                item(
                    onClick = {
                        nicknameDraft = displaySetting.userNickname
                        showNicknameDialog = true
                    },
                    leadingContent = {
                        UIAvatar(
                            name = displaySetting.userNickname.ifBlank {
                                stringResource(R.string.user_default_name)
                            },
                            value = displaySetting.userAvatar,
                            avatarSize = 40.dp,
                            onUpdate = { avatar ->
                                updateDisplaySetting(displaySetting.copy(userAvatar = avatar))
                            },
                        )
                    },
                    headlineContent = {
                        Text(
                            text = displaySetting.userNickname.ifBlank {
                                stringResource(R.string.user_default_name)
                            }
                        )
                    },
                    supportingContent = { Text(stringResource(R.string.setting_page_profile_desc)) },
                )
            }
            if (showNicknameDialog) {
                AlertDialog(
                    onDismissRequest = { showNicknameDialog = false },
                    title = { Text(stringResource(R.string.chat_page_edit_nickname)) },
                    text = {
                        OutlinedTextField(
                            value = nicknameDraft,
                            onValueChange = { nicknameDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.chat_page_nickname_placeholder)) },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                updateDisplaySetting(displaySetting.copy(userNickname = nicknameDraft.trim()))
                                showNicknameDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.chat_page_save))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showNicknameDialog = false }) {
                            Text(stringResource(R.string.chat_page_cancel))
                        }
                    },
                )
            }

            var languageTag by remember { mutableStateOf(AppLanguage.currentTag(context)) }
            var showLanguageDialog by remember { mutableStateOf(false) }
            CardGroup {
                item(
                    onClick = { showLanguageDialog = true },
                    trailingContent = {
                        CardGroupValue(
                            if (languageTag.isBlank()) {
                                stringResource(R.string.setting_page_language_system)
                            } else {
                                AppLanguage.displayName(languageTag)
                            }
                        )
                    },
                ) {
                    Text(stringResource(R.string.setting_page_language))
                }
            }
            if (showLanguageDialog) {
                LanguagePickerDialog(
                    selected = languageTag,
                    onSelect = { tag ->
                        AppLanguage.setTag(context, tag)
                        languageTag = tag
                        showLanguageDialog = false
                        context.getActivity()?.recreate()
                    },
                    onDismiss = { showLanguageDialog = false },
                )
            }

            var createNewConversationOnStart by rememberSharedPreferenceBoolean(
                "create_new_conversation_on_start",
                true
            )
            CardGroup {
                switchItem(
                    checked = createNewConversationOnStart,
                    onCheckedChange = { createNewConversationOnStart = it },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_create_new_conversation_on_start_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_create_new_conversation_on_start_title)) },
                )
                switchItem(
                    checked = displaySetting.sendOnEnter,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(sendOnEnter = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_send_on_enter_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_send_on_enter_title)) },
                )
                switchItem(
                    checked = settings.enableMessageQueue,
                    onCheckedChange = { enabled ->
                        vm.updateSettings { it.copy(enableMessageQueue = enabled) }
                    },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_message_queue_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_message_queue_title)) },
                )
            }

            CardGroup {
                switchItem(
                    checked = displaySetting.showMessageJumper,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showMessageJumper = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_show_message_jumper_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_show_message_jumper_title)) },
                )
                if (displaySetting.showMessageJumper) {
                    switchItem(
                        checked = displaySetting.messageJumperOnLeft,
                        onCheckedChange = { updateDisplaySetting(displaySetting.copy(messageJumperOnLeft = it)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_message_jumper_position_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_message_jumper_position_title)) },
                    )
                }
                switchItem(
                    checked = displaySetting.messageNavOnLeft,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(messageNavOnLeft = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_message_nav_position_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_message_nav_position_title)) },
                )
                switchItem(
                    checked = displaySetting.enableAutoScroll,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableAutoScroll = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_enable_auto_scroll_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_enable_auto_scroll_title)) },
                )
            }

            CardGroup {
                formItem(
                    headlineContent = { Text(stringResource(R.string.setting_display_page_background_effect_title)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_background_effect_desc)) },
                ) {
                    ConnectedChoiceButtons(
                        options = BackgroundEffectType.entries,
                        selected = displaySetting.effectiveBackgroundEffect,
                        onSelect = { type ->
                            updateDisplaySetting(
                                displaySetting.copy(
                                    backgroundEffectType = type,
                                    enableBlurEffect = type != BackgroundEffectType.OFF,
                                )
                            )
                        },
                    ) { type, selected ->
                        ConnectedChoiceLabel(
                            text = when (type) {
                                BackgroundEffectType.OFF -> stringResource(R.string.setting_display_page_background_effect_off)
                                BackgroundEffectType.BLUR -> stringResource(R.string.setting_display_page_background_effect_blur)
                                BackgroundEffectType.GLASS -> stringResource(R.string.setting_display_page_background_effect_glass)
                            },
                            selected = selected,
                        )
                    }
                }
                if (displaySetting.effectiveBackgroundEffect == BackgroundEffectType.BLUR) {
                    formItem(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_blur_opacity_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_blur_opacity_desc)) },
                        trailingContent = {
                            CardGroupValue("${(displaySetting.blurOpacity * 100).roundToInt()}%")
                        },
                    ) {
                        Slider(
                            value = displaySetting.blurOpacity,
                            onValueChange = {
                                updateDisplaySetting(displaySetting.copy(blurOpacity = it))
                            },
                            valueRange = 0f..1f,
                            steps = 19,
                        )
                    }
                }
                if (displaySetting.enableBlurEffect) {
                    formItem(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_glass_opacity_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_glass_opacity_desc)) },
                        trailingContent = {
                            CardGroupValue("${(displaySetting.glassOpacity * 100).roundToInt()}%")
                        },
                    ) {
                        Slider(
                            value = displaySetting.glassOpacity,
                            onValueChange = {
                                updateDisplaySetting(displaySetting.copy(glassOpacity = it))
                            },
                            valueRange = 0f..1f,
                            steps = 19,
                        )
                    }
                }
                formItem(
                    headlineContent = { Text(stringResource(R.string.setting_display_page_input_layout_title)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_input_layout_desc)) },
                ) {
                    ConnectedChoiceButtons(
                        options = InputLayoutStyle.entries,
                        selected = displaySetting.inputLayoutStyle,
                        onSelect = { layout ->
                            updateDisplaySetting(displaySetting.copy(inputLayoutStyle = layout))
                        },
                    ) { layout, selected ->
                        ConnectedChoiceLabel(
                            text = when (layout) {
                                InputLayoutStyle.SINGLE -> stringResource(R.string.setting_display_page_input_layout_single)
                                InputLayoutStyle.DOUBLE -> stringResource(R.string.setting_display_page_input_layout_double)
                            },
                            selected = selected,
                        )
                    }
                }
                switchItem(
                    checked = displaySetting.enableMessageGenerationHapticEffect,
                    onCheckedChange = {
                        updateDisplaySetting(displaySetting.copy(enableMessageGenerationHapticEffect = it))
                    },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_title)) },
                )
            }

            CardGroup {
                switchItem(
                    checked = displaySetting.skipCropImage,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(skipCropImage = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_skip_crop_image_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_skip_crop_image_title)) },
                )
                switchItem(
                    checked = displaySetting.pasteLongTextAsFile,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(pasteLongTextAsFile = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_as_file_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_as_file_title)) },
                )
                if (displaySetting.pasteLongTextAsFile) {
                    formItem(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_threshold_title)) },
                        trailingContent = { CardGroupValue("${displaySetting.pasteLongTextThreshold}") },
                    ) {
                        NumberSlider(
                            value = displaySetting.pasteLongTextThreshold,
                            onValueChange = {
                                updateDisplaySetting(displaySetting.copy(pasteLongTextThreshold = it))
                            },
                            valueRange = 100..10_000,
                        )
                    }
                }
            }

            CardGroup {
                switchItem(
                    checked = displaySetting.enableVolumeKeyScroll,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableVolumeKeyScroll = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_title)) },
                )
                if (displaySetting.enableVolumeKeyScroll) {
                    formItem(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_ratio)) },
                        trailingContent = { CardGroupValue("${(displaySetting.volumeKeyScrollRatio * 100).toInt()}%") },
                    ) {
                        Slider(
                            value = displaySetting.volumeKeyScrollRatio,
                            onValueChange = {
                                updateDisplaySetting(displaySetting.copy(volumeKeyScrollRatio = it))
                            },
                            valueRange = 0.25f..1.0f,
                            steps = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            CardGroup(
                title = { Text(stringResource(R.string.setting_page_tts_settings)) },
            ) {
                formItem(
                    headlineContent = { Text(stringResource(R.string.setting_tts_page_default_playback_speed)) },
                    supportingContent = { Text(stringResource(R.string.setting_tts_page_default_playback_speed_description)) },
                    trailingContent = { CardGroupValue("x${"%.1f".format(ttsPlaybackSpeed)}") },
                ) {
                    Slider(
                        value = ttsPlaybackSpeed,
                        onValueChange = {
                            ttsPlaybackSpeed = (it * 10).roundToInt() / 10f
                        },
                        onValueChangeFinished = {
                            vm.updateSettings {
                                it.copy(defaultTTSPlaybackSpeed = ttsPlaybackSpeed)
                            }
                        },
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                switchItem(
                    checked = displaySetting.ttsOnlyReadQuoted,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(ttsOnlyReadQuoted = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_tts_only_read_quoted_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_tts_only_read_quoted_title)) },
                )
                switchItem(
                    checked = displaySetting.ttsOnlyReadOutsideBrackets,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(ttsOnlyReadOutsideBrackets = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_tts_read_outside_brackets_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_tts_read_outside_brackets_title)) },
                )
                switchItem(
                    checked = displaySetting.autoPlayTTSAfterGeneration,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(autoPlayTTSAfterGeneration = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_auto_play_tts_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_auto_play_tts_title)) },
                )
                switchItem(
                    checked = displaySetting.voiceModeListenWhileReplying,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(voiceModeListenWhileReplying = it)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_voice_listen_while_replying_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_voice_listen_while_replying_title)) },
                )
            }

            CardGroup(title = { Text(stringResource(R.string.setting_app_lock_title)) }) {
                switchItem(
                    checked = settings.appLockEnabled,
                    onCheckedChange = { enabled ->
                        vm.updateSettings { it.copy(appLockEnabled = enabled) }
                    },
                    supportingContent = { Text(stringResource(R.string.setting_app_lock_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_app_lock_switch)) },
                )
                if (settings.appLockEnabled) {
                    formItem(
                        headlineContent = { Text(stringResource(R.string.setting_app_lock_timeout)) },
                        supportingContent = { Text(stringResource(R.string.setting_app_lock_timeout_desc)) },
                    ) {
                        ConnectedChoiceButtons(
                            options = listOf(0, 1, 5, 15),
                            selected = settings.appLockTimeoutMinutes,
                            onSelect = { minutes ->
                                vm.updateSettings { it.copy(appLockTimeoutMinutes = minutes) }
                            },
                        ) { minutes, selected ->
                            ConnectedChoiceLabel(
                                text = if (minutes == 0) {
                                    stringResource(R.string.setting_app_lock_immediately)
                                } else {
                                    stringResource(R.string.setting_app_lock_minutes, minutes)
                                },
                                selected = selected,
                            )
                        }
                    }
                }
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
                        Text(stringResource(R.string.setting_page_preferences_general))
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

@Composable
private fun LanguagePickerDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_page_language)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                (listOf("") + AppLanguage.supportedTags).forEach { tag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(tag) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = tag == selected, onClick = { onSelect(tag) })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (tag.isBlank()) {
                                stringResource(R.string.setting_page_language_system)
                            } else {
                                AppLanguage.displayName(tag)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
