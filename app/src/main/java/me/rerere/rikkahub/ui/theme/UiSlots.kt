package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import me.rerere.rikkahub.R
import kotlinx.serialization.Serializable

/**
 * 界面上的一个具体位置。
 *
 * M3 的 ColorScheme 给的是「角色」：primary 既是按钮底色、又是强调文字、又是图标，
 * 调一次得同时照顾三处的明度需求，往上调按钮好看文字就发灰，往下压文字清楚按钮就发闷。
 *
 * 这一层给的是「位置」：一个位置只画一处。默认全部从角色派生，视觉和以前一模一样；
 * 想单独改哪一处就改哪一处，不会牵动别的地方。
 */
@Immutable
data class UiSlots(
    /** 聊天页、设置页的最底层 */
    val pageBackground: Color,
    /** 顶栏底色 */
    val topBar: Color,
    /** 卡片底 */
    val card: Color,
    /** 列表项底 */
    val listItem: Color,
    /** 弹窗、菜单、悬浮面板 */
    val panel: Color,
    /** 输入框底 */
    val inputBar: Color,
    /** AI 气泡底 */
    val aiBubble: Color,
    /** 我发出去的气泡底 */
    val userBubble: Color,
    /** 我发出去的气泡里的字 */
    val userBubbleText: Color,
    /** 标题 */
    val titleText: Color,
    /** 正文 */
    val bodyText: Color,
    /** 说明、时间戳、占位符 */
    val captionText: Color,
    /** 强调文字、链接 */
    val accentText: Color,
    /** 图标 */
    val accentIcon: Color,
    /** 按钮底 */
    val button: Color,
    /** 按钮上的字 */
    val buttonText: Color,
    /** 分隔线 */
    val divider: Color,
    /** 边框、描边 */
    val border: Color,
    /** 错误文字、删除按钮 */
    val errorText: Color,
    /** 错误提示底 */
    val errorBackground: Color,
    /** 错误提示上的字 */
    val errorOnBackground: Color,
    /** 弹窗背后的遮罩 */
    val scrim: Color,
) {
    /** 按 [UiSlot] 的声明顺序列出，给编辑面板用。 */
    fun values(): List<Color> = UiSlot.entries.map { get(it) }

    fun get(slot: UiSlot): Color = when (slot) {
        UiSlot.PAGE_BACKGROUND -> pageBackground
        UiSlot.TOP_BAR -> topBar
        UiSlot.CARD -> card
        UiSlot.LIST_ITEM -> listItem
        UiSlot.PANEL -> panel
        UiSlot.INPUT_BAR -> inputBar
        UiSlot.AI_BUBBLE -> aiBubble
        UiSlot.USER_BUBBLE -> userBubble
        UiSlot.USER_BUBBLE_TEXT -> userBubbleText
        UiSlot.TITLE_TEXT -> titleText
        UiSlot.BODY_TEXT -> bodyText
        UiSlot.CAPTION_TEXT -> captionText
        UiSlot.ACCENT_TEXT -> accentText
        UiSlot.ACCENT_ICON -> accentIcon
        UiSlot.BUTTON -> button
        UiSlot.BUTTON_TEXT -> buttonText
        UiSlot.DIVIDER -> divider
        UiSlot.BORDER -> border
        UiSlot.ERROR_TEXT -> errorText
        UiSlot.ERROR_BACKGROUND -> errorBackground
        UiSlot.ERROR_ON_BACKGROUND -> errorOnBackground
        UiSlot.SCRIM -> scrim
    }

    fun with(slot: UiSlot, color: Color): UiSlots = when (slot) {
        UiSlot.PAGE_BACKGROUND -> copy(pageBackground = color)
        UiSlot.TOP_BAR -> copy(topBar = color)
        UiSlot.CARD -> copy(card = color)
        UiSlot.LIST_ITEM -> copy(listItem = color)
        UiSlot.PANEL -> copy(panel = color)
        UiSlot.INPUT_BAR -> copy(inputBar = color)
        UiSlot.AI_BUBBLE -> copy(aiBubble = color)
        UiSlot.USER_BUBBLE -> copy(userBubble = color)
        UiSlot.USER_BUBBLE_TEXT -> copy(userBubbleText = color)
        UiSlot.TITLE_TEXT -> copy(titleText = color)
        UiSlot.BODY_TEXT -> copy(bodyText = color)
        UiSlot.CAPTION_TEXT -> copy(captionText = color)
        UiSlot.ACCENT_TEXT -> copy(accentText = color)
        UiSlot.ACCENT_ICON -> copy(accentIcon = color)
        UiSlot.BUTTON -> copy(button = color)
        UiSlot.BUTTON_TEXT -> copy(buttonText = color)
        UiSlot.DIVIDER -> copy(divider = color)
        UiSlot.BORDER -> copy(border = color)
        UiSlot.ERROR_TEXT -> copy(errorText = color)
        UiSlot.ERROR_BACKGROUND -> copy(errorBackground = color)
        UiSlot.ERROR_ON_BACKGROUND -> copy(errorOnBackground = color)
        UiSlot.SCRIM -> copy(scrim = color)
    }
}

/** 位置清单。顺序就是编辑面板里的顺序。 */
enum class UiSlot(val labelRes: Int, val usageRes: Int) {
    PAGE_BACKGROUND(R.string.palette_slot_page_background, R.string.palette_slot_use_page_background),
    TOP_BAR(R.string.palette_slot_top_bar, R.string.palette_slot_use_top_bar),
    CARD(R.string.palette_slot_card, R.string.palette_slot_use_card),
    LIST_ITEM(R.string.palette_slot_list_item, R.string.palette_slot_use_list_item),
    PANEL(R.string.palette_slot_panel, R.string.palette_slot_use_panel),
    INPUT_BAR(R.string.palette_slot_input_bar, R.string.palette_slot_use_input_bar),
    AI_BUBBLE(R.string.palette_slot_ai_bubble, R.string.palette_slot_use_ai_bubble),
    USER_BUBBLE(R.string.palette_slot_user_bubble, R.string.palette_slot_use_user_bubble),
    USER_BUBBLE_TEXT(R.string.palette_slot_user_bubble_text, R.string.palette_slot_use_user_bubble_text),
    TITLE_TEXT(R.string.palette_slot_title_text, R.string.palette_slot_use_title_text),
    BODY_TEXT(R.string.palette_slot_body_text, R.string.palette_slot_use_body_text),
    CAPTION_TEXT(R.string.palette_slot_caption_text, R.string.palette_slot_use_caption_text),
    ACCENT_TEXT(R.string.palette_slot_accent_text, R.string.palette_slot_use_accent_text),
    ACCENT_ICON(R.string.palette_slot_accent_icon, R.string.palette_slot_use_accent_icon),
    BUTTON(R.string.palette_slot_button, R.string.palette_slot_use_button),
    BUTTON_TEXT(R.string.palette_slot_button_text, R.string.palette_slot_use_button_text),
    DIVIDER(R.string.palette_slot_divider, R.string.palette_slot_use_divider),
    BORDER(R.string.palette_slot_border, R.string.palette_slot_use_border),
    ERROR_TEXT(R.string.palette_slot_error_text, R.string.palette_slot_use_error_text),
    ERROR_BACKGROUND(R.string.palette_slot_error_background, R.string.palette_slot_use_error_background),
    ERROR_ON_BACKGROUND(R.string.palette_slot_error_on_background, R.string.palette_slot_use_error_on_background),
    SCRIM(R.string.palette_slot_scrim, R.string.palette_slot_use_scrim),
    ;
}

/** 用户改过的位置。null 表示没动过，跟着角色走。 */
@Serializable
data class UiSlotColors(
    val pageBackground: Long? = null,
    val topBar: Long? = null,
    val card: Long? = null,
    val listItem: Long? = null,
    val panel: Long? = null,
    val inputBar: Long? = null,
    val aiBubble: Long? = null,
    val userBubble: Long? = null,
    val userBubbleText: Long? = null,
    val titleText: Long? = null,
    val bodyText: Long? = null,
    val captionText: Long? = null,
    val accentText: Long? = null,
    val accentIcon: Long? = null,
    val button: Long? = null,
    val buttonText: Long? = null,
    val divider: Long? = null,
    val border: Long? = null,
    val errorText: Long? = null,
    val errorBackground: Long? = null,
    val errorOnBackground: Long? = null,
    val scrim: Long? = null,
) {
    fun get(slot: UiSlot): Long? = when (slot) {
        UiSlot.PAGE_BACKGROUND -> pageBackground
        UiSlot.TOP_BAR -> topBar
        UiSlot.CARD -> card
        UiSlot.LIST_ITEM -> listItem
        UiSlot.PANEL -> panel
        UiSlot.INPUT_BAR -> inputBar
        UiSlot.AI_BUBBLE -> aiBubble
        UiSlot.USER_BUBBLE -> userBubble
        UiSlot.USER_BUBBLE_TEXT -> userBubbleText
        UiSlot.TITLE_TEXT -> titleText
        UiSlot.BODY_TEXT -> bodyText
        UiSlot.CAPTION_TEXT -> captionText
        UiSlot.ACCENT_TEXT -> accentText
        UiSlot.ACCENT_ICON -> accentIcon
        UiSlot.BUTTON -> button
        UiSlot.BUTTON_TEXT -> buttonText
        UiSlot.DIVIDER -> divider
        UiSlot.BORDER -> border
        UiSlot.ERROR_TEXT -> errorText
        UiSlot.ERROR_BACKGROUND -> errorBackground
        UiSlot.ERROR_ON_BACKGROUND -> errorOnBackground
        UiSlot.SCRIM -> scrim
    }

    fun with(slot: UiSlot, color: Long?): UiSlotColors = when (slot) {
        UiSlot.PAGE_BACKGROUND -> copy(pageBackground = color)
        UiSlot.TOP_BAR -> copy(topBar = color)
        UiSlot.CARD -> copy(card = color)
        UiSlot.LIST_ITEM -> copy(listItem = color)
        UiSlot.PANEL -> copy(panel = color)
        UiSlot.INPUT_BAR -> copy(inputBar = color)
        UiSlot.AI_BUBBLE -> copy(aiBubble = color)
        UiSlot.USER_BUBBLE -> copy(userBubble = color)
        UiSlot.USER_BUBBLE_TEXT -> copy(userBubbleText = color)
        UiSlot.TITLE_TEXT -> copy(titleText = color)
        UiSlot.BODY_TEXT -> copy(bodyText = color)
        UiSlot.CAPTION_TEXT -> copy(captionText = color)
        UiSlot.ACCENT_TEXT -> copy(accentText = color)
        UiSlot.ACCENT_ICON -> copy(accentIcon = color)
        UiSlot.BUTTON -> copy(button = color)
        UiSlot.BUTTON_TEXT -> copy(buttonText = color)
        UiSlot.DIVIDER -> copy(divider = color)
        UiSlot.BORDER -> copy(border = color)
        UiSlot.ERROR_TEXT -> copy(errorText = color)
        UiSlot.ERROR_BACKGROUND -> copy(errorBackground = color)
        UiSlot.ERROR_ON_BACKGROUND -> copy(errorOnBackground = color)
        UiSlot.SCRIM -> copy(scrim = color)
    }

    /** 每格改成同一个颜色，用来一次性确认哪些地方接上了。 */
    fun filledWith(argb: Long): UiSlotColors = UiSlot.entries.fold(this) { acc, slot ->
        acc.with(slot, argb)
    }

    fun overlay(base: UiSlots): UiSlots = UiSlot.entries.fold(base) { acc, slot ->
        val value = get(slot)
        if (value == null) acc else acc.with(slot, Color(value.toInt()))
    }
}

/** 角色 → 位置的默认对应关系。和改造之前每个地方实际用的角色一致。 */
fun uiSlotsFrom(scheme: ColorScheme): UiSlots = UiSlots(
    pageBackground = scheme.background,
    topBar = scheme.surfaceContainer,
    card = scheme.surfaceContainerHighest,
    listItem = scheme.surfaceBright,
    panel = scheme.surface,
    inputBar = scheme.surfaceContainerLow,
    aiBubble = scheme.surfaceContainerHigh,
    userBubble = scheme.primaryContainer,
    userBubbleText = scheme.onPrimaryContainer,
    titleText = scheme.onSurface,
    bodyText = scheme.onSurfaceVariant,
    captionText = scheme.onSurfaceVariant,
    accentText = scheme.primary,
    accentIcon = scheme.primary,
    button = scheme.primary,
    buttonText = scheme.onPrimary,
    divider = scheme.outlineVariant,
    border = scheme.outline,
    errorText = scheme.error,
    errorBackground = scheme.errorContainer,
    errorOnBackground = scheme.onErrorContainer,
    scrim = scheme.scrim,
)

val LocalUiSlots = compositionLocalOf { uiSlotsFrom(lightColorScheme()) }
