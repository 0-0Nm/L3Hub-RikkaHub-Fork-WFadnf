package me.rerere.rikkahub.service

/** Why a model run was opened; only actual user turns consume next-message steering implicitly. */
internal enum class GenerationRunOrigin {
    USER_MESSAGE,
    NOTICE,
    REGENERATION,
    LIVE_GUIDANCE_RETRY,
    TOOL_APPROVAL,
}

internal fun GenerationRunOrigin.consumesNextMessageGuidance(): Boolean = when (this) {
    GenerationRunOrigin.USER_MESSAGE,
    GenerationRunOrigin.LIVE_GUIDANCE_RETRY,
    -> true

    GenerationRunOrigin.NOTICE,
    GenerationRunOrigin.REGENERATION,
    GenerationRunOrigin.TOOL_APPROVAL,
    -> false
}
