package com.yumiru11.githubapp.core.githubrest.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [RelativeLinkRewriter] 纯函数测试。
 *
 * 覆盖：Markdown 图片/链接、HTML img/a、../ 回溯、./ 前缀、绝对 URL/锚点/Data URI 不修改、
 * 空输入、baseUrl 带文件名/尾斜杠、无匹配文本不变。
 */
class RelativeLinkRewriterTest {
    // ── Markdown 图片 ──────────────────────────────────────────────

    @Test
    fun `rewrite relative image url resolved against base`() {
        val md = "![Logo](assets/logo.png)"
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
        val result = RelativeLinkRewriter.rewrite(md, base)
        assertEquals(
            "![Logo](https://raw.githubusercontent.com/octocat/Hello-World/main/assets/logo.png)",
            result,
        )
    }

    @Test
    fun `rewrite relative image with dot-slash prefix`() {
        val md = "![Screenshot](./screenshots/ui.png)"
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
        val result = RelativeLinkRewriter.rewrite(md, base)
        assertEquals(
            "![Screenshot](https://raw.githubusercontent.com/octocat/Hello-World/main/screenshots/ui.png)",
            result,
        )
    }

    @Test
    fun `rewrite absolute image url unchanged`() {
        val md = "![Logo](https://example.com/logo.png)"
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
        val result = RelativeLinkRewriter.rewrite(md, base)
        assertEquals(md, result)
    }

    @Test
    fun `rewrite image with data URI unchanged`() {
        val md =
            "![Icon](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==)"
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
        val result = RelativeLinkRewriter.rewrite(md, base)
        assertEquals(md, result)
    }

    // ── Markdown 链接 ──────────────────────────────────────────────

    @Test
    fun `rewrite relative link resolved against base`() {
        val md = "[Guide](docs/guide.md)"
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
        val result = RelativeLinkRewriter.rewrite(md, base)
        assertEquals(
            "[Guide](https://raw.githubusercontent.com/octocat/Hello-World/main/docs/guide.md)",
            result,
        )
    }

    @Test
    fun `rewrite absolute link unchanged`() {
        val md = "[GitHub](https://github.com)"
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
        val result = RelativeLinkRewriter.rewrite(md, base)
        assertEquals(md, result)
    }

    @Test
    fun `rewrite anchor link unchanged`() {
        val md = "[Jump to section](#installation)"
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
        val result = RelativeLinkRewriter.rewrite(md, base)
        assertEquals(md, result)
    }

    @Test
    fun `rewrite protocol-relative link unchanged`() {
        val md = "[CDN](//cdn.example.com/lib.js)"
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
        val result = RelativeLinkRewriter.rewrite(md, base)
        assertEquals(md, result)
    }

    @Test
    fun `rewrite mailto link unchanged`() {
        val md = "[Email](mailto:user@example.com)"
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
        val result = RelativeLinkRewriter.rewrite(md, base)
        assertEquals(md, result)
    }

    // ── ../ 向上回溯 ─────────────────────────────────────────────────

    @Test
    fun `rewrite parent directory traversal`() {
        val md = "![Logo](../assets/logo.png)"
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
        val result = RelativeLinkRewriter.rewrite(md, base)
        assertEquals(
            "![Logo](https://raw.githubusercontent.com/octocat/Hello-World/assets/logo.png)",
            result,
        )
    }

    @Test
    fun `rewrite multiple parent directory traversal`() {
        val md = "![Logo](../../assets/logo.png)"
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/docs/"
        val result = RelativeLinkRewriter.rewrite(md, base)
        assertEquals(
            "![Logo](https://raw.githubusercontent.com/octocat/Hello-World/assets/logo.png)",
            result,
        )
    }

    @Test
    fun `rewrite parent traversal does not go above host`() {
        val md = "[Root](../../../../../../etc/passwd)"
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
        val result = RelativeLinkRewriter.rewrite(md, base)
        // 最多回溯到 https://raw.githubusercontent.com/
        assertEquals(
            "[Root](https://raw.githubusercontent.com/etc/passwd)",
            result,
        )
    }

    // ── 混合内容 ────────────────────────────────────────────────────

    @Test
    fun `rewrite mixed content with relative and absolute links`() {
        val md =
            """
            # Project

            ![Logo](assets/logo.png)

            See [GitHub](https://github.com) for details.

            Check the [Guide](docs/guide.md) for setup.
            """.trimIndent()
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
        val result = RelativeLinkRewriter.rewrite(md, base)
        val expected =
            """
            # Project

            ![Logo](https://raw.githubusercontent.com/octocat/Hello-World/main/assets/logo.png)

            See [GitHub](https://github.com) for details.

            Check the [Guide](https://raw.githubusercontent.com/octocat/Hello-World/main/docs/guide.md) for setup.
            """.trimIndent()
        assertEquals(expected, result)
    }

    // ── HTML 标签 ───────────────────────────────────────────────────

    @Test
    fun `rewrite HTML img src relative`() {
        val html = """<img src="assets/logo.png" alt="Logo">"""
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
        val result = RelativeLinkRewriter.rewrite(html, base)
        assertEquals(
            """<img src="https://raw.githubusercontent.com/octocat/Hello-World/main/assets/logo.png" alt="Logo">""",
            result,
        )
    }

    @Test
    fun `rewrite HTML a href relative`() {
        val html = """<a href="docs/guide.md">Guide</a>"""
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
        val result = RelativeLinkRewriter.rewrite(html, base)
        assertEquals(
            """<a href="https://raw.githubusercontent.com/octocat/Hello-World/main/docs/guide.md">Guide</a>""",
            result,
        )
    }

    // ── baseUrl 边界 ────────────────────────────────────────────────

    @Test
    fun `rewrite baseUrl with trailing slash`() {
        val md = "![Logo](assets/logo.png)"
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
        val result = RelativeLinkRewriter.rewrite(md, base)
        assertEquals(
            "![Logo](https://raw.githubusercontent.com/octocat/Hello-World/main/assets/logo.png)",
            result,
        )
    }

    @Test
    fun `rewrite baseUrl with filename`() {
        val md = "![Logo](assets/logo.png)"
        // download_url 格式：https://raw.githubusercontent.com/octocat/Hello-World/main/README.md
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/README.md"
        val result = RelativeLinkRewriter.rewrite(md, base)
        assertEquals(
            "![Logo](https://raw.githubusercontent.com/octocat/Hello-World/main/assets/logo.png)",
            result,
        )
    }

    @Test
    fun `rewrite baseUrl without trailing slash`() {
        val md = "![Logo](assets/logo.png)"
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main"
        val result = RelativeLinkRewriter.rewrite(md, base)
        assertEquals(
            "![Logo](https://raw.githubusercontent.com/octocat/Hello-World/main/assets/logo.png)",
            result,
        )
    }

    // ── 边界情况 ────────────────────────────────────────────────────

    @Test
    fun `rewrite empty input returns empty`() {
        val result = RelativeLinkRewriter.rewrite("", "https://raw.githubusercontent.com/octocat/Hello-World/main/")
        assertEquals("", result)
    }

    @Test
    fun `rewrite no links returns unchanged`() {
        val md = "Just plain text.\nNo links here."
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
        val result = RelativeLinkRewriter.rewrite(md, base)
        assertEquals(md, result)
    }

    @Test
    fun `rewrite blank url in link does not crash`() {
        val md = "[]( )"
        val base = "https://raw.githubusercontent.com/octocat/Hello-World/main/"
        val result = RelativeLinkRewriter.rewrite(md, base)
        assertEquals(md, result)
    }

    // ── resolveUrl 内部方法 ─────────────────────────────────────────

    @Test
    fun `resolveUrl absolute https returns null`() {
        assertNull(RelativeLinkRewriter.resolveUrl("https://example.com/img.png", "https://raw.githubusercontent.com/"))
    }

    @Test
    fun `resolveUrl anchor returns null`() {
        assertNull(RelativeLinkRewriter.resolveUrl("#section", "https://raw.githubusercontent.com/"))
    }

    @Test
    fun `resolveUrl blank returns null`() {
        assertNull(RelativeLinkRewriter.resolveUrl("", "https://raw.githubusercontent.com/"))
    }

    @Test
    fun `resolveUrl relative returns resolved`() {
        val result = RelativeLinkRewriter.resolveUrl("img.png", "https://raw.githubusercontent.com/octocat/Hello-World/main/")
        assertEquals("https://raw.githubusercontent.com/octocat/Hello-World/main/img.png", result)
    }

    // ── normalizeBaseUrl 内部方法 ───────────────────────────────────

    @Test
    fun `normalizeBaseUrl trailing slash unchanged`() {
        val result = RelativeLinkRewriter.normalizeBaseUrl("https://raw.githubusercontent.com/octocat/Hello-World/main/")
        assertEquals("https://raw.githubusercontent.com/octocat/Hello-World/main/", result)
    }

    @Test
    fun `normalizeBaseUrl filename stripped`() {
        val result = RelativeLinkRewriter.normalizeBaseUrl("https://raw.githubusercontent.com/octocat/Hello-World/main/README.md")
        assertEquals("https://raw.githubusercontent.com/octocat/Hello-World/main/", result)
    }

    @Test
    fun `normalizeBaseUrl no slash added`() {
        val result = RelativeLinkRewriter.normalizeBaseUrl("https://raw.githubusercontent.com/octocat/Hello-World/main")
        assertEquals("https://raw.githubusercontent.com/octocat/Hello-World/main/", result)
    }

    @Test
    fun `normalizeBaseUrl directory without trailing slash stays`() {
        val result = RelativeLinkRewriter.normalizeBaseUrl("https://raw.githubusercontent.com/octocat/Hello-World")
        assertEquals("https://raw.githubusercontent.com/octocat/Hello-World/", result)
    }
}
