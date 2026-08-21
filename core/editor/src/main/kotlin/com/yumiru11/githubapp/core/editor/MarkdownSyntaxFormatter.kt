package com.yumiru11.githubapp.core.editor

/**
 * Markdown 编辑器工具栏动作（plan.md §7.1）。
 *
 * 每个动作对应一个工具栏按钮；[MarkdownSyntaxFormatter.apply] 负责把动作
 * 应用到「文本 + 选区」上（插入/包裹语法），纯函数、无 Android 依赖，可 JVM 单测。
 */
enum class MarkdownToolbarAction {
    /** 加粗 `**text**` */
    BOLD,

    /** 斜体 `*text*` */
    ITALIC,

    /** 行内代码 `` `text` `` */
    INLINE_CODE,

    /** 代码块（``` 围栏） */
    CODE_BLOCK,

    /** 标题（行首 `# `） */
    HEADING,

    /** 无序列表（行首 `- `） */
    UNORDERED_LIST,

    /** 有序列表（行首 `1. `） */
    ORDERED_LIST,

    /** 任务列表（行首 `- [ ] `） */
    TASK_LIST,

    /** 链接 `[text](url)` */
    LINK,

    /** 图片 `![alt](url)` */
    IMAGE,

    /** 引用（行首 `> `） */
    QUOTE,
}

/**
 * 语法编辑结果：新文本 + 新选区（光标位置）。
 *
 * 工具栏操作后光标/选区落点：
 * - 包裹类（加粗/斜体/行内码）：有选区 → 选区保留在内容上；无选区 → 光标落在标记中间
 * - 链接/图片：有选区 → 选中 url 占位符；无选区 → 光标落在 url 占位符
 * - 代码块：有选区 → 光标在围栏后；无选区 → 光标在围栏中间
 * - 行前缀类（标题/列表/引用）：选区保留在内容上（随前缀位移）
 */
data class SyntaxEditResult(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

/**
 * Markdown 工具栏语法应用器（plan.md §7.1）。
 *
 * 纯函数：输入（动作, 全文, 选区起止）→ 输出（新全文, 新选区）。
 * 不接触编辑器实例，便于对每种语法包裹场景做穷尽单测。
 */
object MarkdownSyntaxFormatter {
    private const val LINK_URL_PLACEHOLDER = "url"
    private const val IMAGE_URL_PLACEHOLDER = "url"

    /**
     * 应用工具栏动作。
     *
     * @param action 工具栏动作
     * @param text 当前全文
     * @param selectionStart 选区起点（绝对索引，含）
     * @param selectionEnd 选区终点（绝对索引，不含；等于起点 = 无选区）
     */
    fun apply(
        action: MarkdownToolbarAction,
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
    ): SyntaxEditResult {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(start, text.length)
        return when (action) {
            MarkdownToolbarAction.BOLD -> wrap(text, start, end, "**", "**")
            MarkdownToolbarAction.ITALIC -> wrap(text, start, end, "*", "*")
            MarkdownToolbarAction.INLINE_CODE -> wrap(text, start, end, "`", "`")
            MarkdownToolbarAction.CODE_BLOCK -> applyCodeBlock(text, start, end)
            MarkdownToolbarAction.HEADING -> applyLinePrefix(text, start, end, "# ")
            MarkdownToolbarAction.UNORDERED_LIST -> applyLinePrefix(text, start, end, "- ")
            MarkdownToolbarAction.ORDERED_LIST -> applyLinePrefix(text, start, end, "1. ")
            MarkdownToolbarAction.TASK_LIST -> applyLinePrefix(text, start, end, "- [ ] ")
            MarkdownToolbarAction.LINK -> applyLink(text, start, end, image = false)
            MarkdownToolbarAction.IMAGE -> applyLink(text, start, end, image = true)
            MarkdownToolbarAction.QUOTE -> applyLinePrefix(text, start, end, "> ")
        }
    }

    /** 包裹类动作：`before + 选区 + after`；无选区时光标落在标记中间。 */
    private fun wrap(
        text: String,
        start: Int,
        end: Int,
        before: String,
        after: String,
    ): SyntaxEditResult {
        val selected = text.substring(start, end)
        val newText = text.substring(0, start) + before + selected + after + text.substring(end)
        return if (selected.isEmpty()) {
            val cursor = start + before.length
            SyntaxEditResult(newText, cursor, cursor)
        } else {
            SyntaxEditResult(newText, start + before.length, end + before.length)
        }
    }

    /** 代码块：``` 围栏包裹；无选区时光标落在围栏中间。 */
    private fun applyCodeBlock(
        text: String,
        start: Int,
        end: Int,
    ): SyntaxEditResult {
        val selected = text.substring(start, end)
        val fence = "```"
        val newText =
            if (selected.isEmpty()) {
                text.substring(0, start) + "$fence\n\n$fence" + text.substring(end)
            } else {
                text.substring(0, start) + "$fence\n$selected\n$fence" + text.substring(end)
            }
        return if (selected.isEmpty()) {
            // 无选区：光标落在第一个围栏后的空行（用户可直接输入代码）
            val cursor = start + fence.length + 1
            SyntaxEditResult(newText, cursor, cursor)
        } else {
            // 有选区：光标落在整个代码块之后（便于继续输入）
            val cursor = start + fence.length + 1 + selected.length + 1 + fence.length
            SyntaxEditResult(newText, cursor, cursor)
        }
    }

    /** 链接/图片：`[text](url)` / `![alt](url)`；url 占位符被选中（无选区时光标落在其中）。 */
    private fun applyLink(
        text: String,
        start: Int,
        end: Int,
        image: Boolean,
    ): SyntaxEditResult {
        val selected = text.substring(start, end)
        val prefix = if (image) "![" else "["
        val placeholder = if (image) IMAGE_URL_PLACEHOLDER else LINK_URL_PLACEHOLDER
        val newText =
            text.substring(0, start) + prefix + selected + "](" + placeholder + ")" + text.substring(end)
        val urlStart = start + prefix.length + selected.length + 2
        val urlEnd = urlStart + placeholder.length
        return SyntaxEditResult(newText, urlStart, urlEnd)
    }

    /**
     * 行前缀类动作（标题/列表/引用）：对选区覆盖的每一行加前缀。
     *
     * - 无选区 → 只作用于光标所在行
     * - 有选区 → 作用于选区覆盖的所有行（含首尾行）
     * - 新选区：保留在内容上（起点随首行前缀位移，终点随总前缀位移）
     */
    private fun applyLinePrefix(
        text: String,
        start: Int,
        end: Int,
        prefix: String,
    ): SyntaxEditResult {
        val startLine = lineIndexOf(text, start)
        val endLine = if (end > start) lineIndexOf(text, end - 1) else startLine
        val lines = text.split("\n")
        val newLines = lines.mapIndexed { index, line -> if (index in startLine..endLine) prefix + line else line }
        val newText = newLines.joinToString("\n")
        val added = prefix.length * (endLine - startLine + 1)
        return SyntaxEditResult(newText, start + prefix.length, end + added)
    }

    /** 绝对索引 → 所在行号（0 起）。 */
    private fun lineIndexOf(
        text: String,
        index: Int,
    ): Int = text.take(index.coerceIn(0, text.length)).count { it == '\n' }
}
