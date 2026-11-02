package me.rerere.rikkahub.ui.pages.setting

import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionNotification
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.floating.FloatingPanelSizing
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPadding
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import kotlin.math.roundToInt

private val UPDATE_PAUSE_DAY_OPTIONS = listOf(7, 14, 21)
private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1_000L

@Composable
fun SettingPreferencesNotificationPage(embedded: Boolean = false, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    var showUpdatePauseDialog by remember { mutableStateOf(false) }
    var showFloatingIconPicker by remember { mutableStateOf(false) }
    var selectedUpdatePauseDays by remember { mutableStateOf(UPDATE_PAUSE_DAY_OPTIONS.first()) }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings { it.copy(displaySetting = setting) }
    }

    // 滑块拖动时只改这三份草稿，松手才写设置：每帧落盘要把整份 DisplaySetting 重新编码一遍
    var draftPanelWidth by remember(displaySetting.floatingPanelWidthRatio) {
        mutableFloatStateOf(FloatingPanelSizing.clampWidthRatio(displaySetting.floatingPanelWidthRatio))
    }
    var draftPanelHeight by remember(displaySetting.floatingPanelHeightRatio) {
        mutableFloatStateOf(FloatingPanelSizing.clampHeightRatio(displaySetting.floatingPanelHeightRatio))
    }
    var draftPreviewHeight by remember(displaySetting.floatingPreviewMaxHeight) {
        mutableFloatStateOf(
            FloatingPanelSizing.clampPreviewHeight(displaySetting.floatingPreviewMaxHeight).toFloat()
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val updateChecksEnabled =
        BuildConfig.UPDATE_CHECK_ENABLED &&
            displaySetting.updateCheckDisabledUntilEpochMillis <= System.currentTimeMillis()

    val permissionState = rememberPermissionState(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setOf(
            PermissionNotification
        ) else emptySet(),
    )
    PermissionManager(permissionState = permissionState)

    val context = LocalContext.current
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    // 从系统设置页授权回来后刷新
    LifecycleResumeEffect(Unit) {
        canDrawOverlays = Settings.canDrawOverlays(context)
        onPauseOrDispose {}
    }

    fun requestOverlayPermission() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
        )
    }

    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    var ignoringBatteryOptimizations by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }
    LifecycleResumeEffect(Unit) {
        ignoringBatteryOptimizations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        onPauseOrDispose {}
    }

    fun requestIgnoreBatteryOptimizations() {
        // 有的系统没有这个弹窗，退回电池优化列表页
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            "package:${context.packageName}".toUri(),
        )
        runCatching { context.startActivity(intent) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }

    

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .adaptiveHorizontalPadding(16.dp)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Play 版由商店负责更新，不提供更新提醒
            if (BuildConfig.UPDATE_CHECK_ENABLED && !BuildConfig.IS_PLAY_STORE) {
                CardGroup {
                    item(
                        onClick = {
                            selectedUpdatePauseDays = UPDATE_PAUSE_DAY_OPTIONS.first()
                            showUpdatePauseDialog = true
                        },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_updates_title)) },
                        supportingContent = {
                            Text(
                                if (updateChecksEnabled) {
                                    stringResource(R.string.setting_update_reminder_enabled)
                                } else {
                                    stringResource(
                                        R.string.setting_update_reminder_paused_until,
                                        Instant.ofEpochMilli(displaySetting.updateCheckDisabledUntilEpochMillis)
                                            .toLocalDateTime(),
                                    )
                                }
                            )
                        },
                        trailingContent = {
                            Icon(HugeIcons.ArrowRight01, contentDescription = null)
                        },
                    )
                }
            
            }

            CardGroup(title = { Text(stringResource(R.string.setting_page_preferences_notification_background_generation)) }) {
                switchItem(
                    checked = displaySetting.enableNotificationOnMessageGeneration,
                    onCheckedChange = {
                        if (it && !permissionState.allPermissionsGranted) {
                            permissionState.requestPermissions()
                        }
                        updateDisplaySetting(displaySetting.copy(enableNotificationOnMessageGeneration = it))
                    },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_notification_message_generated_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_notification_message_generated)) },
                )
                if (displaySetting.enableNotificationOnMessageGeneration) {
                    switchItem(
                        checked = displaySetting.enableLiveUpdateNotification,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(enableLiveUpdateNotification = it))
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_live_update_notification_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_live_update_notification)) },
                    )
                }
                switchItem(
                    checked = displaySetting.enableFloatingChatWindow,
                    onCheckedChange = {
                        if (it && !canDrawOverlays) requestOverlayPermission()
                        updateDisplaySetting(displaySetting.copy(enableFloatingChatWindow = it))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.setting_page_preferences_notification_floating_window_desc))
                    },
                    headlineContent = { Text(stringResource(R.string.setting_page_preferences_notification_floating_window)) },
                )
                if (displaySetting.enableFloatingChatWindow) {
                    item(
                        onClick = { showFloatingIconPicker = true },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                FloatingBubbleIconThumb(
                                    icon = displaySetting.floatingBubbleIcon,
                                    size = 20.dp,
                                )
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.floating_bubble_icon_title)) },
                        supportingContent = { Text(stringResource(R.string.floating_bubble_icon_desc)) },
                        trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null) },
                    )
                    formItem(
                        headlineContent = { Text(stringResource(R.string.floating_panel_width)) },
                        trailingContent = {
                            CardGroupValue(FloatingPanelSizing.percentLabel(draftPanelWidth))
                        },
                    ) {
                        Slider(
                            value = draftPanelWidth,
                            onValueChange = { draftPanelWidth = FloatingPanelSizing.clampWidthRatio(it) },
                            onValueChangeFinished = {
                                updateDisplaySetting(displaySetting.copy(floatingPanelWidthRatio = draftPanelWidth))
                            },
                            valueRange = FloatingPanelSizing.MIN_WIDTH_RATIO..FloatingPanelSizing.MAX_WIDTH_RATIO,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    formItem(
                        headlineContent = { Text(stringResource(R.string.floating_panel_height)) },
                        trailingContent = {
                            CardGroupValue(FloatingPanelSizing.percentLabel(draftPanelHeight))
                        },
                    ) {
                        Slider(
                            value = draftPanelHeight,
                            onValueChange = { draftPanelHeight = FloatingPanelSizing.clampHeightRatio(it) },
                            onValueChangeFinished = {
                                updateDisplaySetting(displaySetting.copy(floatingPanelHeightRatio = draftPanelHeight))
                            },
                            valueRange = FloatingPanelSizing.MIN_HEIGHT_RATIO..FloatingPanelSizing.MAX_HEIGHT_RATIO,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    formItem(
                        headlineContent = { Text(stringResource(R.string.floating_preview_height)) },
                        trailingContent = {
                            CardGroupValue(
                                stringResource(R.string.floating_size_dp, draftPreviewHeight.roundToInt())
                            )
                        },
                    ) {
                        Slider(
                            value = draftPreviewHeight,
                            onValueChange = { draftPreviewHeight = it },
                            onValueChangeFinished = {
                                updateDisplaySetting(
                                    displaySetting.copy(
                                        floatingPreviewMaxHeight =
                                            FloatingPanelSizing.clampPreviewHeight(draftPreviewHeight.roundToInt()),
                                    )
                                )
                            },
                            valueRange = FloatingPanelSizing.MIN_PREVIEW_HEIGHT_DP.toFloat()..
                                FloatingPanelSizing.MAX_PREVIEW_HEIGHT_DP.toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (displaySetting.enableFloatingChatWindow && !canDrawOverlays) {
                    item(
                        onClick = ::requestOverlayPermission,
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_notification_overlay_permission)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_notification_overlay_permission_desc)) },
                        trailingContent = {
                            Icon(HugeIcons.ArrowRight01, contentDescription = null)
                        },
                    )
                }
                item(
                    onClick = {
                        if (!ignoringBatteryOptimizations) requestIgnoreBatteryOptimizations()
                    },
                    headlineContent = { Text(stringResource(R.string.setting_page_preferences_notification_battery_optimization)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (ignoringBatteryOptimizations) {
                                    R.string.setting_page_preferences_notification_battery_optimization_granted
                                } else {
                                    R.string.setting_page_preferences_notification_battery_optimization_desc
                                }
                            )
                        )
                    },
                    trailingContent = if (ignoringBatteryOptimizations) {
                        null
                    } else {
                        { Icon(HugeIcons.ArrowRight01, contentDescription = null) }
                    },
                )
            }

        }
    }
    if (showFloatingIconPicker) {
        FloatingBubbleIconPicker(
            current = displaySetting.floatingBubbleIcon,
            onSelect = { icon ->
                // 旧图片由 FilesManager.saveBubbleIcon 自己在写入成功之后清掉，这里只管存设置
                updateDisplaySetting(displaySetting.copy(floatingBubbleIcon = icon))
            },
            onDismiss = { showFloatingIconPicker = false },
        )
    }
    if (embedded) {
        content()
    } else {
        Scaffold(
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(stringResource(R.string.setting_page_preferences_notification))
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
