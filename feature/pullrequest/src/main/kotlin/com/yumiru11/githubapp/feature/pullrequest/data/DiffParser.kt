@file:Suppress("CyclomaticComplexMethod") // unified patch 分型分支天然多（hunk/+/ -/context/\/meta 五类），拆分反损可读性（T3 先例）

package com.yumiru11.githubapp.feature.pullrequest.data

import com.yumiru11.githubapp.feature.pullrequest.model.DiffLine
import com.yumiru11.githubapp.feature.pullrequest.model.DiffLineKind
import com.yumiru11.githubapp.feature.pullrequest.model.DiffSideRow

/**
 * unified patch 文本 → [DiffLine] 列表的原生解析器（T16 自研轻量 diff）。
 *
 * - 解析 hunk 头（@@ -a,b +c,d @@）并据此推进新旧行号（比逐行计数更稳）；
 * - 分型：+ / - / 空格（context）/ \\ No newline at end of file / 其余元信息行；
 * - 畸形输入（缺 hunk 头/行号不可推）不崩溃：按行类型退化推进。
 */
object DiffParser {
    private val HUNK_HEADER = Regex("""^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@.*$""")

    fun parse(patch: String?): List<DiffLine> {
        if (patch.isNullOrBlank()) return emptyList()
        val lines = patch.split("\n")
        val result = ArrayList<DiffLine>(lines.size)
        var oldNumber: Int? = null
        var newNumber: Int? = null
        for (raw in lines) {
            val text = raw.let { if (it.isNotEmpty() && it.last() == '\r') it.dropLast(1) else it }
            when {
                text.startsWith("@@") -> {
                    HUNK_HEADER.matchEntire(text)?.let { match ->
                        oldNumber = match.groupValues[1].toIntOrNull()
                        newNumber = match.groupValues[2].toIntOrNull()
                        result += DiffLine(kind = DiffLineKind.HEADER, text = text)
                    } ?: run {
                        // 形如 "@@" 但缺行号（畸形）→ 按元信息行处理
                        result += DiffLine(kind = DiffLineKind.HEADER, text = text)
                    }
                }

                text.startsWith("+") -> {
                    result += DiffLine(oldNumber = null, newNumber = newNumber, kind = DiffLineKind.ADDED, text = text.substring(1))
                    newNumber = newNumber?.plus(1)
                }

                text.startsWith("-") -> {
                    result += DiffLine(oldNumber = oldNumber, newNumber = null, kind = DiffLineKind.REMOVED, text = text.substring(1))
                    oldNumber = oldNumber?.plus(1)
                }

                text.startsWith("\\") -> {
                    result += DiffLine(kind = DiffLineKind.NO_NEWLINE, text = text)
                }

                text.startsWith(" ") -> {
                    result += DiffLine(oldNumber = oldNumber, newNumber = newNumber, kind = DiffLineKind.CONTEXT, text = text.substring(1))
                    oldNumber = oldNumber?.plus(1)
                    newNumber = newNumber?.plus(1)
                }

                else -> {
                    // diff --git / index / --- / +++ / Binary files ... 元信息行
                    result += DiffLine(kind = DiffLineKind.HEADER, text = text)
                }
            }
        }
        return result
    }

    /**
     * unified [lines] → side-by-side 行。同 hunk 内连续 REMOVED/ADDED 按序配对，
     * 其余单侧展示；HEADER/NO_NEWLINE 双栏占位。
     */
    fun toSideRows(lines: List<DiffLine>): List<DiffSideRow> {
        val rows = ArrayList<DiffSideRow>(lines.size)
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when (line.kind) {
                DiffLineKind.HEADER,
                DiffLineKind.NO_NEWLINE,
                -> {
                    rows += DiffSideRow(old = line, new = null)
                    i++
                }

                DiffLineKind.REMOVED -> {
                    val next = lines.getOrNull(i + 1)
                    if (next?.kind == DiffLineKind.ADDED) {
                        rows += DiffSideRow(old = line, new = next)
                        i += 2
                    } else {
                        rows += DiffSideRow(old = line, new = null)
                        i++
                    }
                }

                DiffLineKind.ADDED -> {
                    val next = lines.getOrNull(i + 1)
                    if (next?.kind == DiffLineKind.REMOVED) {
                        rows += DiffSideRow(old = next, new = line)
                        i += 2
                    } else {
                        rows += DiffSideRow(old = null, new = line)
                        i++
                    }
                }

                DiffLineKind.CONTEXT -> {
                    rows += DiffSideRow(old = line, new = line)
                    i++
                }
            }
        }
        return rows
    }
}
