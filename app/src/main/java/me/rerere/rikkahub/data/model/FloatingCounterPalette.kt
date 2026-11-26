package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

/** The five RGB stops of the floating subtask numeral, not the task-state indicator. */
@Serializable
data class FloatingCounterPalette(
    val at0: String = "#D0C7B5",
    val at25: String = "#AD9566",
    val at50: String = "#75846C",
    val at75: String = "#88758A",
    val at100: String = "#A4655D",
) {
    private fun rawAt(stop: Int): String = when (stop) {
        0 -> at0
        25 -> at25
        50 -> at50
        75 -> at75
        100 -> at100
        else -> error("Not a counter color stop: $stop")
    }

    /** A malformed imported string falls back for this stop only. */
    fun hexAt(stop: Int): String = normalizeHex(rawAt(stop)) ?: Default.rawAt(stop)

    /** Incomplete drafts stay in the editor, never in the saved palette. */
    fun withHex(stop: Int, input: String): FloatingCounterPalette {
        val hex = normalizeHex(input) ?: return this
        return when (stop) {
            0 -> copy(at0 = hex)
            25 -> copy(at25 = hex)
            50 -> copy(at50 = hex)
            75 -> copy(at75 = hex)
            100 -> copy(at100 = hex)
            else -> error("Not a counter color stop: $stop")
        }
    }

    companion object {
        val Default = FloatingCounterPalette()
        val stops = 0..100 step 25
        private val hexPattern = Regex("#?[0-9a-fA-F]{6}")

        fun normalizeHex(input: String): String? = if (hexPattern.matches(input)) {
            "#" + input.removePrefix("#").uppercase()
        } else null
    }
}
