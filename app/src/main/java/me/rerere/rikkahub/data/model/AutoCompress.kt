package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
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

/** 自动压缩保留多少最近上下文：按消息条数或按 token 数 */
@Serializable
enum class AutoCompressTarget {
    MESSAGES,
    TOKENS,
}

/**
 * 压缩后保留的起点。
 *
 * [nodeIndex] 之前的消息全部压进摘要；[partIndex] 大于 0 时，[nodeIndex] 这条消息只保留
 * 从该 part 开始的后缀（思考、正文、工具调用等 part 的边界），避免为了凑 token 数整条丢弃。
 */
data class ContextTrim(val nodeIndex: Int, val partIndex: Int = 0)

private const val MESSAGE_TOKEN_OVERHEAD = 4

/**
 * 从对话末尾往前累计，找出离 [targetTokens] 最近的 part 边界。
 *
 * 只统计最后一个压缩检查点之后的消息：检查点之前的内容已经由摘要代替，不会发给模型。
 * token 数按字符粗略估算（CJK 一字约一 token，其它约四字符一 token），
 * 只用来决定压缩范围，不要求精确。
 */
fun Conversation.keepRecentContextForTokens(targetTokens: Int): ContextTrim {
    if (targetTokens <= 0 || messageNodes.isEmpty()) return ContextTrim(messageNodes.size)

    var used = 0
    var keptAny = false
    var index = messageNodes.lastIndex
    while (index >= 0) {
        val node = messageNodes[index]
        val message = node.messages.getOrNull(node.selectIndex)
        if (message == null) {
            index--
            continue
        }
        if (message.isContextCheckpoint) break

        val partTokens = message.parts.map(::estimatePartTokens)
        val messageTokens = MESSAGE_TOKEN_OVERHEAD + partTokens.sum()
        if (used + messageTokens <= targetTokens) {
            used += messageTokens
            keptAny = true
            index--
            continue
        }

        // 用户消息不拆，宁可整条保留或整条压掉；附件和文本拆开容易破坏语义。
        // 最新一条消息必须保留，否则刚发出的问题会被压进摘要、模型看不到。
        if (message.role == MessageRole.USER || message.parts.isEmpty()) {
            return ContextTrim(if (keptAny) index + 1 else index)
        }

        // 这条消息放不下：在它的 part 边界里挑一个估算值最接近目标的
        var best = if (keptAny) ContextTrim(index + 1) else ContextTrim(index)
        var bestDiff = if (keptAny) {
            kotlin.math.abs(targetTokens - used)
        } else {
            kotlin.math.abs(targetTokens - (used + messageTokens))
        }
        var suffixTokens = 0
        for (partIndex in message.parts.indices.reversed()) {
            suffixTokens += partTokens[partIndex]
            // 空文本/空 part 不能当成切割边界，否则截断后内容没有变化
            if (partTokens[partIndex] <= 0) continue
            val estimate = used + MESSAGE_TOKEN_OVERHEAD + suffixTokens
            val diff = kotlin.math.abs(targetTokens - estimate)
            if (diff < bestDiff || (diff == bestDiff && estimate <= targetTokens)) {
                best = ContextTrim(index, partIndex)
                bestDiff = diff
            }
            if (estimate >= targetTokens) break
        }
        return best
    }
    return ContextTrim(index + 1)
}

/** 实际会发给模型的上下文 token 估算；压缩前的老消息和 part 边界之前的内容都不计入 */
fun Conversation.estimateContextTokens(): Int =
    currentMessages.limitContext(0).sumOf(::estimateMessageTokens)

/** 单条消息的 token 估算，工具调用的输入输出也算在内 */
internal fun estimateMessageTokens(message: UIMessage): Int =
    MESSAGE_TOKEN_OVERHEAD + message.parts.sumOf(::estimatePartTokens)

/** 单个 part 的 token 估算，用来决定消息内部的截断位置 */
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
 * 计算自动压缩的截断点。返回值已经过范围校验：null 表示没有可压缩的消息。
 *
 * [reservedTokens] 是压缩后依然占着上下文的那部分固定开销（摘要、systemPrompt、工具定义等）。
 * 门限判断把它们算进了用量，截断目标就必须先把它们扣掉，否则两把尺子不一致：
 * 保留区一大，压完立刻又超限。[limitTokens] 是门限本身，只在按条数压缩时用于复核 ——
 * 条数达标但估算仍然超限时退化为按 token 再压一轮，同一次调用里最多回退一次。
 *
 * 循环压缩（[force] && [keepTailNodes]）优先保留末尾那条正在生成的回复；如果检查点之后
 * 只有这一条，继续保留会让压缩范围为空，此时退化为压缩全部历史，避免整轮直接失败。
 */
internal fun Conversation.resolveAutoCompressTrim(
    target: AutoCompressTarget,
    targetValue: Int,
    keepTailNodes: Int?,
    force: Boolean,
    reservedTokens: Int = 0,
    limitTokens: Int? = null,
): ContextTrim? {
    val checkpointIndex = messageNodes.indexOfLast { it.currentMessage.isContextCheckpoint }
    if (messageNodes.size - checkpointIndex - 1 < 1) return null

    val preferred = if (keepTailNodes != null) {
        ContextTrim((messageNodes.size - keepTailNodes).coerceAtLeast(checkpointIndex + 1))
    } else {
        when (target) {
            AutoCompressTarget.MESSAGES -> {
                val byCount = ContextTrim(messageNodes.size - targetValue)
                // 条数模式没有 token 目标，压完用门限复核一次：固定开销加上保留区估算
                // 仍然超限，说明按条数留得太多，改用 token 预算再算；没超限就保持按条数
                val budget = limitTokens?.takeIf { it > 0 }
                val retained = if (budget == null) {
                    0
                } else {
                    reservedTokens + estimateRetainedTokens(byCount.nodeIndex.coerceAtLeast(checkpointIndex + 1))
                }
                if (budget != null && retained > budget) {
                    trimToTokenBudget(budget, reservedTokens)
                } else {
                    byCount
                }
            }

            AutoCompressTarget.TOKENS -> trimToTokenBudget(targetValue, reservedTokens)
        }
    }
    val trim = if (force && keepTailNodes != null && preferred.nodeIndex <= checkpointIndex + 1) {
        ContextTrim(messageNodes.size)
    } else {
        preferred
    }
    val onlyCheckpointTail = trim.nodeIndex == checkpointIndex + 1
    if (trim.nodeIndex < checkpointIndex + 1 ||
        trim.nodeIndex > messageNodes.size ||
        trim.nodeIndex == 0 ||
        (onlyCheckpointTail && trim.partIndex == 0)
    ) {
        return null
    }
    return trim
}

/**
 * token 模式的截断目标：先扣掉固定开销，再留门限 25% 的下限，
 * 防止摘要和 systemPrompt 本身就把预算吃光时把保留区压成空。
 */
private fun Conversation.trimToTokenBudget(budget: Int, reservedTokens: Int): ContextTrim {
    val floor = (budget * 0.25f).toInt()
    val effectiveTarget = (budget - reservedTokens).coerceAtLeast(floor)
    return keepRecentContextForTokens(effectiveTarget)
}

/** [start] 之后（含 [start]）仍会发给模型的 token 估算；固定开销不算在内 */
private fun Conversation.estimateRetainedTokens(start: Int): Int {
    if (messageNodes.isEmpty()) return 0
    val from = start.coerceIn(0, messageNodes.size)
    var total = 0
    for (index in from..messageNodes.lastIndex) {
        total += estimateMessageTokens(messageNodes[index].currentMessage)
    }
    return total
}
