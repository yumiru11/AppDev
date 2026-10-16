package com.yumiru11.githubapp.core.markdown.html

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 原生 HTML 子集渲染器的语义契约（README 常见写法逐类断言）。
 *
 * 命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ComposeHtmlTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun render(
        html: String,
        onLinkClick: (String) -> Unit = {},
    ) {
        composeRule.setContent { MaterialTheme { ComposeHtml(html = html, onLinkClick = onLinkClick) } }
    }

    @Test
    fun headings_rendersAllLevels() {
        render("<h1>Release notes</h1><h3>Fixes</h3>")
        composeRule.onNodeWithText("Release notes").assertExists()
        composeRule.onNodeWithText("Fixes").assertExists()
    }

    @Test
    fun table_rendersHeaderAndCellText() {
        render("<table><thead><tr><th>Name</th><th>Value</th></tr></thead><tbody><tr><td>Stars</td><td>42</td></tr></tbody></table>")
        composeRule.onNodeWithText("Name").assertExists()
        composeRule.onNodeWithText("Stars").assertExists()
        composeRule.onNodeWithText("42").assertExists()
    }

    @Test
    fun unorderedList_rendersItemText() {
        render("<ul><li>First</li><li>Second</li></ul>")
        composeRule.onNodeWithText("First").assertExists()
        composeRule.onNodeWithText("Second").assertExists()
    }

    @Test
    fun paragraphWithBoldAndCode_keepsFullText() {
        render("<p>Use <b>bold</b> and <code>inline()</code> here</p>")
        composeRule.onNodeWithText("Use bold and inline() here").assertExists()
    }

    @Test
    fun anchorLink_rendersLinkText() {
        // 链接点击由 LinkAnnotation 交给 LocalUriHandler 路径，Robolectric 语义层不暴露
        // 可点击节点（与 MarkdownViewer 同款注解链路），故此处只锁定渲染结果。
        render("<p>See <a href=\"https://example.com/x\">the docs</a></p>")
        composeRule.onNodeWithText("See the docs").assertExists()
    }

    @Test
    fun emojiImage_rendersAltGlyph() {
        render("<p>Ship it <img class=\"emoji\" alt=\"R\" src=\"https://example.com/e.png\"> today</p>")
        composeRule.onNodeWithText("Ship it R today").assertExists()
    }

    @Test
    fun preCodeBlockWithLanguage_rendersCodeText() {
        render("<pre><code class=\"language-kotlin\">val x = 1</code></pre>")
        composeRule.onNodeWithText("KOTLIN").assertExists()
        composeRule.onNodeWithText("val x = 1").assertExists()
    }

    @Test
    fun details_withoutOpen_hidesBodyUntilClicked() {
        render("<details><summary>More</summary><p>Hidden body</p></details>")
        composeRule.onNodeWithText("More").assertExists()
        composeRule.onNodeWithText("Hidden body").assertDoesNotExist()
        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Hidden body").assertExists()
    }

    @Test
    fun unknownTag_recursesIntoChildren_doesNotShowSource() {
        render("<figure><figcaption>Caption text</figcaption></figure>")
        composeRule.onNodeWithText("Caption text").assertExists()
    }
}
