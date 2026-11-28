package me.rerere.rikkahub.ui.pages.chat

/** 空白和 0 保留原手势的 300dp；正数避免过短误触，非法/负数不写入设置。 */
const val DEFAULT_PULL_NEW_CHAT_THRESHOLD_DP = 300
const val MIN_PULL_NEW_CHAT_THRESHOLD_DP = 48

fun effectivePullNewChatThresholdDp(configuredDp: Int): Int = when {
    configuredDp <= 0 -> DEFAULT_PULL_NEW_CHAT_THRESHOLD_DP
    configuredDp < MIN_PULL_NEW_CHAT_THRESHOLD_DP -> MIN_PULL_NEW_CHAT_THRESHOLD_DP
    else -> configuredDp
}

/** null 表示输入非法，调用方应保留旧设置；空白和 0 都显式映射到默认距离。 */
fun parsePullNewChatThresholdInput(input: String): Int? = when {
    input.isBlank() -> 0
    else -> input.toIntOrNull()?.takeIf { it >= 0 }
}
