package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.FileEdit
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.components.richtext.HighlightCodeBlock
import org.koin.compose.koinInject

/** workspace_apply_patch：显示改了哪些文件，以及冲突时哪个 hunk 哪一行 */
object ApplyPatchToolUI : ToolUIRenderer {
    override val toolName: String = "workspace_apply_patch"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.FileEdit

    private fun files(context: ToolUIContext): List<JsonObject> =
        (context.content?.jsonObjectOrNull?.get("files") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            .orEmpty()

    private fun conflicts(context: ToolUIContext): List<JsonObject> =
        (context.content?.jsonObjectOrNull?.get("conflicts") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            .orEmpty()

    @Composable
    override fun title(context: ToolUIContext): String {
        val count = files(context).size
        val conflicts = conflicts(context).size
        return when {
            conflicts > 0 -> "补丁冲突：$conflicts 处，未写入"
            count > 0 -> "补丁已应用：$count 个文件"
            else -> "应用补丁"
        }
    }

    override fun hasSummary(context: ToolUIContext): Boolean =
        files(context).isNotEmpty() || conflicts(context).isNotEmpty()

    @Composable
    override fun Summary(context: ToolUIContext) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            files(context).take(3).forEach { file ->
                Text(
                    text = "${file.str("path").orEmpty().substringAfterLast('/')}  +${file.int("added")} -${file.int("removed")}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            conflicts(context).take(2).forEach { conflict ->
                Text(
                    text = "${conflict.str("path").orEmpty()}:${conflict.int("line")} ${conflict.str("reason").orEmpty()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("补丁结果", style = MaterialTheme.typography.titleMedium)
            files(context).forEach { file ->
                Text(
                    text = "${file.str("path").orEmpty()}  +${file.int("added")} -${file.int("removed")}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            conflicts(context).forEach { conflict ->
                Text(
                    text = buildString {
                        append(conflict.str("path").orEmpty())
                        append("  hunk ")
                        append(conflict.int("hunk"))
                        append("  line ")
                        append(conflict.int("line"))
                        append("\n")
                        append(conflict.str("reason").orEmpty())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            context.content?.jsonObjectOrNull?.get("hint")?.jsonPrimitive?.contentOrNull?.let { hint ->
                Text(hint, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** workspace_test：汇总通过/失败数量，失败用例点开看文件对应行 */
object WorkspaceTestToolUI : ToolUIRenderer {
    override val toolName: String = "workspace_test"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Folder01

    private fun diagnostics(context: ToolUIContext): List<JsonObject> =
        (context.content?.jsonObjectOrNull?.get("diagnostics") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            .orEmpty()

    @Composable
    override fun title(context: ToolUIContext): String {
        val content = context.content?.jsonObjectOrNull
        val failed = content?.get("failed")?.jsonPrimitive?.intOrNull ?: 0
        val passed = content?.get("passed")?.jsonPrimitive?.intOrNull ?: 0
        if (content == null) return "运行测试"
        return if (failed > 0) "测试失败 $failed / 通过 $passed" else "测试通过 $passed"
    }

    override fun hasSummary(context: ToolUIContext): Boolean = diagnostics(context).isNotEmpty()

    @Composable
    override fun Summary(context: ToolUIContext) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            diagnostics(context).take(2).forEach { diagnostic ->
                Text(
                    text = "${diagnostic.str("file").orEmpty()}:${diagnostic.int("line")}  ${diagnostic.str("message").orEmpty()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val repository: WorkspaceRepository = koinInject()
        // 工具入参里的 workspace 才是这次实际用的工作区，多工作区助手不能只按主工作区读
        val workspaceId = context.arguments.str("workspace")
            ?: context.assistant?.primaryWorkspaceId?.toString()
        val content = context.content?.jsonObjectOrNull
        var expanded by remember { mutableStateOf<JsonObject?>(null) }
        var snippet by remember { mutableStateOf<String?>(null) }
        var snippetLanguage by remember { mutableStateOf("kotlin") }

        LaunchedEffect(expanded) {
            val target = expanded ?: return@LaunchedEffect
            val path = target.str("file") ?: return@LaunchedEffect
            val line = target.int("line")
            snippet = null
            snippet = withContext(Dispatchers.IO) {
                if (workspaceId == null) return@withContext null
                runCatching {
                    val text = repository.readText(workspaceId, path)
                    val lines = text.lines()
                    val from = (line - 6).coerceAtLeast(1)
                    val to = (line + 6).coerceAtMost(lines.size)
                    if (from > to) return@runCatching null
                    lines.subList(from - 1, to).joinToString("\n")
                }.getOrNull()
            }
            snippetLanguage = syntaxLanguageOf(path)
        }

        Column(
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = buildString {
                    append("测试结果")
                    content?.get("framework")?.jsonPrimitive?.contentOrNull?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "通过 ${content?.get("passed")?.jsonPrimitive?.intOrNull ?: 0} · " +
                    "失败 ${content?.get("failed")?.jsonPrimitive?.intOrNull ?: 0} · " +
                    "跳过 ${content?.get("skipped")?.jsonPrimitive?.intOrNull ?: 0}",
                style = MaterialTheme.typography.bodySmall,
            )
            content?.get("command")?.jsonPrimitive?.contentOrNull?.let { command ->
                Text(
                    text = command,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            diagnostics(context).forEach { diagnostic ->
                val isExpanded = expanded === diagnostic
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = if (isExpanded) null else diagnostic },
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "${diagnostic.str("file").orEmpty()}:${diagnostic.int("line")}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        diagnostic.str("test")?.let { test ->
                            Text(test, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(diagnostic.str("message").orEmpty(), style = MaterialTheme.typography.bodySmall)
                        if (isExpanded) {
                            val code = snippet
                            if (code == null) {
                                Text("读取文件…", style = MaterialTheme.typography.labelSmall)
                            } else {
                                HighlightCodeBlock(
                                    code = code,
                                    language = snippetLanguage,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
            content?.get("output")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { output ->
                HighlightCodeBlock(
                    code = output,
                    language = "plaintext",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

private fun JsonElement?.str(key: String): String? =
    this?.jsonObjectOrNull?.get(key)?.jsonPrimitive?.contentOrNull

private fun JsonElement?.int(key: String): Int =
    this?.jsonObjectOrNull?.get(key)?.jsonPrimitive?.intOrNull ?: 0
