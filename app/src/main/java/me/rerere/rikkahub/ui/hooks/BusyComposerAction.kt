package me.rerere.rikkahub.ui.hooks

/** UI routing only. Submission, acknowledgement and draft ownership stay with E345 authorities. */
internal enum class BusyComposerAction { ORDINARY, TEMPORARY, CHOOSER }

internal fun busyComposerAction(
    busy: Boolean,
    hasText: Boolean,
    hasAttachments: Boolean,
    editing: Boolean,
    longPress: Boolean = false,
): BusyComposerAction = when {
    !busy || editing || (!hasText && !hasAttachments) -> BusyComposerAction.ORDINARY
    longPress || !hasText -> BusyComposerAction.CHOOSER
    else -> BusyComposerAction.TEMPORARY
}
