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

/**
 * 实际会发给模型的上下文 token 估算；压缩前的老消息和 part 边界之前的内容都不计入。
 *
 * [messageLimit] 必须和组装请求时的 `assistant.contextMessageLimit` 一致：判定用的尺子比发送用的
 * 尺子长，就会为一堆根本不会发出去的尾部消息反复触发压缩，每次还都是一次全量摘要请求。
 */
fun Conversation.estimateContextTokens(messageLimit: Int = 0): Int =
    currentMessages.limitContext(messageLimit).sumOf(::estimateMessageTokens)

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
 * 计算自动压缩的截断点。返回值已经过范围校验：null 表示没有可压缩的消息。
 *
 * [reservedTokens] 是压缩后依然占着上下文的那部分固定开销（摘要、systemPrompt 等）。
 * [limitTokens] 是门限判断实际用掉的那个占用值（真实 usage 与估算取大），压缩目标按它的
 * [AUTO_COMPRESS_TARGET_RATIO] 折算：压完留出余量，否则保留区贴着门限，下一轮立刻再压一次。
 *
 * 循环压缩（[force] && [keepTailNodes]）优先保留末尾那条正在生成的回复；检查点之后只剩
 * 这几条时压缩范围为空，此时返回 null 放弃这一轮——硬压会把正在生成的回复也总结掉，
 * 重试请求里连用户的问题都不剩。
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

    // 压缩目标只取占用的一半：贴着实测占用压，下一轮加一条消息就又过线
    val budget = limitTokens?.takeIf { it > 0 }?.let { (it * AUTO_COMPRESS_TARGET_RATIO).toInt() }

    val preferred = if (keepTailNodes != null) {
        ContextTrim((messageNodes.size - keepTailNodes).coerceAtLeast(checkpointIndex + 1))
    } else {
        when (target) {
            AutoCompressTarget.MESSAGES -> {
                val byCount = ContextTrim(messageNodes.size - targetValue)
                // 条数模式没有 token 目标，压完用预算复核一次：固定开销加上保留区估算
                // 超过预算，说明按条数留得太多，改用 token 预算再算；没超就保持按条数
                val retained = if (budget == null) {
                    0
                } else {
                    reservedTokens + estimateRetainedTokens(byCount.nodeIndex.coerceAtLeast(checkpointIndex + 1))
                }
                // 保留条数比现有消息还多时，byCount 落到检查点之前，范围校验会直接返回 null——
                // 等于「设了保留 20 条，于是永远不压缩」，而门限已经判定超限，请求照发只会被拒。
                // 这时退回 token 预算，至少还能按占用的一半压一次
                val byCountUnusable = byCount.nodeIndex < checkpointIndex + 1
                if (budget != null && (byCountUnusable || retained > budget)) {
                    trimToTokenBudget(budget, reservedTokens)
                } else {
                    byCount
                }
            }

            AutoCompressTarget.TOKENS -> {
                // 设置页只校验下限，保留量可能比门限还大；那样等于要求整段上下文都留下，
                // 压缩永远不会发生，所以夹到预算以内
                trimToTokenBudget(
                    if (budget == null) targetValue else minOf(targetValue, budget),
                    reservedTokens,
                )
            }
        }
    }
    // 检查点之后只剩要保留的那几条：再压就只能把它们也压掉
    if (force && keepTailNodes != null && preferred.nodeIndex <= checkpointIndex + 1) return null
    val onlyCheckpointTail = preferred.nodeIndex == checkpointIndex + 1
    if (preferred.nodeIndex < checkpointIndex + 1 ||
        preferred.nodeIndex > messageNodes.size ||
        preferred.nodeIndex == 0 ||
        (onlyCheckpointTail && preferred.partIndex == 0)
    ) {
        return null
    }
    return preferred
}

/**
 * token 模式的截断目标：先扣掉固定开销，再留门限 25% 的下限，
 * 防止摘要和 systemPrompt 本身就把预算吃光时把保留区压成空。
 */
private fun Conversation.trimToTokenBudget(budget: Int, reservedTokens: Int): ContextTrim {
    val floor = (budget * 0.25f).toInt()
    val effectiveTarget = (budget - reservedTokens).coerceAtLeast(floor)
    val trim = keepRecentContextForTokens(effectiveTarget)
    // 「取最接近目标的边界」允许轻微超出。超出到检查点之后一条都压不掉时，预算等于没起作用，
    // 减半再算一次，保证至少切掉一条，否则压缩会在最后一步空转成「没有可压缩范围」
    val checkpointIndex = messageNodes.indexOfLast { it.currentMessage.isContextCheckpoint }
    if (trim.nodeIndex <= checkpointIndex + 1 && trim.partIndex == 0 && effectiveTarget > 0) {
        return keepRecentContextForTokens(effectiveTarget / 2)
    }
    return trim
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
