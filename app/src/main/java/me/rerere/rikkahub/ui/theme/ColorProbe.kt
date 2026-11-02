package me.rerere.rikkahub.ui.theme

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.ui.ColorPickerRow
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.hooks.rememberUserSettingsState
import me.rerere.rikkahub.utils.getActivity
import org.koin.compose.koinInject

/** 编辑模式的开关。开着的时候整块屏幕变成一个取色器。 */
object ColorProbeState {
    var enabled by mutableStateOf(false)
}

/** AMOLED 下被 Theme.kt 强制压成纯黑、取色修改不生效的底色槽。与 Theme.amoledSlots() 保持一致。 */
private val AMOLED_SLOTS = setOf(
    UiSlot.PAGE_BACKGROUND, UiSlot.TOP_BAR, UiSlot.CARD, UiSlot.LIST_ITEM,
    UiSlot.PANEL, UiSlot.INPUT_BAR, UiSlot.AI_BUBBLE,
)

private data class ProbeMatch(
    val slot: UiSlot?,
    val role: PaletteRole?,
    val sampled: Color,
    val distance: Double,
)

/** 加权 RGB 距离，绿通道权重最高，和人眼感受接近一点。 */
private fun colorDistance(a: Color, b: Color): Double {
    val dr = (a.red - b.red).toDouble()
    val dg = (a.green - b.green).toDouble()
    val db = (a.blue - b.blue).toDouble()
    val da = (a.alpha - b.alpha).toDouble()
    return dr * dr * 2.0 + dg * dg * 4.0 + db * db * 3.0 + da * da * 10.0
}

/** 超出这个距离就认为未匹配到任何颜色——多半是图片、渐变或者半透明叠出来的。 */
private const val MATCH_THRESHOLD = 0.0035

internal fun ColorScheme.roleColors(): Map<PaletteRole, Color> = mapOf(
    PaletteRole.PRIMARY to primary,
    PaletteRole.ON_PRIMARY to onPrimary,
    PaletteRole.PRIMARY_CONTAINER to primaryContainer,
    PaletteRole.ON_PRIMARY_CONTAINER to onPrimaryContainer,
    PaletteRole.INVERSE_PRIMARY to inversePrimary,
    PaletteRole.SECONDARY to secondary,
    PaletteRole.ON_SECONDARY to onSecondary,
    PaletteRole.SECONDARY_CONTAINER to secondaryContainer,
    PaletteRole.ON_SECONDARY_CONTAINER to onSecondaryContainer,
    PaletteRole.TERTIARY to tertiary,
    PaletteRole.ON_TERTIARY to onTertiary,
    PaletteRole.TERTIARY_CONTAINER to tertiaryContainer,
    PaletteRole.ON_TERTIARY_CONTAINER to onTertiaryContainer,
    PaletteRole.BACKGROUND to background,
    PaletteRole.ON_BACKGROUND to onBackground,
    PaletteRole.SURFACE to surface,
    PaletteRole.ON_SURFACE to onSurface,
    PaletteRole.SURFACE_VARIANT to surfaceVariant,
    PaletteRole.ON_SURFACE_VARIANT to onSurfaceVariant,
    PaletteRole.SURFACE_DIM to surfaceDim,
    PaletteRole.SURFACE_BRIGHT to surfaceBright,
    PaletteRole.SURFACE_CONTAINER_LOWEST to surfaceContainerLowest,
    PaletteRole.SURFACE_CONTAINER_LOW to surfaceContainerLow,
    PaletteRole.SURFACE_CONTAINER to surfaceContainer,
    PaletteRole.SURFACE_CONTAINER_HIGH to surfaceContainerHigh,
    PaletteRole.SURFACE_CONTAINER_HIGHEST to surfaceContainerHighest,
    PaletteRole.INVERSE_SURFACE to inverseSurface,
    PaletteRole.INVERSE_ON_SURFACE to inverseOnSurface,
    PaletteRole.SURFACE_TINT to surfaceTint,
    PaletteRole.ERROR to error,
    PaletteRole.ON_ERROR to onError,
    PaletteRole.ERROR_CONTAINER to errorContainer,
    PaletteRole.ON_ERROR_CONTAINER to onErrorContainer,
    PaletteRole.OUTLINE to outline,
    PaletteRole.OUTLINE_VARIANT to outlineVariant,
    PaletteRole.SCRIM to scrim,
)

private fun resolveMatch(sampled: Color, slots: UiSlots, scheme: ColorScheme): ProbeMatch {
    var best = ProbeMatch(UiSlot.PAGE_BACKGROUND, null, sampled, Double.MAX_VALUE)
    UiSlot.entries.forEach { slot ->
        val d = colorDistance(sampled, slots.get(slot))
        if (d < best.distance) best = ProbeMatch(slot, null, sampled, d)
    }
    scheme.roleColors().forEach { (role, color) ->
        val d = colorDistance(sampled, color)
        if (d < best.distance) best = ProbeMatch(null, role, sampled, d)
    }
    return best
}

/** 截当前窗口，取 [point] 那一个像素。坐标是窗口坐标系。 */
private fun capturePixel(window: Window, point: Offset, onResult: (Color?) -> Unit) {
    val decor = window.decorView
    val width = decor.width
    val height = decor.height
    if (width <= 0 || height <= 0) {
        onResult(null)
        return
    }
    val bitmap = try {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    } catch (_: Throwable) {
        onResult(null)
        return
    }
    PixelCopy.request(
        window,
        bitmap,
        { code ->
            if (code == PixelCopy.SUCCESS) {
                val x = point.x.toInt().coerceIn(0, width - 1)
                val y = point.y.toInt().coerceIn(0, height - 1)
                onResult(Color(bitmap.getPixel(x, y)))
            } else {
                onResult(null)
            }
            bitmap.recycle()
        },
        Handler(Looper.getMainLooper()),
    )
}

/**
 * 屏幕取色器：点任意一处，截那一刻的像素，在位置和角色里找最接近的一个。
 *
 * Compose 没有「这个像素是谁画的」这种接口，所以只能反查——把当前所有色值列出来，
 * 看哪个和采样到的像素最接近。对不上就说明那块是图片、渐变或者半透明叠出来的。
 */
@Composable
fun ColorProbeOverlay() {
    if (!ColorProbeState.enabled) return

    val settings by rememberUserSettingsState()
    val store = koinInject<SettingsStore>()
    val scope = rememberCoroutineScope()
    val dark = LocalDarkMode.current
    val amoledDarkMode by rememberAmoledDarkMode()
    val slots = LocalUiSlots.current
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context, view) { context.getActivity() }

    var origin by remember { mutableStateOf(Offset.Zero) }
    var match by remember { mutableStateOf<ProbeMatch?>(null) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(Color.White) }
    var hint by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }

    fun save(slot: UiSlot, argb: Long): Boolean {
        // 终端风格：位置层整体走终端配色，调色板的位置覆盖不参与（见 Theme.kt），改了看不见
        if (settings.displaySetting.terminalStyle) {
            saveError = "终端风格下位置颜色不生效；请先关闭终端风格"
            return false
        }
        // 动态取色优先级高于调色板（见 Theme.kt），开了改了也看不见，先拦下说明
        if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            saveError = "已开启「跟随系统动态取色」，此修改不会生效；请先关闭动态取色"
            return false
        }
        // 没启用调色板时，位置色是「跟着角色走」的；直接落盘会悄悄把基座切成默认调色板
        if (!settings.palette.enabled) {
            saveError = "尚未启用调色板，此修改不会生效；请先在「调色板」设置中启用"
            return false
        }
        // AMOLED 深色下底色槽被固定为纯黑（见 Theme.amoledSlots），改了界面也不变
        if (dark && amoledDarkMode && slot in AMOLED_SLOTS) {
            saveError = "AMOLED 模式下底色槽固定为纯黑；请改其他位置或先关闭 AMOLED"
            return false
        }
        saveError = null
        scope.launch {
            try {
                store.update { s ->
                    val p = s.palette
                    val current = if (dark) p.darkSlots else p.lightSlots
                    val next = (current ?: UiSlotColors()).with(slot, argb)
                    s.copy(
                        palette = p.copy(
                            presetId = p.presetId ?: DEFAULT_PALETTE_ID,
                            lightSlots = if (dark) p.lightSlots else next,
                            darkSlots = if (dark) next else p.darkSlots,
                        )
                    )
                }
            } catch (e: Exception) {
                hint = "保存失败：${e.message ?: "未知错误"}"
            }
        }
        return true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .pointerInput(activity) {
                detectTapGestures { offset ->
                    val act = activity
                    if (act == null) {
                        hint = "找不到 Activity，无法截图"
                        return@detectTapGestures
                    }
                    capturePixel(act.window, offset + origin) { sampled ->
                        if (sampled == null) {
                            hint = "无法截取屏幕"
                        } else {
                            hint = null
                            saveError = null
                            match = resolveMatch(sampled, slots, scheme)
                            editing = false
                        }
                    }
                }
            }
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = scheme.inverseSurface,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "点击任意位置查看颜色",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.inverseOnSurface,
                )
                TextButton(onClick = { ColorProbeState.enabled = false }) {
                    Text("退出", color = scheme.inversePrimary)
                }
            }
        }

        hint?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = scheme.errorContainer,
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onErrorContainer,
                )
            }
        }

        match?.let { result ->
            val slot = result.slot
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = scheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(result.sampled)
                        )
                        Column(Modifier.weight(1f)) {
                            val name = when {
                                result.distance > MATCH_THRESHOLD -> "未匹配到任何颜色"
                                slot != null -> "位置：${slotLabel(slot)}"
                                result.role != null -> "角色：${slotRoleLabel(result.role)}"
                                else -> "未知"
                            }
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleSmall,
                                color = scheme.onSurface,
                            )
                            Text(
                                text = formatHexColor(result.sampled.argbLong()),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }

                    when {
                        result.distance > MATCH_THRESHOLD -> Text(
                            text = "该区域由图片、渐变或半透明叠加而成，不来自单一色值。",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )

                        slot != null && !editing -> Button(
                            onClick = {
                                draft = slots.get(slot)
                                saveError = null
                                editing = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("编辑此颜色")
                        }

                        slot != null && editing -> {
                            saveError?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.error,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                            Box(Modifier.heightIn(max = 320.dp)) {
                                ColorPickerRow(
                                    color = draft,
                                    onColorChange = { draft = it },
                                    showAlpha = true,
                                    scrollable = true,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { editing = false; saveError = null }) { Text("取消") }
                                Button(
                                    onClick = {
                                        if (save(slot, draft.argbLong())) {
                                            editing = false
                                            match = null
                                        }
                                    }
                                ) { Text("应用") }
                            }
                        }

                        else -> Text(
                            text = "此颜色可在「按颜色」列表中找到，位置列表暂未收录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 位置名。不能在这里用 stringResource（在 pointerInput 之外但仍是 composable 作用域），直接给中文表。 */
private fun slotLabel(slot: UiSlot): String = when (slot) {
    UiSlot.PAGE_BACKGROUND -> "页面背景"
    UiSlot.TOP_BAR -> "顶栏"
    UiSlot.CARD -> "卡片"
    UiSlot.LIST_ITEM -> "列表项"
    UiSlot.PANEL -> "弹窗面板"
    UiSlot.INPUT_BAR -> "输入框"
    UiSlot.AI_BUBBLE -> "AI 消息气泡"
    UiSlot.USER_BUBBLE -> "用户消息气泡"
    UiSlot.USER_BUBBLE_TEXT -> "我的气泡文字"
    UiSlot.TITLE_TEXT -> "标题文字"
    UiSlot.BODY_TEXT -> "正文"
    UiSlot.CAPTION_TEXT -> "说明文字"
    UiSlot.ACCENT_TEXT -> "强调文字"
    UiSlot.ACCENT_ICON -> "图标"
    UiSlot.BUTTON -> "按钮"
    UiSlot.BUTTON_TEXT -> "按钮文字"
    UiSlot.DIVIDER -> "分隔线"
    UiSlot.BORDER -> "边框"
    UiSlot.ERROR_TEXT -> "错误文字"
    UiSlot.ERROR_BACKGROUND -> "错误提示背景"
    UiSlot.ERROR_ON_BACKGROUND -> "错误提示文字"
    UiSlot.SCRIM -> "遮罩"
}

private fun slotRoleLabel(role: PaletteRole): String = role.name.lowercase().replace('_', ' ')
