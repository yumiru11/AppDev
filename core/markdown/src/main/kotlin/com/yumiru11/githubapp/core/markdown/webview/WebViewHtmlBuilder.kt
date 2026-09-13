@file:Suppress("ComplexCondition", "CyclomaticComplexMethod")
// 相对资源路径改写分支是需求本身（T3 先例 + 2026-08-16）

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
 *   <meta name="color-scheme" content="light dark">
 *   <link rel="stylesheet" href="github-markdown.css">
 *   <link rel="stylesheet" href="markdown-you.css">
 *   <link rel="stylesheet" href="highlight-theme.css">
 *   [math] <link rel="stylesheet" href="katex/katex.min.css">
 *   <style id="theme-vars">Material You + GitHub semantic variables</style>
 *   [offline] <script src="markdown-it.min.js"></script>
 *   [offline] <script src="highlight.min.js"></script>
 *   [math] <script src="katex/katex.min.js"></script>
 *   [mermaid] <script src="mermaid/mermaid.tiny.js"></script>
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

    /** 离线 markdown 里的本地图片 `![alt](assets/xxx.png)` → 绝对 appassets URL。 */
    private val REWRITE_ASSETS_IMAGE_REGEX =
        Regex("""!\[([^\]]*)\]\(assets/([^)]*)\)""")

    /** GitHub 站点路由关键字（与 `GitHubLinkParser.parsePath` 的 vocabulary 对齐）。 */
    private val SITE_ROUTE_SEGMENTS =
        setOf("issues", "pull", "blob", "tree", "commit", "releases", "discussions", "raw")

    /** 内联 CSS 模式下 KaTeX 样式在 [inlineCss] 里的键（其余三份 CSS 用文件名）。 */
    const val KATEX_CSS_KEY: String = "katex/katex.min.css"

    /** KaTeX 字体在 assets 里的绝对基址（内联 `<style>` 的相对 URL 基于文档 base，须绝对化）。 */
    private const val KATEX_FONT_BASE = "${ASSET_BASE}katex/fonts/"

    // SERVER_HTML 通道的数学标记（实测，见 katex-mermaid-offline-feasibility §3.5）：GitHub 把
    // $…$ / $$…$$ 渲染成 <math-renderer> 占位元素 + 原文，DOMPurify 移除元素但保留文本。
    private val MATH_HTML_REGEX =
        Regex(
            "<math-renderer\\b|\\\$\\\$[^$]+\\\$\\\$|\\\$(?![\\d\\s])[^$]*[^\\s$]\\\$",
            RegexOption.IGNORE_CASE,
        )

    // SERVER_HTML 通道的 Mermaid 标记（实测，见可行性报告 §3.5）：GitHub 把 ```mermaid 围栏
    // 渲染成语法高亮代码块 `<div class="highlight highlight-source-mermaid"><pre>…`，
    // 图由 github.com 前端脚本水合——App 侧用离线 Mermaid Tiny 水合。
    private val MERMAID_HTML_REGEX = Regex("""highlight-source-mermaid|language-mermaid""", RegexOption.IGNORE_CASE)

    private val REPO_ROUTE_NUMBER_REGEX = Regex("""\d+""")

    private val REPO_ROUTE_SHA_REGEX = Regex("""[0-9a-fA-F]{7,40}""")

    /**
     * 兼容旧签名：仅注入 [MarkdownThemeTokens] 的 md-sys 变量（T8/T9 既有调用）。
     */
    fun build(
        sanitizedHtml: String,
        tokens: MarkdownThemeTokens,
        renderMode: RenderMode = RenderMode.SERVER_HTML,
        baseRepoUrl: String? = null,
    ): String = build(sanitizedHtml, tokens.toCssVariables(), tokens.isDark, renderMode, baseRepoUrl)

    /**
     * 组装完整 HTML 文档（融合版）。
     *
     * @param sanitizedHtml 待渲染内容（SERVER_HTML 模式：服务端 HTML，构建内强制清洗；
     *   OFFLINE_MARKDOWN_IT 模式：原始 markdown 文本，由 markdown-it 在 WebView 内渲染）
     * @param themeVariables [MaterialYouFusionMapper] 生成的完整 CSS 变量声明块，
     *   必须放在 github-markdown-css 之后注入（后声明同特异性规则胜出）
     * @param isDark 当前深色主题（同时设置 `<html data-theme>` 与 `<body data-theme>`）
     * @param mermaidRuntimeSupported 当前 WebView 是否支持 Mermaid 运行时（Chromium ≥ 94，
     *   由 [WebViewMermaidSupport] 按设备 UA 判定）。为 false 时即使内容含图也**不注入**
     *   脚本（mermaid.tiny.js 的 class static block 在老引擎上是不可捕获的语法级失败）。
     *   默认 true 仅服务纯 JVM 测试与能力未知场景：WebView 内 renderer.js 仍有独立探测兜底。
     */
    fun build(
        sanitizedHtml: String,
        themeVariables: String,
        isDark: Boolean,
        renderMode: RenderMode = RenderMode.SERVER_HTML,
        baseRepoUrl: String? = null,
        inlineCss: Map<String, String> = emptyMap(),
        mermaidRuntimeSupported: Boolean = true,
    ): String {
        val themeMarker = if (isDark) "dark" else "light"
        val contentBlock = buildContentBlock(sanitizedHtml, renderMode, baseRepoUrl)
        val mathEnabled = needsKatex(renderMode, sanitizedHtml)
        val mermaidEnabled = mermaidRuntimeSupported && needsMermaid(renderMode, sanitizedHtml)
        val runtimeScripts = runtimeScripts(renderMode, sanitizedHtml, mathEnabled, mermaidEnabled)

        return buildString {
            append("<!DOCTYPE html>\n")
            append("<html lang=\"en\" data-theme=\"$themeMarker\">\n")
            append("<head>\n")
            append("  <meta charset=\"utf-8\">\n")
            append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1\">\n")
            append("  <meta name=\"color-scheme\" content=\"light dark\">\n")
            if (inlineCss.isEmpty()) {
                appendKatexStylesheetLink(mathEnabled)
                append("  <link rel=\"stylesheet\" href=\"${ASSET_BASE}github-markdown.css\">")
                append("\n  <link rel=\"stylesheet\" href=\"${ASSET_BASE}markdown-you.css\">")
                append("\n  <link rel=\"stylesheet\" href=\"${ASSET_BASE}highlight-theme.css\">")
            } else {
                append("<style id=\"app-css\">\n")
                appendInlineKatexCss(inlineCss, mathEnabled)
                listOf("github-markdown.css", "markdown-you.css", "highlight-theme.css").forEach { name ->
                    inlineCss[name]?.let { append(it).append("\n") }
                }
                append("</style>")
            }
            // theme-vars 必须位于 CSS <link> 之后：github-markdown-css 与 markdown-you.css
            // 的变量声明会被后声明同特异性规则覆盖，保证 Material You 融合生效。
            append("\n  <style id=\"theme-vars\">\n")
            append(themeVariables)
            append("  </style>\n")
            append(runtimeScripts)
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
     * KaTeX 是否启用（决定注入 katex.min.css / katex.min.js）。
     *
     * - OFFLINE_MARKDOWN_IT：内容是原始 markdown，用 [FeatureDetector.containsMath]（先剥离围栏代码块）
     * - SERVER_HTML：内容是 GitHub HTML，用服务端数学标记 [MATH_HTML_REGEX]
     *
     * KaTeX 的渲染本身发生在 WebView 内 `sanitizeNode` 之后（renderer.js 的 renderMath）——
     * 这里只决定「是否加载运行时」，正确性判据是 JS 侧的严格扫描（防御性双保险）。
     */
    private fun needsKatex(
        renderMode: RenderMode,
        content: String,
    ): Boolean =
        when (renderMode) {
            RenderMode.OFFLINE_MARKDOWN_IT -> FeatureDetector.containsMath(content)
            RenderMode.SERVER_HTML -> MATH_HTML_REGEX.containsMatchIn(content)
        }

    /**
     * Mermaid 是否启用（决定是否注入 mermaid.tiny.js）。
     *
     * - OFFLINE_MARKDOWN_IT：内容是原始 markdown，用 [FeatureDetector.containsMermaid]（```mermaid 围栏）
     * - SERVER_HTML：内容是 GitHub HTML，用 [MERMAID_HTML_REGEX]（highlight-source-mermaid）
     *
     * 渲染本身发生在 WebView 内 `sanitizeNode` 之后（renderer.js 的 renderMermaid）——
     * 这里只决定「是否加载运行时」，正确性判据是 JS 侧对清洗后 DOM 的独立扫描。
     */
    private fun needsMermaid(
        renderMode: RenderMode,
        content: String,
    ): Boolean =
        when (renderMode) {
            RenderMode.OFFLINE_MARKDOWN_IT -> FeatureDetector.containsMermaid(content)
            RenderMode.SERVER_HTML -> MERMAID_HTML_REGEX.containsMatchIn(content)
        }

    /** 非内联 CSS 模式：KaTeX 样式表放最前，让 markdown-you.css / theme-vars 的覆盖规则胜出。 */
    private fun StringBuilder.appendKatexStylesheetLink(mathEnabled: Boolean) {
        if (!mathEnabled) return
        append("  <link rel=\"stylesheet\" href=\"${ASSET_BASE}katex/katex.min.css\">\n")
    }

    /**
     * 内联 CSS 模式：KaTeX 样式表同样内联（App 真机走 [WebViewMarkdownRenderer] 的 inlineCss，
     * 不内联则公式无排版）。字体 URL 必须绝对化——内联 `<style>` 的相对 URL 解析基于**文档 base**
     * 而不是 CSS 文件位置，`fonts/x.woff2` 会 404。
     */
    private fun StringBuilder.appendInlineKatexCss(
        inlineCss: Map<String, String>,
        mathEnabled: Boolean,
    ) {
        if (!mathEnabled) return
        inlineCss[KATEX_CSS_KEY]
            ?.let { append(it.replace("url(fonts/", "url($KATEX_FONT_BASE")).append("\n") }
    }

    /**
     * 运行时脚本：离线模式恒加载 markdown-it + highlight.js；服务端 HTML 模式只在内容含代码块
     * （`<pre`）时加载 highlight.js —— README 主通道此前完全不高亮（2026-09-12 审计缺口 1），
     * 但无代码块的页面也不应为约 130KB 的 highlight.js 付解析成本。KaTeX（约 273KB）同理：
     * 仅内容检测到数学时加载（KaTeX 0.18.7，仅 woff2 字体，离线 assets）。Mermaid Tiny（约 2.5MB
     * 未压缩，deflate 后 ~657KB）同构：仅检测到图定义且设备 WebView 过 Chromium 门禁时加载。
     */
    private fun runtimeScripts(
        renderMode: RenderMode,
        sanitizedHtml: String,
        mathEnabled: Boolean,
        mermaidEnabled: Boolean,
    ): String =
        buildString {
            if (renderMode == RenderMode.OFFLINE_MARKDOWN_IT) {
                append("\n    <script src=\"${ASSET_BASE}markdown-it.min.js\"></script>")
            }
            if (renderMode == RenderMode.OFFLINE_MARKDOWN_IT || containsCodeBlock(sanitizedHtml)) {
                append("\n    <script src=\"${ASSET_BASE}highlight.min.js\"></script>")
            }
            if (mathEnabled) {
                append("\n    <script src=\"${ASSET_BASE}katex/katex.min.js\"></script>")
            }
            if (mermaidEnabled) {
                append("\n    <script src=\"${ASSET_BASE}mermaid/mermaid.tiny.js\"></script>")
            }
        }

    /** 服务端 HTML 的代码块形态：GitHub 渲染为 `<div class="highlight"><pre><code …>`。 */
    private fun containsCodeBlock(html: String): Boolean = html.contains("<pre", ignoreCase = true)

    /**
     * 构建内容块（取决于渲染模式）。
     *
     * - SERVER_HTML：强制经 [HtmlSanitizer] 清洗 + [rewriteRelativeUrls] 改写相对资源路径后嵌入
     *   （组件内建安全责任，任何进入 WebView 的 HTML 必经清洗，即使调用方未清洗）
     * - OFFLINE_MARKDOWN_IT：将原始 markdown 转义后注入 `<div data-markdown-raw="…">`，
     *   由 renderer.js 调用 markdown-it 渲染（原始 markdown 不做正则清洗，避免破坏代码围栏；
     *   渲染产物由 DOMPurify 在 WebView 内权威清洗）。
     *   **相对链接/图片的改写在 renderer.js 的渲染产物层完成**（见 [repoContext] 的说明）：
     *   这里只把仓库上下文以 `data-base-repo` 传给脚本。
     */
    private fun buildContentBlock(
        content: String,
        renderMode: RenderMode,
        baseRepoUrl: String?,
    ): String =
        when (renderMode) {
            RenderMode.SERVER_HTML -> {
                rewriteRelativeUrls(HtmlSanitizer.sanitize(content), baseRepoUrl)
            }

            RenderMode.OFFLINE_MARKDOWN_IT -> {
                // 离线 raw 里的相对图片路径改写成绝对 appassets URL：
                // DOMPurify 的 URI 白名单会把相对 src（assets/...）从 img 上删掉，
                // 导致图片永远加载不出来（2026-08-16 真机诊断：IMG_HTML 无 src）。
                val absolutized =
                    REWRITE_ASSETS_IMAGE_REGEX.replace(content) { match ->
                        val alt = match.groupValues[1]
                        val path = match.groupValues[2].removePrefix("./")
                        "![$alt](https://appassets.androidplatform.net/assets/$path)"
                    }
                val escaped = escapeForHtmlAttribute(absolutized)
                val repoAttribute =
                    repoContext(baseRepoUrl)?.let { " data-base-repo=\"${escapeForHtmlAttribute(it)}\"" }.orEmpty()
                "    <div id=\"markdown-raw\" data-markdown-raw=\"$escaped\"$repoAttribute></div>"
            }
        }

    /**
     * 仓库上下文（`owner/repo`）：离线通道交给 renderer.js 改写相对链接/图片的入参。
     *
     * ## 为什么改写必须在 renderer.js（渲染产物层）而不是这里（转义前/后）
     *
     * 离线模式的输入是**未解析的 markdown 文本**。在这一层改写只有两条路，都不稳：
     * 1. 作用在转义后的属性值上（2026-09-11 之前的实现）——正则 `<img[^>]*?\ssrc="…"`
     *    根本匹配不到 markdown 文本，功能等于没做（D3 的根因）；
     * 2. 作用在转义前的 raw markdown 上——`./docs/x.png` 与代码围栏/行内代码里的示例文本
     *    无法区分，正则必然误伤（既有 [REWRITE_ASSETS_IMAGE_REGEX] 就有这个毛病）。
     *
     * markdown-it 解析后，代码块里的内容已是转义文本、链接目标已是真实属性，改写既准确又
     * 覆盖引用式链接（`[ref]: path`）与内联 HTML `<img src>`。规则与 [rewriteRelativeUrls]
     * 完全一致（同一目标域），因此离线通道与服务端 HTML 通道对同一个仓库解析出同样的链接。
     */
    internal fun repoContext(baseRepoUrl: String?): String? {
        if (baseRepoUrl == null) return null
        val (owner, repo) = parseBaseRepo(baseRepoUrl) ?: return null
        return "$owner/$repo"
    }

    /**
     * 改写**服务端 HTML**（已渲染产物）中的相对资源路径（2026-08-14 真机走查修复：README 相对图/链接空白）。
     *
     * - img src 相对路径 → `https://raw.githubusercontent.com/{owner}/{repo}/HEAD/{path}`
     * - a href 相对路径 → `https://github.com/{owner}/{repo}/blob/HEAD/{path}`
     * - 绝对 URL（http/https/data）与无 baseRepoUrl 时原样保留
     *
     * 只用于 [RenderMode.SERVER_HTML]：该模式的输入已经是 HTML，属性正则安全。
     * 离线通道的输入是 markdown 文本，同一套规则由 `renderer.js` 在渲染产物上执行
     * （见 [repoContext]）。
     */
    private fun rewriteRelativeUrls(
        html: String,
        baseRepoUrl: String?,
    ): String {
        if (baseRepoUrl == null) return html
        val (owner, repo) = parseBaseRepo(baseRepoUrl) ?: return html

        // img src：相对路径 → raw 域（assets/ → appassets 域）
        var out =
            UrlRegexes.IMG_SRC_REGEX.replace(html) { match ->
                val src = match.groupValues[1]
                val target = resolveImageTarget(src, owner, repo)
                if (target == null) match.value else match.value.replaceFirst(src, target)
            }
        // a href：相对路径 → github 路由（点击后由链接分发走应用内导航）
        out =
            UrlRegexes.ANCHOR_HREF_REGEX.replace(out) { match ->
                val href = match.groupValues[1]
                val target = resolveLinkTarget(href, owner, repo)
                if (target == null) match.value else match.value.replaceFirst(href, target)
            }
        return out
    }

    /**
     * 图片目标绝对化（离线侧对应 `renderer.js` 的 `resolveImageTarget`）。
     *
     * `assets/` 是 app 内置资产（原型/测试夹具）→ appassets 域，避免 raw 域被墙；
     * 其余相对路径 → `raw.githubusercontent.com`。返回 null = 原样保留。
     */
    private fun resolveImageTarget(
        src: String,
        owner: String,
        repo: String,
    ): String? {
        if (src.startsWith("http") || src.startsWith("data:") || src.startsWith("#")) return null
        val normalized = normalizeRepoRelativePath(src)
        return if (normalized.startsWith("assets/")) {
            "$ASSET_BASE${normalized.removePrefix("assets/")}"
        } else {
            "https://raw.githubusercontent.com/$owner/$repo/HEAD/$normalized"
        }
    }

    /**
     * 链接目标绝对化（离线侧对应 `renderer.js` 的 `resolveLinkTarget`）。返回 null = 原样保留。
     *
     * 分支顺序（两条通道同一约定）：
     * 1. 绝对 URL / 锚点 → 原样；
     * 2. 站点路径形态（前导 `/` + 第 3 段是 GitHub 路由关键字，如 `/owner/repo/issues/123`）
     *    → `https://github.com{path}`（plan.md §2.11 的输入形态，最终落到应用内路由）；
     * 3. 仓库内路由形态（`issues/123`、`commit/<sha>`）→ `https://github.com/{owner}/{repo}/{path}`
     *    —— 否则会被当成仓库里名为 `issues/123` 的文件；
     * 4. 其余 → `https://github.com/{owner}/{repo}/blob/HEAD/{path}`。
     */
    private fun resolveLinkTarget(
        href: String,
        owner: String,
        repo: String,
    ): String? {
        if (href.startsWith("http") || href.startsWith("mailto:") || href.startsWith("#")) return null
        if (isSiteRoutePath(href)) return "https://github.com/${href.trimStart('/')}"
        val normalized = normalizeRepoRelativePath(href)
        return if (isRepoRoutePath(normalized)) {
            "https://github.com/$owner/$repo/$normalized"
        } else {
            "https://github.com/$owner/$repo/blob/HEAD/$normalized"
        }
    }

    /** 站点路径形态：前导 `/` 且第 3 段是路由关键字（`/owner/repo/issues/123`）。 */
    private fun isSiteRoutePath(path: String): Boolean {
        if (!path.startsWith("/")) return false
        val segments = path.trimStart('/').split('/')
        return segments.size >= 4 && segments[2].lowercase() in SITE_ROUTE_SEGMENTS
    }

    /** 仓库内路由形态：`issues/123`、`pull/7`、`commit/<sha>`、`blob/<ref>/<path>`。 */
    private fun isRepoRoutePath(path: String): Boolean {
        val segments = path.split('/')
        if (segments.size < 2) return false
        return when (segments[0].lowercase()) {
            "issues", "pull", "discussions" -> REPO_ROUTE_NUMBER_REGEX.matches(segments[1])
            "commit" -> REPO_ROUTE_SHA_REGEX.matches(segments[1])
            "blob", "tree", "raw" -> segments.size >= 3 && segments[1].isNotEmpty()
            "releases" -> segments.size >= 3 && segments[1] == "tag" && segments[2].isNotEmpty()
            else -> false
        }
    }

    /**
     * 相对路径归一化（两条通道共用的约定，离线侧对应 `renderer.js` 的 `normalizeRelative`）。
     *
     * 去掉 `./` 与前导 `/`，折叠 `.` 与 `..`；`..` 越出仓库根时**钳制到根**——
     * 否则会产出 `blob/HEAD/../CONTRIBUTING.md` 这类含 `..` 的死链（app 内的
     * [com.yumiru11.githubapp.core.navigation.link.GitHubLinkParser] 会把它当成字面文件名）。
     */
    private fun normalizeRepoRelativePath(path: String): String {
        val normalized = mutableListOf<String>()
        path.removePrefix("/").split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (normalized.isNotEmpty()) normalized.removeAt(normalized.lastIndex)
                else -> normalized.add(segment)
            }
        }
        return if (normalized.isEmpty()) path else normalized.joinToString("/")
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
