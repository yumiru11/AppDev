package com.yumiru11.githubapp.core.markdown.webview

/**
 * WebView 渲染模式（plan.md §2.9）。
 *
 * - [SERVER_HTML]：GitHub 服务端已渲染的 HTML（GET /repos/{o}/{r}/readme Accept html 或 POST /markdown gfm+context）
 * - [OFFLINE_MARKDOWN_IT]：离线 markdown-it + Shiki 渲染（assets 打包，不从网络加载）
 */
enum class RenderMode {
    SERVER_HTML,
    OFFLINE_MARKDOWN_IT,
}

/**
 * WebView HTML 文档模板组装器（plan.md §2.9 / §2.14）。
 *
 * 纯函数，无 Android 依赖，可 JVM 单测。
 *
 * 输出 HTML 结构：
 * ```
 * <!DOCTYPE html>
 * <html>
 * <head>
 *   <meta charset="utf-8">
 *   <meta name="viewport" ...>
 *   <style id="theme-vars">:root { --md-sys-color-*: #...; }</style>
 *   <link rel="stylesheet" href="markdown-you.css">
 *   [offline] <script src="markdown-it.min.js"></script>
 *   [offline] <script src="shiki.min.js"></script>
 *   <script src="purify.min.js"></script>
 * </head>
 * <body data-theme="light|dark">
 *   <div class="markdown-body">[sanitized content or raw markdown for offline]</div>
 *   <script src="renderer.js"></script>
 * </body>
 * </html>
 * ```
 *
 * 安全（plan.md §2.14）：
 * - token 绝不进入 HTML/JS（本函数仅接收 sanitized HTML + theme tokens，无 token 入参）
 * - 内容已由 [HtmlSanitizer] 预清洗（调用方负责），DOMPurify 在 WebView 内二次清洗
 * - 资源经 WebViewAssetLoader 加载（appassets.androidplatform.net 域，禁 file://）
 */
object WebViewHtmlBuilder {
    private const val ASSET_BASE = "https://appassets.androidplatform.net/assets/webview/"

    /**
     * 组装完整 HTML 文档。
     *
     * @param sanitizedHtml 已清洗的内容（SERVER_HTML 模式：服务端 HTML；
     *   OFFLINE_MARKDOWN_IT 模式：原始 markdown 文本，由 markdown-it 在 WebView 内渲染）
     * @param tokens Material You 主题令牌（注入 :root CSS 变量）
     * @param renderMode 渲染模式（决定是否加载 markdown-it/Shiki 与内容注入方式）
     * @return 完整 HTML 文档字符串
     */
    fun build(
        sanitizedHtml: String,
        tokens: MarkdownThemeTokens,
        renderMode: RenderMode = RenderMode.SERVER_HTML,
    ): String {
        val themeVars = tokens.toCssVariables()
        val themeMarker = if (tokens.isDark) "dark" else "light"
        val contentBlock = buildContentBlock(sanitizedHtml, renderMode)
        val offlineScripts =
            if (renderMode == RenderMode.OFFLINE_MARKDOWN_IT) {
                "\n    <script src=\"${ASSET_BASE}markdown-it.min.js\"></script>" +
                    "\n    <script src=\"${ASSET_BASE}shiki.min.js\"></script>"
            } else {
                ""
            }

        return buildString {
            append("<!DOCTYPE html>\n")
            append("<html lang=\"en\">\n")
            append("<head>\n")
            append("  <meta charset=\"utf-8\">\n")
            append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1\">\n")
            append("  <style id=\"theme-vars\">\n")
            append(themeVars)
            append("  </style>\n")
            append("  <link rel=\"stylesheet\" href=\"${ASSET_BASE}markdown-you.css\">")
            append("\n  <link rel=\"stylesheet\" href=\"${ASSET_BASE}highlight-theme.css\">")
            append(offlineScripts)
            append("\n  <script src=\"${ASSET_BASE}purify.min.js\"></script>\n")
            append("</head>\n")
            append("<body data-theme=\"$themeMarker\">\n")
            append("  <div class=\"markdown-body\">\n")
            append(contentBlock)
            append("\n  </div>\n")
            append("  <script src=\"${ASSET_BASE}renderer.js\"></script>\n")
            append("</body>\n")
            append("</html>\n")
        }
    }

    /**
     * 构建内容块（取决于渲染模式）。
     *
     * - SERVER_HTML：直接嵌入 sanitized HTML
     * - OFFLINE_MARKDOWN_IT：将原始 markdown 转义后注入 `<div data-markdown-raw="...">`，
     *   由 renderer.js 调用 markdown-it 渲染
     */
    private fun buildContentBlock(
        content: String,
        renderMode: RenderMode,
    ): String =
        when (renderMode) {
            RenderMode.SERVER_HTML -> {
                content
            }

            RenderMode.OFFLINE_MARKDOWN_IT -> {
                val escaped = escapeForHtmlAttribute(content)
                "    <div id=\"markdown-raw\" data-markdown-raw=\"$escaped\"></div>"
            }
        }

    /** 转义 markdown 文本以安全注入 HTML 属性值（双引号、&、<） */
    private fun escapeForHtmlAttribute(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("\n", "&#10;")
            .replace("\r", "&#13;")
}
