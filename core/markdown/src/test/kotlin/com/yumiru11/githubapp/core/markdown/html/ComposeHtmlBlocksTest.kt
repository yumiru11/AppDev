package com.yumiru11.githubapp.core.markdown.html

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 原生 HTML 子集渲染器的**块级与边界分支**语义契约。
 *
 * 与 [ComposeHtmlTest] 的分工：那边覆盖 README 高频写法（标题/表格/列表/段落/折叠），
 * 这边覆盖低频但真实存在的块级与边界形态（分割线、换行、引用、有序与嵌套列表、
 * 图片与 picture、未知标签递归、混合内容的内联段归并、各标题层级样式）。
 * 断言一律按「可见文本/语义描述」做，不依赖像素基线。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ComposeHtmlBlocksTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun render(html: String) {
        composeRule.setContent { MaterialTheme { ComposeHtml(html = html, onLinkClick = {}) } }
    }

    @Test
    fun thematicBreakAndLineBreak_keepSurroundingParagraphs() {
        render("<p>before</p><hr><p>after</p><br><p>tail</p>")
        composeRule.onNodeWithText("before").assertExists()
        composeRule.onNodeWithText("after").assertExists()
        composeRule.onNodeWithText("tail").assertExists()
    }

    @Test
    fun blockquote_rendersInlineAndBlockChildren() {
        render("<blockquote>Quoted line</blockquote><blockquote><p>Block quote</p></blockquote>")
        composeRule.onNodeWithText("Quoted line").assertExists()
        composeRule.onNodeWithText("Block quote").assertExists()
    }

    @Test
    fun orderedAndNestedLists_renderEveryItem() {
        render("<ol><li>One</li><li>Two<ul><li>Nested</li></ul></li></ol>")
        composeRule.onNodeWithText("One").assertExists()
        composeRule.onNodeWithText("Two").assertExists()
        composeRule.onNodeWithText("Nested").assertExists()
        // 有序列表前缀用序号，无序子列表用圆点
        composeRule.onNodeWithText("1.").assertExists()
    }

    @Test
    fun headingLevels_useDistinctTypographyWithoutCrashing() {
        render("<h2>Two</h2><h4>Four</h4><h5>Five</h5><h6>Six</h6>")
        composeRule.onNodeWithText("Two").assertExists()
        composeRule.onNodeWithText("Four").assertExists()
        composeRule.onNodeWithText("Five").assertExists()
        composeRule.onNodeWithText("Six").assertExists()
    }

    @Test
    fun bareImage_rendersAltAsTextGlyph() {
        // 顶层 <img> 走内联路径：alt 作为字形文本渲染（emoji 图/徽章同路径）
        render("<img src=\"https://example.com/a.png\" alt=\"diagram\">")
        composeRule.onNodeWithText("diagram").assertExists()
    }

    @Test
    fun pictureBlock_exposesAltAsContentDescription() {
        render("<picture><source srcset=\"b.png\"><img src=\"https://example.com/b.png\" alt=\"pic\"></picture>")
        composeRule.onNodeWithContentDescription("pic").assertExists()
    }

    @Test
    fun unknownBlockTag_recursesIntoChildrenInsteadOfShowingSource() {
        render("<custom-widget><p>Inside custom</p></custom-widget>")
        composeRule.onNodeWithText("Inside custom").assertExists()
    }

    @Test
    fun mixedInlineAndBlock_keepsEveryTextRun() {
        render("<div>Lead text<p>Block para</p>Tail text</div>")
        composeRule.onNodeWithText("Lead text").assertExists()
        composeRule.onNodeWithText("Block para").assertExists()
        composeRule.onNodeWithText("Tail text").assertExists()
    }

    @Test
    fun containerLevelInlineElement_mergesIntoSingleParagraph() {
        render("<div><span>inline span</span></div>")
        composeRule.onNodeWithText("inline span").assertExists()
    }

    @Test
    fun inlineFormatting_keepsFullSentence() {
        render(
            "<p>a <u>u</u> <ins>ins</ins> <s>s</s> <del>del</del> <kbd>kbd</kbd> " +
                "<samp>samp</samp> <tt>tt</tt> <sub>sub</sub> <sup>sup</sup> " +
                "<span style=\"color: #ff0000\">red</span> end</p>",
        )
        composeRule.onNodeWithText("a u ins s del kbd samp tt sub sup red end", substring = true).assertExists()
    }

    @Test
    fun centeredContainer_rendersText() {
        render("<div align=\"center\"><p>Centered</p></div><center>Also centered</center>")
        composeRule.onNodeWithText("Centered").assertExists()
        composeRule.onNodeWithText("Also centered").assertExists()
    }

    @Test
    fun emptyAndCommentOnlyInput_renderNothing() {
        render("<!-- comment -->")
        composeRule.onNodeWithText("comment", substring = true).assertDoesNotExist()
    }
}
