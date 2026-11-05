package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import me.rerere.ai.agent.AgentHooks
import me.rerere.ai.agent.AgentRequest
import me.rerere.ai.agent.StepRequest
import me.rerere.ai.agent.withRetry
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.prompts.buildMemoryPrompt
import me.rerere.rikkahub.data.ai.prompts.buildMergeCompressPrompt
import me.rerere.rikkahub.data.ai.prompts.buildPartialCompressPrompt
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_NOTICE_FRAMING
import me.rerere.rikkahub.data.ai.tools.ChatToolFactory
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.ai.transformers.TimeReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.TodoStateTransformer
import me.rerere.rikkahub.data.ai.transformers.WorkspaceReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.onGenerationFinish
import me.rerere.rikkahub.data.ai.transformers.transformThinkTags
import me.rerere.rikkahub.data.ai.transformers.transforms
import me.rerere.rikkahub.data.ai.transformers.visualTransforms
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.model.chunkMessagesForCompression
import me.rerere.rikkahub.data.model.effectiveContextWindow
import me.rerere.rikkahub.data.model.estimateMessageTokens
import me.rerere.rikkahub.data.model.estimateTokens
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.repository.memoryId
import java.util.Locale
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val TAG = "ChatRequestFactory"

/**
 * 一轮对话的请求上下文，按会话固定的配置解析一次。
 *
 * 正常对话和压缩用同一份上下文组装请求，系统提示词、工具定义、参数和 sessionId 都相同，
 * 压缩请求的前缀才能和上一轮对话一致，命中提示词缓存。
 */
class ChatTurnContext(
    val settings: Settings,
    val assistant: Assistant,
    val model: Model,
    val providerSetting: ProviderSetting,
    val conversationId: Uuid,
    val conversationSystemPrompt: String?,
    val workspaceCwds: Map<String, String>,
    val memories: List<MemoryFile>,
    val tools: List<Tool>,
    /** 会话级任务清单，注入到消息尾部（不进系统提示词，见 TodoStateTransformer） */
    val todos: List<TodoItem> = emptyList(),
)

/** 把会话变成发给模型的请求：系统提示词、上下文裁剪、输入输出转换都在这里 */
class ChatRequestFactory(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val usageRecorder: UsageRecorder,
    private val chatToolFactory: ChatToolFactory,
    private val memoryRepository: MemoryRepository,
    templateTransformer: TemplateTransformer,
    workspaceRepository: WorkspaceRepository,
) {
    private val inputTransformers: List<InputMessageTransformer> = listOf(
        TimeReminderTransformer,
        PromptInjectionTransformer,
        PlaceholderTransformer,
        DocumentAsPromptTransformer,
        OcrTransformer,
        templateTransformer,
        WorkspaceReminderTransformer(workspaceRepository),
        // 放在最后: 其它转换器可能插入消息, 清单要贴着最后一条真实 user 消息
        TodoStateTransformer,
    )

    /**
     * 分段压缩请求用的转换器：只保留把附件内容变成文本的那几个。
     *
     * 分段不追求提示词缓存，但文档正文和 OCR 文本必须进摘要——provider 不认识
     * [UIMessagePart.Document]，跳过这部分等于把附件内容从历史里永久删掉。
     * 时间提醒、注入、模板这类装饰性转换跳过，也不给分段预算添乱。
     */
    private val compressionContentTransformers: List<InputMessageTransformer> =
        listOf(DocumentAsPromptTransformer, OcrTransformer)

    private val outputTransformers: List<OutputMessageTransformer> = listOf(
        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer,
        RegexOutputTransformer,
    )

    /** 解析本轮的助手、模型、记忆和工具；MCP 服务名不合法时抛出 InvalidMcpServerNamesException */
    suspend fun createContext(settings: Settings, conversation: Conversation): ChatTurnContext {
        // 模型、思考级别、搜索、工具等以会话上固定的配置为准
        val assistant = settings.getAssistantOf(conversation)
        val model = settings.getChatModelOf(conversation)
            ?: throw IllegalStateException("No chat model selected")
        val provider = model.findProvider(settings.providers) ?: error("Provider not found")
        val workspaceCwds = conversation.effectiveWorkspaceCwds(assistant)
        return ChatTurnContext(
            settings = settings,
            assistant = assistant,
            model = model,
            providerSetting = provider,
            conversationId = conversation.id,
            conversationSystemPrompt = conversation.customSystemPrompt,
            workspaceCwds = workspaceCwds,
            memories = if (assistant.enableMemory) memoryRepository.getFiles(assistant.memoryId) else emptyList(),
            todos = conversation.todos,
            tools = chatToolFactory.createTools(
                settings = settings,
                assistant = assistant,
                model = model,
                workspaceCwds = workspaceCwds,
                conversationId = conversation.id,
                // 只有会话本身是子代理会话时才继承派发能力；助手上的「设为子代理」只管「能被派发」
                isSubAgentConversation = conversation.parentConversationId != null,
                // 子代理会话是自己跑完的，没有人给它点审批
                autoApproveTools = conversation.parentConversationId != null,
            ),
        )
    }

    /**
     * 本轮交给 runAgent 的请求和钩子。[messages] 的最后一条是助手消息时从它继续，否则新建一条助手消息。
     * [processingStatus] 用来显示 OCR 这类输入转换的进度。
     */
    fun createAgent(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        processingStatus: MutableStateFlow<String?>,
        pendingInjections: suspend () -> List<UIMessage> = { emptyList() },
        beforeStep: suspend (List<UIMessage>) -> List<UIMessage>? = { null },
    ): Pair<AgentRequest, AgentHooks> {
        val injectedMessages = mutableListOf<UIMessage>()
        val request = AgentRequest(
            provider = usageRecorder.wrap(
                providerManager.getProviderByType(turn.providerSetting),
                UsageTag(UsageSource.CHAT, turn.assistant.id, turn.conversationId),
            ),
            providerSetting = turn.providerSetting,
            messages = messages,
            tools = turn.tools,
            stream = turn.assistant.streamOutput,
            retry = turn.settings.retryPolicy(),
        )
        val hooks = object : AgentHooks {
            override suspend fun prepareStep(messages: List<UIMessage>): StepRequest {
                Log.i(TAG, "prepareStep: ${turn.model.id}")
                // 压缩这类会换掉历史的操作要在组装请求之前做，返回新的历史表示这一步必须按它重新组装；
                // 放到请求发出去之后再处理，压缩就只能对下一次请求生效，这一步仍然带着旧上下文
                val refreshed = beforeStep(messages)
                // 外部注入消息在每一步请求前取一次，取到后这一步之后的步骤也要继续带上
                injectedMessages += pendingInjections()
                val step = buildStep(turn, refreshed ?: messages, processingStatus)
                return if (injectedMessages.isEmpty()) {
                    step
                } else {
                    step.copy(messages = step.messages + injectedMessages)
                }
            }

            override suspend fun previewStep(message: UIMessage, newPartsFrom: Int) =
                message.transformNewParts(newPartsFrom) {
                    it.visualTransforms(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                }

            override suspend fun finishStep(message: UIMessage, newPartsFrom: Int) =
                message.transformNewParts(newPartsFrom) {
                    it.transforms(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                        .visualTransforms(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                        .onGenerationFinish(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                }
        }
        return request to hooks
    }

    /**
     * 生成压缩摘要。
     *
     * 请求按正常对话组装（系统提示词、工具定义、参数和 sessionId 都相同），只在末尾追加压缩指令，
     * 前缀与上一轮对话一致，可以命中提示词缓存，模型也能看到工具调用和结果。
     * 工具定义必须保留：它属于请求前缀，去掉或改动 tool_choice 都会让缓存失效。
     *
     * 压缩范围本身也可能装不进模型窗口（这往往正是要压缩的原因）。这时分段摘要再合并：
     * 把整段历史原样再发一次只会被同样拒绝，自动压缩就等于失效。
     */
    suspend fun generateCompressionSummary(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        compressPrompt: String,
    ): String {
        val budget = turn.model.compressionInputBudget()
        val chunks = if (budget == null) listOf(messages) else chunkMessagesForCompression(messages, budget)
        val summary = if (chunks.size <= 1) {
            // 用切完的那一份：单条消息自己超预算时会被截断，这里不能退回原文。
            // 首条不是 user 的会话（比如助手预设消息开头的会话）同样要补一条，否则 Claude/Gemini 会拒绝
            requestCompressionSummary(turn, chunks.single().withLeadingUserTurn(), compressPrompt)
        } else {
            Log.i(TAG, "generateCompressionSummary: split ${messages.size} messages into ${chunks.size} parts")
            val partials = chunks.mapIndexed { index, chunk ->
                requestCompressionSummary(
                    turn = turn,
                    messages = chunk.asCompressionChunk(),
                    prompt = buildPartialCompressPrompt(
                        part = index + 1,
                        total = chunks.size,
                        locale = Locale.getDefault().displayName,
                    ),
                    transformers = compressionContentTransformers,
                )
            }
            // 合并阶段的内容是各段摘要，不是对话本身：一个转换器都不能过
            requestCompressionSummary(
                turn = turn,
                messages = emptyList(),
                prompt = buildMergeCompressPrompt(partials, compressPrompt),
                transformers = emptyList(),
                // 合并阶段只有摘要文本，不需要工具定义：省 token，也不给模型调工具的机会
                includeTools = false,
            )
        }
        // 模型拒绝或答非所问时不满足七段结构，宁可压缩失败也别拿无效文本替换历史
        if (!summary.isUsableCompressionSummary()) {
            error(context.getString(R.string.chat_page_compress_malformed))
        }
        return summary
    }

    /**
     * 发一次摘要请求。
     *
     * `max_tokens` 同样占用窗口（Anthropic 按 输入 + max_tokens 校验），不能置空：
     * 置空后 Claude 会兜底成 64k，把大上下文下的压缩请求自己顶出窗口。
     * 其它 provider 不传时由服务端按剩余窗口分配，这里保持原来的行为。
     */
    private suspend fun requestCompressionSummary(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        prompt: String,
        transformers: List<InputMessageTransformer> = inputTransformers,
        includeTools: Boolean = true,
    ): String {
        // 压缩要看整段范围，不能再按条数截一次
        val (requestMessages, params) = buildStep(
            turn = turn,
            messages = messages,
            respectMessageLimit = false,
            transformers = transformers,
            includeTools = includeTools,
        )
        val inputTokens = requestMessages.sumOf { estimateMessageTokens(it) } + estimateTokens(prompt)
        // 直连 Claude 时 SDK 对 null 兜底 64k，大上下文下会把 prompt + max_tokens 顶出窗口；
        // 其它 provider 不传 max_tokens 时由服务端按剩余窗口分配，硬填反而可能超过模型自己的输出上限
        val maxTokens = when (turn.providerSetting) {
            is ProviderSetting.Claude -> turn.model.compressionMaxTokens(inputTokens)
            else -> null
        }
        // 压缩范围经常以 user 消息结尾（保留区从下一条开始），再追加一条 user 会变成连续两条 user；
        // 末尾是 user 时把指令并进那条消息，前缀不变，也避免 provider 的角色交替校验
        val messagesWithPrompt = if (requestMessages.lastOrNull()?.role == MessageRole.USER) {
            val last = requestMessages.last()
            requestMessages.dropLast(1) + last.copy(parts = last.parts + UIMessagePart.Text(prompt))
        } else {
            requestMessages + UIMessage.user(prompt)
        }
        val result = withRetry(
            policy = turn.settings.retryPolicy(),
            onRetry = { Log.w(TAG, "Compression request failed, retrying in ${it.delay}", it.cause) },
        ) {
            usageRecorder.wrap(
                providerManager.getProviderByType(turn.providerSetting),
                UsageTag(UsageSource.COMPRESSION, turn.assistant.id, turn.conversationId),
            ).generateText(
                providerSetting = turn.providerSetting,
                messages = messagesWithPrompt,
                params = params.copy(maxTokens = maxTokens),
            )
        }
        // 输出被截断时摘要只写到一半：当成完整摘要保存，后半段历史就对模型永久消失了
        if (result.finishReason.isTruncatedOutput()) {
            error(context.getString(R.string.chat_page_compress_truncated))
        }
        return result.message.compressionSummaryOrNull()
            ?: error(context.getString(R.string.chat_page_compress_tool_call_response))
    }

    /** 一步请求实际发出的消息和参数；正常对话和压缩共用，二者的请求前缀必须一致才能命中提示词缓存 */
    private suspend fun buildStep(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        respectMessageLimit: Boolean = true,
        transformers: List<InputMessageTransformer> = inputTransformers,
        includeTools: Boolean = true,
    ): StepRequest {
        val assistant = turn.assistant
        val tools = if (includeTools) turn.tools else emptyList()
        val baseMessages = buildList {
            val system = buildString {
                val effectiveSystemPrompt =
                    if (assistant.allowConversationSystemPrompt && !turn.conversationSystemPrompt.isNullOrBlank()) {
                        turn.conversationSystemPrompt
                    } else {
                        assistant.systemPrompt
                    }
                if (effectiveSystemPrompt.isNotBlank()) {
                    append(effectiveSystemPrompt)
                }

                // 计划模式：只读探索，先把方案讲清楚
                if (assistant.planMode) {
                    appendLine()
                    append(
                        "## Plan mode\n" +
                            "Only read-only tools are available. File writes, git changes, test runs and non-read shell commands are rejected.\n" +
                            "Explore the workspace, then present a concrete plan and ask the user to turn plan mode off before making changes."
                    )
                }
                // 记忆
                if (assistant.enableMemory) {
                    appendLine()
                    append(buildMemoryPrompt(turn.memories))
                }
                // 工具prompt
                tools.forEach { tool ->
                    appendLine()
                    append(tool.systemPrompt(turn.model, messages))
                }
            }
            if (system.isNotBlank()) {
                add(UIMessage.system(prompt = system).copy(isSynthetic = true))
            }
            // 压缩请求必须看到整个压缩范围：这里再按条数砍一次的话，被砍掉的中间那段
            // 既没进摘要、也不会留在检查点之后，模型以后再也看不到它
            val contextMessages = if (respectMessageLimit) {
                messages.limitContext(assistant.contextMessageLimit)
            } else {
                messages
            }
            addAll(
                contextMessages.map { message ->
                    // 子代理回投走 USER 角色（provider 都认），这里补一句说明，模型才不会当成用户说的
                    if (!message.isSubAgentNotice) message else message.copy(
                        parts = listOf(UIMessagePart.Text(SUB_AGENT_NOTICE_FRAMING)) + message.parts,
                    )
                }
            )
        }
        val requestMessages = if (transformers.isEmpty()) {
            baseMessages
        } else {
            baseMessages.transforms(
                transformers = transformers,
                context = context,
                model = turn.model,
                assistant = assistant,
                settings = turn.settings,
                processingStatus = processingStatus,
                workspaceCwds = turn.workspaceCwds,
                todos = turn.todos,
            )
        }
        val params = TextGenerationParams(
            model = turn.model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            topK = assistant.topK,
            maxTokens = assistant.maxTokens,
            tools = tools,
            reasoningLevel = assistant.reasoningLevel,
            customHeaders = buildList {
                addAll(assistant.customHeaders)
                addAll(turn.model.customHeaders)
            },
            customBody = buildList {
                addAll(assistant.customBodies)
                addAll(turn.model.customBodies)
            },
            sessionId = turn.conversationId.toString(),
        )
        return StepRequest(requestMessages, params)
    }
}

// 压缩请求的输出上限：够写七段摘要，又不至于把输入挤出窗口（max_tokens 也占窗口）
private const val COMPRESSION_MAX_OUTPUT_TOKENS = 8_192
private const val COMPRESSION_MIN_OUTPUT_TOKENS = 1_024
// 摘要请求的固定开销：systemPrompt、工具定义与工具 prompt、压缩指令和估算误差
private const val COMPRESSION_FIXED_INPUT_RESERVE = 12_288
// 分段下限：预算再小也按这个切，否则会把历史切成几十段、每段一次请求
private const val COMPRESSION_MIN_INPUT_BUDGET = 2_048

/** 压缩请求每次最多输入多少 token；窗口未知时返回 null，表示不分段 */
internal fun Model.compressionInputBudget(): Int? {
    val window = compressionWindow() ?: return null
    return (window - COMPRESSION_MAX_OUTPUT_TOKENS - COMPRESSION_FIXED_INPUT_RESERVE)
        .coerceAtLeast(COMPRESSION_MIN_INPUT_BUDGET)
}

/** 摘要请求的 max_tokens：装得下就按输出上限，装不下就按窗口剩余空间收窄 */
internal fun Model.compressionMaxTokens(inputTokens: Int): Int {
    val window = compressionWindow() ?: return COMPRESSION_MAX_OUTPUT_TOKENS
    val room = window - inputTokens - COMPRESSION_FIXED_INPUT_RESERVE
    return room.coerceAtMost(COMPRESSION_MAX_OUTPUT_TOKENS).coerceAtLeast(COMPRESSION_MIN_OUTPUT_TOKENS)
}

/**
 * 压缩可用的窗口。门限和 contextLength 都可能不准（门限可能比真实窗口大），
 * 取两者里小的那个，宁可保守。
 */
private fun Model.compressionWindow(): Int? = listOfNotNull(
    effectiveContextWindow,
    contextLength.takeIf { it > 0 },
).minOrNull()

/**
 * 分段可能从助手消息开始（按 token 预算切的消息边界），而 Anthropic 要求首条是 user；
 * 一条超长消息按 part 拆开后还会出现连续同角色，Google 又要求 user/model 交替。
 * 这里合并相邻同角色消息，再补开头的 user。
 */
private fun List<UIMessage>.asCompressionChunk(): List<UIMessage> {
    // 显式给出元素类型：buildList 的 builder 是 @BuilderInference，先读 lastOrNull()
    // 时元素类型还没定下来，K2 会报「receiver 类型尚未推断」
    val merged = buildList<UIMessage> {
        for (message in this@asCompressionChunk) {
            val last = lastOrNull()
            // 子代理通知有自己的 framing，并进普通用户消息会让两边都读错，当成边界
            if (last != null && last.role == message.role &&
                !last.isSubAgentNotice && !message.isSubAgentNotice
            ) {
                set(lastIndex, last.copy(parts = last.parts + message.parts))
            } else {
                add(message)
            }
        }
    }
    return merged.withLeadingUserTurn()
}

/**
 * 首条不是 user 时补一条说明。单段压缩不做同角色合并——那会改动请求前缀，
 * 丢掉这一路本来能命中的提示词缓存。
 */
private fun List<UIMessage>.withLeadingUserTurn(): List<UIMessage> =
    if (firstOrNull()?.role == MessageRole.USER) this
    else listOf(UIMessage.user(COMPRESSION_CHUNK_OPENING)) + this

private const val COMPRESSION_CHUNK_OPENING =
    "(Continuing an earlier conversation; the messages below start mid-thread.)"

/**
 * 取出压缩请求回复里的摘要正文。
 *
 * 指令要求不调用工具，模型仍可能发起调用。这时附带的文本只是"我先看看文件"之类的说明，
 * 当成摘要保存会让之后的请求丢掉全部历史，所以返回 null 交给调用方报错。
 * 压缩请求不经过输出转换器，正文开头的 <think> 思考内容也要在这里剥掉，否则会存进摘要。
 */
internal fun UIMessage.compressionSummaryOrNull(): String? {
    if (getTools().isNotEmpty()) return null
    return listOf(this)
        .transformThinkTags(now = Clock.System.now(), generationFinished = true)
        .single()
        .toText()
        .trim()
}

/** 各家表示「输出没写完」的 finishReason：OpenAI length、Claude max_tokens、Gemini MAX_TOKENS、Responses incomplete */
private val TRUNCATED_FINISH_REASONS = setOf(
    "length",
    "max_tokens",
    "max_output_tokens",
    "incomplete",
)

private fun String?.isTruncatedOutput(): Boolean {
    val reason = this?.lowercase() ?: return false
    return reason in TRUNCATED_FINISH_REASONS
}

/**
 * 摘要的最低门槛：至少两个二级标题、正文长度够。
 * 指令要求七段结构；模型拒绝或答非所问时这两条都不满足。
 */
private const val MIN_SUMMARY_CHARS = 80
// ## 后面不强制空白：中文输出里「##用户目标」很常见，\s+ 会把合格摘要判成格式错误
private val SUMMARY_HEADING = Regex("""(?m)^##\s*\S""")

internal fun String.isUsableCompressionSummary(): Boolean =
    length >= MIN_SUMMARY_CHARS && SUMMARY_HEADING.findAll(this).count() >= 2

/**
 * 输出转换只处理这一步新生成的 part：之前步骤的内容已经处理过，再处理一遍会让正则这类改写叠加，
 * 也能让每一步开头的 <think> 都被识别出来。
 */
internal suspend fun UIMessage.transformNewParts(
    newPartsFrom: Int,
    transform: suspend (List<UIMessage>) -> List<UIMessage>,
): UIMessage {
    val transformed = transform(listOf(copy(parts = parts.drop(newPartsFrom)))).single()
    return transformed.copy(parts = parts.take(newPartsFrom) + transformed.parts)
}

/** 标题、建议、记忆整理这类后台请求的参数：只带模型自己的自定义请求头和请求体 */
internal fun backgroundTextGenerationParams(
    model: Model,
    sessionId: Uuid,
    reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
): TextGenerationParams = TextGenerationParams(
    model = model,
    reasoningLevel = reasoningLevel,
    customHeaders = model.customHeaders,
    customBody = model.customBodies,
    sessionId = sessionId.toString(),
)

// 旧会话只存了主工作区的一个 cwd，读的时候并进多工作区表
private fun Conversation.effectiveWorkspaceCwds(assistant: Assistant): Map<String, String> {
    val legacy = workspaceCwd?.takeIf { it.isNotBlank() } ?: return workspaceCwds
    val primary = assistant.primaryWorkspaceId?.toString() ?: return workspaceCwds
    return if (workspaceCwds.containsKey(primary)) workspaceCwds else workspaceCwds + (primary to legacy)
}
