package com.yumiru11.githubapp.core.markdown.fixture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §2.3 夹具目录的自校验（纯 JVM，无像素依赖）。
 *
 * 存在意义：截图基线尚未录制时（CI 首轮 verify 必然红），**这一层仍能证明「§2.3 清单已被
 * 逐条覆盖」**——即回归集的骨架是完整的，缺的只是像素基线本身。相反，若夹具文件缺失/
 * 条目对不上，本类会先红，避免「回归集看起来在跑，其实没查」的历史教训重演。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class MarkdownGfmFixtureCatalogTest {
    @Test
    fun clausesAndFixtures_clauseSetCompared_areExactlyEqual() {
        val declared = MarkdownGfmFixtures.CLAUSES.toSet()
        val covered = MarkdownGfmFixtures.ALL.map { it.clause }.toSet()

        assertEquals(
            "每个 §2.3 原子条目必须恰好有一个夹具；夹具的 clause 必须逐字取自 plan.md §2.3",
            declared,
            covered,
        )
    }

    @Test
    fun clausesAndFixtures_catalogCompared_haveSameSize() {
        assertEquals(
            "夹具数与 §2.3 原子条目数必须一致（一条目一夹具，不允许合并且不允许遗漏）",
            MarkdownGfmFixtures.CLAUSES.size,
            MarkdownGfmFixtures.ALL.size,
        )
    }

    @Test
    fun everyFixture_resourceFileExists_hasNonBlankMarkdown() {
        val problems =
            MarkdownGfmFixtures.ALL.mapNotNull { fixture ->
                val body = runCatching { fixture.markdown() }.getOrElse { return@mapNotNull "${fixture.id}: ${it.message}" }
                when {
                    body.isBlank() -> "${fixture.id}: fixture body is blank"
                    else -> null
                }
            }

        assertTrue("夹具资源必须存在且非空（缺失 = 静默漏测）:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    @Test
    fun everyFixture_markdownBody_notContainsFixtureMetadataComment() {
        // 夹具正文本身会被渲染，不允许混入元数据注释污染像素基线
        val offenders =
            MarkdownGfmFixtures.ALL
                .filter { fixture ->
                    val body = fixture.markdown()
                    body.contains("§2.3") || body.contains("fixture:") || body.startsWith("---")
                }.map { it.id }

        assertTrue("夹具正文必须是纯 Markdown（元数据放本目录的 KDoc 与 README）: $offenders", offenders.isEmpty())
    }

    @Test
    fun fixtureIds_fileNamesCompared_areUnique() {
        val ids = MarkdownGfmFixtures.ALL.map { it.id }
        assertEquals("夹具 id 必须唯一", ids.size, ids.toSet().size)
    }

    @Test
    fun fixtureIds_numericPrefixOrder_matchesCatalogOrder() {
        val prefixes = MarkdownGfmFixtures.ALL.map { it.id.substringBefore('-').toInt() }
        assertEquals(
            "夹具 id 的数字前缀必须与目录顺序一致（便于人工对照 plan.md §2.3）",
            (1..MarkdownGfmFixtures.ALL.size).toList(),
            prefixes,
        )
    }

    @Test
    fun nativeFixtures_isNativeFlag_consistentWithPaths() {
        MarkdownGfmFixtures.nativeFixtures().forEach { fixture ->
            assertTrue(
                "${fixture.id}: nativeFixtures() 只能返回声明了 NATIVE 路径的夹具",
                MarkdownGfmFixtures.RenderPath.NATIVE in fixture.paths,
            )
        }
    }

    @Test
    fun darkFixtures_nativeSubsetOnly_areDeclared() {
        val darkIds = MarkdownGfmFixtures.darkFixtures().map { it.id }.toSet()
        val nativeIds = MarkdownGfmFixtures.nativeFixtures().map { it.id }.toSet()

        assertTrue("深色基线必须是原生基线的子集", nativeIds.containsAll(darkIds))
        assertFalse("深色基线集合不得为空（深色主题是代码块/Alert 的真实变量）", darkIds.isEmpty())
    }

    @Test
    fun unimplementedFixtures_declareEmptyPathSet() {
        val empty =
            MarkdownGfmFixtures.ALL
                .filter { it.paths.isEmpty() }
                .map { it.id }
                .toSet()
        val reported = MarkdownGfmFixtures.unimplementedFixtures().map { it.id }.toSet()

        assertEquals("三条路径都不渲染的夹具必须被显式列为未实现", empty, reported)
    }

    @Test
    fun unimplementedFixtures_planChecklistOptionalItems_areEnumerated() {
        // §2.3 明确标注「兜底通道，可选」与未落的条目：报告必须点名，不允许被静默合入「已覆盖」
        val expected = setOf("28-anchor-jump", "29-image-lazy", "33-math-katex", "34-mermaid")
        val actual = MarkdownGfmFixtures.unimplementedFixtures().map { it.id }.toSet()

        assertEquals("未实现集合发生变化时必须同步更新本断言与一致性报告", expected, actual)
    }

    @Test
    fun baselineNames_inventory_isUniqueAndCoversNativeAndDarkFixtures() {
        val names = MarkdownGfmFixtures.baselineNames()

        assertEquals("基线文件名必须唯一", names.size, names.toSet().size)
        assertEquals(
            "基线清单 = 全部原生夹具（浅色）+ 深色子集",
            MarkdownGfmFixtures.nativeFixtures().size + MarkdownGfmFixtures.darkFixtures().size,
            names.size,
        )
        assertTrue(
            "基线名必须带主题后缀",
            names.all { it.endsWith("_light") || it.endsWith("_dark") },
        )
    }

    @Test
    fun byId_unknownId_throwsInsteadOfSilentlySkipping() {
        val failure = runCatching { MarkdownGfmFixtures.byId("does-not-exist") }.exceptionOrNull()

        assertTrue("未知夹具 id 必须抛异常（防止静默跳过）", failure is IllegalStateException)
    }
}
