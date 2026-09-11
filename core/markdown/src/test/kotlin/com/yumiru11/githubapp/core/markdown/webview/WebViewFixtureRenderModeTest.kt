package com.yumiru11.githubapp.core.markdown.webview

import androidx.compose.material3.lightColorScheme
import com.yumiru11.githubapp.core.markdown.fixture.MarkdownGfmFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WebView 路径的**两级管线产物回归**（ADR-0007：服务端 HTML 优先 → 离线 GFM 降级），
 * 逐夹具（§2.3 全清单）验证注入 HTML 的结构契约。
 *
 * ## 为什么是「产物」而不是「像素」
 *
 * `docs/research/screenshot-automation-alt.md` 已确认：Linux JVM（Robolectric）下 WebView
 * **不能真实栅格化**。对这种路径做像素 golden 只会得到一个假绿的测试。因此本类只断言
 * **注入 WebView 的那份 HTML/CSS 是否正确**——这是纯函数 [WebViewHtmlBuilder] 的产物，
 * 可以逐字节核查；真实渲染像素由 CI 模拟器截图（`.github/scripts/screenshots.sh`
 * 的 `readme-webview.png` / `readme-mermaid.png` 帧）与真机走查兜底。
 *
 * ## 分流契约（本类锁定的核心）
 *
 * | 输入 | RenderMode | 期望产物 |
 * |---|---|---|
 * | GitHub 服务端 HTML | `SERVER_HTML` | 内建 [HtmlSanitizer] 清洗后的 HTML 直接内嵌；**不**加载 markdown-it |
 * | 原始 Markdown | `OFFLINE_MARKDOWN_IT` | 转义后放入 `data-markdown-raw`；加载 markdown-it + highlight.min.js |
 *
 * 端到端「服务端取不到 → 降级离线」的**通道选择**逻辑在 `feature:repo` 的
 * `RepoRepository.getReadme`（`feature/repo/src/main/.../RepoRepository.kt:155-179`），
 * 已由 `RepoRepositoryTest` 覆盖；该选择与 markdown 内容无关，故此处只锁产物。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class WebViewFixtureRenderModeTest {
    private val themeVariables = MaterialYouFusionMapper.buildCss(lightColorScheme(), isDark = false)

    @Test
    fun offlineMode_everyFixture_roundTripsRawMarkdownUncorrupted() {
        // 核心回归：夹具正文经「转义 → data-markdown-raw → 反解」后必须逐字节还原。
        // 若某个字符被吞（历史真机缺陷：& 未转义导致属性截断），任何含该字符的夹具都会红。
        val failures =
            MarkdownGfmFixtures.ALL.mapNotNull { fixture ->
                val markdown = fixture.markdown()
                if (ASSETS_IMAGE_TOKEN in markdown) {
                    return@mapNotNull "${fixture.id}: 夹具含 assets/ 图片，round-trip 期望需同步（见 WebViewHtmlBuilder 的 assets 改写）"
                }

                val html = buildOffline(markdown)
                val raw = extractRawMarkdownAttribute(html) ?: return@mapNotNull "${fixture.id}: data-markdown-raw 属性缺失"
                if (unescapeHtmlAttribute(raw) ==
                    markdown
                ) {
                    null
                } else {
                    "${fixture.id}: round-trip 不等\n  期望: ${markdown.length} 字符\n  实际: ${raw.length} 字符（转义后）"
                }
            }

        assertTrue("离线模式的原始 Markdown 必须无损往返:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun offlineMode_everyFixture_loadsOfflineRendererBundleOnly() {
        MarkdownGfmFixtures.ALL.forEach { fixture ->
            val html = buildOffline(fixture.markdown())
            assertTrue("${fixture.id}: 离线模式必须加载 markdown-it", html.contains("markdown-it.min.js"))
            assertTrue("${fixture.id}: 离线模式必须加载 highlight.js", html.contains("highlight.min.js"))
        }
    }

    @Test
    fun serverHtmlMode_everyFixture_routesThroughSanitizerInsteadOfOfflineRenderer() {
        val failures =
            MarkdownGfmFixtures.ALL.mapNotNull { fixture ->
                val html = buildServerHtml(fixture.markdown())
                when {
                    html.contains("markdown-it.min.js") -> "${fixture.id}: SERVER_HTML 不得加载 markdown-it"
                    html.contains("data-markdown-raw") -> "${fixture.id}: SERVER_HTML 不得注入离线 raw 容器"
                    html.contains("<script>alert") -> "${fixture.id}: 内建 HtmlSanitizer 未生效"
                    !html.contains("class=\"markdown-body\"") -> "${fixture.id}: 缺少 markdown-body 容器"
                    else -> null
                }
            }

        assertTrue("服务端 HTML 通道的分流契约被破坏:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun everyFixture_everyRenderMode_injectsThemeVarsAndNoCredential() {
        MarkdownGfmFixtures.ALL.forEach { fixture ->
            listOf(buildOffline(fixture.markdown()), buildServerHtml(fixture.markdown())).forEach { html ->
                assertTrue("${fixture.id}: 必须注入 theme-vars", html.contains("id=\"theme-vars\""))
                assertTrue("${fixture.id}: 必须注入 Material You primary 变量", html.contains("--md-sys-color-primary"))
                assertTrue("${fixture.id}: 必须加载 DOMPurify（WebView 内权威清洗）", html.contains("purify.min.js"))
                assertTrue("${fixture.id}: 必须加载 renderer.js（bridge 绑定）", html.contains("renderer.js"))
                assertFalse("${fixture.id}: token 绝不进入 HTML", html.contains("ghp_"))
                assertFalse("${fixture.id}: Authorization 绝不进入 HTML", html.contains("Authorization"))
                assertFalse("${fixture.id}: color-mix() 绝不进入注入 CSS", html.contains("color-mix("))
            }
        }
    }

    @Test
    fun everyFixture_renderModeDecidesThemeMarker_notContent() {
        MarkdownGfmFixtures.ALL.take(5).forEach { fixture ->
            val light =
                WebViewHtmlBuilder.build(
                    sanitizedHtml = fixture.markdown(),
                    themeVariables = themeVariables,
                    isDark = false,
                    renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
                )
            val dark =
                WebViewHtmlBuilder.build(
                    sanitizedHtml = fixture.markdown(),
                    themeVariables = themeVariables,
                    isDark = true,
                    renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
                )

            assertTrue("${fixture.id}: 浅色必须带 data-theme=light", light.contains("<html lang=\"en\" data-theme=\"light\">"))
            assertTrue("${fixture.id}: 深色必须带 data-theme=dark", dark.contains("<html lang=\"en\" data-theme=\"dark\">"))
            assertTrue("${fixture.id}: body 也必须带 data-theme", dark.contains("<body data-theme=\"dark\">"))
        }
    }

    @Test
    fun inlineCssContract_everyFixture_omitsStylesheetLinksAndKeepsThemeVarsAfterCss() {
        // 真机修复回归（2026-08-16）：assets 加载失败会丢光样式，故调用点内联 CSS；
        // 且 theme-vars 必须在 CSS 之后，否则 github-markdown-css 的 :root 覆盖注入令牌。
        MarkdownGfmFixtures.ALL.take(3).forEach { fixture ->
            val html =
                WebViewHtmlBuilder.build(
                    sanitizedHtml = fixture.markdown(),
                    themeVariables = themeVariables,
                    isDark = false,
                    renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
                    inlineCss =
                        mapOf(
                            "github-markdown.css" to "/*gfm*/",
                            "markdown-you.css" to "/*you*/",
                            "highlight-theme.css" to "/*hl*/",
                        ),
                )

            assertFalse("${fixture.id}: 内联 CSS 时不得再发 <link>", html.contains("<link rel=\"stylesheet\""))
            assertTrue("${fixture.id}: 必须内联 app-css", html.contains("<style id=\"app-css\">"))
            assertTrue(
                "${fixture.id}: theme-vars 必须位于内联 CSS 之后",
                html.indexOf("id=\"theme-vars\"") > html.indexOf("id=\"app-css\""),
            )
        }
    }

    @Test
    fun serverHtmlMode_fixtureContainingInlineScript_stripsItBeforeInjection() {
        // 夹具 32 内嵌 HTML 里带 <script>，直通服务端 HTML 通道时必须被清洗（纵深防御）
        val html = buildServerHtml(MarkdownGfmFixtures.byId("32-inline-html").markdown())

        assertFalse("script 必须被清除", html.contains("<script>alert"))
        assertFalse("javascript: scheme 必须被清除", html.contains("javascript:"))
    }

    @Test
    fun relativeUrlRewrite_fixture17And21_rewritesOnlyInServerHtmlMode() {
        // 离线模式不改写原始 markdown 的相对路径（raw 已转义为属性值，正则无从匹配）；
        // 这是 catalog 中 17/21 不含 OFFLINE_GFM 路径的代码级证据。
        val imageFixture = MarkdownGfmFixtures.byId("17-image-relative").markdown()
        val linkFixture = MarkdownGfmFixtures.byId("21-relative-link").markdown()

        val offlineImageHtml =
            WebViewHtmlBuilder.build(
                sanitizedHtml = imageFixture,
                themeVariables = themeVariables,
                isDark = false,
                renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
                baseRepoUrl = "https://github.com/octocat/Hello-World",
            )
        val offlineLinkHtml =
            WebViewHtmlBuilder.build(
                sanitizedHtml = linkFixture,
                themeVariables = themeVariables,
                isDark = false,
                renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
                baseRepoUrl = "https://github.com/octocat/Hello-World",
            )

        assertFalse("离线模式不得改写 raw markdown 的相对图片", offlineImageHtml.contains("raw.githubusercontent.com/octocat/Hello-World/HEAD"))
        assertTrue(
            "离线模式原样保留相对图片路径（因此取不到图，见 catalog 17 的说明）",
            offlineImageHtml.contains("./docs/screenshot.png"),
        )
        assertTrue(
            "离线模式原样保留相对链接路径（因此点不开，见 catalog 21 的说明）",
            offlineLinkHtml.contains("./docs/architecture.md"),
        )

        val serverHtml =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<img src=\"docs/screenshot.png\"><a href=\"docs/architecture.md\">d</a>",
                themeVariables = themeVariables,
                isDark = false,
                renderMode = RenderMode.SERVER_HTML,
                baseRepoUrl = "https://github.com/octocat/Hello-World",
            )
        assertTrue(
            "服务端 HTML 模式必须把相对图片改写到 raw 域",
            serverHtml.contains("raw.githubusercontent.com/octocat/Hello-World/HEAD/docs/screenshot.png"),
        )
        assertTrue(
            "服务端 HTML 模式必须把相对链接改写到 blob 域",
            serverHtml.contains("https://github.com/octocat/Hello-World/blob/HEAD/docs/architecture.md"),
        )

        assertEquals(
            "17/21 必须被登记为「离线路径不支持」——上面的产物断言就是这条登记的依据",
            setOf(MarkdownGfmFixtures.RenderPath.NATIVE, MarkdownGfmFixtures.RenderPath.SERVER_HTML),
            MarkdownGfmFixtures.byId("17-image-relative").paths,
        )
        assertEquals(
            setOf(MarkdownGfmFixtures.RenderPath.NATIVE, MarkdownGfmFixtures.RenderPath.SERVER_HTML),
            MarkdownGfmFixtures.byId("21-relative-link").paths,
        )
    }

    private fun buildOffline(markdown: String): String =
        WebViewHtmlBuilder.build(
            sanitizedHtml = markdown,
            themeVariables = themeVariables,
            isDark = false,
            renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
        )

    private fun buildServerHtml(content: String): String =
        WebViewHtmlBuilder.build(
            sanitizedHtml = content,
            themeVariables = themeVariables,
            isDark = false,
            renderMode = RenderMode.SERVER_HTML,
        )

    /** 取 `data-markdown-raw="..."` 的属性值（属性内 `"` 已被转义为 `&quot;`，故首个 `"` 即结束）。 */
    private fun extractRawMarkdownAttribute(html: String): String? {
        val marker = "data-markdown-raw=\""
        val start = html.indexOf(marker)
        if (start < 0) return null
        val valueStart = start + marker.length
        val valueEnd = html.indexOf('"', valueStart)
        if (valueEnd < 0) return null
        return html.substring(valueStart, valueEnd)
    }

    /** [WebViewHtmlBuilder] 属性转义的逆运算（`&amp;` 必须最后还原）。 */
    private fun unescapeHtmlAttribute(value: String): String =
        value
            .replace("&#13;", "\r")
            .replace("&#10;", "\n")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&gt;", ">")
            .replace("&lt;", "<")
            .replace("&amp;", "&")

    private companion object {
        const val ASSETS_IMAGE_TOKEN = "](assets/"
    }
}
