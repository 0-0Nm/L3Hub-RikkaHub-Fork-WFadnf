package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.utils.SimpleCache
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

@Serializable
data class Assistant(
    val id: Uuid = Uuid.random(),
    val chatModelId: Uuid? = null, // 如果为null, 使用全局默认模型
    val name: String = "",
    // 标记为子代理：允许父会话通过 dispatch_sub_agent 把任务派发进来
    val isSubAgent: Boolean = false,
    val avatar: Avatar = Avatar.Dummy,
    val useAssistantAvatar: Boolean = false, // 使用助手头像替代模型头像
    val tags: List<Uuid> = emptyList(),
    val systemPrompt: String = "",
    val temperature: Float? = null,
    val topP: Float? = null,
    // 上下文消息条数上限, 超出后阶梯式截断; 0 表示不限制
    val contextMessageLimit: Int = 0,
    val streamOutput: Boolean = true,
    // 关闭后这个助手不再发系统通知（生成完成 / 进度 / 压缩状态）
    val enableNotification: Boolean = true,
    // 计划模式：只允许只读工具，写文件/执行命令/改仓库一律拒绝
    val planMode: Boolean = false,
    // 工作区工具的审批档位：只读 / 读写 / 审批 / 自动
    val executionPermission: ExecutionPermission = ExecutionPermission.APPROVAL,
    val enableMemory: Boolean = false,
    val useGlobalMemory: Boolean = false, // 使用全局共享记忆而非助手隔离记忆
    val autoConsolidateMemory: Boolean = true, // 对话停下来后在后台自动整理记忆; 关闭后只能手动整理
    val dreamingModelId: Uuid? = null, // 整理记忆(Dreaming)用的模型, 为 null 跟随全局
    // 上下文到达门限后自动压缩，效果与手动压缩一致（插入检查点摘要）
    val autoCompress: Boolean = false,
    val autoCompressThreshold: AutoCompressThreshold = AutoCompressThreshold.WARN,
    val autoCompressTarget: AutoCompressTarget = AutoCompressTarget.MESSAGES,
    val autoCompressTargetValue: Int = 8, // 按条数时保留的消息条数；按 token 时保留的 token 数
    // 模型循环中请求上下文超限时，压缩后继续本轮生成
    val autoCompressLoop: Boolean = false,
    val enableRecentChatsReference: Boolean = false,
    val messageTemplate: String = "{{ message }}",
    val presetMessages: List<UIMessage> = emptyList(),
    val quickMessageIds: Set<Uuid> = emptySet(),
    val regexes: List<AssistantRegex> = emptyList(),
    val reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val maxTokens: Int? = null,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val mcpServers: Set<Uuid> = emptySet(),
    val localTools: List<LocalToolOption> = listOf(LocalToolOption.TimeInfo),
    val enableWebSearch: Boolean = false, // 网络搜索开关(每个助手独立)
    val workspaceId: Uuid? = null, // 主工作区，保持旧数据兼容
    val workspaceIds: Set<Uuid> = emptySet(), // 可同时绑定的工作区
    val background: String? = null, // 聊天页背景图地址(本地文件 URI 或网络 URL), 为 null 时无背景
    val backgroundOpacity: Float = 1.0f, // 背景图不透明度(0~1)
    val backgroundBlur: Float = 0f, // 背景图模糊半径比例(0~1)
    val useGradientBackground: Boolean = false, // 开启后聊天页使用动态渐变背景
    val modeInjectionIds: Set<Uuid> = emptySet(), // 关联的模式注入 ID
    val lorebookIds: Set<Uuid> = emptySet(), // 关联的 Lorebook ID
    val enabledSkills: Set<String> = emptySet(), // 启用的 skill 名称列表
    val enableTimeReminder: Boolean = false, // 时间间隔提醒注入
    val timeReminderIntervalMinutes: Int = 60, // 时间提醒间隔（分钟，至少 1 分钟）
    val allowConversationSystemPrompt: Boolean = false, // 允许对话单独重写 system prompt
    val heartbeat: HeartbeatSetting = HeartbeatSetting(), // 定时主动说话；没配模型时只当闹钟
) {
    /** 主工作区在前，兼容只写过 [workspaceId] 的旧助手。 */
    val allWorkspaceIds: Set<Uuid>
        get() = buildSet {
            workspaceId?.let { add(it) }
            addAll(workspaceIds)
        }

    val primaryWorkspaceId: Uuid?
        get() = workspaceId ?: workspaceIds.firstOrNull()
}

/**
 * 工作区工具的执行权限档位。
 *
 * 只影响「写文件 / 执行命令 / 改仓库 / 跑测试」，读类工具在任何档位下都能用。
 */
@Serializable
enum class ExecutionPermission {
    /** 只读：只注册读类工具，shell 也只允许只读命令 */
    READ_ONLY,

    /** 读写：写文件免审批，执行命令仍然要确认 */
    READ_WRITE,

    /** 审批：写操作和命令按工具默认值与工作区里的开关逐条确认（旧行为） */
    APPROVAL,

    /** 自动：全部放行，不再逐条确认 */
    FULL,
}

@Serializable
data class QuickMessage(
    val id: Uuid = Uuid.random(),
    val title: String = "",
    val content: String = "",
)

@Serializable
enum class AssistantAffectScope {
    USER,
    ASSISTANT,
}

@Serializable
data class AssistantRegex(
    val id: Uuid,
    val name: String = "",
    val enabled: Boolean = true,
    val findRegex: String = "", // 正则表达式
    val replaceString: String = "", // 替换字符串
    val affectingScope: Set<AssistantAffectScope> = setOf(),
    val visualOnly: Boolean = false, // 是否仅在视觉上影响
)

// 流式输出时每个chunk都会调用replaceRegexes，正则必须缓存编译结果，
// 否则长回复期间会重复编译上万次；编译失败也缓存，避免反复构造异常
private val regexCache = SimpleCache.builder<String, Result<Regex>>()
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .build()

private fun compileRegexCached(pattern: String): Regex? {
    regexCache.getIfPresent(pattern)?.let { return it.getOrNull() }
    val result = runCatching { Regex(pattern) }.onFailure { it.printStackTrace() }
    regexCache.put(pattern, result)
    return result.getOrNull()
}

fun String.replaceRegexes(
    assistant: Assistant?,
    scope: AssistantAffectScope,
    visual: Boolean = false
): String {
    if (assistant == null) return this
    if (assistant.regexes.isEmpty()) return this
    return assistant.regexes.fold(this) { acc, regex ->
        if (regex.enabled && regex.visualOnly == visual && regex.affectingScope.contains(scope)) {
            val compiled = compileRegexCached(regex.findRegex) ?: return@fold acc
            try {
                acc.replace(
                    regex = compiled,
                    replacement = regex.replaceString,
                )
            } catch (e: Exception) {
                e.printStackTrace()
                // 替换字符串可能引用不存在的分组，失败时返回原字符串
                acc
            }
        } else {
            acc
        }
    }
}

/** 对用户输入的文本应用助手作用于用户消息的正则替换 */
fun List<UIMessagePart>.replaceUserRegexes(assistant: Assistant): List<UIMessagePart> = map { part ->
    if (part is UIMessagePart.Text) {
        part.copy(text = part.text.replaceRegexes(assistant, AssistantAffectScope.USER, visual = false))
    } else {
        part
    }
}

/**
 * 注入位置
 */
@Serializable
enum class InjectionPosition {
    @SerialName("before_system_prompt")
    BEFORE_SYSTEM_PROMPT, // 系统提示词之前

    @SerialName("after_system_prompt")
    AFTER_SYSTEM_PROMPT, // 系统提示词之后（最常用）

    @SerialName("top_of_chat")
    TOP_OF_CHAT, // 对话最开头（第一条用户消息之前）

    @SerialName("bottom_of_chat")
    BOTTOM_OF_CHAT, // 最新消息之前（当前用户输入之前）

    @SerialName("at_depth")
    AT_DEPTH, // 在指定深度位置插入（从最新消息往前数）
}

/**
 * 提示词注入
 *
 * - ModeInjection: 基于模式开关的注入（如学习模式）
 * - RegexInjection: 基于正则匹配的注入（Lorebook）
 */
@Serializable
sealed class PromptInjection {
    abstract val id: Uuid
    abstract val name: String
    abstract val enabled: Boolean
    abstract val priority: Int
    abstract val position: InjectionPosition
    abstract val content: String
    abstract val injectDepth: Int // 当 position 为 AT_DEPTH 时使用，表示从最新消息往前数的位置
    abstract val role: MessageRole // 注入角色：USER 或 ASSISTANT

    /**
     * 模式注入 - 基于开关状态触发
     */
    @Serializable
    @SerialName("mode")
    data class ModeInjection(
        override val id: Uuid = Uuid.random(),
        override val name: String = "",
        override val enabled: Boolean = true,
        override val priority: Int = 0,
        override val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        override val content: String = "",
        override val injectDepth: Int = 4,
        override val role: MessageRole = MessageRole.USER,
    ) : PromptInjection()

    /**
     * 正则注入 - 基于内容匹配触发（世界书）
     */
    @Serializable
    @SerialName("regex")
    data class RegexInjection(
        override val id: Uuid = Uuid.random(),
        override val name: String = "",
        override val enabled: Boolean = true,
        override val priority: Int = 0,
        override val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        override val content: String = "",
        override val injectDepth: Int = 4,
        override val role: MessageRole = MessageRole.USER,
        val keywords: List<String> = emptyList(), // 触发关键词
        val useRegex: Boolean = false, // 是否使用正则匹配
        val caseSensitive: Boolean = false, // 大小写敏感
        val scanDepth: Int = 4, // 扫描最近N条消息
        val constantActive: Boolean = false, // 常驻激活（无需匹配）
    ) : PromptInjection()
}

/**
 * Lorebook - 组织管理多个 RegexInjection
 */
@Serializable
data class Lorebook(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val entries: List<PromptInjection.RegexInjection> = emptyList(),
)

/**
 * 检查 RegexInjection 是否被触发
 *
 * @param context 要扫描的上下文文本
 * @return 是否触发
 */
fun PromptInjection.RegexInjection.isTriggered(context: String): Boolean {
    if (!enabled) return false
    if (constantActive) return true
    if (keywords.isEmpty()) return false

    return keywords.any { keyword ->
        if (useRegex) {
            try {
                val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                Regex(keyword, options).containsMatchIn(context)
            } catch (e: Exception) {
                false
            }
        } else {
            if (caseSensitive) {
                context.contains(keyword)
            } else {
                context.contains(keyword, ignoreCase = true)
            }
        }
    }
}

/**
 * 从消息列表中提取用于匹配的上下文文本
 *
 * @param messages 消息列表
 * @param scanDepth 扫描深度（最近N条消息）
 * @return 拼接的文本内容
 */
fun extractContextForMatching(
    messages: List<UIMessage>,
    scanDepth: Int
): String {
    return messages
        .takeLast(scanDepth)
        .joinToString("\n") { it.toText() }
}

/**
 * 获取所有被触发的注入，按优先级排序
 *
 * @param injections 所有注入规则
 * @param context 上下文文本
 * @return 被触发的注入列表，按优先级降序排列
 */
fun getTriggeredInjections(
    injections: List<PromptInjection.RegexInjection>,
    context: String
): List<PromptInjection.RegexInjection> {
    return injections
        .filter { it.isTriggered(context) }
        .sortedByDescending { it.priority }
}
