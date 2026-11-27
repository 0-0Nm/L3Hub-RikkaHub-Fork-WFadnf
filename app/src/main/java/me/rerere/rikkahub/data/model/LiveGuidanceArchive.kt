package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import kotlin.uuid.Uuid

/**
 * Run-finished steering kept as conversation metadata, never as a model/user message.
 *
 * [id] is the original command ID, so archiving the same run twice cannot duplicate the entry.
 * [nodeId] anchors the entry right after the assistant reply of its originating run; [messageId]
 * names that reply for readers that need the exact branch message.
 */
@Serializable
data class ArchivedLiveGuidance(
    val id: Uuid,
    val runId: Uuid,
    val content: String,
    val mode: LiveGuidanceMode = LiveGuidanceMode.TEMPORARY,
    val nodeId: Uuid,
    val messageId: Uuid,
)

/** De-dupe by command ID: a repeated finish keeps one entry with the newest anchor. */
fun List<ArchivedLiveGuidance>.upsertArchivedGuidance(
    record: ArchivedLiveGuidance,
): List<ArchivedLiveGuidance> = filterNot { it.id == record.id } + record

/**
 * Only a note whose originating run finished after real provider output becomes history.
 * Missed ([LiveGuidanceDelivery.NOT_USED]) notes stay active and keep their next-message recovery.
 */
fun LiveGuidance.archiveRecord(nodeId: Uuid, messageId: Uuid): ArchivedLiveGuidance? {
    val origin = runId
    return if (finishLocked && origin != null &&
        delivery == LiveGuidanceDelivery.DELIVERED && deliveredRunId == origin
    ) {
        ArchivedLiveGuidance(
            id = id,
            runId = origin,
            content = content,
            mode = mode,
            nodeId = nodeId,
            messageId = messageId,
        )
    } else {
        null
    }
}

/** Prefer the reply this run wrote; otherwise the trailing assistant reply of the current branch. */
fun Conversation.liveGuidanceAnchor(replyMessageId: Uuid?): Pair<Uuid, Uuid>? {
    if (replyMessageId != null) {
        val node = messageNodes.firstOrNull { candidate ->
            candidate.messages.any { it.id == replyMessageId && it.role == MessageRole.ASSISTANT }
        }
        if (node != null) return node.id to replyMessageId
    }
    val trailing = messageNodes.lastOrNull { it.currentMessage.role == MessageRole.ASSISTANT }
    return trailing?.let { it.id to it.currentMessage.id }
}

/**
 * Close the active note for [runId]. Delivery acknowledgement and the final mode choice are already
 * recorded; archival moves the note and clears the compact card in ONE config value, so the partial
 * config-column write can never persist a half-archived state.
 */
fun ConversationConfig.finishLiveGuidance(
    runId: Uuid,
    anchor: () -> Pair<Uuid, Uuid>?,
): ConversationConfig {
    if (liveGuidanceEntries().isEmpty()) return this
    val replyAnchor = anchor()
    val records = mutableListOf<ArchivedLiveGuidance>()
    val retained = liveGuidanceEntries().map { item ->
        val finished = item.finish(runId)
        if (finished.finishLocked && replyAnchor != null) {
            finished.archiveRecord(replyAnchor.first, replyAnchor.second)?.let(records::add)
        }
        finished
    }.filter { item -> records.none { record -> record.id == item.id } }
    val archive = records.fold(liveGuidanceArchive) { current, record ->
        current.upsertArchivedGuidance(record)
    }
    return withLiveGuidanceEntries(retained).copy(liveGuidanceArchive = archive)
}

/**
 * Reopen/migrate an old config without touching history: a delivered note from an already finished
 * run (E346 layout, or a crash between finish and archival) becomes a durable entry anchored to the
 * best known reply. An unfinished waiting run still degrades to NOT_USED and stays on the card.
 */
fun Conversation.recoverLiveGuidance(): Conversation {
    val current = config ?: return this
    if (current.liveGuidanceEntries().isEmpty()) return this
    val replyAnchor = liveGuidanceAnchor(replyMessageId = null)
    val records = mutableListOf<ArchivedLiveGuidance>()
    val recovered = current.liveGuidanceEntries().map { item ->
        item.recoverAfterReopen().also { restored ->
            if (restored.finishLocked && restored.delivery == LiveGuidanceDelivery.DELIVERED &&
                replyAnchor != null
            ) {
                restored.archiveRecord(replyAnchor.first, replyAnchor.second)?.let(records::add)
            }
        }
    }.filter { item -> records.none { record -> record.id == item.id } }
    val archive = records.fold(current.liveGuidanceArchive) { entries, record ->
        entries.upsertArchivedGuidance(record)
    }
    val updated = current.withLiveGuidanceEntries(recovered).copy(liveGuidanceArchive = archive)
    return if (updated == current) this else copy(config = updated)
}

/** History placement: entries of one node render right after it, in archival order. */
fun ConversationConfig?.archivedGuidanceByNode(): Map<Uuid, List<ArchivedLiveGuidance>> =
    this?.liveGuidanceArchive.orEmpty().groupBy { it.nodeId }
