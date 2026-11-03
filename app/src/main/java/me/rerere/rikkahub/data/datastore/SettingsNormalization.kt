package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.withContextPresets

/** 读盘后和每次修改后都要过一遍，保证内存里的设置始终是规整的。 */
internal fun Settings.normalized(): Settings =
    withBuiltInDefaults().withoutInvalidReferences().withValuesInRange().withContextThresholds()

// 补齐缺失的内置提供商/助手/TTS，内置提供商的描述信息始终以代码里的为准
internal fun Settings.withBuiltInDefaults(): Settings {
    var providers = this.providers.ifEmpty { DEFAULT_PROVIDERS }.toMutableList()
    DEFAULT_PROVIDERS.forEach { defaultProvider ->
        if (providers.none { it.id == defaultProvider.id }) {
            providers.add(defaultProvider.copyProvider())
        }
    }
    providers = providers.map { provider ->
        val defaultProvider = DEFAULT_PROVIDERS.find { it.id == provider.id }
        if (defaultProvider != null) {
            provider.copyProvider(
                builtIn = defaultProvider.builtIn,
                description = defaultProvider.description,
                shortDescription = defaultProvider.shortDescription,
            )
        } else {
            provider
        }
    }.toMutableList()
    val assistants = this.assistants.ifEmpty { DEFAULT_ASSISTANTS }.toMutableList()
    DEFAULT_ASSISTANTS.forEach { defaultAssistant ->
        if (assistants.none { it.id == defaultAssistant.id }) {
            assistants.add(defaultAssistant.copy())
        }
    }
    val ttsProviders = this.ttsProviders.ifEmpty { DEFAULT_TTS_PROVIDERS }.toMutableList()
    DEFAULT_TTS_PROVIDERS.forEach { defaultTTSProvider ->
        if (ttsProviders.none { provider -> provider.id == defaultTTSProvider.id }) {
            ttsProviders.add(defaultTTSProvider.copyProvider())
        }
    }
    return copy(
        providers = providers,
        assistants = assistants,
        ttsProviders = ttsProviders,
    )
}

// 去重并清理无效引用
internal fun Settings.withoutInvalidReferences(): Settings {
    val settings = this
    val validMcpServerIds = settings.mcpServers.map { it.id }.toSet()
    val validModeInjectionIds = settings.modeInjections.map { it.id }.toSet()
    val validLorebookIds = settings.lorebooks.map { it.id }.toSet()
    val validQuickMessageIds = settings.quickMessages.map { it.id }.toSet()
    // 只有仍然存在的子代理才能被派发；删掉目标或取消「设为子代理」后，
    // 允许列表里的残留 id 会清掉，否则它会把派发能力永久锁死
    val validSubAgentIds = settings.assistants.filter { it.isSubAgent }.map { it.id }.toSet()
    val validModelIds by lazy { settings.providers.flatMap { it.models }.mapTo(HashSet()) { it.id } }
    val asrProviders = settings.asrProviders.distinctBy { it.id }
    return settings.copy(
        providers = settings.providers.distinctBy { it.id }.map { provider ->
            provider.copyProvider(models = provider.models.distinctBy { model -> model.id })
        },
        assistants = settings.assistants.distinctBy { it.id }.map { assistant ->
            assistant.copy(
                // 过滤掉不存在的 MCP 服务器 ID
                mcpServers = assistant.mcpServers.filter { serverId ->
                    serverId in validMcpServerIds
                }.toSet(),
                // 过滤掉不存在的模式注入 ID
                modeInjectionIds = assistant.modeInjectionIds.filter { id ->
                    id in validModeInjectionIds
                }.toSet(),
                // 过滤掉不存在的 Lorebook ID
                lorebookIds = assistant.lorebookIds.filter { id ->
                    id in validLorebookIds
                }.toSet(),
                // 过滤掉不存在的快捷消息 ID
                quickMessageIds = assistant.quickMessageIds.filter { id ->
                    id in validQuickMessageIds
                }.toSet(),
                // 过滤掉不存在的、已取消子代理标记的、以及自己的 id
                allowedSubAgentIds = assistant.allowedSubAgentIds.filter { id ->
                    id != assistant.id && id in validSubAgentIds
                }.toSet()
            )
        },
        ttsProviders = settings.ttsProviders.distinctBy { it.id },
        asrProviders = asrProviders,
        selectedASRProviderId = settings.selectedASRProviderId
            ?.takeIf { id -> asrProviders.any { provider -> provider.id == id } }
            ?: asrProviders.firstOrNull()?.id,
        favoriteModels = settings.favoriteModels.filter { it in validModelIds },
        modeInjections = settings.modeInjections.distinctBy { it.id },
        lorebooks = settings.lorebooks.distinctBy { it.id },
        quickMessages = settings.quickMessages.distinctBy { it.id },
    )
}

// 把取值收回合法范围，比如删掉搜索服务后选中的下标可能越界
private fun Settings.withValuesInRange(): Settings = copy(
    searchServiceSelected = searchServiceSelected.coerceIn(0, (searchServices.size - 1).coerceAtLeast(0)),
    defaultTTSPlaybackSpeed = defaultTTSPlaybackSpeed.coerceIn(0.5f, 2.0f),
)

// 没设置过上下文门限的模型按型号预填一份，用户改过的不动
private fun Settings.withContextThresholds(): Settings = copy(
    providers = providers.map { provider ->
        provider.copyProvider(models = provider.models.map { it.withContextPresets() })
    },
)
