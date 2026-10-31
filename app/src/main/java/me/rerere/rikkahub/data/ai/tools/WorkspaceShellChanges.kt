package me.rerere.rikkahub.data.ai.tools

import kotlin.uuid.Uuid

/** 扫描结果里的单条路径；[created] 为 null 表示服务器没给出创建时间，不知道是不是本轮新建的 */
internal data class ShellChangedPath(val path: String, val created: Boolean?)

/** 读回扫描结果的超时，正常只是一次 cat */
internal const val SHELL_SCAN_TIMEOUT_MILLIS = 10_000L

/** 清单最多回传多少条，避免一次命令改了几万个文件时把工具结果撑爆 */
internal const val SHELL_CHANGE_MAX_ENTRIES = 200

/**
 * 只能用 shell 改文件的工作区（SSH）没有文件快照，也就没法撤销。
 * 为了让聊天页仍然能列出「本轮改过哪些文件」，命令前后各加一步：
 * 先在工作区之外放一个标记文件，命令跑完后找出比标记新的文件，清单写到临时文件里再读回来。
 *
 * 这里只关心路径，不碰内容；扫描失败就当没有改动，不影响命令本身的结果。
 */
internal class ShellChangeScan private constructor(
    private val workspaceDir: String,
    private val markPath: String,
    private val listPath: String,
) {
    /** 把用户命令夹在「放标记」和「扫描」之间；退出码和 stdout/stderr 保持用户命令原样 */
    fun wrap(command: String): String {
        if (command.isBlank()) return command
        return buildString {
            appendLine("__l3hub_mark=${markPath.shellQuoted()}")
            appendLine("__l3hub_list=${listPath.shellQuoted()}")
            appendLine("__l3hub_ws=${workspaceDir.shellQuoted()}")
            appendLine(": > \"\$__l3hub_mark\" 2>/dev/null || exit 1")
            // 标记往前挪 1 秒：粗粒度文件系统上同一秒内的修改否则会被 -newer 漏掉
            appendLine("touch -d \"@\$((\$(date +%s) - 1))\" \"\$__l3hub_mark\" 2>/dev/null || true")
            appendLine("(")
            append(command)
            if (!command.endsWith("\n")) appendLine()
            appendLine(")")
            appendLine("__l3hub_rc=\$?")
            appendLine(SCAN_SCRIPT)
            appendLine("rm -f \"\$__l3hub_mark\" 2>/dev/null")
            appendLine("exit \$__l3hub_rc")
        }
    }

    /** 读回清单并清理临时文件；[run] 负责真正执行一条远程命令，返回它的 stdout */
    suspend fun collect(run: suspend (String) -> String?): List<ShellChangedPath> {
        val listed = listPath.shellQuoted()
        val output = run("cat $listed 2>/dev/null; rm -f $listed") ?: return emptyList()
        return parseShellChangeScan(output)
    }

    companion object {
        fun create(workspaceDir: String, token: String = Uuid.random().toString()): ShellChangeScan =
            ShellChangeScan(
                workspaceDir = workspaceDir,
                markPath = "/tmp/l3hub-turn-$token.mark",
                listPath = "/tmp/l3hub-turn-$token.list",
            )
    }
}

/**
 * 扫描脚本：第一行是标记文件的时间戳，后面每行一个文件。
 *
 * `stat -c %W` 是创建时间，服务器不支持时拿不到，就把创建时间留 0 交给上层当「不确定」；
 * `stat -c` 整个不支持（如 BSD/macOS）时两列都是 0，只影响「是否新建」的判断，路径照样能列出来。
 */
private val SCAN_SCRIPT: String = """
__l3hub_mark_y=${'$'}(stat -c %Y "${'$'}__l3hub_mark" 2>/dev/null || echo 0)
{
  printf 'mark\t%s\n' "${'$'}__l3hub_mark_y"
  cd "${'$'}__l3hub_ws" 2>/dev/null && find . \( -name .git -o -name node_modules -o -name build -o -name dist -o -name out -o -name target -o -name __pycache__ -o -name .venv -o -name venv -o -name .dart_tool -o -name .next -o -name coverage \) -prune -o -type f -newer "${'$'}__l3hub_mark" -print 2>/dev/null | head -n ${SHELL_CHANGE_MAX_ENTRIES} | while IFS= read -r __l3hub_f; do
    set -- ${'$'}(stat -c '%W %Y' "${'$'}__l3hub_f" 2>/dev/null)
    printf 'file\t%s\t%s\t%s\n' "${'$'}{1:-0}" "${'$'}{2:-0}" "${'$'}PWD/${'$'}{__l3hub_f#./}"
  done
} > "${'$'}__l3hub_list" 2>/dev/null
""".trim()

/** 解析扫描输出；坏行直接跳过，宁可少列几个文件也不要让工具结果出错 */
internal fun parseShellChangeScan(output: String): List<ShellChangedPath> {
    var markEpoch = 0L
    val files = mutableListOf<ShellChangedPath>()
    output.lineSequence().forEach { line ->
        val columns = line.split('\t')
        when {
            columns.size >= 2 && columns[0] == "mark" -> {
                markEpoch = columns[1].trim().toLongOrNull() ?: 0L
            }

            columns.size >= 4 && columns[0] == "file" -> {
                val born = columns[1].trim().toLongOrNull() ?: 0L
                val path = columns.drop(3).joinToString("\t").trim()
                if (path.isNotEmpty()) {
                    files += ShellChangedPath(
                        path = path,
                        // 创建时间只精确到秒，留 1 秒余量；拿不到就不猜
                        created = if (born > 0 && markEpoch > 0) born + 1 >= markEpoch else null,
                    )
                }
            }
        }
    }
    return files.distinctBy { it.path }
}

private fun String.shellQuoted(): String = "'" + replace("'", "'\"'\"'") + "'"
