package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.limitContext

/** 自动压缩的触发点：上下文占用到达模型配置的 WARN 或 MAX 门限 */
@Serializable
enum class AutoCompressThreshold {
    WARN,
    MAX,
}

/**
 * 压缩后保留多少最近上下文。
 *
 * 现在只按消息条数保留。按 token 数保留的算法已经移除：它要靠字符估算在消息内部找 part
 * 边界，切点经常落在半条消息上，附件、工具调用和正文对不上，模型读到的是残缺的一轮。
 * [TOKENS] 只是为了旧设置文件还能解码，运行时按 [AUTO_COMPRESS_DEFAULT_KEEP_MESSAGES] 处理。
 */
@Serializable
enum class AutoCompressTarget {
    MESSAGES,
    TOKENS,
}

/** 自动压缩默认保留的消息条数 */
internal const val AUTO_COMPRESS_DEFAULT_KEEP_MESSAGES = 8

/** 自动压缩保留的消息条数；旧版本按 token 数配置的助手退回默认条数 */
val Assistant.autoCompressKeepMessages: Int
    get() = if (autoCompressTarget == AutoCompressTarget.MESSAGES) {
        autoCompressTargetValue.coerceAtLeast(1)
    } else {
        AUTO_COMPRESS_DEFAULT_KEEP_MESSAGES
    }

/**
 * 压缩后保留的起点：[nodeIndex] 之前的消息全部压进摘要，从这里开始的消息原样发给模型。
 *
 * 切点一定落在两条消息之间。压缩只改变发给模型的上下文，会话里存的消息一条都不删。
 */
data class ContextTrim(val nodeIndex: Int)

private const val MESSAGE_TOKEN_OVERHEAD = 4

/**
 * 实际会发给模型的上下文 token 估算；压缩前的老消息和检查点之前的 part 都不计入。
 *
 * [messageLimit] 必须和组装请求时的 `assistant.contextMessageLimit` 一致：判定用的尺子比发送用的
 * 尺子长，就会为一堆根本不会发出去的尾部消息反复触发压缩，每次还都是一次全量摘要请求。
 */
fun Conversation.estimateContextTokens(messageLimit: Int = 0): Int =
    currentMessages.limitContext(messageLimit).sumOf(::estimateMessageTokens)

/** 单条消息的 token 估算，工具调用的输入输出也算在内 */
internal fun estimateMessageTokens(message: UIMessage): Int =
    MESSAGE_TOKEN_OVERHEAD + message.parts.sumOf(::estimatePartTokens)

/** 从 [start] 开始（含）仍会发给模型的消息的 token 估算，用来核对保留区有没有超预算 */
internal fun Conversation.estimateNodesTokens(start: Int): Int {
    var total = 0
    for (index in start.coerceIn(0, messageNodes.size) until messageNodes.size) {
        total += estimateMessageTokens(messageNodes[index].currentMessage)
    }
    return total
}

/** 单个 part 的 token 估算 */
internal fun estimatePartTokens(part: UIMessagePart): Int = when (part) {
    is UIMessagePart.Text -> estimateTokens(part.text)
    is UIMessagePart.Reasoning -> estimateTokens(part.reasoning)
    is UIMessagePart.Tool ->
        estimateTokens(part.toolName) +
            estimateTokens(part.input) +
            part.output.filterIsInstance<UIMessagePart.Text>().sumOf { estimateTokens(it.text) }

    is UIMessagePart.ServerTool ->
        estimateTokens(part.toolName) +
            estimateTokens(part.input?.toString().orEmpty()) +
            estimateTokens(part.output?.toString().orEmpty())

    is UIMessagePart.ToolCall -> estimateTokens(part.toolName) + estimateTokens(part.arguments)

    is UIMessagePart.ToolResult ->
        estimateTokens(part.toolName) +
            estimateTokens(part.content.toString()) +
            estimateTokens(part.arguments.toString())

    is UIMessagePart.Document -> 512 + estimateTokens(part.fileName)
    is UIMessagePart.Image, is UIMessagePart.Video, is UIMessagePart.Audio -> 768
    else -> 0
}

internal fun estimateTokens(text: String): Int {
    if (text.isEmpty()) return 0
    var cjk = 0
    var other = 0
    for (ch in text) {
        if (ch.code >= 0x2E80) cjk++ else other++
    }
    return cjk + (other + 3) / 4
}

/**
 * 压缩目标占当前占用的比例。
 *
 * 按实测占用压满，压完就贴着门限：下一轮加一条消息立刻再次触发，每发一条就多一次
 * 整段上下文的摘要请求。留一半余量，压一次能撑几轮。
 */
internal const val AUTO_COMPRESS_TARGET_RATIO = 0.5f

/** 按字符估算偏低（不含工具定义等），真实 usage 不可用时留一点余量 */
internal const val CONTEXT_ESTIMATE_SAFETY = 1.1f

/**
 * 门限判断用的上下文占用。
 *
 * 真实 usage 看不到刚追加、还没发出去的消息；字符估算看不到工具定义，精度也差。
 * 两边都算一遍取大的——谁更悲观就信谁。压缩后 usage 会短暂缺失，那时只剩估算，
 * 所以估算这一侧仍然乘安全系数。
 */
internal fun resolveContextUsage(
    realTokens: Int?,
    visibleTokens: Int,
    fixedTokens: Int,
): Int {
    val estimated = ((visibleTokens + fixedTokens) * CONTEXT_ESTIMATE_SAFETY).toInt()
    return maxOf(realTokens ?: 0, estimated)
}

/**
 * 自动压缩实际使用的门限。WARN/MAX 未配置时依次退回另一个门限和旧的 contextLength，
 * 避免只填了模型上下文长度时自动压缩被判成“没有门限”而永不触发。
 */
fun Model.autoCompressLimit(threshold: AutoCompressThreshold): Int? {
    val warn = warnContextTokens?.takeIf { it > 0 }
    val max = maxContextTokens?.takeIf { it > 0 }
    val legacy = contextLength.takeIf { it > 0 }
    return when (threshold) {
        AutoCompressThreshold.WARN -> warn ?: max ?: legacy
        AutoCompressThreshold.MAX -> max ?: warn ?: legacy
    }
}

/**
 * 压缩预算：这次要把上下文压到多少 token 以内。
 *
 * 取「当前占用的一半」和「门限的一半」里更小的那个。按门限压满，下一轮加一条消息就又过线；
 * 占用远超门限时（导入的长历史、换了窗口更小的模型）只看占用的一半还会连压几轮，
 * 每一轮都是一次整段摘要请求，所以再用门限的一半封顶。
 */
internal fun autoCompressBudget(usedTokens: Int, thresholdTokens: Int?): Int {
    val halfUsage = (usedTokens * AUTO_COMPRESS_TARGET_RATIO).toInt()
    val halfThreshold = thresholdTokens?.takeIf { it > 0 }
        ?.let { (it * AUTO_COMPRESS_TARGET_RATIO).toInt() }
    return if (halfThreshold == null) halfUsage else minOf(halfUsage, halfThreshold)
}

/**
 * 保留区能用的预算：总预算先扣掉摘要和 systemPrompt 这些压缩后仍然占着上下文的固定开销。
 * 至少留四分之一，防止固定开销自己就把预算吃光时把保留区压成空。
 */
internal fun autoCompressRetainedBudget(budgetTokens: Int, reservedTokens: Int): Int =
    (budgetTokens - reservedTokens).coerceAtLeast((budgetTokens * 0.25f).toInt())

/**
 * 计算自动压缩的截断点：保留最近 [keepMessages] 条消息，其余压进摘要。
 *
 * 只在消息边界切。以前按 token 数找「最接近目标的 part 边界」，切在半条消息上时
 * 附件、工具调用和正文会拆开，模型读到的是残缺的一轮；估算本身也不准，切点经常偏掉。
 *
 * [minTailNodes] 是末尾必须留下的节点数：模型循环里压缩时，末尾那条是正在生成的回复，
 * 压进摘要就等于把这一轮的提问和输出一起丢掉。
 *
 * 保留区估算超过 [retainedBudget] 时从旧的一端整条丢弃，压完不会立刻又贴着门限；
 * 保留条数配得比现有消息还多、一条都压不掉时按一半压——门限已经判定超限，
 * 一条都不压等于把超限的请求原样发出去。
 *
 * [maxEndIndex] 是截断点的上界（不含）：重新生成较早的消息时，检查点不能插到那个节点后面，
 * 否则它连同刚生成的回复一起被排除在请求之外，内容既不在摘要里也不发给模型。
 *
 * 返回 null 表示没有可压缩的范围。
 */
internal fun Conversation.resolveAutoCompressTrim(
    keepMessages: Int,
    minTailNodes: Int = 1,
    retainedBudget: Int? = null,
    maxEndIndex: Int = messageNodes.size,
): ContextTrim? {
    val checkpointIndex = messageNodes.indexOfLast { it.currentMessage.isContextCheckpoint }
    val tailNodes = messageNodes.size - checkpointIndex - 1
    if (tailNodes <= minTailNodes) return null

    val limit = maxEndIndex.coerceIn(checkpointIndex + 1, messageNodes.size)
    if (limit <= checkpointIndex + 1) return null

    val keep = if (keepMessages in 1 until tailNodes) keepMessages
    else (tailNodes / 2).coerceAtLeast(minTailNodes)
    var endIndex = minOf(messageNodes.size - keep, limit)

    if (retainedBudget != null && retainedBudget > 0) {
        val lastKeepable = minOf(messageNodes.size - minTailNodes, limit)
        var retained = estimateNodesTokens(endIndex)
        while (retained > retainedBudget && endIndex < lastKeepable) {
            retained -= estimateMessageTokens(messageNodes[endIndex].currentMessage)
            endIndex++
        }
    }

    if (endIndex <= checkpointIndex + 1) return null
    return ContextTrim(endIndex)
}

/**
 * 把压缩范围切成每段不超过 [budgetTokens] 的若干段。
 *
 * 摘要请求必须装进模型窗口：超限时把整段历史原样再发一次，只会被同样拒绝，
 * 自动压缩永远救不回这一轮。消息之间按整条切；单条自己就超预算时退到 part 边界，
 * 单个 part 还超就截断正文——对摘要来说，丢掉末尾的一小段也比整段失败好。
 */
internal fun chunkMessagesForCompression(
    messages: List<UIMessage>,
    budgetTokens: Int,
): List<List<UIMessage>> {
    if (messages.isEmpty()) return listOf(messages)
    val budget = budgetTokens.coerceAtLeast(1)
    val chunks = mutableListOf<List<UIMessage>>()
    val current = mutableListOf<UIMessage>()
    var used = 0

    fun flush() {
        if (current.isEmpty()) return
        chunks += current.toList()
        current.clear()
        used = 0
    }

    messages.forEach { message ->
        val messageTokens = estimateMessageTokens(message)
        if (messageTokens <= budget) {
            if (used + messageTokens > budget) flush()
            current += message
            used += messageTokens
            return@forEach
        }
        flush()
        splitOversizedMessage(message, budget).forEach { piece ->
            val pieceTokens = estimateMessageTokens(piece)
            if (used + pieceTokens > budget) flush()
            current += piece
            used += pieceTokens
        }
    }
    flush()
    return chunks.ifEmpty { listOf(messages) }
}

/** 单条消息超过预算时按 part 边界拆开；单个 part 仍然超预算就截断它 */
private fun splitOversizedMessage(message: UIMessage, budgetTokens: Int): List<UIMessage> {
    if (message.parts.isEmpty()) return listOf(message)
    val pieces = mutableListOf<UIMessage>()
    val current = mutableListOf<UIMessagePart>()
    var used = MESSAGE_TOKEN_OVERHEAD

    fun flush() {
        if (current.isEmpty()) return
        pieces += message.copy(parts = current.toList())
        current.clear()
        used = MESSAGE_TOKEN_OVERHEAD
    }

    message.parts.forEach { part ->
        val partTokens = estimatePartTokens(part)
        if (used + partTokens <= budgetTokens) {
            current += part
            used += partTokens
            return@forEach
        }
        flush()
        val trimmed = truncatePart(part, budgetTokens - MESSAGE_TOKEN_OVERHEAD)
        current += trimmed
        used += estimatePartTokens(trimmed)
    }
    flush()
    return pieces.ifEmpty { listOf(message) }
}

/** 把单个 part 截到大约 [maxTokens]；放得下就原样返回 */
private fun truncatePart(part: UIMessagePart, maxTokens: Int): UIMessagePart {
    val budget = maxTokens.coerceAtLeast(1)
    if (estimatePartTokens(part) <= budget) return part
    return when (part) {
        is UIMessagePart.Text -> part.copy(text = part.text.truncateToTokens(budget))
        is UIMessagePart.Reasoning -> part.copy(reasoning = part.reasoning.truncateToTokens(budget))
        is UIMessagePart.Tool -> {
            // 调用参数通常很短，也是继续对话要用的信息，截断它等于把这次调用抹成空参调用；
            // 只有参数自己就超预算时（write_file 这类）才截，其余情况预算全给输出
            val head = estimateTokens(part.toolName) + estimateTokens(part.input)
            val input = if (head > budget) part.input.truncateToTokens(budget / 2) else part.input
            val outputBudget = (budget - estimateTokens(part.toolName) - estimateTokens(input))
                .coerceAtLeast(1)
            val textOutputs = part.output.count { it is UIMessagePart.Text }.coerceAtLeast(1)
            val each = (outputBudget / textOutputs).coerceAtLeast(1)
            part.copy(
                input = input,
                output = part.output.map { output ->
                    if (output is UIMessagePart.Text) {
                        output.copy(text = output.text.truncateToTokens(each))
                    } else {
                        output
                    }
                },
            )
        }

        is UIMessagePart.ToolCall -> part.copy(arguments = part.arguments.truncateToTokens(budget))
        // 搜索/抓取这类服务端工具只保留文本形态：换掉整个 part 才不会留下半截
        // server_tool_use / tool_result 配对，provider 回放时也不会拒绝
        is UIMessagePart.ServerTool -> UIMessagePart.Text(
            buildString {
                append("[${part.toolName}] ")
                part.input?.toString()?.let {
                    append(it.truncateToTokens(budget / 2))
                    append('\n')
                }
                part.output?.toString()?.let { append(it.truncateToTokens(budget / 2)) }
            }
        )

        is UIMessagePart.ToolResult -> UIMessagePart.Text(
            "[${part.toolName}] " + part.content.toString().truncateToTokens(budget)
        )

        else -> part
    }
}

/**
 * 按 [estimateTokens] 的口径把正文截到大约 [maxTokens]。
 * 只用于摘要请求：单条消息自己就超过整段预算时，截断比整段失败好。
 */
internal fun String.truncateToTokens(maxTokens: Int): String {
    if (maxTokens <= 0) return ""
    var cjk = 0
    var other = 0
    var end = 0
    for ((index, ch) in withIndex()) {
        if (ch.code >= 0x2E80) cjk++ else other++
        if (cjk + (other + 3) / 4 > maxTokens) break
        end = index + 1
    }
    if (end >= length) return this
    // 不要把代理对切成两半
    if (end > 0 && this[end].isLowSurrogate()) end--
    return substring(0, end) + TRUNCATED_MARKER
}

private const val TRUNCATED_MARKER = "\n[truncated]"
