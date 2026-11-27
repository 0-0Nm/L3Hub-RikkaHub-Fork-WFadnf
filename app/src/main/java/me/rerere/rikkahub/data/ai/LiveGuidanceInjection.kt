package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.ArchivedLiveGuidance
import me.rerere.rikkahub.data.model.ConversationConfig
import me.rerere.rikkahub.data.model.LiveGuidance
import me.rerere.rikkahub.data.model.LiveGuidanceMode
import me.rerere.rikkahub.data.model.liveGuidanceEntries
import kotlin.uuid.Uuid

private const val LIVE_GUIDANCE_FRAME =
    "Live steering from the user; apply it to the current work without treating it as a new turn:\n"

/**
 * Snapshot eligible steering as request-only USER text. The stable ID identifies this card when
 * provider output arrives; building the message does not acknowledge delivery.
 */
fun LiveGuidance.requestMessage(runId: Uuid): UIMessage? =
    takeIf { shouldInject(runId) }?.let { steeringRequestMessage(id, content) }

/** Same request-only frame for an archived note; no history message is ever created. */
fun ArchivedLiveGuidance.requestMessage(): UIMessage = steeringRequestMessage(id, content)

private fun steeringRequestMessage(id: Uuid, content: String): UIMessage =
    UIMessage.user(LIVE_GUIDANCE_FRAME + content).copy(id = id, isSynthetic = true)

/**
 * Request snapshot for one run: the active card wins while it is eligible; otherwise the latest
 * persistent archived note keeps steering THIS conversation. Temporary archives stay UI-only.
 */
fun ConversationConfig.liveGuidanceRequestMessage(runId: Uuid): UIMessage? =
    liveGuidanceEntries().firstNotNullOfOrNull { it.requestMessage(runId) }
        ?: liveGuidanceArchive.lastOrNull { it.mode == LiveGuidanceMode.PERSISTENT }?.requestMessage()

/** All eligible notes in stable order; archived persistence remains scoped to this config. */
fun ConversationConfig.liveGuidanceRequestMessages(runId: Uuid): List<UIMessage> =
    (liveGuidanceArchive.filter { it.mode == LiveGuidanceMode.PERSISTENT }.map { it.requestMessage() } +
        liveGuidanceEntries().mapNotNull { it.requestMessage(runId) })
        .distinctBy { it.id }

/** Request-scoped only: a replacement never leaves the previous synthetic message in this request. */
internal fun List<UIMessage>.withRequestInjections(injected: List<UIMessage>): List<UIMessage> =
    this + injected.distinctBy { it.id }
