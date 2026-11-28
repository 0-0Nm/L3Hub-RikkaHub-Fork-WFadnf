package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.ArchivedLiveGuidance
import me.rerere.rikkahub.data.model.ConversationConfig
import me.rerere.rikkahub.data.model.LiveGuidance
import me.rerere.rikkahub.data.model.LiveGuidanceMode
import me.rerere.rikkahub.data.model.liveGuidanceEntries
import kotlin.uuid.Uuid

/**
 * Bounded marker for a persistent preference whose original anchor is no longer retained.
 * It only marks the text as an OLD user preference; it never claims system/developer authority
 * and the message always stays a plain USER contribution placed before the retained history.
 */
private const val ARCHIVED_PREFERENCE_PREFIX = "Earlier user preference in this conversation: "

/**
 * Snapshot eligible steering as a request-only plain USER text at the next safe checkpoint
 * (the end of the assembled step). No canned imperative frame: the user's own wording is the
 * newest ordinary user contribution, so newer user instructions keep their natural priority.
 * The stable ID identifies this card when provider output arrives; building the message does
 * not acknowledge delivery.
 */
fun LiveGuidance.requestMessage(runId: Uuid): UIMessage? =
    takeIf { shouldInject(runId) }?.let {
        UIMessage.user(content).copy(id = id, isSynthetic = true)
    }

/** Chronological history form: the original wording right after its anchored assistant reply. */
fun ArchivedLiveGuidance.historyMessage(): UIMessage =
    UIMessage.user(content).copy(id = id, isSynthetic = true)

/**
 * Fallback for a persistent note whose anchor was clipped, compacted or pruned: the same old
 * preference with a bounded prefix, placed before the retained history instead of after the
 * latest normal user message.
 */
fun ArchivedLiveGuidance.fallbackPreferenceMessage(): UIMessage =
    UIMessage.user(ARCHIVED_PREFERENCE_PREFIX + content).copy(id = id, isSynthetic = true)

/**
 * Active cards only, in stable eligible order. Archived notes are NOT appended here anymore;
 * they enter the request through [withArchivedGuidance] as chronological history.
 */
fun ConversationConfig.liveGuidanceRequestMessages(runId: Uuid): List<UIMessage> =
    liveGuidanceEntries().mapNotNull { it.requestMessage(runId) }.distinctBy { it.id }

/**
 * Project delivered archives into the request as ordinary chronological USER history:
 *
 * - An entry whose anchored assistant reply is retained (and is not the trailing message) is
 *   inserted right after that reply, in archival order. Tool calls/results stay inside their
 *   own message parts, so message-boundary insertion never splits tool adjacency.
 * - A TEMPORARY entry whose anchor is missing, or whose anchor is the trailing message, is not
 *   injected at all: it remains UI-only history instead of a re-activated direction.
 * - A PERSISTENT entry in the same situation falls back to a bounded-prefix USER message before
 *   the retained history (after any leading system prompt). It is never moved after the latest
 *   normal user message and never fabricates an unanswered user turn at the end.
 * - IDs are de-duplicated, so one note appears exactly once and order stays stable.
 */
internal fun List<UIMessage>.withArchivedGuidance(
    archive: List<ArchivedLiveGuidance>,
): List<UIMessage> {
    if (archive.isEmpty()) return this
    val existingIds = mapTo(mutableSetOf()) { it.id }
    val entries = archive.distinctBy { it.id }.filter { it.id !in existingIds }
    if (entries.isEmpty()) return this

    val byAnchor = entries.groupBy { it.messageId }
    // Expansion can split one logical assistant reply around notices/tool results, keeping its ID.
    val lastFragmentByAnchor = mutableMapOf<Uuid, Int>()
    forEachIndexed { index, message ->
        if (message.role == MessageRole.ASSISTANT && message.id in byAnchor) {
            lastFragmentByAnchor[message.id] = index
        }
    }
    val projected = mutableListOf<UIMessage>()
    val used = mutableSetOf<Uuid>()
    forEachIndexed { index, message ->
        projected += message
        // A trailing anchor would fabricate an unanswered user turn at the end of the request
        // (retry/alternate generation continuing that reply); those entries use the rules above.
        if (index != lastIndex && lastFragmentByAnchor[message.id] == index) {
            byAnchor[message.id].orEmpty().forEach { entry ->
                if (used.add(entry.id)) projected += entry.historyMessage()
            }
        }
    }

    val fallback = entries.filter { it.id !in used && it.mode == LiveGuidanceMode.PERSISTENT }
    if (fallback.isNotEmpty()) {
        val firstNonSystem = projected.indexOfFirst { it.role != MessageRole.SYSTEM }
        val insertAt = if (firstNonSystem < 0) projected.size else firstNonSystem
        projected.addAll(insertAt, fallback.map { it.fallbackPreferenceMessage() })
    }
    return projected
}

/** Request-scoped only: a replacement never leaves the previous synthetic message in this request. */
internal fun List<UIMessage>.withRequestInjections(injected: List<UIMessage>): List<UIMessage> =
    this + injected.distinctBy { it.id }
