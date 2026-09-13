package com.yumiru11.githubapp.core.markdown.fixture

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import com.mikepenz.markdown.annotator.DefaultAnnotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.yumiru11.githubapp.core.markdown.native.MarkdownInlineSemantics
import com.yumiru11.githubapp.core.markdown.native.MarkdownInlineStyles
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 原生渲染链的**解析阶段**能力探针（纯 JVM，无需 Robolectric/像素）。
 *
 * ## 为什么需要它
 *
 * 原生像素基线要等云端录制才能证明「渲染出什么」，但「原生链到底认不认得这种写法」是
 * **今天就能查清的事实**。本类用 mikepenz 0.38.1 实际使用的同一个解析器
 * （`GFMFlavourDescriptor`，见 `multiplatform-markdown-renderer` 的
 * `MarkdownStateKt`/`MarkdownKt` 字节码常量）解析每个夹具，断言 AST 里出现了对应元素。
 *
 * 因此它提供的是**分层证据**：
 * - 本类通过 = 解析器认得该写法（AST 层证据，可立即核查）；
 * - `MarkdownFixtureScreenshotTest` 通过 = 该写法在原生链上渲染出的像素与基线一致
 *   （像素层证据，基线待录制）。
 *
 * 两者都不能替代 WebView 路径的证据——见 `WebViewFixtureRenderModeTest` 与
 * `docs/agents/markdown-consistency-2026-09-11.md` 的分层说明。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class GfmNativeParserCapabilityTest {
    private val parser = MarkdownParser(GFMFlavourDescriptor())

    @Test
    fun nativeFixtures_declaredNativePath_produceTheirExpectedAstElement() {
        val failures =
            NATIVE_AST_EXPECTATIONS.mapNotNull { (fixtureId, expected) ->
                val types = elementTypesOf(fixtureId)
                if (expected.any { it in types }) {
                    null
                } else {
                    "$fixtureId: 期望 ${expected.joinToString(" | ") { it.toString() }}，" +
                        "实际 AST 元素 = ${types.joinToString(", ") { it.toString() }}"
                }
            }

        assertTrue(
            "声明 NATIVE 路径的夹具，必须至少产生一个对应的 GFM AST 元素：\n${failures.joinToString("\n")}",
            failures.isEmpty(),
        )
    }

    @Test
    fun headingsFixture_containingAllSixLevels_producesAtx1ToAtx6() {
        val types = elementTypesOf("01-headings")
        val levels =
            listOf(
                MarkdownElementTypes.ATX_1,
                MarkdownElementTypes.ATX_2,
                MarkdownElementTypes.ATX_3,
                MarkdownElementTypes.ATX_4,
                MarkdownElementTypes.ATX_5,
                MarkdownElementTypes.ATX_6,
            )

        val missing = levels.filterNot { it in types }
        assertTrue("H1–H6 六级标题必须全部被解析：缺 $missing", missing.isEmpty())
        assertTrue(
            "Setext 标题（=== / ---）也必须被解析",
            MarkdownElementTypes.SETEXT_1 in types && MarkdownElementTypes.SETEXT_2 in types,
        )
    }

    @Test
    fun strikethroughFixture_doubleTilde_producesGfmStrikethroughElement() {
        assertTrue(
            "~~删除线~~ 必须被 GFM 解析为 STRIKETHROUGH（渲染为 TextDecoration.LineThrough）",
            GFMElementTypes.STRIKETHROUGH in elementTypesOf("06-strikethrough"),
        )
    }

    @Test
    fun tableFixture_pipeTable_producesGfmTableElement() {
        val types = elementTypesOf("07-table")

        assertTrue("管道表格必须被解析为 GFM TABLE", GFMElementTypes.TABLE in types)
        assertTrue("表格行必须被解析为 ROW", GFMElementTypes.ROW in types)
        assertTrue("表格单元格必须被解析为 CELL", GFMTokenTypes.CELL in types)
    }

    @Test
    fun taskListFixture_checkboxSyntax_producesCheckBoxToken() {
        assertTrue(
            "`- [ ]` / `- [x]` 必须被解析为 GFM CHECK_BOX（渲染为 MarkdownCheckBox）",
            GFMTokenTypes.CHECK_BOX in elementTypesOf("11-task-list"),
        )
    }

    @Test
    fun nestedListFixture_threeLevels_produceNestedListElements() {
        val depth = maxListDepth(parse("10-nested-list"))
        assertTrue("三级嵌套列表必须被解析为至少 3 层 list 元素，实际深度 = $depth", depth >= 3)
    }

    @Test
    fun fencedCodeFixture_withAndWithoutLanguage_produceFenceLangToken() {
        val types = elementTypesOf("13-fenced-code-block")

        assertTrue("围栏代码块必须被解析为 CODE_FENCE", MarkdownElementTypes.CODE_FENCE in types)
        assertTrue(
            "带语言标注的围栏必须产出 FENCE_LANG（代码语言标注 / 高亮的输入）",
            MarkdownTokenTypes.FENCE_LANG in types,
        )
    }

    @Test
    fun languageTagFixture_eightLanguages_produceEightFenceLangTokens() {
        val count = countOf(parse("14-code-language-tag"), MarkdownTokenTypes.FENCE_LANG)
        assertTrue("夹具含 8 个带语言的围栏，实际 FENCE_LANG = $count", count == 8)
    }

    @Test
    fun imageFixtures_bangSyntax_produceImageElements() {
        val offenders =
            IMAGE_FIXTURE_IDS.filterNot { MarkdownElementTypes.IMAGE in elementTypesOf(it) }
        assertTrue("图片夹具必须被解析为 IMAGE 元素：$offenders", offenders.isEmpty())
    }

    @Test
    fun linkFixtures_linkAndAutolink_produceLinkElements() {
        assertTrue(
            "显式链接必须被解析为 INLINE_LINK",
            MarkdownElementTypes.INLINE_LINK in elementTypesOf("19-external-link"),
        )

        val autolinkTypes = elementTypesOf("20-autolink")
        val autolinkElements =
            setOf(
                GFMTokenTypes.GFM_AUTOLINK,
                MarkdownElementTypes.AUTOLINK,
                MarkdownTokenTypes.EMAIL_AUTOLINK,
            )
        assertTrue(
            "裸 URL / <url> / 裸邮箱必须产出自动链接元素，实际 = $autolinkTypes",
            autolinkElements.any { it in autolinkTypes },
        )
    }

    @Test
    fun inlineHtmlFixture_detailsBlock_producesHtmlBlockElement() {
        assertTrue(
            "<details> 折叠块必须被解析为 HTML_BLOCK（交 EnhancedHtmlBlock 处理）",
            MarkdownElementTypes.HTML_BLOCK in elementTypesOf("32-inline-html"),
        )
    }

    @Test
    fun mathFixture_dollarSyntax_parserRecognizesButNoNativeComponentExists() {
        val types = elementTypesOf("33-math-katex")

        // 诚实分层：解析器认得 $-math（GFM flavour 内建），但原生链 markdownComponents 无 math 槽位
        // → 不走 NATIVE；WebView 两条通道 2026-09-13 起由离线 KaTeX 在清洗后渲染（见 MathRenderExecutionTest）。
        assertTrue(
            "GFM 解析器应识别 $...$ 为 INLINE_MATH，实际 = $types",
            GFMElementTypes.INLINE_MATH in types || GFMElementTypes.BLOCK_MATH in types,
        )
        val paths = MarkdownGfmFixtures.byId("33-math-katex").paths
        assertTrue(
            "33-math-katex 必须登记服务端 HTML + 离线 GFM 两条 WebView 路径",
            MarkdownGfmFixtures.RenderPath.SERVER_HTML in paths &&
                MarkdownGfmFixtures.RenderPath.OFFLINE_GFM in paths,
        )
        assertTrue("原生链无 math 槽位，仍不得登记 NATIVE", MarkdownGfmFixtures.RenderPath.NATIVE !in paths)
    }

    @Test
    fun mermaidFixture_staysCodeFenceInNativeParser_webViewChannelsRenderIt() {
        val types = elementTypesOf("34-mermaid")

        assertTrue("mermaid 围栏在原生解析器里仍是普通 CODE_FENCE（原生链无图表槽位）", MarkdownElementTypes.CODE_FENCE in types)
        val paths = MarkdownGfmFixtures.byId("34-mermaid").paths
        assertTrue(
            "34-mermaid 必须登记服务端 HTML + 离线 GFM 两条 WebView 路径（2026-09-13 离线 Mermaid 落地）",
            MarkdownGfmFixtures.RenderPath.SERVER_HTML in paths &&
                MarkdownGfmFixtures.RenderPath.OFFLINE_GFM in paths,
        )
        assertTrue("原生链无图表槽位，仍不得登记 NATIVE", MarkdownGfmFixtures.RenderPath.NATIVE !in paths)
    }

    @Test
    fun nonNativeFixtures_produceNoDedicatedParserElement_renderingIsAnnotatorLayer() {
        // 解析器层面的事实（保留红证明能力）：这些写法没有任何专用 AST 元素。
        // 渲染在**注解层**补齐——@user（#251）、#123 / 完整 40 位 sha（本票）由
        // MarkdownInlineSemantics 写 LinkAnnotation.Url；emoji（26）与脚注（35）原生链仍按纯文本渲染。
        val noDedicatedElement =
            listOf("22-mention-user", "23-mention-org-team", "24-issue-ref", "25-commit-sha-ref", "26-emoji-shortcode", "35-footnote")

        noDedicatedElement.forEach { id ->
            val types = elementTypesOf(id)
            val suspicious =
                types.filter { type ->
                    val name = type.toString().uppercase()
                    name.contains("MENTION") || name.contains("EMOJI") || name.contains("FOOTNOTE") || name.contains("ISSUE")
                }
            assertTrue(
                "$id 声称无原生专用元素，但 AST 出现了 $suspicious（若解析器新增了支持，请更新 catalog 与报告）",
                suspicious.isEmpty(),
            )
        }
    }

    @Test
    fun issueReferenceAndShaFixtures_annotatorLayer_producesRepoLinks() {
        // 解析器不 linkify（见上），但注解层必须补齐：
        //   #123 → {repo}/issues/123（GitHub 对 PR 会 302 到 /pull/N）
        //   完整 40 位 hex sha → {repo}/commit/<sha>
        // 这是「能力存在」的正向证据；负向（代码/URL/已有链接内不链接）由 MarkdownInlineSemanticsTest 覆盖。
        val issueUrls = linkUrlsOf(MarkdownGfmFixtures.byId("24-issue-ref").markdown())
        assertTrue(
            "24-issue-ref 的裸 #123 必须被注解器渲染为 issue 链接，实际 = $issueUrls",
            "$REPO_URL/issues/123" in issueUrls,
        )

        val shaUrls = linkUrlsOf(MarkdownGfmFixtures.byId("25-commit-sha-ref").markdown())
        assertTrue(
            "25-commit-sha-ref 的完整 sha 必须被注解器渲染为 commit 链接，实际 = $shaUrls",
            "$REPO_URL/commit/$FULL_SHA" in shaUrls,
        )
    }

    @Test
    fun issueReferenceFixture_hashSyntax_isNotAutolinkedByParser_annotatorCompensates() {
        // #123 / owner/repo#123 / gh-123 不会被解析器 linkify（GitHubLinkParser 负责点击；
        // 正文引用由注解层补齐——见上一条测试）
        val types = elementTypesOf("24-issue-ref")
        val autolinks =
            listOf(GFMTokenTypes.GFM_AUTOLINK, MarkdownElementTypes.AUTOLINK, MarkdownTokenTypes.EMAIL_AUTOLINK)
                .filter { it in types }

        assertTrue("正文裸 #123 / gh-123 不应被解析器自动链接，实际出现 $autolinks", autolinks.isEmpty())
    }

    /** 用生产注解器（真实 mikepenz AnnotatedString 构建链）取 markdown 产物里的链接 URL。 */
    private fun linkUrlsOf(markdown: String): List<String> {
        val annotated =
            markdown.buildMarkdownAnnotatedString(
                style = TextStyle(),
                annotatorSettings =
                    DefaultAnnotatorSettings(
                        linkTextSpanStyle = TextLinkStyles(),
                        codeSpanStyle = SpanStyle(),
                        annotator = MarkdownInlineSemantics.annotator(TEST_STYLES, REPO_URL),
                    ),
            )
        return annotated
            .getLinkAnnotations(0, annotated.length)
            .mapNotNull { (it.item as? LinkAnnotation.Url)?.url }
    }

    private fun parse(fixtureId: String): ASTNode = parser.buildMarkdownTreeFromString(MarkdownGfmFixtures.byId(fixtureId).markdown())

    private fun elementTypesOf(fixtureId: String): Set<IElementType> {
        val collected = mutableSetOf<IElementType>()
        collect(parse(fixtureId), collected)
        return collected
    }

    private fun collect(
        node: ASTNode,
        into: MutableSet<IElementType>,
    ) {
        into += node.type
        node.children.forEach { collect(it, into) }
    }

    private fun countOf(
        node: ASTNode,
        type: IElementType,
    ): Int {
        var count = if (node.type == type) 1 else 0
        node.children.forEach { count += countOf(it, type) }
        return count
    }

    /** 列表最大嵌套深度（UNORDERED_LIST / ORDERED_LIST 的链式层数）。 */
    private fun maxListDepth(
        node: ASTNode,
        currentDepth: Int = 0,
    ): Int {
        val isList = node.type == MarkdownElementTypes.UNORDERED_LIST || node.type == MarkdownElementTypes.ORDERED_LIST
        val depth = if (isList) currentDepth + 1 else currentDepth
        return node.children.maxOfOrNull { maxListDepth(it, depth) } ?: depth
    }

    private companion object {
        const val REPO_URL = "https://github.com/octocat/Hello-World"
        const val FULL_SHA = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"

        val TEST_STYLES =
            MarkdownInlineStyles(
                link = TextLinkStyles(),
                kbd = SpanStyle(),
                subscript = SpanStyle(),
                superscript = SpanStyle(),
            )

        val IMAGE_FIXTURE_IDS =
            listOf("17-image-relative", "18-image-github-cache-domain", "29-image-lazy", "30-image-zoom", "31-image-gif")

        /**
         * 夹具 → 期望出现的 AST 元素（任一命中即通过）。
         *
         * 一行一类的原因：夹具只验证「解析器认得」，不锁死具体元素名组合（intellij-markdown
         * 在 ATX/SETEXT、AUTOLINK/GFM_AUTOLINK 等处存在多种等价表示）。
         */
        val NATIVE_AST_EXPECTATIONS: Map<String, List<IElementType>> =
            mapOf(
                "01-headings" to listOf(MarkdownElementTypes.ATX_1, MarkdownElementTypes.SETEXT_1),
                "02-paragraphs" to listOf(MarkdownElementTypes.PARAGRAPH),
                "03-blockquote" to listOf(MarkdownElementTypes.BLOCK_QUOTE),
                "04-bold" to listOf(MarkdownElementTypes.STRONG),
                "05-italic" to listOf(MarkdownElementTypes.EMPH),
                "06-strikethrough" to listOf(GFMElementTypes.STRIKETHROUGH),
                "07-table" to listOf(GFMElementTypes.TABLE),
                "08-ordered-list" to listOf(MarkdownElementTypes.ORDERED_LIST, MarkdownTokenTypes.LIST_NUMBER),
                "09-unordered-list" to listOf(MarkdownElementTypes.UNORDERED_LIST, MarkdownTokenTypes.LIST_BULLET),
                "10-nested-list" to listOf(MarkdownElementTypes.LIST_ITEM),
                "11-task-list" to listOf(GFMTokenTypes.CHECK_BOX),
                "12-inline-code" to listOf(MarkdownElementTypes.CODE_SPAN),
                "13-fenced-code-block" to listOf(MarkdownElementTypes.CODE_FENCE),
                "14-code-language-tag" to listOf(MarkdownTokenTypes.FENCE_LANG),
                "15-syntax-highlight" to listOf(MarkdownElementTypes.CODE_FENCE),
                "16-code-copy" to listOf(MarkdownElementTypes.CODE_FENCE),
                "17-image-relative" to listOf(MarkdownElementTypes.IMAGE),
                "18-image-github-cache-domain" to listOf(MarkdownElementTypes.IMAGE),
                "19-external-link" to listOf(MarkdownElementTypes.INLINE_LINK),
                "20-autolink" to listOf(GFMTokenTypes.GFM_AUTOLINK, MarkdownElementTypes.AUTOLINK, MarkdownTokenTypes.EMAIL_AUTOLINK),
                "21-relative-link" to listOf(MarkdownElementTypes.INLINE_LINK),
                "27-github-alerts" to listOf(MarkdownElementTypes.BLOCK_QUOTE),
                "30-image-zoom" to listOf(MarkdownElementTypes.IMAGE),
                "32-inline-html" to listOf(MarkdownElementTypes.HTML_BLOCK),
            )
    }
}
