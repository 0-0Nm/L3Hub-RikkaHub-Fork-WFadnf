package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.FloatingBubbleIcon
import me.rerere.rikkahub.ui.components.ui.EmojiPicker
import me.rerere.rikkahub.ui.floating.BubbleIconContent
import me.rerere.rikkahub.ui.floating.BubblePresets
import me.rerere.rikkahub.ui.floating.bubblePresetIcon
import me.rerere.rikkahub.ui.terminal.OutlinedButton
import org.koin.compose.koinInject

/**
 * 悬浮球图标选择：内置图标、Emoji 和相册图片。
 *
 * 选中后立刻回调，怎么存由调用处决定。相册图片会先复制到应用私有目录，
 * 相册里的原件被删掉或改掉都不影响已经选好的图标。
 */
@Composable
internal fun FloatingBubbleIconPicker(
    current: FloatingBubbleIcon,
    onSelect: (FloatingBubbleIcon) -> Unit,
    onDismiss: () -> Unit,
) {
    val filesManager = koinInject<FilesManager>()
    val scope = rememberCoroutineScope()
    var showEmojiPicker by remember { mutableStateOf(false) }
    var pickingImage by remember { mutableStateOf(false) }
    var pickFailed by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickingImage = true
            pickFailed = false
            scope.launch {
                // 复制文件是同步 IO，别放在主线程
                val local = withContext(Dispatchers.IO) {
                    filesManager.saveBubbleIcon(uri)
                }
                pickingImage = false
                if (local == null) {
                    // 复制失败要说一声，不然点了相册像是没反应
                    pickFailed = true
                    return@launch
                }
                onSelect(FloatingBubbleIcon.Image(local.toString()))
                onDismiss()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (showEmojiPicker) {
            EmojiPicker(
                onEmojiSelected = { emoji ->
                    onSelect(FloatingBubbleIcon.Emoji(emoji.emoji))
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.floating_bubble_icon_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.floating_bubble_icon_preset),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconChoice(
                        selected = current is FloatingBubbleIcon.AppLogo,
                        label = stringResource(R.string.floating_bubble_icon_app_logo),
                        onClick = {
                            onSelect(FloatingBubbleIcon.AppLogo)
                            onDismiss()
                        },
                    ) {
                        BubbleIconPreview(FloatingBubbleIcon.AppLogo)
                    }
                    BubblePresets.forEach { preset ->
                        IconChoice(
                            selected = current is FloatingBubbleIcon.Preset && current.name == preset.name,
                            label = preset.name,
                            onClick = {
                                onSelect(FloatingBubbleIcon.Preset(preset.name))
                                onDismiss()
                            },
                        ) {
                            BubbleIconPreview(FloatingBubbleIcon.Preset(preset.name))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.floating_bubble_icon_custom),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { showEmojiPicker = true },
                        enabled = !pickingImage,
                    ) {
                        Text(stringResource(R.string.floating_bubble_icon_emoji))
                    }
                    OutlinedButton(
                        onClick = { imagePicker.launch("image/*") },
                        enabled = !pickingImage,
                    ) {
                        Text(stringResource(R.string.floating_bubble_icon_gallery))
                    }
                }
                if (pickFailed) {
                    Text(
                        text = stringResource(R.string.floating_bubble_icon_load_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun IconChoice(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.size(width = 64.dp, height = 84.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(colors.secondaryContainer)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) colors.primary else colors.outlineVariant,
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
        )
    }
}

/** 选择器里的大预览，尺寸和悬浮球上的一致 */
@Composable
internal fun BubbleIconPreview(icon: FloatingBubbleIcon) {
    BubbleIconContent(
        icon = icon,
        size = 52.dp,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

/** 设置页列表项里的小预览 */
@Composable
internal fun FloatingBubbleIconThumb(icon: FloatingBubbleIcon, size: Dp) {
    BubbleIconContent(
        icon = icon,
        size = size,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}
