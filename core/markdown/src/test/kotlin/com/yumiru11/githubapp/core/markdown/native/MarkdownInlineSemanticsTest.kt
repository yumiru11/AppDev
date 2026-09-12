package com.yumiru11.githubapp.core.markdown.native

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import com.mikepenz.markdown.annotator.DefaultAnnotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 原生链内联语义注解器的纯 JVM 回归（mikepenz `MarkdownAnnotator` 钩子）。
 *
 * 走**真实遍历**：`buildMarkdownAnnotatedString`（mikepenz 0.38.1 与生产同一条
 * `AnnotatedString` 构建链），断言产物的 text / spanStyles / linkAnnotations——
 * 不是复刻实现，也不是源码文本断言。
 *
 * 覆盖：@user 提及 → 用户页链接（含「代码/行内代码/已有链接/邮箱/团队提及不链接」的陷阱）、
 * `<kbd>/<sub>/<sup>` 标签消费与样式落地。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class MarkdownInlineSemanticsTest {
    @Test
    fun annotate_userMentionInParagraph_producesProfileLink() {
        val result = annotate("提及 @octocat 与 @yumiru11。")

        val links = result.getLinkAnnotations(0, result.length)
        assertEquals("两个用户提及必须各产出一条链接", 2, links.size)
        assertEquals("https://github.com/octocat", (links[0].item as LinkAnnotation.Url).url)
        assertEquals("https://github.com/yumiru11", (links[1].item as LinkAnnotation.Url).url)
        assertEquals("@octocat", result.text.substring(links[0].start, links[0].end))
        assertEquals("@yumiru11", result.text.substring(links[1].start, links[1].end))
    }

    @Test
    fun annotate_mentionWithSurroundingPunctuation_keepsPrefixCharacter() {
        val result = annotate("(@octocat)：@yumiru11")

        assertEquals("(@octocat)：@yumiru11", result.text)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(2, links.size)
        assertEquals("括号不能被吞进链接文本", "@octocat", result.text.substring(links[0].start, links[0].end))
    }

    @Test
    fun annotate_mentionInsideInlineCode_isLeftLiteral() {
        val result = annotate("代码里的 `@octocat` 不链接")

        assertTrue("行内代码里的 @user 不能被改写", result.text.contains("@octocat"))
        assertEquals("行内代码里不得产出提及链接", 0, result.getLinkAnnotations(0, result.length).size)
    }

    @Test
    fun annotate_mentionInsideExistingLink_doesNotNestSecondLink() {
        val result = annotate("[@octocat](https://example.com)")

        val links = result.getLinkAnnotations(0, result.length)
        assertEquals("已有链接内部不得再嵌一层提及链接", 1, links.size)
        assertEquals("https://example.com", (links[0].item as LinkAnnotation.Url).url)
    }

    @Test
    fun annotate_teamMention_staysPlainTextWithoutPartialUserLink() {
        val result = annotate("团队 @github/docs 与 @android/compose-reviewers")

        assertEquals("团队提及必须整段保持纯文本", 0, result.getLinkAnnotations(0, result.length).size)
        assertTrue("不得把 @github 从 @github/docs 里切出来链接", result.text.contains("@github/docs"))
    }

    @Test
    fun annotate_emailAddress_producesNoGithubProfileLink() {
        val result = annotate("联系 octocat@github.com")

        assertTrue(
            "邮箱不得被当作提及链接",
            result.getLinkAnnotations(0, result.length).none {
                it.item is LinkAnnotation.Url && (it.item as LinkAnnotation.Url).url.startsWith("https://github.com/")
            },
        )
        assertTrue("邮箱原文保持不变", result.text.contains("octocat@github.com"))
    }

    @Test
    fun annotate_kbdInlineHtml_consumesTagsAndAppliesMonospaceStyle() {
        val result = annotate("按键 <kbd>Ctrl</kbd> + <kbd>C</kbd>")

        assertTrue("HTML 标签本身不能出现在产物文本里", !result.text.contains("<kbd>") && !result.text.contains("</kbd>"))
        assertTrue("键帽文本必须保留", result.text.contains("Ctrl"))
        assertEquals(
            "两个键帽必须各自命中一段等宽样式",
            2,
            result.spanStyles.count { it.item.fontFamily == FontFamily.Monospace },
        )
    }

    @Test
    fun annotate_subInlineHtml_appliesSubscriptBaselineShift() {
        val result = annotate("水分子 H<sub>2</sub>O")

        assertEquals("标签被消费后只剩正文", "水分子 H2O", result.text)
        assertTrue(
            "sub 内容必须命中下标（BaselineShift.Subscript）",
            result.hasStyleOf("2", { it.baselineShift == BaselineShift.Subscript }),
        )
    }

    @Test
    fun annotate_supInlineHtml_appliesSuperscriptBaselineShift() {
        val result = annotate("平方 x<sup>2</sup>")

        assertEquals("平方 x2", result.text)
        assertTrue(
            "sup 内容必须命中上标（BaselineShift.Superscript）",
            result.hasStyleOf("2", { it.baselineShift == BaselineShift.Superscript }),
        )
    }

    @Test
    fun annotate_htmlTagInsideInlineCode_doesNotApplySubscriptStyle() {
        val result = annotate("代码里的 `<sub>x</sub>` 不触发下标")

        // 诚实声明：mikepenz 0.38.1 的默认处理没有 HTML_TAG 分支，代码里的 HTML 标签
        // 本就不会出现在产物里（既有行为，非本票引入）；本票只保证不套语义样式。
        assertTrue("行内代码内容必须保留", result.text.contains("x"))
        assertTrue(
            "行内代码里的示例不得命中下标样式",
            result.spanStyles.none { it.item.baselineShift == BaselineShift.Subscript },
        )
    }

    @Test
    fun annotate_plainParagraph_passesThroughDefaultTextHandling() {
        val result = annotate("普通段落，没有需要补齐的语义。")

        assertEquals("普通段落不能被注解器改写", "普通段落，没有需要补齐的语义。", result.text)
        assertEquals(0, result.getLinkAnnotations(0, result.length).size)
    }

    // ── 共用 ────────────────────────────────────────────────────────────

    /** 用生产注解器跑 mikepenz 真实的 AnnotatedString 构建链（含默认节点处理）。 */
    private fun annotate(markdown: String) =
        markdown.buildMarkdownAnnotatedString(
            style = TextStyle(),
            annotatorSettings =
                DefaultAnnotatorSettings(
                    linkTextSpanStyle = TextLinkStyles(),
                    codeSpanStyle = SpanStyle(),
                    annotator = MarkdownInlineSemantics.annotator(TEST_STYLES),
                ),
        )

    /** 断言 needle 命中的区间上有满足 predicate 的 SpanStyle。 */
    private fun androidx.compose.ui.text.AnnotatedString.hasStyleOf(
        needle: String,
        predicate: (SpanStyle) -> Boolean,
    ): Boolean {
        val index = text.indexOf(needle)
        if (index < 0) return false
        val end = index + needle.length
        return spanStyles.any { it.start <= index && it.end >= end && predicate(it.item) }
    }

    private companion object {
        val TEST_STYLES =
            MarkdownInlineStyles(
                link = TextLinkStyles(style = SpanStyle(color = Color.Red, textDecoration = TextDecoration.Underline)),
                kbd = SpanStyle(fontFamily = FontFamily.Monospace),
                subscript = SpanStyle(baselineShift = BaselineShift.Subscript),
                superscript = SpanStyle(baselineShift = BaselineShift.Superscript),
            )
    }
}
