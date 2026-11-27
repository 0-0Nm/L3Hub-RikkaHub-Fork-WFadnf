package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.LiveGuidance
import kotlin.uuid.Uuid

private const val LIVE_GUIDANCE_FRAME =
    "Live steering from the user; apply it to the current work without treating it as a new turn:\n"

/**
 * Snapshot eligible steering as request-only USER text. The stable ID identifies this card when
 * provider output arrives; building the message does not acknowledge delivery.
 */
fun LiveGuidance.requestMessage(runId: Uuid): UIMessage? =
    takeIf { shouldInject(runId) }?.let {
        UIMessage.user(LIVE_GUIDANCE_FRAME + content).copy(id = id, isSynthetic = true)
    }

/** Request-scoped only: a replacement never leaves the previous synthetic message in this request. */
internal fun List<UIMessage>.withRequestInjections(injected: List<UIMessage>): List<UIMessage> =
    this + injected.distinctBy { it.id }
