@file:Suppress("ComplexCondition") // 相对资源路径判定（http/https/data:/# 四种绝对形态 + 相对）分支天然多，拆散反损可读性（T3 先例）

package com.yumiru11.githubapp.core.markdown.webview

/**
 * WebView 渲染模式（plan.md §2.9）。
 *
 * - [SERVER_HTML]：GitHub 服务端已渲染的 HTML（GET /repos/{o}/{r}/readme Accept html 或 POST /markdown gfm+context）
 * - [OFFLINE_MARKDOWN_IT]：离线 markdown-it + highlight.js 渲染（assets 打包，不从网络加载）
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
 *   <link rel="stylesheet" href="markdown-you.css">
 *   <link rel="stylesheet" href="highlight-theme.css">
 *   <style id="theme-vars">:root { --md-sys-color-*: #...; }</style>
 *   [offline] <script src="markdown-it.min.js"></script>
 *   [offline] <script src="highlight.min.js"></script>
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
 * - SERVER_HTML 内容在 [build] 内强制经 [HtmlSanitizer] 清洗（组件内建责任，不依赖调用方），
 *   DOMPurify 在 WebView 内二次清洗
 * - 资源经 WebViewAssetLoader 加载（appassets.androidplatform.net 域，禁 file://）
 */
object WebViewHtmlBuilder {
    private const val ASSET_BASE = "https://appassets.androidplatform.net/assets/webview/"

    /**
     * 组装完整 HTML 文档。
     *
     * @param sanitizedHtml 待渲染内容（SERVER_HTML 模式：服务端 HTML，[build] 内强制清洗；
     *   OFFLINE_MARKDOWN_IT 模式：原始 markdown 文本，由 markdown-it 在 WebView 内渲染）
     * @param tokens Material You 主题令牌（注入 :root CSS 变量）
     * @param renderMode 渲染模式（决定是否加载 markdown-it/highlight.js 与内容注入方式）
     * @return 完整 HTML 文档字符串
     */
    fun build(
        sanitizedHtml: String,
        tokens: MarkdownThemeTokens,
        renderMode: RenderMode = RenderMode.SERVER_HTML,
        baseRepoUrl: String? = null,
    ): String {
        val themeVars = tokens.toCssVariables()
        val themeMarker = if (tokens.isDark) "dark" else "light"
        val contentBlock = rewriteRelativeUrls(buildContentBlock(sanitizedHtml, renderMode), baseRepoUrl)
        val offlineScripts =
            if (renderMode == RenderMode.OFFLINE_MARKDOWN_IT) {
                "\n    <script src=\"${ASSET_BASE}markdown-it.min.js\"></script>" +
                    "\n    <script src=\"${ASSET_BASE}highlight.min.js\"></script>"
            } else {
                ""
            }

        return buildString {
            append("<!DOCTYPE html>\n")
            append("<html lang=\"en\">\n")
            append("<head>\n")
            append("  <meta charset=\"utf-8\">\n")
            append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1\">\n")
            append("  <link rel=\"stylesheet\" href=\"${ASSET_BASE}markdown-you.css\">")
            append("\n  <link rel=\"stylesheet\" href=\"${ASSET_BASE}highlight-theme.css\">")
            // theme-vars 必须位于 CSS <link> 之后：markdown-you.css 的 :root 静态色
            // 会覆盖注入令牌，后声明同特异性规则胜出（暗色主题修复，审查确认）
            append("\n  <style id=\"theme-vars\">\n")
            append(themeVars)
            append("  </style>\n")
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
     * - SERVER_HTML：强制经 [HtmlSanitizer] 清洗后嵌入（组件内建安全责任，任何进入
     *   WebView 的 HTML 必经清洗，即使调用方未清洗）
     * - OFFLINE_MARKDOWN_IT：将原始 markdown 转义后注入 `<div data-markdown-raw="...">`，
     *   由 renderer.js 调用 markdown-it 渲染（原始 markdown 不做正则清洗，避免破坏代码围栏；
     *   渲染产物由 DOMPurify 在 WebView 内权威清洗）
     */
    private fun buildContentBlock(
        content: String,
        renderMode: RenderMode,
    ): String =
        when (renderMode) {
            RenderMode.SERVER_HTML -> {
                HtmlSanitizer.sanitize(content)
            }

            RenderMode.OFFLINE_MARKDOWN_IT -> {
                val escaped = escapeForHtmlAttribute(content)
                "    <div id=\"markdown-raw\" data-markdown-raw=\"$escaped\"></div>"
            }
        }

    /**
     * 改写服务端 HTML 中的相对资源路径（2026-08-14 真机走查修复：README 相对图/链接空白）。
     *
     * - img src 相对路径 → `https://raw.githubusercontent.com/{owner}/{repo}/HEAD/{path}`
     * - a href 相对路径 → `https://github.com/{owner}/{repo}/blob/HEAD/{path}`
     * - 绝对 URL（http/https/data）与无 baseRepoUrl 时原样保留
     */
    private fun rewriteRelativeUrls(
        html: String,
        baseRepoUrl: String?,
    ): String {
        if (baseRepoUrl == null) return html
        val (owner, repo) = parseBaseRepo(baseRepoUrl) ?: return html

        fun resolve(path: String): String =
            if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("data:") || path.startsWith("#")) {
                path
            } else {
                val trimmed = path.removePrefix("./").removePrefix("/")
                if (trimmed.isEmpty()) path else trimmed
            }

        var out = html
        // img src：相对路径 → raw 域
        out =
            UrlRegexes.IMG_SRC_REGEX.replace(out) { match ->
                val src = resolve(match.groupValues[1])
                if (src.startsWith("http") || src.startsWith("data:")) {
                    match.value
                } else {
                    val rawUrl = "https://raw.githubusercontent.com/$owner/$repo/HEAD/$src"
                    match.value.replaceFirst(match.groupValues[1], rawUrl)
                }
            }
        // a href：相对路径 → github blob 域（点击后由链接分发走应用内导航）
        out =
            UrlRegexes.ANCHOR_HREF_REGEX.replace(out) { match ->
                val href = resolve(match.groupValues[1])
                if (href.startsWith("http") || href.startsWith("mailto:") || href.startsWith("#")) {
                    match.value
                } else {
                    val blobUrl = "https://github.com/$owner/$repo/blob/HEAD/$href"
                    match.value.replaceFirst(match.groupValues[1], blobUrl)
                }
            }
        return out
    }

    /** 解析 `https://github.com/{owner}/{repo}` → (owner, repo)；非法格式返回 null */
    private fun parseBaseRepo(baseRepoUrl: String): Pair<String, String>? {
        val parts = baseRepoUrl.removePrefix("https://github.com/").split('/')
        return if (parts.size >= 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
            parts[0] to parts[1]
        } else {
            null
        }
    }

    private object UrlRegexes {
        val IMG_SRC_REGEX = Regex("""<img[^>]*?\ssrc="([^"]*)"""")
        val ANCHOR_HREF_REGEX = Regex("""<a[^>]*?\shref="([^"]*)"""")
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
