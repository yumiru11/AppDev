package com.yumiru11.githubapp.core.githubrest.util

/**
 * 解析 Markdown 文本中的相对链接/图片路径为绝对 URL（基于 GitHub raw content 或 blob 基准 URL）。
 *
 * 典型用法（baseUrl 来自 [ReadmeDto.downloadUrl] 或 raw.githubusercontent.com 前缀）：
 * ```
 * val baseUrl = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
 * val result = RelativeLinkRewriter.rewrite(markdown, baseUrl)
 * ```
 *
 * 规则：
 * - 绝对 URL（http://、https://、data:、mailto:、tel:）→ 不修改
 * - 锚点（#xxx）→ 不修改
 * - 协议相对 URL（//xxx）→ 不修改
 * - 相对路径（./xxx、xxx、../xxx）→ 基于 baseUrl 解析为绝对 URL
 * - 同时处理 Markdown 语法 `[text](url)`、`![alt](url)` 和内联 HTML `<img src>`、`<a href>`
 */
object RelativeLinkRewriter {
    /**
     * @param markdown 原始 Markdown 文本
     * @param baseUrl 基准 URL，例如 `https://raw.githubusercontent.com/owner/repo/branch/`
     *   或 `https://github.com/owner/repo/blob/branch/README.md`（自动处理文件名和尾斜杠）
     * @return 相对链接已解析为绝对 URL 的 Markdown 文本
     */
    fun rewrite(
        markdown: String,
        baseUrl: String,
    ): String {
        val baseDir = normalizeBaseUrl(baseUrl)
        // 1. 处理 Markdown 图片 ![alt](url) 和链接 [text](url)
        var result =
            markdown.replace(MARKDOWN_LINK_REGEX) { match ->
                val prefix = match.groupValues[1] // "!" for images, "" for links
                val text = match.groupValues[2]
                val url = match.groupValues[3]
                val resolved = resolveUrl(url, baseDir)
                if (resolved != null) {
                    "$prefix[$text]($resolved)"
                } else {
                    match.value
                }
            }
        // 2. 处理 HTML <img src="..."> 和 <a href="...">
        result =
            result.replace(HTML_ATTR_REGEX) { match ->
                val url = match.groupValues[2]
                val resolved = resolveUrl(url, baseDir)
                if (resolved != null) {
                    match.value.replace(url, resolved)
                } else {
                    match.value
                }
            }
        return result
    }

    /**
     * 解析相对 URL 为绝对 URL。
     * @return 解析后的绝对 URL，若无需修改（已是绝对/锚点等）则返回 null
     */
    internal fun resolveUrl(
        url: String,
        baseDir: String,
    ): String? {
        if (url.isBlank()) return null

        // 跳过无需修改的格式
        if (SKIP_PREFIXES.any { url.startsWith(it) }) return null

        // 拼接路径
        val cleanPath = url.removePrefix("./")
        return if (cleanPath.startsWith("../")) {
            resolveUpPath(baseDir, cleanPath)
        } else {
            baseDir + cleanPath
        }
    }

    /**
     * 处理 ../ 向上回溯路径。
     * 例如 baseDir = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
     * path = "../assets/logo.png" → "https://raw.githubusercontent.com/octocat/Hello-World/assets/logo.png"
     */
    internal fun resolveUpPath(
        baseDir: String,
        path: String,
    ): String {
        var segments = baseDir.removeSuffix("/").split("/").toMutableList()
        val parts = path.split("/")
        var upCount = 0
        val remainingParts = mutableListOf<String>()
        for (part in parts) {
            if (part == "..") {
                upCount++
            } else if (part.isNotBlank()) {
                remainingParts.add(part)
            }
        }
        // 向上回溯，保留至少 3 段（协议 + 主机名）
        val maxUp = (segments.size - 3).coerceAtLeast(0)
        repeat(upCount.coerceAtMost(maxUp)) {
            if (segments.isNotEmpty()) segments.removeLast()
        }
        return segments.joinToString("/") + "/" + remainingParts.joinToString("/")
    }

    /**
     * 规范化基准 URL：确保以 / 结尾、去掉文件名（如果有）。
     */
    internal fun normalizeBaseUrl(baseUrl: String): String {
        var url = baseUrl.trim()
        if (!url.endsWith("/")) {
            val lastSegment = url.substringAfterLast("/")
            if (lastSegment.contains(".")) {
                url = url.substringBeforeLast("/") + "/"
            } else {
                url += "/"
            }
        }
        return url
    }

    private val MARKDOWN_LINK_REGEX = Regex("""(!?)\[([^\]]*)\]\(([^)]+)\)""")
    private val HTML_ATTR_REGEX = Regex("""<(?:img|a)\s[^>]*?(src|href)=["']([^"']+)["'][^>]*?>""")

    private val SKIP_PREFIXES =
        listOf(
            "http://",
            "https://",
            "data:",
            "mailto:",
            "tel:",
            "#",
            "//",
        )
}
