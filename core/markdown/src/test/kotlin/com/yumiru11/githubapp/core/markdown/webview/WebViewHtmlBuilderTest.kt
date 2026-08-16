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
        assertTrue("highlight-theme.css must be loaded", html.contains("highlight-theme.css"))
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
    fun build_sanitizesServerHtmlEvenWhenCallerDidNot() {
        // 安全红线（审查确认）：清洗是组件内建责任，调用方未清洗也必须被强制清洗
        val dangerousHtml = "<p>safe</p><script>alert(1)</script><a href=javascript:alert(2)>x</a>"

        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = dangerousHtml,
                tokens = MarkdownThemeTokens.fromLightScheme(),
            )

        assertFalse("script must be stripped by built-in sanitizer", html.contains("<script>alert"))
        assertFalse("unquoted javascript: href must be stripped", html.contains("javascript:"))
        assertTrue("safe content must survive", html.contains("<p>safe</p>"))
    }

    @Test
    fun build_offlineMode_doesNotMangleRawMarkdownCodeFence() {
        // 离线模式原始 markdown 不做正则清洗（避免破坏代码围栏），渲染产物由 DOMPurify 权威清洗
        val markdown = "```html\n<script>alert(1)</script>\n```"

        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = markdown,
                tokens = MarkdownThemeTokens.fromLightScheme(),
                renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
            )

        assertTrue("code fence content must survive offline mode", html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
    }

    @Test
    fun build_themeVarsStyleComesAfterCssLinks() {
        // 暗色主题修复（审查确认）：theme-vars 必须位于 CSS <link> 之后，
        // 否则 markdown-you.css 的 :root 静态色覆盖注入令牌
        val html = WebViewHtmlBuilder.build(sanitizedHtml = "<p>x</p>", tokens = MarkdownThemeTokens.fromDarkScheme())

        val cssLinkIndex = html.indexOf("markdown-you.css")
        val themeVarsIndex = html.indexOf("id=\"theme-vars\"")
        assertTrue("theme-vars style must exist", themeVarsIndex >= 0)
        assertTrue(
            "theme-vars style must come after markdown-you.css link",
            themeVarsIndex > cssLinkIndex,
        )
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
        assertTrue("offline mode must load highlight.min.js", html.contains("highlight.min.js"))
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

    // ── A9 补强：离线模式 assets/ 相对图片改写 ─────────────────────────

    @Test
    fun build_offlineMode_rewritesAssetsImageToAbsoluteUrl() {
        val markdown = "![alt](assets/logo.png)"

        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = markdown,
                tokens = MarkdownThemeTokens.fromLightScheme(),
                renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
            )

        assertTrue(
            "assets/ image must be rewritten to absolute appassets URL",
            html.contains("![alt](https://appassets.androidplatform.net/assets/logo.png)"),
        )
        assertFalse("relative assets/ path must not remain", html.contains("(assets/logo.png)"))
    }

    @Test
    fun build_offlineMode_rewritesAssetsImageWithDotSlashAndNestedPaths() {
        val markdown = "![a](assets/./img.png) ![b](assets/sub/dir/x.png)"

        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = markdown,
                tokens = MarkdownThemeTokens.fromLightScheme(),
                renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
            )

        assertTrue(
            "./ prefix must be removed in rewritten URL",
            html.contains("![a](https://appassets.androidplatform.net/assets/img.png)"),
        )
        assertTrue("nested asset path must be preserved", html.contains("![b](https://appassets.androidplatform.net/assets/sub/dir/x.png)"))
    }

    @Test
    fun build_offlineMode_keepsAbsoluteImageUrlUnchanged() {
        val markdown = "![x](https://example.com/a.png)"

        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = markdown,
                tokens = MarkdownThemeTokens.fromLightScheme(),
                renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
            )

        assertTrue("absolute image URL must stay untouched", html.contains("![x](https://example.com/a.png)"))
    }

    @Test
    fun build_offlineMode_keepsNonAssetsRelativeImageUnchanged() {
        val markdown = "![x](images/a.png)"

        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = markdown,
                tokens = MarkdownThemeTokens.fromLightScheme(),
                renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
            )

        assertTrue("non-assets relative image must stay untouched", html.contains("![x](images/a.png)"))
        assertFalse(
            "non-assets image must not be rewritten to appassets",
            html.contains("https://appassets.androidplatform.net/assets/images/a.png"),
        )
    }

    // ── A9 补强：data-markdown-raw 特殊字符转义 ───────────────────────

    @Test
    fun build_offlineMode_escapesSpecialCharactersInDataMarkdownRaw() {
        val markdown = "# T & < > \" ' `"

        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = markdown,
                tokens = MarkdownThemeTokens.fromLightScheme(),
                renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
            )

        assertTrue("raw markdown must be wrapped in data-markdown-raw div", html.contains("<div id=\"markdown-raw\" data-markdown-raw=\""))
        assertTrue(html.contains("&amp;"))
        assertTrue(html.contains("&lt;"))
        assertTrue(html.contains("&gt;"))
        assertTrue(html.contains("&quot;"))
        assertTrue(html.contains("&#39;"))
        assertFalse("raw special chars must not leak unescaped", html.contains("# T & < >"))
    }

    @Test
    fun build_offlineMode_escapesNewlinesAndCarriageReturns() {
        val markdown = "line1\nline2\r\nline3"

        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = markdown,
                tokens = MarkdownThemeTokens.fromLightScheme(),
                renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
            )

        assertTrue("newline must be escaped as &#10;", html.contains("line1&#10;line2"))
        assertTrue("carriage return must be escaped as &#13;", html.contains("line2&#13;&#10;line3"))
    }

    // ── A9 补强：inline CSS 注入 ──────────────────────────────────────

    @Test
    fun build_inlineCss_injectsAppCssBlockAndOmitsLinks() {
        val css =
            mapOf(
                "github-markdown.css" to "body{color:red}",
                "markdown-you.css" to ".md-you{x:1}",
                "highlight-theme.css" to ".hljs{y:2}",
            )

        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<p>x</p>",
                themeVariables = "--md-sys-color-primary: #123456;",
                isDark = false,
                inlineCss = css,
            )

        assertTrue("inline css must be wrapped in app-css style block", html.contains("<style id=\"app-css\">"))
        assertTrue(html.contains("body{color:red}"))
        assertTrue(html.contains(".md-you{x:1}"))
        assertTrue(html.contains(".hljs{y:2}"))
        assertFalse("link tags must be omitted when inline css provided", html.contains("<link rel=\"stylesheet\""))
    }

    @Test
    fun build_inlineCss_partialMapInjectsOnlyPresentStyles() {
        val css = mapOf("github-markdown.css" to "body{color:red}")

        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<p>x</p>",
                themeVariables = "--md-sys-color-primary: #123456;",
                isDark = false,
                inlineCss = css,
            )

        assertTrue("present css must be injected", html.contains("body{color:red}"))
        assertFalse("absent css contents must not appear", html.contains(".md-you"))
        assertFalse("absent css must not fall back to link tags", html.contains("<link rel=\"stylesheet\""))
    }

    @Test
    fun build_emptyInlineCss_usesStylesheetLinks() {
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<p>x</p>",
                themeVariables = "--md-sys-color-primary: #123456;",
                isDark = false,
            )

        assertTrue(
            "default build must use link tags",
            html.contains("<link rel=\"stylesheet\" href=\"https://appassets.androidplatform.net/assets/webview/github-markdown.css\""),
        )
        assertFalse(html.contains("app-css"))
    }

    // ── A9 补强：baseRepoUrl 相对路径改写 ─────────────────────────────

    @Test
    fun build_withBaseRepoUrl_rewritesRelativeImgSrcToRawUrl() {
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<img src=\"docs/logo.png\"><p>x</p>",
                tokens = MarkdownThemeTokens.fromLightScheme(),
                baseRepoUrl = "https://github.com/octo/hello",
            )

        assertTrue(
            "relative img src must be rewritten to raw.githubusercontent.com",
            html.contains("src=\"https://raw.githubusercontent.com/octo/hello/HEAD/docs/logo.png\""),
        )
    }

    @Test
    fun build_withBaseRepoUrl_rewritesRelativeAnchorHrefToBlobUrl() {
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<a href=\"docs/page.md\">page</a>",
                tokens = MarkdownThemeTokens.fromLightScheme(),
                baseRepoUrl = "https://github.com/octo/hello",
            )

        assertTrue(
            "relative anchor href must be rewritten to github blob url",
            html.contains("href=\"https://github.com/octo/hello/blob/HEAD/docs/page.md\""),
        )
    }

    @Test
    fun build_withBaseRepoUrl_keepsAbsoluteAndHashUrls() {
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<img src=\"https://cdn.example.com/x.png\"><a href=\"#section\">s</a>",
                tokens = MarkdownThemeTokens.fromLightScheme(),
                baseRepoUrl = "https://github.com/octo/hello",
            )

        assertTrue("absolute img src must stay untouched", html.contains("src=\"https://cdn.example.com/x.png\""))
        assertTrue("hash anchor must stay untouched", html.contains("href=\"#section\""))
    }

    @Test
    fun build_withBaseRepoUrl_rewritesAssetsImgSrcToAppassetsUrl() {
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<img src=\"assets/foo.png\">",
                tokens = MarkdownThemeTokens.fromLightScheme(),
                baseRepoUrl = "https://github.com/octo/hello",
            )

        assertTrue(
            "assets/ img src must be rewritten to appassets url",
            html.contains("src=\"https://appassets.androidplatform.net/assets/webview/foo.png\""),
        )
    }

    @Test
    fun build_withBaseRepoUrl_stripsDotAndRootSlashPrefixes() {
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<img src=\"./docs/x.png\"><img src=\"/docs/y.png\">",
                tokens = MarkdownThemeTokens.fromLightScheme(),
                baseRepoUrl = "https://github.com/octo/hello",
            )

        assertTrue("./ prefix must be stripped", html.contains("src=\"https://raw.githubusercontent.com/octo/hello/HEAD/docs/x.png\""))
        assertTrue("/ prefix must be stripped", html.contains("src=\"https://raw.githubusercontent.com/octo/hello/HEAD/docs/y.png\""))
        assertFalse(html.contains("./docs"))
    }

    @Test
    fun build_baseRepoUrlNull_keepsRelativeUrls() {
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<img src=\"docs/logo.png\"><a href=\"docs/page.md\">p</a>",
                tokens = MarkdownThemeTokens.fromLightScheme(),
            )

        assertTrue("relative img src must stay untouched without baseRepoUrl", html.contains("src=\"docs/logo.png\""))
        assertTrue("relative anchor href must stay untouched without baseRepoUrl", html.contains("href=\"docs/page.md\""))
        assertFalse(html.contains("raw.githubusercontent.com"))
    }

    @Test
    fun build_baseRepoUrlBlank_keepsRelativeUrls() {
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<img src=\"docs/logo.png\">",
                tokens = MarkdownThemeTokens.fromLightScheme(),
                baseRepoUrl = "",
            )

        assertTrue("blank baseRepoUrl must be treated as absent", html.contains("src=\"docs/logo.png\""))
        assertFalse(html.contains("raw.githubusercontent.com"))
    }

    @Test
    fun build_baseRepoUrlMalformed_keepsRelativeUrls() {
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<img src=\"docs/logo.png\">",
                tokens = MarkdownThemeTokens.fromLightScheme(),
                baseRepoUrl = "https://github.com/onlyowner",
            )

        assertTrue("malformed baseRepoUrl must be treated as absent", html.contains("src=\"docs/logo.png\""))
        assertFalse(html.contains("raw.githubusercontent.com"))
    }

    @Test
    fun build_baseRepoUrlWithTrailingSlash_rewrites() {
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<img src=\"docs/logo.png\">",
                tokens = MarkdownThemeTokens.fromLightScheme(),
                baseRepoUrl = "https://github.com/octo/hello/",
            )

        assertTrue(
            "trailing slash must not break rewriting",
            html.contains("src=\"https://raw.githubusercontent.com/octo/hello/HEAD/docs/logo.png\""),
        )
    }

    @Test
    fun build_offlineMode_withBaseRepoUrl_doesNotRewriteEscapedRawMarkdown() {
        // 离线原始 markdown 已转义注入属性值，<img src="..."> 文本不得被 URL 改写正则误伤
        val markdown = "```html\n<img src=\"docs/x.png\">\n```"

        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = markdown,
                tokens = MarkdownThemeTokens.fromLightScheme(),
                renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
                baseRepoUrl = "https://github.com/octo/hello",
            )

        assertTrue("escaped code fence must survive", html.contains("&lt;img src=&quot;docs/x.png&quot;&gt;"))
        assertFalse("raw markdown must not be url-rewritten", html.contains("raw.githubusercontent.com"))
    }
}
