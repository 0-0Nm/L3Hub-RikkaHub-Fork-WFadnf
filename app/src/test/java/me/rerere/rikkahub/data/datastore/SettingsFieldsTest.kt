package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ProviderSetting
import me.rerere.asr.ASRProviderSetting
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.FloatingCounterPalette
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.ui.theme.CustomTheme
import me.rerere.rikkahub.ui.theme.PaletteColors
import me.rerere.rikkahub.ui.theme.PaletteSetting
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.uuid.Uuid

class SettingsFieldsTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `every settings property has exactly one field`() {
        val properties = Settings.serializer().descriptor.elementNames.sorted()

        assertEquals(properties, SETTINGS_FIELDS.map { it.property.name }.sorted())
    }

    @Test
    fun `settings survive a round trip through preferences`() {
        // 样本里和默认值相同的字段测不出读写是否对得上
        val defaults = emptyPreferences().toSettings()
        assertEquals(
            emptyList<String>(),
            SETTINGS_FIELDS.filterNot { it.changed(defaults, SAMPLE) }.map { it.property.name },
        )

        val preferences = mutablePreferencesOf().apply { putSettings(SAMPLE) }

        assertEquals(SAMPLE, preferences.toSettings())
    }

    // 这里的 key 名和取值就是用户盘上的格式，改动它们等于丢掉已有的设置
    @Test
    fun `stored format stays compatible with existing data`() {
        val stored = mutablePreferencesOf().apply { putSettings(SAMPLE) }.asMap().mapKeys { it.key.name }

        assertEquals(STORED_KEYS, stored.keys)
        assertEquals(false, stored["dynamic_color"])
        assertEquals(9090, stored["web_server_port"])
        assertEquals(1.5f, stored["default_tts_playback_speed"])
        assertEquals("title", stored["title_prompt"])
        assertEquals("00000000-0000-0000-0000-000000000001", stored["chat_model"])
        assertEquals("LOW", stored["fast_model_reasoning_level"])
        assertEquals("""[{"id":"00000000-0000-0000-0000-000000000007","name":"tag"}]""", stored["assistant_tags"])
        assertEquals("""{"resultSize":3}""", stored["search_common"])
    }

    @Test
    fun `missing keys read as first launch values`() {
        val settings = emptyPreferences().toSettings()

        assertEquals(DEFAULT_AUTO_MODEL_ID, settings.chatModelId)
        assertEquals(DEFAULT_AUTO_MODEL_ID, settings.fastModelId)
        assertEquals(DEFAULT_AUTO_MODEL_ID, settings.translateModeId)
        assertEquals(DEFAULT_MODE_INJECTIONS, settings.modeInjections)
        assertEquals(DEFAULT_ASSISTANT_ID, settings.assistantId)
        assertEquals(DEFAULT_SYSTEM_TTS_ID, settings.selectedTTSProviderId)
        assertEquals(null, settings.selectedASRProviderId)
        assertEquals(null, settings.memoryModelId)
        assertEquals(true, settings.dynamicColor)
        assertEquals(8080, settings.webServerPort)
        assertEquals(false, settings.sshServerEnabled)
        assertEquals(2222, settings.sshServerPort)
        assertEquals(false, settings.sshServerAllowLan)
        assertEquals(0f, settings.displaySetting.floatingCounterOffsetX, 0f)
        assertEquals(0f, settings.displaySetting.floatingCounterOffsetY, 0f)
        assertEquals(1f, settings.displaySetting.floatingCounterScale, 0f)
    }

    @Test
    fun `E341 display settings retain offsets when editing and resetting the numeral palette`() {
        val preferences = mutablePreferencesOf(
            SettingsStore.DISPLAY_SETTING to """{"floatingCounterOffsetX":-0.75,"floatingCounterOffsetY":1.0}""",
        )
        val legacy = preferences.toSettings()
        val original = legacy.displaySetting
        assertEquals(-0.75f, original.floatingCounterOffsetX, 0f)
        assertEquals(1f, original.floatingCounterOffsetY, 0f)
        assertEquals(FloatingCounterPalette.Default, original.floatingCounterPalette)
        assertEquals(1f, original.floatingCounterScale, 0f)

        val palette = original.floatingCounterPalette.withHex(50, "a0B1c2").withHex(50, "#12")
        val edited = legacy.copy(displaySetting = original.copy(floatingCounterPalette = palette))
        preferences.putSettings(edited, persisted = legacy)
        val reloaded = preferences.toSettings()
        assertEquals(original.copy(floatingCounterPalette = FloatingCounterPalette(at50 = "#A0B1C2")), reloaded.displaySetting)

        preferences.putSettings(legacy, persisted = reloaded)
        assertEquals(original, preferences.toSettings().displaySetting)
    }

    @Test
    fun `E342 display settings default counter scale without losing palette or offsets`() {
        val preferences = mutablePreferencesOf(
            SettingsStore.DISPLAY_SETTING to """{"floatingCounterPalette":{"at0":"#010203","at25":"#112233","at50":"#445566","at75":"#778899","at100":"#AABBCC"},"floatingCounterOffsetX":-0.75,"floatingCounterOffsetY":1.0,"floatingCounterFontPath":"fonts/counter.ttf","floatingBubbleScale":1.5,"floatingDetailTextScale":0.75}""",
        )
        val legacy = preferences.toSettings()
        val original = legacy.displaySetting
        assertEquals(1f, original.floatingCounterScale, 0f)
        assertEquals(FloatingCounterPalette("#010203", "#112233", "#445566", "#778899", "#AABBCC"), original.floatingCounterPalette)
        assertEquals(-0.75f, original.floatingCounterOffsetX, 0f)
        assertEquals(1f, original.floatingCounterOffsetY, 0f)
        assertEquals("fonts/counter.ttf", original.floatingCounterFontPath)
        assertEquals(1.5f, original.floatingBubbleScale, 0f)
        assertEquals(0.75f, original.floatingDetailTextScale, 0f)

        val resized = legacy.copy(displaySetting = original.copy(floatingCounterScale = 2f))
        preferences.putSettings(resized, persisted = legacy)
        val reloaded = preferences.toSettings()
        assertEquals(original.copy(floatingCounterScale = 2f), reloaded.displaySetting)

        preferences.putSettings(legacy, persisted = reloaded)
        assertEquals(original, preferences.toSettings().displaySetting)
    }

    @Test
    fun `counter scale round trips from 50 through 200 percent independently of bubble size`() {
        val preferences = mutablePreferencesOf().apply { putSettings(SAMPLE) }
        var persisted = preferences.toSettings()
        // Includes 50%, 100%, 200%, every integer percentage, and a fractional slider value.
        val scales = (50..200).map { it / 100f } + 0.73125f
        for (scale in scales) {
            val updated = persisted.copy(
                displaySetting = persisted.displaySetting.copy(floatingCounterScale = scale),
            )
            preferences.putSettings(updated, persisted = persisted)
            val reloaded = preferences.toSettings()
            assertEquals("counter scale $scale", scale, reloaded.displaySetting.floatingCounterScale, 0f)
            assertEquals(SAMPLE.displaySetting.copy(floatingCounterScale = scale), reloaded.displaySetting)
            persisted = reloaded
        }
    }

    @Test
    fun `palette and position changes preserve a resized counter`() {
        val preferences = mutablePreferencesOf().apply { putSettings(SAMPLE) }
        val persisted = preferences.toSettings()
        val updated = persisted.copy(
            displaySetting = persisted.displaySetting.copy(
                floatingCounterPalette = FloatingCounterPalette.Default,
                floatingCounterOffsetX = 1f,
                floatingCounterOffsetY = -1f,
            ),
        )
        preferences.putSettings(updated, persisted = persisted)

        assertEquals(updated.displaySetting, preferences.toSettings().displaySetting)
        assertEquals(1.75f, preferences.toSettings().displaySetting.floatingCounterScale, 0f)
    }

    @Test
    fun `unknown reasoning level reads as auto`() {
        val preferences = mutablePreferencesOf(SettingsStore.FAST_MODEL_REASONING_LEVEL to "REMOVED_LEVEL")

        assertEquals(ReasoningLevel.AUTO, preferences.toSettings().fastModelReasoningLevel)
    }

    @Test
    fun `clearing the asr provider removes its key`() {
        val preferences = mutablePreferencesOf().apply { putSettings(SAMPLE) }

        preferences.putSettings(SAMPLE.copy(selectedASRProviderId = null), persisted = SAMPLE)

        assertFalse(SettingsStore.SELECTED_ASR_PROVIDER in preferences)
        assertEquals(null, preferences.toSettings().selectedASRProviderId)
    }

    @Test
    fun `only changed fields are written`() {
        val preferences = mutablePreferencesOf().apply { putSettings(SAMPLE) }
        // 盘上的值故意和已落盘的那份不一致：没变的字段如果也被重写，它就会变回去
        preferences[SettingsStore.TITLE_PROMPT] = "on disk"

        preferences.putSettings(SAMPLE.copy(themeId = "changed"), persisted = SAMPLE)

        assertEquals("changed", preferences[SettingsStore.THEME_ID])
        assertEquals("on disk", preferences[SettingsStore.TITLE_PROMPT])
    }
}

private fun id(n: Int) = Uuid.parse("00000000-0000-0000-0000-%012d".format(n))

// 每个字段都取了和默认值不同的值
private val SAMPLE = Settings(
    dynamicColor = false,
    themeId = "custom",
    customThemes = listOf(CustomTheme(id = "theme", name = "Theme")),
    palette = PaletteSetting(
        presetId = "ocean",
        light = PaletteColors(primary = 0xFF112233, onPrimary = 0xFF445566),
        dark = PaletteColors(primary = 0xFF778899),
    ),
    developerMode = true,
    appLockEnabled = true,
    appLockTimeoutMinutes = 15,
    displaySetting = DisplaySetting(
        userNickname = "me",
        displayMode = DisplayMode.LANDSCAPE,
        floatingCounterPalette = FloatingCounterPalette("#010203", "#112233", "#445566", "#778899", "#AABBCC"),
        floatingCounterScale = 1.75f,
        floatingCounterOffsetX = -0.75f,
        floatingCounterOffsetY = 1f,
    ),
    networkSetting = NetworkSetting(
        userAgent = "agent",
        retryMaxCount = 5,
        retryIntervalSeconds = 3,
        retryErrorCodes = "429,500",
    ),
    favoriteModels = listOf(id(30)),
    chatModelId = id(1),
    fastModelId = id(2),
    fastModelReasoningLevel = ReasoningLevel.LOW,
    memoryModelId = id(6),
    memoryModelReasoningLevel = ReasoningLevel.HIGH,
    imageGenerationModelId = id(3),
    enableAutoTitle = false,
    enableMessageQueue = false,
    titlePrompt = "title",
    translateModeId = id(4),
    translatePrompt = "translate",
    translateThinkingBudget = 1024,
    translateTargetLanguage = "ja",
    enableSuggestion = false,
    suggestionPrompt = "suggestion",
    ocrModelId = id(5),
    ocrPrompt = "ocr",
    assistantId = id(20),
    providers = listOf(ProviderSetting.OpenAI(id = id(31), name = "Provider")),
    providerGroups = listOf("work"),
    providerGroupAssignments = mapOf(id(31).toString() to "work"),
    providerProxies = mapOf("api.example.com" to "socks5://127.0.0.1:1080"),
    providerProxyUsernames = mapOf("api.example.com" to "user"),
    providerProxyPasswords = mapOf("api.example.com" to "pass"),
    customCaCertificates = listOf("-----BEGIN CERTIFICATE-----"),
    assistants = listOf(Assistant(id = id(20), name = "Assistant")),
    assistantTags = listOf(Tag(id = id(7), name = "tag")),
    searchServices = listOf(
        SearchServiceOptions.BingLocalOptions(id = id(21)),
        SearchServiceOptions.BingLocalOptions(id = id(22)),
    ),
    searchCommonOptions = SearchCommonOptions(resultSize = 3),
    searchServiceSelected = 1,
    enabledSearchServiceIds = setOf(id(21).toString()),
    mcpServers = listOf(McpServerConfig.SseTransportServer(id = id(8), url = "https://example.com/sse")),
    webDavConfig = WebDavConfig(url = "https://dav.example.com"),
    s3Config = S3Config(bucket = "backup"),
    uploadS3Config = S3Config(bucket = "upload"),
    assetStorageType = AssetStorageType.WEBDAV,
    uploadWebDavConfig = WebDavConfig(
        url = "https://dav.example.com",
        username = "user",
        password = "pass",
        path = "media",
    ),
    mediaGenerationProviders = listOf(MediaGenerationProviderSetting.OpenAI(id = id(9))),
    ttsProviders = DEFAULT_TTS_PROVIDERS.take(1),
    selectedTTSProviderId = id(10),
    defaultTTSPlaybackSpeed = 1.5f,
    asrProviders = listOf(ASRProviderSetting.OpenAIRealtime(id = id(11))),
    selectedASRProviderId = id(11),
    modeInjections = listOf(PromptInjection.ModeInjection(id = id(14), name = "mode")),
    lorebooks = listOf(Lorebook(id = id(12), name = "lorebook")),
    quickMessages = listOf(QuickMessage(id = id(13), title = "hi")),
    guideMessages = listOf(QuickMessage(id = id(41), title = "guide", content = "keep it")),
    webServerEnabled = true,
    webServerPort = 9090,
    webServerJwtEnabled = true,
    webServerAccessPassword = "secret",
    webServerLocalhostOnly = true,
    sshServerEnabled = true,
    sshServerPort = 2223,
    sshServerAllowLan = true,
    backupReminderConfig = BackupReminderConfig(enabled = true),
    sponsorAlertDismissedAt = 7,
)

private val STORED_KEYS = setOf(
    "dynamic_color",
    "theme_id",
    "custom_themes",
    "palette",
    "display_setting",
    "network_setting",
    "developer_mode",
    "app_lock_enabled",
    "app_lock_timeout_minutes",
    "favorite_models",
    "chat_model",
    "fast_model",
    "fast_model_reasoning_level",
    "memory_model",
    "memory_model_reasoning_level",
    "translate_model",
    "enable_suggestion",
    "enable_auto_title",
    "enable_message_queue",
    "image_generation_model",
    "title_prompt",
    "translation_prompt",
    "translate_thinking_budget",
    "translate_target_language",
    "suggestion_prompt",
    "ocr_model",
    "ocr_prompt",
    "providers",
    "provider_groups",
    "provider_group_assignments",
    "provider_proxies",
    "provider_proxy_usernames",
    "provider_proxy_passwords",
    "custom_ca_certificates",
    "select_assistant",
    "assistants",
    "assistant_tags",
    "search_services",
    "search_common",
    "search_selected",
    "enabled_search_service_ids",
    "mcp_servers",
    "webdav_config",
    "s3_config",
    "upload_s3_config",
    "upload_webdav_config",
    "asset_storage_type",
    "media_generation_providers",
    "tts_providers",
    "selected_tts_provider",
    "default_tts_playback_speed",
    "asr_providers",
    "selected_asr_provider",
    "web_server_enabled",
    "web_server_port",
    "web_server_jwt_enabled",
    "web_server_access_password",
    "web_server_localhost_only",
    "ssh_server_enabled",
    "ssh_server_port",
    "ssh_server_allow_lan",
    "mode_injections",
    "lorebooks",
    "quick_messages",
    "guide_messages",
    "backup_reminder_config",
    "sponsor_alert_dismissed_at",
)
