package com.yumiru11.githubapp.core.markdown.fixture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「一致性报告 ←→ 夹具目录」的对齐守卫。
 *
 * ## 为什么把文档也纳入测试
 *
 * `docs/agents/markdown-consistency-2026-09-11.md` 是本票对用户最有价值的产物——它把
 * 「与网页端一致」变成一张可核查的表。但**散文会腐烂**：夹具改了、路径支持变了、某条
 * §2.3 实现了，表格却还写着旧结论，报告就会从「可信的现状」退化成「过期的宣传」。
 *
 * 因此本类解析报告中 `BEGIN/END FIXTURE TABLE` 标记之间的表格（标记之外的散文不受约束），
 * 逐行与 [MarkdownGfmFixtures.ALL] 比对。**改夹具不改报告 → 测试红。**
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class MarkdownConsistencyReportTest {
    private val reportFile = File("../../docs/agents/markdown-consistency-2026-09-11.md")

    @Test
    fun reportFile_existsAtDocumentedPath_guardAgainstSilentNoop() {
        assertTrue(
            "一致性报告必须存在（路径漂移会让本类静默空转）: ${reportFile.absolutePath}",
            reportFile.isFile,
        )
    }

    @Test
    fun reportFixtureTable_rowsMatchCatalog_oneToOne() {
        val rows = parseFixtureTable()

        assertEquals("报告表格行数必须等于夹具数", MarkdownGfmFixtures.ALL.size, rows.size)
        assertEquals(
            "报告表格的夹具 id 与顺序必须与目录一致",
            MarkdownGfmFixtures.ALL.map { it.id },
            rows.map { it.id },
        )
    }

    @Test
    fun reportFixtureTable_clausesMatchCatalog() {
        val mismatches =
            parseFixtureTable().mapNotNull { row ->
                val fixture = MarkdownGfmFixtures.ALL.first { it.id == row.id }
                if (fixture.clause == row.clause) null else "${row.id}: 报告「${row.clause}」≠ 目录「${fixture.clause}」"
            }

        assertTrue("§2.3 条目原文必须与目录逐字一致:\n${mismatches.joinToString("\n")}", mismatches.isEmpty())
    }

    @Test
    fun reportFixtureTable_renderPathMarksMatchCatalog() {
        val mismatches =
            parseFixtureTable().mapNotNull { row ->
                val fixture = MarkdownGfmFixtures.ALL.first { it.id == row.id }
                val expected =
                    listOf(
                        MarkdownGfmFixtures.RenderPath.NATIVE,
                        MarkdownGfmFixtures.RenderPath.SERVER_HTML,
                        MarkdownGfmFixtures.RenderPath.OFFLINE_GFM,
                    ).map { if (it in fixture.paths) MARK else DASH }
                val actual = listOf(row.native, row.serverHtml, row.offlineGfm)
                if (expected == actual) null else "${row.id}: 报告 $actual ≠ 目录支持情况 $expected"
            }

        assertTrue("三条渲染路径的支持标记必须与目录一致:\n${mismatches.joinToString("\n")}", mismatches.isEmpty())
    }

    @Test
    fun reportFixtureTable_statusColumnMatchesDerivedStatus() {
        val mismatches =
            parseFixtureTable().mapNotNull { row ->
                val expected = deriveStatus(MarkdownGfmFixtures.byId(row.id).paths)
                if (expected == row.status) null else "${row.id}: 报告状态 ${row.status} ≠ 推导状态 $expected"
            }

        assertTrue("回归状态列必须由支持情况推导得出:\n${mismatches.joinToString("\n")}", mismatches.isEmpty())
    }

    @Test
    fun reportSummaryCounts_matchCatalogDerivedCounts() {
        val native = MarkdownGfmFixtures.nativeFixtures().size
        val unimplemented = MarkdownGfmFixtures.unimplementedFixtures().size
        val webViewOnlyReady = MarkdownGfmFixtures.ALL.count { it.paths.isNotEmpty() && !it.isNative }
        val text = reportFile.readText()

        listOf(
            "| §2.3 原子条目总数 | 35 |" to (MarkdownGfmFixtures.ALL.size == 35),
            "**9**" to (webViewOnlyReady == 9),
            "**24**" to (native == 24),
            "**2**" to (unimplemented == 2),
        ).forEach { (needle, ok) ->
            assertTrue("报告统计与目录不一致：期望报告中出现「$needle」", ok && text.contains(needle))
        }
    }

    @Test
    fun reportSummary_offlineAndServerCoverageCounts_matchCatalog() {
        val serverHtml = MarkdownGfmFixtures.ALL.count { MarkdownGfmFixtures.RenderPath.SERVER_HTML in it.paths }
        val offline = MarkdownGfmFixtures.ALL.count { MarkdownGfmFixtures.RenderPath.OFFLINE_GFM in it.paths }
        val text = reportFile.readText()

        assertEquals("服务端 HTML 覆盖数（报告写到 32/35）", 32, serverHtml)
        assertEquals("离线 GFM 覆盖数（报告写到 29/35，2026-09-12 修复后）", 29, offline)
        assertTrue("报告必须写出 32 / 35", text.contains("32 / 35"))
        assertTrue("报告必须写出 29 / 35", text.contains("29 / 35"))
    }

    private fun parseFixtureTable(): List<ReportRow> {
        val lines = reportFile.readLines()
        val begin = lines.indexOfFirst { it.contains(BEGIN_MARKER) }
        val end = lines.indexOfFirst { it.contains(END_MARKER) }

        assertTrue("报告缺少 $BEGIN_MARKER 标记", begin >= 0)
        assertTrue("报告缺少 $END_MARKER 标记", end > begin)

        return lines
            .subList(begin + 1, end)
            .map { it.trim() }
            .filter { it.startsWith("|") }
            .filterNot { it.startsWith("|---") || it.startsWith("| 夹具") }
            .map { line ->
                val cells = line.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                assertTrue("报告表格行字段数应为 6，实际 ${cells.size}：$line", cells.size == 6)
                ReportRow(
                    id = cells[0].removeSurrounding("`"),
                    clause = cells[1],
                    native = cells[2],
                    serverHtml = cells[3],
                    offlineGfm = cells[4],
                    status = cells[5],
                )
            }
    }

    private data class ReportRow(
        val id: String,
        val clause: String,
        val native: String,
        val serverHtml: String,
        val offlineGfm: String,
        val status: String,
    )

    /**
     * 报告的状态列推导规则（必须与报告 §2 图例逐字一致）：
     * 无任何路径 → ❌；带原生路径 → 🟡（像素基线待录制）；其余 → ✅。
     */
    private fun deriveStatus(paths: Set<MarkdownGfmFixtures.RenderPath>): String =
        when {
            paths.isEmpty() -> STATUS_UNIMPLEMENTED
            MarkdownGfmFixtures.RenderPath.NATIVE in paths -> STATUS_NATIVE_PENDING
            else -> STATUS_READY
        }

    private companion object {
        const val BEGIN_MARKER = "<!-- BEGIN FIXTURE TABLE -->"
        const val END_MARKER = "<!-- END FIXTURE TABLE -->"
        const val MARK = "✅"
        const val DASH = "—"
        const val STATUS_READY = "✅"
        const val STATUS_NATIVE_PENDING = "🟡"
        const val STATUS_UNIMPLEMENTED = "❌"
    }
}
