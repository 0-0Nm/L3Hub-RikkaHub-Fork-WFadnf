package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ProviderSetting
import me.rerere.asr.ASRProviderSetting
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.FloatingBubbleIcon
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.ui.theme.CustomTheme
import me.rerere.rikkahub.ui.theme.PaletteSetting
import me.rerere.rikkahub.ui.theme.PresetThemes
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import me.rerere.tts.provider.TTSProviderSetting
import kotlin.uuid.Uuid

@Serializable
data class Settings(
    @Transient
    val init: Boolean = false,
    val dynamicColor: Boolean = true,
    val themeId: String = PresetThemes[0].id,
    val customThemes: List<CustomTheme> = emptyList(),
    // 调色板：预设或用户逐个改过的深浅两套颜色，默认不启用（跟随 themeId / 动态取色）
    val palette: PaletteSetting = PaletteSetting(),
    val developerMode: Boolean = false,
    val displaySetting: DisplaySetting = DisplaySetting(),
    val networkSetting: NetworkSetting = NetworkSetting(),
    val favoriteModels: List<Uuid> = emptyList(),
    val chatModelId: Uuid = Uuid.random(),
    val fastModelId: Uuid = Uuid.random(),
    val fastModelReasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    // 记忆整理用的模型，没设置时用快速模型
    val memoryModelId: Uuid? = null,
    val memoryModelReasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val imageGenerationModelId: Uuid = Uuid.random(),
    val enableAutoTitle: Boolean = true,
    // 生成过程中发送的消息是否进入队列排队; 关闭后直接打断当前回复立即发送
    val enableMessageQueue: Boolean = true,
    val titlePrompt: String = DEFAULT_TITLE_PROMPT,
    val translateModeId: Uuid = Uuid.random(),
    val translatePrompt: String = DEFAULT_TRANSLATION_PROMPT,
    val translateThinkingBudget: Int = 0,
    // 翻译页的目标语言，存 BCP 47 语言标签
    val translateTargetLanguage: String = "zh-CN",
    val enableSuggestion: Boolean = true,
    val suggestionPrompt: String = DEFAULT_SUGGESTION_PROMPT,
    val ocrModelId: Uuid = Uuid.random(),
    val ocrPrompt: String = DEFAULT_OCR_PROMPT,
    val assistantId: Uuid = DEFAULT_ASSISTANT_ID,
    val providers: List<ProviderSetting> = DEFAULT_PROVIDERS,
    // 手动维护的提供商分组（有序）与 提供商ID -> 分组名 的映射
    val providerGroups: List<String> = emptyList(),
    val providerGroupAssignments: Map<String, String> = emptyMap(),
    // 按接口域名单独给某个提供商设置代理: host -> proxyUrl
    val providerProxies: Map<String, String> = emptyMap(),
    val providerProxyUsernames: Map<String, String> = emptyMap(),
    val providerProxyPasswords: Map<String, String> = emptyMap(),
    // 应用锁：离开指定时间后要求验证锁屏密码
    val appLockEnabled: Boolean = false,
    val appLockTimeoutMinutes: Int = 5,
    // 额外信任的自签 CA（PEM 文本）
    val customCaCertificates: List<String> = emptyList(),
    val assistants: List<Assistant> = DEFAULT_ASSISTANTS,
    val assistantTags: List<Tag> = emptyList(),
    val searchServices: List<SearchServiceOptions> = listOf(SearchServiceOptions.DEFAULT),
    // 同时启用的搜索引擎 id；为空时只用 searchServiceSelected 指向的那个
    val enabledSearchServiceIds: Set<String> = emptySet(),
    val searchCommonOptions: SearchCommonOptions = SearchCommonOptions(),
    val searchServiceSelected: Int = 0,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val s3Config: S3Config = S3Config(),
    // 素材上传后端：S3 或 WebDAV
    val assetStorageType: AssetStorageType = AssetStorageType.S3,
    // RemoteFileStore 上传临时素材用的桶，和备份的 s3Config 互不影响；items 字段在这里不使用
    val uploadS3Config: S3Config = S3Config(),
    // 素材上传走 WebDAV 时的连接配置；path 是存放素材的目录
    val uploadWebDavConfig: WebDavConfig = WebDavConfig(path = "rikkahub_uploads"),
    val mediaGenerationProviders: List<MediaGenerationProviderSetting> = emptyList(),
    val ttsProviders: List<TTSProviderSetting> = DEFAULT_TTS_PROVIDERS,
    val selectedTTSProviderId: Uuid = DEFAULT_SYSTEM_TTS_ID,
    val defaultTTSPlaybackSpeed: Float = 1.0f,
    val asrProviders: List<ASRProviderSetting> = emptyList(),
    val selectedASRProviderId: Uuid? = null,
    val modeInjections: List<PromptInjection.ModeInjection> = DEFAULT_MODE_INJECTIONS,
    val lorebooks: List<Lorebook> = emptyList(),
    val quickMessages: List<QuickMessage> = emptyList(),
    val webServerEnabled: Boolean = false,
    val webServerPort: Int = 8080,
    val webServerJwtEnabled: Boolean = false,
    val webServerAccessPassword: String = "",
    val webServerLocalhostOnly: Boolean = false,
    // 内置 SSH 服务端：密码沿用 Web 服务器的访问密码，不单独维护第二份令牌
    val sshServerEnabled: Boolean = false,
    val sshServerPort: Int = 2222,
    val sshServerAllowLan: Boolean = false,
    val backupReminderConfig: BackupReminderConfig = BackupReminderConfig(),
    val sponsorAlertDismissedAt: Int = 0,
) {
    companion object {
        // 构造一个用于初始化的settings, 但它不能用于保存，防止使用初始值存储
        fun dummy() = Settings(init = true)
    }
}

@Serializable
data class NetworkSetting(
    val userAgent: String = "",
    val proxyUrl: String = "",
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    // 自定义重试：开关沿用旧字段名，升级后原来的开关状态不会丢
    val enableAutoRetry: Boolean = true,
    val retryMaxCount: Int = 3,
    val retryIntervalSeconds: Int = 1,
    // 逗号分隔的 HTTP 错误码，空表示只重试网络错误
    val retryErrorCodes: String = "",
)

@Serializable
enum class ChatFontFamily {
    @SerialName("default")
    DEFAULT,

    @SerialName("serif")
    SERIF,

    @SerialName("monospace")
    MONOSPACE,

    @SerialName("custom")
    CUSTOM,
}

@Serializable
enum class BackgroundEffectType {
    @SerialName("off")
    OFF,

    @SerialName("blur")
    BLUR,

    @SerialName("glass")
    GLASS,
}

@Serializable
enum class InputLayoutStyle {
    @SerialName("single")
    SINGLE,

    @SerialName("double")
    DOUBLE,
}

@Serializable
enum class ConversationSortOrder {
    @SerialName("update_time")
    UPDATE_TIME,

    @SerialName("create_time")
    CREATE_TIME,
}

@Serializable
enum class ContextMeterStyle {
    OFF,
    BAR,
    RING,
}

/** 页面/列表过渡的速度手感，只在没关动效时生效。 */
@Serializable
enum class AnimationSpeed {
    /** 跟随 Material 的 expressive 曲线 */
    SYSTEM,

    /** 收紧：同样的位移用更短的时间走完 */
    FAST,

    /** 摊开：慢一点，尾段拖得长一点 */
    GENTLE,
}

@Serializable
data class DisplaySetting(
    val userAvatar: Avatar = Avatar.Dummy,
    val userNickname: String = "",
    val showUserAvatar: Boolean = true,
    val showAssistantBubble: Boolean = false,
    val bubbleOpacity: Float = 1.0f,
    val showModelIcon: Boolean = true,
    val showModelName: Boolean = true,
    val showDateTimeInMessage: Boolean = false,
    val showTokenUsage: Boolean = true,
    // 输入框上方的上下文预警样式
    val contextMeterStyle: ContextMeterStyle = ContextMeterStyle.BAR,
    // 使用 L3Hub 的思考强度图标和圆形滑块
    val useL3HubReasoningStyle: Boolean = false,
    // 为按钮与开关显示阴影
    val buttonShadow: Boolean = false,
    // 任务清单全部完成后从输入框上方收起；回看走输入框左边的「+」菜单
    val hideCompletedTodoCard: Boolean = true,
    // Usage 看板的上下文占用用饼图显示
    val usagePieChart: Boolean = false,
    // 关闭所有动效：Material 的 motionScheme 换成 snap，列表/卡片的过渡直接跳变
    val disableAnimations: Boolean = false,
    // 页面转场和列表展开的速度手感；disableAnimations 打开时忽略这一项
    val animationSpeed: AnimationSpeed = AnimationSpeed.SYSTEM,
    // 上下文预警条用绝对值(数字/数字)显示, 关闭时显示百分比
    val contextMeterAbsolute: Boolean = false,
    val showThinkingContent: Boolean = true,
    val autoCloseThinking: Boolean = true,
    // 折叠的思考卡标题取思考内容的第一句，关掉则只显示思考时长
    val showThinkingSummary: Boolean = true,
    val updateCheckDisabledUntilEpochMillis: Long = 0L,
    val showMessageJumper: Boolean = true,
    val messageJumperOnLeft: Boolean = false,
    // 消息导航横条单独控制左右，和上面的跳转按钮互不影响
    val messageNavOnLeft: Boolean = false,
    val fontSizeRatio: Float = 1.0f,
    val enableMessageGenerationHapticEffect: Boolean = false,
    val skipCropImage: Boolean = true,
    val enableNotificationOnMessageGeneration: Boolean = false,
    val enableLiveUpdateNotification: Boolean = false,
    val enableFloatingChatWindow: Boolean = false,
    // 面板宽度占屏幕宽度的比例，设置里的滑块和面板右下角的拖动改的是同一个值
    val floatingPanelWidthRatio: Float = 1.0f,
    // 面板最高占可用高度的比例；内容少时照常收缩，这个只是上限
    val floatingPanelHeightRatio: Float = 1.0f,
    // 面板里预览区的最大高度(dp)，超出后在预览区内部滚动
    val floatingPreviewMaxHeight: Int = 200,
    val floatingBubbleIcon: FloatingBubbleIcon = FloatingBubbleIcon.AppLogo,
    val codeBlockAutoWrap: Boolean = false,
    val codeBlockAutoCollapse: Boolean = false,
    val showLineNumbers: Boolean = false,
    val ttsOnlyReadQuoted: Boolean = false,
    val ttsOnlyReadOutsideBrackets: Boolean = false,
    val autoPlayTTSAfterGeneration: Boolean = false,
    val voiceModeListenWhileReplying: Boolean = true,
    val pasteLongTextAsFile: Boolean = false,
    val pasteLongTextThreshold: Int = 1000,
    val sendOnEnter: Boolean = false,
    val enableAutoScroll: Boolean = true,
    val enableLatexRendering: Boolean = true,
    val enableQuoteHighlight: Boolean = false,
    val enableBlurEffect: Boolean = false,
    val backgroundEffectType: BackgroundEffectType = BackgroundEffectType.BLUR,
    val inputLayoutStyle: InputLayoutStyle = InputLayoutStyle.DOUBLE,
    val showToolCallOutput: Boolean = true,
    val uiCornerRadius: Int = 24,
    // 输入框卡片的内边距，和圆角一起决定内部元素是否和外框同心
    val inputPadding: Int = 8,
    // 全宽组件：气泡/卡片撑满父宽、圆角收成直角、贴边组件去掉左右内边距
    val fullWidthComponents: Boolean = false,
    // 模糊特效下气泡/输入框/顶栏的底色不透明度
    val blurOpacity: Float = 0.45f,
    // 玻璃底（设置项/选项卡/按钮/顶栏图标）的透明度，0 = 全透明，1 = 原实色
    val glassOpacity: Float = 0.35f,
    // 新对话界面中央的自定义标语
    val emptyChatSlogan: String = "",
    // 终端风格会话界面：等宽字体、深色背景和命令行提示符
    val terminalStyle: Boolean = false,
    val chatFontFamily: ChatFontFamily = ChatFontFamily.DEFAULT,
    val chatCustomFontPath: String = "",
    val chatCustomFontName: String = "",
    // 多字体回退链（有序）：同一字形靠前的字体优先，缺失的字形自动顺延到下一款（如英文款→中文款→俄文款）
    val chatCustomFontPaths: List<String> = emptyList(),
    val chatCustomFontNames: List<String> = emptyList(),
    // 聊天字体是否应用到整个应用（不限于聊天界面）；终端风格始终等宽，不受影响
    val fontApplyGlobal: Boolean = false,
    val enableVolumeKeyScroll: Boolean = false,
    val volumeKeyScrollRatio: Float = 1.0f,
    val conversationSortOrder: ConversationSortOrder = ConversationSortOrder.UPDATE_TIME,
)

@Serializable
enum class AssetStorageType {
    S3,
    WEBDAV,
}

@Serializable
data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val path: String = "rikkahub_backups",
    val items: List<BackupItem> = listOf(
        BackupItem.DATABASE,
        BackupItem.FILES
    ),
) {
    @Serializable
    enum class BackupItem {
        DATABASE,
        FILES,
    }
}

@Serializable
data class BackupReminderConfig(
    val enabled: Boolean = false,
    val intervalDays: Int = 7,
    val lastBackupTime: Long = 0L,
)

val DisplaySetting.effectiveBackgroundEffect: BackgroundEffectType
    get() = if (enableBlurEffect) backgroundEffectType else BackgroundEffectType.OFF
