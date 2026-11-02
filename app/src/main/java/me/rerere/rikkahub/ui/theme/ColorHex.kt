package me.rerere.rikkahub.ui.theme

/**
 * 十六进制色号的解析与格式化，统一按 ARGB 处理。
 *
 * 取色器和调色板的「输入色号」都用这里，避免两处各写一套解析。
 * 只做纯计算，不碰 Android API，所以在 JVM 单测里能直接跑。
 */

/** 不透明黑，[withAlpha] 之外的缺省值。 */
private const val OPAQUE = 0xFF000000L

/**
 * 解析 `#RRGGBB` / `#AARRGGBB`，也接受三位简写 `#RGB` 和带 `0x` 前缀的写法。
 *
 * 没写 alpha 时按不透明处理。解析失败返回 null——调用方据此显示错误提示，不要静默当成黑色。
 */
fun parseHexColor(input: String): Long? {
    val text = input.trim()
        .removePrefix("#")
        .removePrefix("0x")
        .removePrefix("0X")
    val hex = when (text.length) {
        3 -> buildString { text.forEach { append(it).append(it) } }
        6, 8 -> text
        else -> return null
    }
    if (hex.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
    val value = hex.toLongOrNull(16) ?: return null
    return if (hex.length == 6) OPAQUE or value else value and 0xFFFFFFFFL
}

/**
 * 输出 `#RRGGBB`；alpha 不是不透明、或 [forceAlpha] 为 true 时输出 `#AARRGGBB`。
 *
 * 默认隐藏 alpha，是为了让常见的六个角色（都是不透明色）看起来和以前一样。
 */
fun formatHexColor(argb: Long, forceAlpha: Boolean = false): String {
    val value = argb and 0xFFFFFFFFL
    val alpha = alphaComponent(value)
    return if (alpha == 0xFF && !forceAlpha) {
        "#%06X".format(value and 0xFFFFFF)
    } else {
        "#%08X".format(value)
    }
}

/** 取 alpha 通道，0..255。 */
fun alphaComponent(argb: Long): Int = ((argb shr 24) and 0xFF).toInt()

/** 换掉 alpha 通道，保留 RGB。 */
fun withAlpha(argb: Long, alpha: Int): Long =
    (argb and 0x00FFFFFFL) or ((alpha.toLong() and 0xFFL) shl 24)
