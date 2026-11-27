package me.rerere.rikkahub.ui.hooks

import me.rerere.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

/** UI-only immutable submission snapshot; never used to overwrite conversation/service state. */
data class ComposerSendSnapshot(
    val conversationId: Uuid,
    val text: String,
    // TextFieldState exposes an immutable text version. Identity is deliberately conservative:
    // even an edit followed by restoring the old characters must not clear a newer draft.
    internal val textVersion: CharSequence,
    val parts: List<UIMessagePart>,
    val attachments: List<UIMessagePart>,
    internal val attachmentVersion: List<UIMessagePart>,
    val editingMessage: Uuid?,
    val reasoning: String?,
    internal val reasoningVersion: CharSequence?,
) {
    val canGuide: Boolean get() = text.isNotBlank() && editingMessage == null
}
