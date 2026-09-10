@file:Suppress("CyclomaticComplexMethod")
// unified patch 分型分支天然多（hunk/+/ -/context/\/meta 五类），拆分反损可读性（DiffParser 先例）。

package com.yumiru11.githubapp.feature.repo

/**
 * 单行 diff 类型（L09）。
 */
enum class CommitDiffLineKind {
    /** hunk 头（@@ -a,b +c,d @@）或文件级元信息（diff --git / index / --- / +++） */
    HEADER,

    /** 新增行（+） */
    ADDED,

    /** 删除行（−） */
    REMOVED,

    /** 上下文行（空格前缀） */
    CONTEXT,

    /** "\ No newline at end of file" 标记 */
    NO_NEWLINE,
}

/**
 * unified diff 单行（含新旧行号；单侧行号在另一侧为 null）。
 */
data class CommitDiffLine(
    val kind: CommitDiffLineKind,
    val text: String,
    val oldNumber: Int? = null,
    val newNumber: Int? = null,
)

/**
 * unified patch 文本 → [CommitDiffLine] 列表的原生解析器（L09）。
 *
 * ⚠️ 与 feature:pullrequest 的 `DiffParser` 逻辑同源但**刻意不共享**：Konsist 禁止
 * feature 之间互相 import，而该解析器只有 ~50 行纯逻辑，抽到 core 层需要新增模块
 * 或挤进 core:ui（UI 层不该承载文本解析）。取舍见 PR 说明。
 *
 * 解析策略：hunk 头（`@@ -a,b +c,d @@`）驱动新旧行号推进（比逐行计数稳），
 * 畸形输入（缺 hunk 头）退化为按行类型处理，不崩溃。
 */
object CommitDiffParser {
    private val HUNK_HEADER = Regex("""^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@.*$""")
    private const val CARRIAGE_RETURN = '\r'

    /** patch 为 null/空白（二进制或过大文件）→ 空列表。 */
    fun parse(patch: String?): List<CommitDiffLine> {
        if (patch.isNullOrBlank()) return emptyList()
        val raw = patch.split("\n")
        val lines = ArrayList<CommitDiffLine>(raw.size)
        var oldNumber: Int? = null
        var newNumber: Int? = null
        // hunk 之前的 "--- a/x" / "+++ b/x" 是文件头（元信息），hunk 之内则是普通增删行
        var inHunk = false
        for (line in raw) {
            val text = line.removeSuffix(CARRIAGE_RETURN.toString())
            when {
                text.startsWith("@@") -> {
                    val match = HUNK_HEADER.matchEntire(text)
                    if (match != null) {
                        oldNumber = match.groupValues[1].toIntOrNull()
                        newNumber = match.groupValues[2].toIntOrNull()
                        inHunk = true
                    }
                    lines += CommitDiffLine(kind = CommitDiffLineKind.HEADER, text = text)
                }

                !inHunk && (text.startsWith("--- ") || text.startsWith("+++ ")) -> {
                    lines += CommitDiffLine(kind = CommitDiffLineKind.HEADER, text = text)
                }

                text.startsWith("+") -> {
                    lines += CommitDiffLine(kind = CommitDiffLineKind.ADDED, text = text.substring(1), newNumber = newNumber)
                    newNumber = newNumber?.plus(1)
                }

                text.startsWith("-") -> {
                    lines += CommitDiffLine(kind = CommitDiffLineKind.REMOVED, text = text.substring(1), oldNumber = oldNumber)
                    oldNumber = oldNumber?.plus(1)
                }

                text.startsWith("\\") -> {
                    lines += CommitDiffLine(kind = CommitDiffLineKind.NO_NEWLINE, text = text)
                }

                text.startsWith(" ") -> {
                    lines +=
                        CommitDiffLine(
                            kind = CommitDiffLineKind.CONTEXT,
                            text = text.substring(1),
                            oldNumber = oldNumber,
                            newNumber = newNumber,
                        )
                    oldNumber = oldNumber?.plus(1)
                    newNumber = newNumber?.plus(1)
                }

                else -> {
                    lines += CommitDiffLine(kind = CommitDiffLineKind.HEADER, text = text)
                }
            }
        }
        return lines
    }
}
