package me.rerere.rikkahub.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import me.rerere.rikkahub.data.datastore.ChatFontFamily
import me.rerere.rikkahub.data.datastore.DisplaySetting
import java.io.File

val LocalChatFontFamily = staticCompositionLocalOf<FontFamily?> { null }

@Composable
fun ChatFontProvider(
    displaySetting: DisplaySetting,
    content: @Composable () -> Unit,
) {
    val configured = rememberChatFontFamily(displaySetting)
    val chatFontFamily = if (displaySetting.terminalStyle) FontFamily.Monospace else configured
    CompositionLocalProvider(LocalChatFontFamily provides chatFontFamily) {
        content()
    }
}

@Composable
fun rememberChatFontFamily(displaySetting: DisplaySetting): FontFamily {
    val context = LocalContext.current
    return remember(
        displaySetting.chatFontFamily,
        displaySetting.chatCustomFontPath,
        displaySetting.chatCustomFontPaths,
    ) {
        displaySetting.resolveChatFontFamily(context)
    }
}

fun DisplaySetting.resolveChatFontFamily(context: Context): FontFamily = when (chatFontFamily) {
    ChatFontFamily.DEFAULT -> FontFamily.Default
    ChatFontFamily.SERIF -> FontFamily.Serif
    ChatFontFamily.MONOSPACE -> FontFamily.Monospace
    ChatFontFamily.CUSTOM -> loadCustomFontFamilyChain(context) ?: FontFamily.Default
}

/**
 * 加载自定义字体回退链：优先用 [chatCustomFontPaths]（多字体，有序）；
 * 旧配置只有 [chatCustomFontPath] 时退化为单字体。返回的 FontFamily 按顺序
 * 组成字形回退链——缺失的字形自动顺延到下一款，实现中/英/俄等多脚本混用。
 */
private fun DisplaySetting.loadCustomFontFamilyChain(context: Context): FontFamily? {
    val paths = if (chatCustomFontPaths.isNotEmpty()) {
        chatCustomFontPaths
    } else {
        listOf(chatCustomFontPath).filter { it.isNotBlank() }
    }
    val files = paths.mapNotNull { resolveFilesDirFile(context, it)?.takeIf(File::isFile) }
    if (files.isEmpty()) return null
    // 逐款校验可加载（createFromFile 对无效文件抛异常）：一款坏字体只丢弃自己，不拖垮整条链
    val fonts = files.mapNotNull { file ->
        runCatching {
            Typeface.createFromFile(file)
            Font(file)
        }.getOrNull()
    }
    if (fonts.isEmpty()) return null
    // 用 vararg 构造（List<Font> 重载在不同 Compose 版本可用性不一，vararg 更稳）
    return FontFamily(*fonts.toTypedArray())
}

private fun resolveFilesDirFile(context: Context, relativePath: String): File? {
    if (relativePath.isBlank()) return null
    val filesDir = runCatching { context.filesDir.canonicalFile }.getOrNull() ?: return null
    val file = runCatching { File(filesDir, relativePath).canonicalFile }.getOrNull() ?: return null
    return file.takeIf { it.path.startsWith("${filesDir.path}${File.separator}") }
}
