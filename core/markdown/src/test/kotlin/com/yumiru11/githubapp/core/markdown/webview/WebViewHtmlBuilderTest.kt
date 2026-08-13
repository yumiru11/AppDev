package com.yumiru11.githubapp.core.markdown.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WebViewHtmlBuilder 单元测试（纯 JVM）。
 *
 * 验证 HTML 文档模板组装：CSS 令牌注入、内容注入、token 不进入 HTML。
 * DOMPurify 清洗后的内容断言由 [HtmlSanitizerTest] 覆盖；此处验证模板本身。
 */
class WebViewHtmlBuilderTest {
    @Test
    fun build_emptyContent_producesValidDocument() {
        val tokens = MarkdownThemeTokens.fromLightScheme()

        val html = WebViewHtmlBuilder.build(sanitizedHtml = "", tokens = tokens)

        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("</html>"))
    }

    @Test
    fun build_injectsThemeCssVariables() {
        val tokens =
            MarkdownThemeTokens(
                primary = "#123456",
                onSurface = "#ABCDEF",
                surface = "#000000",
                surfaceContainerLow = "#111111",
                surfaceContainerHigh = "#222222",
                outlineVariant = "#333333",
                isDark = false,
            )

        val html = WebViewHtmlBuilder.build(sanitizedHtml = "<p>x</p>", tokens = tokens)

        assertTrue("primary token must be injected", html.contains("--md-sys-color-primary: #123456"))
        assertTrue("onSurface token must be injected", html.contains("--md-sys-color-on-surface: #ABCDEF"))
    }

    @Test
    fun build_darkTokensIncludeDarkMarker() {
        val tokens =
            MarkdownThemeTokens(
                primary = "#D0BCFF",
                onSurface = "#E6E1E5",
                surface = "#1C1B1F",
                surfaceContainerLow = "#211F26",
                surfaceContainerHigh = "#2B2930",
                outlineVariant = "#49454F",
                isDark = true,
            )

        val html = WebViewHtmlBuilder.build(sanitizedHtml = "<p>x</p>", tokens = tokens)

        assertTrue("dark theme body must carry data-theme=dark", html.contains("data-theme=\"dark\""))
    }

    @Test
    fun build_lightTokensIncludeLightMarker() {
        val tokens = MarkdownThemeTokens.fromLightScheme()

        val html = WebViewHtmlBuilder.build(sanitizedHtml = "<p>x</p>", tokens = tokens)

        assertTrue("light theme body must carry data-theme=light", html.contains("data-theme=\"light\""))
    }

    @Test
    fun build_loadsAssetStylesheetAndScripts() {
        val html = WebViewHtmlBuilder.build(sanitizedHtml = "<p>x</p>", tokens = MarkdownThemeTokens.fromLightScheme())

        assertTrue("markdown-you.css must be loaded", html.contains("markdown-you.css"))
        assertTrue("purify.min.js must be loaded", html.contains("purify.min.js"))
        assertTrue("renderer.js must be loaded", html.contains("renderer.js"))
    }

    @Test
    fun build_doesNotIncludeAnyToken() {
        // 安全红线（plan.md §2.14）：token 绝不进入 HTML/JS
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<p>safe content</p>",
                tokens = MarkdownThemeTokens.fromLightScheme(),
            )

        assertFalse("no ghp_ token must appear", html.contains("ghp_"))
        assertFalse("no Authorization header must appear", html.contains("Authorization"))
        assertFalse("no Bearer token must appear", html.contains("Bearer "))
    }

    @Test
    fun build_appliesSanitizerToContent() {
        val dangerousHtml = "<p>safe</p><script>alert(1)</script>"

        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = HtmlSanitizer.sanitize(dangerousHtml),
                tokens = MarkdownThemeTokens.fromLightScheme(),
            )

        assertFalse("sanitized content must not contain script", html.contains("<script>alert"))
    }

    @Test
    fun build_containsMarkdownBodyWrapper() {
        val html = WebViewHtmlBuilder.build(sanitizedHtml = "<p>x</p>", tokens = MarkdownThemeTokens.fromLightScheme())

        assertTrue("content must be wrapped in markdown-body div", html.contains("class=\"markdown-body\""))
    }

    @Test
    fun build_withOfflineRenderer_usesMarkdownItBundle() {
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "# Heading",
                tokens = MarkdownThemeTokens.fromLightScheme(),
                renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
            )

        assertTrue("offline mode must load markdown-it.min.js", html.contains("markdown-it.min.js"))
        assertTrue("offline mode must load shiki", html.contains("shiki.min.js"))
    }

    @Test
    fun build_withServerHtml_usesServerHtmlDirectly() {
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<h1>Server Rendered</h1>",
                tokens = MarkdownThemeTokens.fromLightScheme(),
                renderMode = RenderMode.SERVER_HTML,
            )

        assertTrue("server html mode must embed sanitized html", html.contains("<h1>Server Rendered</h1>"))
        assertFalse("server html mode must not load markdown-it", html.contains("markdown-it.min.js"))
    }

    @Test
    fun build_escapesContentForOfflineMode() {
        // 离线模式下原始 markdown 注入 data-markdown-raw，需转义引号
        val markdown = "# Title with \"quotes\" and `backtick`"

        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = markdown,
                tokens = MarkdownThemeTokens.fromLightScheme(),
                renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
            )

        assertFalse("quotes must be escaped in offline raw markdown", html.contains("\"quotes\""))
    }

    @Test
    fun build_viewportMetaTagPresent() {
        val html = WebViewHtmlBuilder.build(sanitizedHtml = "", tokens = MarkdownThemeTokens.fromLightScheme())

        assertTrue("viewport meta must be present", html.contains("name=\"viewport\""))
    }

    @Test
    fun build_charsetMetaTagPresent() {
        val html = WebViewHtmlBuilder.build(sanitizedHtml = "", tokens = MarkdownThemeTokens.fromLightScheme())

        assertTrue("charset meta must be present", html.contains("charset=utf-8") || html.contains("charset=\"utf-8\""))
    }

    @Test
    fun build_consistentStructure_lightAndDark() {
        val lightHtml = WebViewHtmlBuilder.build("<p>x</p>", MarkdownThemeTokens.fromLightScheme())
        val darkHtml = WebViewHtmlBuilder.build("<p>x</p>", MarkdownThemeTokens.fromDarkScheme())

        // 结构骨架相同（仅 theme vars + data-theme 不同）
        val lightSkeleton = lightHtml.replace(Regex("--md-sys-color-[a-z-]+: #[0-9A-F]{6};"), "").replace("data-theme=\"light\"", "")
        val darkSkeleton = darkHtml.replace(Regex("--md-sys-color-[a-z-]+: #[0-9A-F]{6};"), "").replace("data-theme=\"dark\"", "")
        assertEquals("structure must be identical modulo theme vars", lightSkeleton, darkSkeleton)
    }
}
