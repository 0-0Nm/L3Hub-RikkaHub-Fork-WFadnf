package me.rerere.rikkahub.ui.hooks

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalSharedTransitionScope
import me.rerere.rikkahub.ui.pages.mediacreation.mediaCreationHeroKey
import me.rerere.rikkahub.ui.theme.adaptiveCornerRadius

/** 源条目侧的圆角。页面到位后收到 0，退场时再长回这个值。 */
val HeroSourceCorner = 20.dp

@Composable
fun Modifier.heroAnimation(
    key: Any,
): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current
    return with(sharedTransitionScope) {
        this@heroAnimation.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedVisibilityScope
        )
    }
}

/**
 * 容器变换：同一个 key 的两个容器（比如列表项和它打开的整页）在页面切换时互相展开、收回，内容交叉淡入淡出。
 * 两边都按宽度缩放、顶部对齐，不会在动画的每一帧重新测量整页。
 */
@Composable
fun Modifier.heroContainer(
    key: Any,
    shape: Shape,
): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current
    return with(sharedTransitionScope) {
        this@heroContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedVisibilityScope,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter),
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

/**
 * 目标页面侧的容器变换：把整页包进去，它就从源条目（同一个 [heroKey]）展开，返回时收回条目里。
 *
 * 圆角跟着进出场做：进场时从源条目的圆角收到直角，退场时再长回去，和 [heroSource] 那一侧对上。
 * 页面自己不再播平移/景深，转场只留淡入淡出（见 RouteActivity 的 transitionSpec）。
 */
@Composable
fun HeroPage(
    heroKey: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // 全宽组件模式下条目是直角，页面起始形状得跟着它，否则展开的第一帧会对不上
    val sourceCorner = adaptiveCornerRadius(HeroSourceCorner)
    val corner by LocalNavAnimatedContentScope.current.transition.animateDp(label = "heroPageCorner") { state ->
        if (state == EnterExitState.Visible) 0.dp else sourceCorner
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            // 弹簧会过冲，负半径会让 RoundedCornerShape 直接抛异常
            .heroContainer(heroKey, RoundedCornerShape(corner.coerceAtLeast(0.dp))),
    ) {
        content()
    }
}

/** 源条目侧：和 [HeroPage] 成对使用，shape 要和条目自身的圆角一致。 */
@Composable
fun Modifier.heroSource(
    screen: Screen,
    shape: Shape? = null,
): Modifier = heroContainer(
    screen.heroKey(),
    shape ?: RoundedCornerShape(adaptiveCornerRadius(HeroSourceCorner)),
)

/**
 * 每个 Screen 一个稳定 key：源条目和目标页面各算一次，两边必须完全一致。
 *
 * 新增 Screen 时这里会编译不过，正好逼着补一条——key 撞车会让两个页面的容器变换互相串台。
 */
fun Screen.heroKey(): String = when (this) {
    is Screen.Chat -> "hero_chat_$id"
    is Screen.ShareHandler -> "hero_share_handler"
    Screen.History -> "hero_history"
    Screen.Favorite -> "hero_favorite"
    Screen.Assistant -> "hero_assistant"
    is Screen.AssistantDetail -> "hero_assistant_detail_$id"
    is Screen.AssistantBasic -> "hero_assistant_basic_$id"
    is Screen.AssistantPrompt -> "hero_assistant_prompt_$id"
    is Screen.AssistantMemory -> "hero_assistant_memory_$id"
    is Screen.MemoryFile -> "hero_memory_file_${memoryId}_${path.orEmpty()}"
    is Screen.AssistantRequest -> "hero_assistant_request_$id"
    is Screen.AssistantHeartbeat -> "hero_assistant_heartbeat_$id"
    is Screen.AssistantMcp -> "hero_assistant_mcp_$id"
    is Screen.AssistantLocalTool -> "hero_assistant_local_tool_$id"
    is Screen.AssistantInjections -> "hero_assistant_injections_$id"
    Screen.Translator -> "hero_translator"
    Screen.Setting -> "hero_setting"
    Screen.Backup -> "hero_backup"
    Screen.ImageGen -> "hero_image_gen"
    Screen.MediaCreationSessions -> "hero_media_creation_sessions"
    // 会话列表那侧用的就是 mediaCreationHeroKey，这里保持一致，免得同一对元素两个 key
    is Screen.MediaCreation -> mediaCreationHeroKey(id)
    is Screen.WebView -> "hero_webview_${contentId}_$url"
    Screen.SettingTheme -> "hero_setting_theme"
    Screen.SettingPalette -> "hero_setting_palette"
    Screen.SettingPreferences -> "hero_setting_preferences"
    Screen.SettingPreferencesTheme -> "hero_setting_preferences_theme"
    Screen.SettingPreferencesNotification -> "hero_setting_preferences_notification"
    Screen.SettingPreferencesGeneral -> "hero_setting_preferences_general"
    Screen.SettingPreferencesUI -> "hero_setting_preferences_ui"
    Screen.SettingPreferencesNetwork -> "hero_setting_preferences_network"
    Screen.SettingProvider -> "hero_setting_provider"
    is Screen.SettingProviderDetail -> "hero_setting_provider_detail_$providerId"
    Screen.SettingModels -> "hero_setting_models"
    Screen.SettingAbout -> "hero_setting_about"
    Screen.SettingSearch -> "hero_setting_search"
    is Screen.SettingSearchDetail -> "hero_setting_search_detail_$serviceId"
    Screen.SettingSpeech -> "hero_setting_speech"
    Screen.SettingMedia -> "hero_setting_media"
    is Screen.SettingMediaProviderDetail -> "hero_setting_media_provider_detail_$providerId"
    Screen.SettingMcp -> "hero_setting_mcp"
    is Screen.SettingMcpDetail -> "hero_setting_mcp_detail_$serverId"
    Screen.SettingDonate -> "hero_setting_donate"
    Screen.SettingFiles -> "hero_setting_files"
    Screen.SettingWeb -> "hero_setting_web"
    Screen.Debug -> "hero_debug"
    Screen.Log -> "hero_log"
    Screen.Extensions -> "hero_extensions"
    Screen.QuickMessages -> "hero_quick_messages"
    Screen.Prompts -> "hero_prompts"
    Screen.Skills -> "hero_skills"
    Screen.Workspaces -> "hero_workspaces"
    is Screen.WorkspaceDetail -> "hero_workspace_detail_$id"
    is Screen.WorkspaceTerminal -> "hero_workspace_terminal_$id"
    is Screen.WorkspaceSshEditor -> "hero_workspace_ssh_editor_${id.orEmpty()}"
    is Screen.WorkspaceFileEditor -> "hero_workspace_file_editor_${id}_${area}_$path"
    is Screen.SkillDetail -> "hero_skill_detail_$skillName"
    Screen.MessageSearch -> "hero_message_search"
    Screen.Stats -> "hero_stats"
}
