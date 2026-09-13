package com.yumiru11.githubapp.core.markdown.webview

import com.yumiru11.githubapp.core.markdown.MarkdownDensity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 阅读密度 **WebView 通道**契约（产物可查，不依赖 WebView 栅格化）：
 *
 * - `markdown-you.css` 必须只消费 `var(--md-reading-*)` 变量，不再硬编码 16px / 1.6；
 * - [WebViewHtmlBuilder] 注入的 `<style id="reading-density">` 必须与
 *   [MarkdownDensity] 令牌逐值一致（默认 [MarkdownDensity.current] = Literal）；
 * - CSS 消费的每个 `--md-reading-*` 变量名都必须被注入方声明（名称漂移即红）。
 *
 * 这一层**不能**证明真机 WebView 的视觉结果——那由 CI 模拟器截图与真机走查兜底。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class MarkdownDensityWebViewContractTest {
    private val cssFile = File("src/main/assets/webview/markdown-you.css")

    @Test
    fun bundledMarkdownYouCss_consumesDensityVariables() {
        assertTrue("CSS 资源必须存在（路径漂移会导致本测试静默空转）: ${cssFile.path}", cssFile.isFile)
        val css = cssFile.readText()

        listOf(
            MarkdownDensity.CSS_VAR_SIDES,
            MarkdownDensity.CSS_VAR_PARAGRAPH_GAP,
            MarkdownDensity.CSS_VAR_LINE_HEIGHT,
            MarkdownDensity.CSS_VAR_INDENT,
        ).forEach { name ->
            assertTrue("markdown-you.css 必须消费 $name（阅读密度单一事实来源）", css.contains("var($name,"))
        }
    }

    @Test
    fun bundledMarkdownYouCss_noLongerHardcodesLegacyDensity() {
        val css = cssFile.readText()

        assertFalse("行高不得再硬编码 1.6（回归为 var(--md-reading-line-height)）", css.contains("line-height: 1.6"))
        assertFalse("正文 padding 不得再硬编码 16px（回归为 var(--md-reading-sides)）", css.contains("padding: 16px"))
        assertFalse("段落间距不得再硬编码 16px（回归为 var(--md-reading-paragraph-gap)）", css.contains("margin: 0 0 16px"))
    }

    @Test
    fun build_defaultDensity_injectsLiteralVariablesAfterThemeVars() {
        val html = buildHtml(density = null)

        assertTrue(html.contains("id=\"reading-density\""))
        assertTrue(html.contains("${MarkdownDensity.CSS_VAR_SIDES}: 43px"))
        assertTrue(html.contains("${MarkdownDensity.CSS_VAR_PARAGRAPH_GAP}: 37px"))
        assertTrue(html.contains("${MarkdownDensity.CSS_VAR_LINE_HEIGHT}: 2.125"))
        assertTrue(html.contains("${MarkdownDensity.CSS_VAR_INDENT}: 24px"))
        assertTrue(
            "reading-density 必须晚于 theme-vars（CSS 变量在文档流上后声明者胜）",
            html.indexOf("id=\"reading-density\"") > html.indexOf("id=\"theme-vars\""),
        )
        assertTrue(
            "reading-density 必须晚于 markdown-you.css（同特异性覆盖）",
            html.indexOf("id=\"reading-density\"") > html.indexOf("markdown-you.css"),
        )
    }

    @Test
    fun build_conservativeDensity_injectsConservativeVariables() {
        val html = buildHtml(density = MarkdownDensity.Conservative)

        assertTrue(html.contains("${MarkdownDensity.CSS_VAR_SIDES}: 24px"))
        assertTrue(html.contains("${MarkdownDensity.CSS_VAR_LINE_HEIGHT}: 1.8"))
        assertFalse("Conservative 注入块不得残留 Literal 值", html.contains("${MarkdownDensity.CSS_VAR_SIDES}: 43px"))
    }

    @Test
    fun injectedVariables_coverEveryVariableConsumedByCss() {
        val css = cssFile.readText()
        val consumed = Regex("""var\((--md-reading-[a-z-]+)""").findAll(css).map { it.groupValues[1] }.toSet()
        assertTrue("CSS 必须至少消费一个 --md-reading-* 变量（防正则失配空转）", consumed.isNotEmpty())

        val injected = buildHtml(density = null)
        val missing = consumed.filterNot { injected.contains("$it:") }
        assertTrue("CSS 消费的变量必须全部被注入（名称漂移即红）：缺 $missing", missing.isEmpty())
    }

    private fun buildHtml(density: com.yumiru11.githubapp.core.markdown.MarkdownDensityTokens?): String =
        if (density == null) {
            WebViewHtmlBuilder.build("<p>density</p>", themeVariables = ":root{}", isDark = false)
        } else {
            WebViewHtmlBuilder.build("<p>density</p>", themeVariables = ":root{}", isDark = false, density = density)
        }
}
