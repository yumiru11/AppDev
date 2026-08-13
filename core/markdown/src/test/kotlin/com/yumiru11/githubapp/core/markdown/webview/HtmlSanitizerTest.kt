package com.yumiru11.githubapp.core.markdown.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HtmlSanitizer 单元测试（纯 JVM，无需 Robolectric）。
 *
 * 验证服务端 HTML 预清洗白名单（plan.md §2.9 / §2.14 安全锁）：
 * script/iframe/object/embed 等危险标签剥离；on* 事件处理器剥离；
 * javascript: 协议剥离。DOMPurify JS 在 WebView 内二次清洗作为权威清洗。
 */
class HtmlSanitizerTest {
    @Test
    fun sanitize_plainHtml_preserved() {
        val html = "<h1>Title</h1><p>Paragraph with <a href=\"https://example.com\">link</a>.</p>"

        val result = HtmlSanitizer.sanitize(html)

        assertTrue(result.contains("<h1>Title</h1>"))
        assertTrue(result.contains("href=\"https://example.com\""))
    }

    @Test
    fun sanitize_scriptTag_stripped() {
        val html = "<p>safe</p><script>alert('xss')</script><p>after</p>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("script tag must be stripped", result.contains("<script"))
        assertFalse("script body must be stripped", result.contains("alert"))
        assertTrue(result.contains("<p>safe</p>"))
        assertTrue(result.contains("<p>after</p>"))
    }

    @Test
    fun sanitize_scriptWithAttributes_stripped() {
        val html = "<script src=\"evil.js\" type=\"text/javascript\"></script><p>ok</p>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse(result.contains("<script"))
        assertFalse(result.contains("evil.js"))
        assertTrue(result.contains("<p>ok</p>"))
    }

    @Test
    fun sanitize_iframeTag_stripped() {
        val html = "<iframe src=\"https://evil.com\"></iframe><p>ok</p>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse(result.contains("<iframe"))
        assertFalse(result.contains("evil.com"))
    }

    @Test
    fun sanitize_objectEmbedTags_stripped() {
        val html = "<object data=\"evil.swf\"></object><embed src=\"evil.swf\"><p>ok</p>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse(result.contains("<object"))
        assertFalse(result.contains("<embed"))
    }

    @Test
    fun sanitize_formTag_stripped() {
        val html = "<form action=\"https://evil.com\"><input name=\"pw\"></form><p>ok</p>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse(result.contains("<form"))
        assertFalse(result.contains("<input"))
    }

    @Test
    fun sanitize_onClickHandler_stripped() {
        val html = "<p onclick=\"steal()\">click me</p>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("onclick handler must be stripped", result.contains("onclick"))
        assertFalse(result.contains("steal()"))
        assertTrue(result.contains("click me"))
    }

    @Test
    fun sanitize_onErrorHandler_stripped() {
        val html = "<img src=\"x\" onerror=\"alert(1)\">"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("onerror must be stripped", result.contains("onerror"))
        assertFalse(result.contains("alert"))
    }

    @Test
    fun sanitize_javascriptProtocol_stripped() {
        val html = "<a href=\"javascript:alert(1)\">click</a>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("javascript: protocol must be stripped", result.contains("javascript:"))
        assertFalse(result.contains("alert"))
    }

    @Test
    fun sanitize_safeRelativeHref_preserved() {
        val html = "<a href=\"./docs\">docs</a><a href=\"#section\">section</a>"

        val result = HtmlSanitizer.sanitize(html)

        assertTrue(result.contains("href=\"./docs\""))
        assertTrue(result.contains("href=\"#section\""))
    }

    @Test
    fun sanitize_dataUriInImg_stripped() {
        // data: URI 在 img 中可被滥用做 XSS，剥离（图片走 Coil/拦截器加载）
        val html = "<img src=\"data:image/svg+xml,<svg onload=alert(1)>\">"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("data: URI must be stripped from img", result.contains("data:"))
        assertFalse(result.contains("onload"))
    }

    @Test
    fun sanitize_nestedScriptInText_stripped() {
        val html = "<p>text</p><scr<script>ipt>alert(1)</script>"

        val result = HtmlSanitizer.sanitize(html)

        // 嵌套构造后剥离顺序：先剥外层，残留 scr + ipt 拼接无 script 标签
        assertFalse(result.contains("<script>"))
        assertFalse(result.contains("alert"))
    }

    @Test
    fun sanitize_emptyInput_returnsEmpty() {
        assertEquals("", HtmlSanitizer.sanitize(""))
    }

    @Test
    fun sanitize_tableTags_preserved() {
        val html = "<table><tr><th>A</th></tr><tr><td>1</td></tr></table>"

        val result = HtmlSanitizer.sanitize(html)

        assertTrue(result.contains("<table>"))
        assertTrue(result.contains("<td>1</td>"))
    }

    @Test
    fun sanitize_detailsSummary_preserved() {
        val html = "<details><summary>click</summary>hidden</details>"

        val result = HtmlSanitizer.sanitize(html)

        assertTrue(result.contains("<details>"))
        assertTrue(result.contains("<summary>click</summary>"))
    }

    @Test
    fun sanitize_styleAttribute_stripped() {
        // style 属性可携带 CSS 表达式注入，剥离
        val html = "<div style=\"color: expression(alert(1))\">styled</div>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("style attribute must be stripped", result.contains("style="))
        assertFalse(result.contains("expression"))
        assertTrue(result.contains("styled"))
    }
}
