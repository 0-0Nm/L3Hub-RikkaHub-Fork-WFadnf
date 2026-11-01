package me.rerere.rikkahub.ui.components.message.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.JsonInstantPretty

/** 参数值在单列字段里的排布形态 */
enum class ToolArgValueShape {
    /** 单行值, 与参数名同行右对齐 */
    Text,

    /** 代码或多行文本, 等宽字体单独成块 */
    Code,

    /** 嵌套对象/数组, 单独成块, 展开后是格式化 JSON */
    Json,
}

/**
 * 工具入参里的一条参数
 *
 * @property value 值的完整文本, [ToolArgValueShape.Json] 时为格式化后的 JSON
 * @property language 语法高亮语言, 仅 [ToolArgValueShape.Code] 使用
 */
data class ToolArgField(
    val name: String,
    val value: String,
    val shape: ToolArgValueShape = ToolArgValueShape.Text,
    val language: String? = null,
) {
    val lineCount: Int get() = value.count { it == '\n' } + 1

    val isMultiline: Boolean get() = value.contains('\n')
}

/** 超长单行值需要另起一行, 而不是和参数名挤在一行 */
fun ToolArgField.needsOwnLine(): Boolean =
    shape == ToolArgValueShape.Text && value.length > ToolArgsPresentation.LONG_TEXT_MAX_CHARS

/** 折叠状态下是否有被藏起来的内容, 有则显示 "展开" 开关 */
fun ToolArgField.isCollapsible(maxLines: Int = ToolArgsPresentation.COLLAPSED_LINES): Boolean =
    when (shape) {
        ToolArgValueShape.Text -> value.length > ToolArgsPresentation.LONG_TEXT_MAX_CHARS
        else -> lineCount > maxLines
    }

/**
 * 工具入参的展示解析, 与 Compose 无关, 便于单测
 *
 * 入参是 JSON 对象时按 key 展开成单列字段; 其它情况 (纯文本命令、数组、非法 JSON、空对象)
 * 一律返回 null, 由调用方回落到原有展示, 保证解析失败不会让卡片空白.
 */
object ToolArgsPresentation {
    /** 多行内容默认展示的行数 */
    const val COLLAPSED_LINES: Int = 3

    /** 超过这个长度的单行值折叠成一行加省略号 */
    const val LONG_TEXT_MAX_CHARS: Int = 64

    fun parse(toolName: String, argsJson: String): List<ToolArgField>? {
        if (argsJson.isBlank()) return null
        val element = runCatching { JsonInstant.parseToJsonElement(argsJson) }.getOrNull() ?: return null
        return parse(toolName, element)
    }

    fun parse(toolName: String, arguments: JsonElement?): List<ToolArgField>? {
        val obj = arguments as? JsonObject ?: return null
        if (obj.isEmpty()) return null
        return obj.map { (name, value) -> obj.toField(toolName, name, value) }
    }

    /** 折叠状态展示的头部若干行, 供 UI 与单测共用 */
    fun previewOf(field: ToolArgField, maxLines: Int = COLLAPSED_LINES): String =
        if (field.lineCount <= maxLines) field.value else field.value.split('\n').take(maxLines).joinToString("\n")

    private fun JsonObject.toField(toolName: String, name: String, value: JsonElement): ToolArgField =
        when (value) {
            is JsonArray -> arrayField(name, value)
            is JsonObject -> ToolArgField(
                name = name,
                value = prettyJson(value),
                shape = ToolArgValueShape.Json,
            )

            else -> {
                val text = primitiveText(value)
                if (text.contains('\n')) {
                    ToolArgField(
                        name = name,
                        value = text,
                        shape = ToolArgValueShape.Code,
                        language = codeLanguageOf(toolName, this, name),
                    )
                } else {
                    ToolArgField(name = name, value = text)
                }
            }
        }

    // 纯量数组 (例如 memory_read 的 path 列表) 拼成一行更好读, 带对象的数组仍按 JSON 展示
    private fun arrayField(name: String, value: JsonArray): ToolArgField {
        val scalarOnly = value.isNotEmpty() && value.all { it !is JsonObject && it !is JsonArray }
        return if (scalarOnly) {
            ToolArgField(name = name, value = value.joinToString(", ") { primitiveText(it) })
        } else {
            ToolArgField(
                name = name,
                value = prettyJson(value),
                shape = ToolArgValueShape.Json,
            )
        }
    }

    private fun primitiveText(element: JsonElement): String =
        (element as? JsonPrimitive)?.contentOrNull ?: element.toString()

    private fun prettyJson(element: JsonElement): String =
        runCatching { JsonInstantPretty.encodeToString(element) }.getOrDefault(element.toString())
}

/** 会被当成文件正文展示的参数名 */
private val FILE_CONTENT_ARGS = setOf("text", "content", "old_str", "new_str", "old_text", "new_text")

/**
 * 参数值该用哪种语法高亮
 *
 * 内置工作区工具优先匹配: shell 的 command 是 bash, 补丁是 diff, 文件正文按路径扩展名推断.
 */
private fun codeLanguageOf(toolName: String, args: JsonObject, name: String): String? = when {
    name == "command" -> "bash"
    toolName == "workspace_shell" && name == "stdin" -> "plaintext"
    toolName == "workspace_apply_patch" && name == "patch" -> "diff"
    toolName.startsWith("memory_") && name in FILE_CONTENT_ARGS -> "markdown"
    toolName.startsWith("workspace_") && name in FILE_CONTENT_ARGS -> syntaxLanguageOf(args.pathOrNull())
    else -> null
}

private fun JsonObject.pathOrNull(): String? =
    (this["path"] as? JsonPrimitive)?.contentOrNull

/** 由文件扩展名推断语法高亮语言 */
internal fun syntaxLanguageOf(path: String?): String = when (
    path?.substringAfterLast('.', "")?.lowercase().orEmpty()
) {
    "kt", "kts" -> "kotlin"
    "java" -> "java"
    "js", "mjs", "cjs" -> "javascript"
    "ts" -> "typescript"
    "tsx" -> "tsx"
    "jsx" -> "jsx"
    "py" -> "python"
    "rb" -> "ruby"
    "go" -> "go"
    "rs" -> "rust"
    "c", "h" -> "c"
    "cpp", "cc", "cxx", "hpp", "hxx" -> "cpp"
    "cs" -> "csharp"
    "swift" -> "swift"
    "php" -> "php"
    "sh", "bash", "zsh" -> "bash"
    "json" -> "json"
    "xml" -> "xml"
    "html", "htm" -> "html"
    "css" -> "css"
    "scss" -> "scss"
    "yaml", "yml" -> "yaml"
    "toml" -> "toml"
    "md", "markdown" -> "markdown"
    "sql" -> "sql"
    "gradle" -> "groovy"
    else -> "plaintext"
}
