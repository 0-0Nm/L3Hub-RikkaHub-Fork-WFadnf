package me.rerere.rikkahub.utils

import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.bettertext.markdown.MarkdownInline
import me.rerere.bettertext.markdown.parseMarkdown

/**
 * 移除字符串中的 Markdown 格式，得到用于朗读的纯文本
 *
 * 复用渲染用的 [parseMarkdown]，哪些内容算 HTML 标签、链接或代码都和界面上显示的一致，
 * 不会把 `x<y && y>0` 这种正文当成标签删掉。代码、公式和图片不朗读。
 * 输出是已解码的纯文本，不要再对结果调用一次，否则 `&lt;tag&gt;` 解码出的文字会被当成标签。
 * @return 移除Markdown格式后的纯文本
 */
fun String.stripMarkdown(): String {
    return buildString { appendBlocks(parseMarkdown(this@stripMarkdown).blocks) }
        // 去掉代码等内容后会留下空行和连续空格
        .replace(Regex("[ \t]*\n\\s*"), "\n")
        .replace(Regex("[ \t]{2,}"), " ")
        .trim()
}

private fun StringBuilder.appendBlocks(blocks: List<MarkdownBlock>) {
    for (block in blocks) {
        when (block) {
            is MarkdownBlock.Paragraph -> appendInlines(block.content)
            is MarkdownBlock.Heading -> appendInlines(block.content)
            is MarkdownBlock.Quote -> appendBlocks(block.children)
            is MarkdownBlock.ListBlock -> block.items.forEach { appendBlocks(it.children) }
            is MarkdownBlock.Table -> (listOf(block.header) + block.rows).forEach { row ->
                row.forEach { cell ->
                    appendInlines(cell.content)
                    append(' ')
                }
                append('\n')
            }

            is MarkdownBlock.Details -> {
                appendInlines(block.summary)
                append('\n')
                appendBlocks(block.children)
            }

            is MarkdownBlock.Container -> appendBlocks(block.children)
            is MarkdownBlock.CodeBlock, MarkdownBlock.Divider -> Unit
        }
        append('\n')
    }
}

private fun StringBuilder.appendInlines(nodes: List<MarkdownInline>) {
    for (node in nodes) {
        when (node) {
            is MarkdownInline.Text -> append(node.text)
            is MarkdownInline.Styled -> appendInlines(node.children)
            is MarkdownInline.Link -> appendInlines(node.children)
            is MarkdownInline.Code, is MarkdownInline.Image, is MarkdownInline.Math -> Unit
        }
    }
}

fun String.extractThinkingTitle(): String? {
    // 按行分割文本
    val lines = this.lines()

    // 从后往前查找最后一个符合条件的加粗文本行
    for (i in lines.indices.reversed()) {
        val line = lines[i].trim()

        // 检查是否为加粗格式且独占一整行
        val boldPattern = Regex("^\\*\\*(.+?)\\*\\*$")
        val match = boldPattern.find(line)

        if (match != null) {
            // 返回加粗标记内的文本内容
            return match.groupValues[1].trim().takeUnless { it.isBlank() }
        }
    }

    return null
}

/** 思考卡标题最多显示多少字符，超出的部分截断并加省略号 */
const val THINKING_TITLE_MAX_CHARS = 24

/** 提取标题时只看思考内容的开头，流式输出时不必每帧重扫全文 */
const val THINKING_SUMMARY_WINDOW = 2000

/** 中英文句末标点；英文句点要求后面是空白或结尾，避开 `0.5`、`A.kt` 这类 */
private val THINKING_SENTENCE_END = Regex("""[。！？；…!?;]|\.(?=\s|$)""")

/** 列表、标题、引用的行首标记，可能叠加（`> - #`），要一层层剥 */
private val THINKING_PREFIX_MARK = Regex("""^\s*(#{1,6}\s+|>\s*|[-+*]\s+|\d+[.)]\s+)""")

/** 加粗、斜体、删除线和行内代码的标记，剥掉后只留文字 */
private val THINKING_INLINE_MARK = Regex("""\*\*|__|~~|[*`]""")

private val THINKING_TITLE_TRAILING = charArrayOf(' ', '\u3000', '，', ',', '、', '。', '；', ';', '：', ':', '—', '-')

/**
 * 取思考链的第一句当折叠卡片的标题。
 *
 * 行首的围栏代码块（``` / ~~~）整段跳过：思考里常在开头贴一段代码，
 * 拿它当标题没有意义。Markdown 标记（`**`、`#`、`-`）会剥掉，
 * 避免标题里露出星号；行内代码只去反引号，保留标识符本身。
 * 第一句超过 [maxChars] 个字符时截断并补省略号，保证标题只占一行。
 *
 * @return 可用的标题，没有可用正文（空串、只有空白/代码块）时返回 null
 */
fun String.extractThinkingSummary(maxChars: Int = THINKING_TITLE_MAX_CHARS): String? {
    var inFence = false
    for (line in lineSequence()) {
        if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
            inFence = !inFence
            continue
        }
        if (inFence) continue

        val candidate = line.stripThinkingMarks()
        // 只有标点的行（`---`、`***`）当不了标题，接着往下找
        if (candidate.isEmpty() || candidate.none { it.isLetterOrDigit() }) continue

        val sentenceEnd = THINKING_SENTENCE_END.find(candidate)?.range?.first ?: candidate.length
        val sentence = candidate.take(sentenceEnd).stripThinkingMarks()
        val title = if (sentence.isEmpty()) candidate else sentence
        return title.truncateThinkingTitle(maxChars)
    }
    return null
}

private fun String.stripThinkingMarks(): String {
    var text = trim()
    while (true) {
        val stripped = text.replace(THINKING_PREFIX_MARK, "")
        if (stripped == text) break
        text = stripped
    }
    return text.replace(THINKING_INLINE_MARK, "").trim()
}

private fun String.truncateThinkingTitle(maxChars: Int): String {
    val limit = maxChars.coerceAtLeast(1)
    val trimmed = trimEnd(*THINKING_TITLE_TRAILING)
    if (trimmed.length <= limit) return trimmed
    // 别把代理对（emoji）从中间劈开
    val cut = if (trimmed[limit - 1].isHighSurrogate()) limit - 1 else limit
    return trimmed.take(cut).trimEnd(*THINKING_TITLE_TRAILING) + "…"
}
