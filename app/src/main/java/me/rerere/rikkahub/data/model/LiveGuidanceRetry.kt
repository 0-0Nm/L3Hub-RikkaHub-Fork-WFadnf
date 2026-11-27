package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import kotlin.uuid.Uuid

/** Existing regenerate targets the trailing user turn if generation failed before any assistant reply. */
fun Conversation.liveGuidanceRetryTarget(): Uuid? = currentMessages.lastOrNull { message ->
    message.role == MessageRole.ASSISTANT ||
        (message.role == MessageRole.USER && !message.isContextCheckpoint)
}?.id
