package me.rerere.rikkahub.data.model

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
    currentMessages.estimateContextTokens(messageLimit)

/** 同一套估算，作用在已经组装好的消息列表上：模型循环手里的那条回复比会话里的新 */
fun List<UIMessage>.estimateContextTokens(messageLimit: Int = 0): Int =
    limitContext(messageLimit).sumOf(::estimateMessageTokens)

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

/** 按字符估算偏低（不含工具定义等），只有估算的那部分乘这个系数 */
internal const val CONTEXT_ESTIMATE_SAFETY = 1.1f

/**
 * 门限判断用的上下文占用：以最近一次请求的真实 usage 打底，只补上它之后新增的内容。
 *
 * 真实 usage 已经包含系统提示词、工具定义和它之前的全部历史；估算只负责 usage 看不到的那部分
 * （刚追加、还没发出去的消息，以及上一次请求之后才执行完的工具输出）。两边不能各自算整段再取大
 * ——那样整段上下文会被放大约一倍（中文按 1 字 1 token 估，现代分词器实际约 0.65），
 * 门限一到六成就反复触发压缩。
 *
 * [fixedTokens] 只在完全没有 usage 时用得上：这时连系统提示词一起估。
 *
 * 检查点之前的回复不算数，哪怕它带着 usage，见 [measuredUsageIndex]。
 *
 * 只有 prompt 侧的 usage 才算实测：有的中转只回 completion、promptTokens 是 0，
 * 那时的 usage 只是这条回复的长度，拿它当整段上下文会一直判不到门限。
 */
internal fun List<UIMessage>.resolveContextUsage(
    messageLimit: Int = 0,
    fixedTokens: Int = 0,
): Int {
    val visible = limitContext(messageLimit)
    val estimated = visible.estimateContextTokens() + fixedTokens
    val measuredIndex = visible.measuredUsageIndex()
    if (measuredIndex != null) {
        val message = visible[measuredIndex]
        val usage = message.lastRequestUsage()
        val measured = (usage?.promptTokens ?: 0) + (usage?.completionTokens ?: 0)
        // 两个条件：prompt 侧确实测过（否则就是上面那种只有 completion 的 usage），
        // 以及 usage 至少是估算的一半——usage 停在很早以前那次请求上时，同样当不了整段上下文。
        // 都不满足就退回估算，估高总比不压好
        if ((usage?.promptTokens ?: 0) > 0 && measured * 2 >= estimated) {
            val grown = message.outputsSinceRequestTokens() +
                visible.subList(measuredIndex + 1, visible.size).sumOf(::estimateMessageTokens)
            return measured + (grown * CONTEXT_ESTIMATE_SAFETY).toInt()
        }
    }
    return (estimated * CONTEXT_ESTIMATE_SAFETY).toInt()
}

/**
 * 上一次请求之后才执行完的工具输出。
 *
 * 工具调用和它的输出是同一个 part：请求发出时 part 里只有调用，输出是之后原地写回去的，
 * 所以 usage 里看不到这部分增长。按执行时间认出来补上——否则循环里压力最大的那一步
 * （刚跑完一堆 shell 或文件操作）判据一动不动。
 *
 * 只算输出正文：调用名和参数是模型的输出，那次请求的 usage 里已经计过。
 * 审批拒绝这类没真正跑过的工具没有 [UIMessagePart.Tool.startedAt]，补不上，量也小。
 */
private fun UIMessage.outputsSinceRequestTokens(): Int {
    val requestAt = finishedAt ?: return 0
    val executed = parts.filterIsInstance<UIMessagePart.Tool>().filter { it.startedAt != null }
    if (executed.isEmpty()) return 0
    val zone = TimeZone.currentSystemDefault()
    return executed
        .filter { it.startedAt?.let { at -> at.toLocalDateTime(zone) >= requestAt } == true }
        .sumOf { tool -> tool.output.filterIsInstance<UIMessagePart.Text>().sumOf { estimateTokens(it.text) } }
}

/**
 * 自动压缩实际使用的门限。
 *
 * 用户填过的门限优先：WARN/MAX 缺一个就退回另一个，和以前一致，手填的值一律不动。
 * 两个都没填时按模型窗口兜底（WARN 75%、MAX 90%）。以前最后一步退回的是
 * [Model.contextLength] 原值：等于让输入占满整个窗口，回复一个 token 都放不下，
 * 压缩只能等 provider 先报超限。
 *
 * 自动填充的门限（[Model.contextThresholdsAuto]）还会按窗口收窄：它可能是按 1M 窗口写下的旧值，
 * 超过真实窗口的门限等于没有门限。收窄比例和预设一致（WARN 75%、MAX 90%）：
 * 两档都夹到 90% 的话 WARN 和 MAX 会落到同一个数，预警那一档等于不存在。
 * 手填的值不收窄——那是用户自己的选择。
 */
fun Model.autoCompressLimit(threshold: AutoCompressThreshold): Int? {
    val warn = warnContextTokens?.takeIf { it > 0 }
    val max = maxContextTokens?.takeIf { it > 0 }
    val window = contextLength.takeIf { it > 0 }
    val percent = when (threshold) {
        AutoCompressThreshold.WARN -> WARN_WINDOW_PERCENT
        AutoCompressThreshold.MAX -> MAX_WINDOW_PERCENT
    }
    val ceiling = window?.let { (it * percent / 100).coerceAtLeast(1) }
    val configured = when (threshold) {
        AutoCompressThreshold.WARN -> warn ?: max
        AutoCompressThreshold.MAX -> max ?: warn
    }
    if (configured != null) {
        return if (contextThresholdsAuto && ceiling != null) minOf(configured, ceiling) else configured
    }
    return ceiling
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
 * [minTailNodes] 是正常情况下末尾保留的节点数：模型循环里压缩时，末尾那条是正在生成的回复，
 * 压进摘要就等于把这一轮的输出丢掉。可压缩范围不够时最多退到 1（只保住这条回复）。
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
    // 末尾至少留下正在生成的那条回复。minTailNodes 是正常情况下希望保留的条数，
    // 但一轮对话只有「提问 + 回复」两条时，坚持留两条就永远压不动：上下文已经超了，
    // 原样发出去只会被拒。这时退到 1，把提问压进摘要，回复照常留在后面。
    if (tailNodes <= 1) return null
    val floor = minTailNodes.coerceIn(1, tailNodes - 1)

    val limit = maxEndIndex.coerceIn(checkpointIndex + 1, messageNodes.size)
    if (limit <= checkpointIndex + 1) return null

    val keep = if (keepMessages in 1 until tailNodes) keepMessages
    else (tailNodes / 2).coerceAtLeast(floor)
    var endIndex = minOf(messageNodes.size - keep, limit)

    if (retainedBudget != null && retainedBudget > 0) {
        val lastKeepable = minOf(messageNodes.size - floor, limit)
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
