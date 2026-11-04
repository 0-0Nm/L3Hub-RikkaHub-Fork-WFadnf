package me.rerere.rikkahub.data.model

/**
 * 会话工作目录的存法与展示。
 *
 * 库里存相对工作区文件区的路径（空 = 工作区根），展示和提示词里再拼回绝对路径。
 * 早期版本存的是绝对路径，这里一并兼容：能剥掉工作区前缀就剥；剥不掉的（换了远端目录后
 * 留下的旧绝对路径）按未设置处理，避免拼出 newDir/old/abs 这种路径。
 */
object WorkspaceCwd {
    fun normalize(workspaceDir: String, stored: String?): String? {
        val raw = stored?.replace('\\', '/')?.trim()?.trimEnd('/').orEmpty()
        if (raw.isEmpty() || raw == ".") return null
        val base = workspaceDir.trimEnd('/')
        if (base.isEmpty()) return raw.trim('/').ifEmpty { null }
        val relative = when {
            raw == base -> ""
            raw.startsWith("$base/") -> raw.removePrefix("$base/")
            raw.startsWith("/") -> return null
            else -> raw
        }.trim('/')
        return relative.ifEmpty { null }
    }

    fun absolute(workspaceDir: String, relative: String?): String {
        val base = workspaceDir.trimEnd('/').ifBlank { "/" }
        val path = relative?.replace('\\', '/')?.trim()?.trim('/').orEmpty()
        if (path.isEmpty()) return base
        return if (base == "/") "/$path" else "$base/$path"
    }
}
