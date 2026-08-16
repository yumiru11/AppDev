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
    fun sanitize_unquotedJavascriptHref_stripped() {
        // 绕过面补强（审查确认）：无引号属性值 href=javascript:...
        val html = "<a href=javascript:alert(1)>click</a>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("unquoted javascript: href must be stripped", result.contains("javascript:"))
        assertFalse(result.contains("alert"))
    }

    @Test
    fun sanitize_xlinkHrefJavascript_stripped() {
        // 绕过面补强（审查确认）：SVG xlink:href 可携带 javascript: 载荷
        val html = "<svg><a xlink:href=\"javascript:alert(1)\">x</a></svg>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("xlink:href javascript: must be stripped", result.contains("javascript:"))
        assertFalse(result.contains("alert"))
    }

    @Test
    fun sanitize_unquotedEventHandler_stripped() {
        // 绕过面补强（审查确认）：无引号事件处理器 onerror=alert(1)
        val html = "<img src=x onerror=alert(1)>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("unquoted onerror must be stripped", result.contains("onerror"))
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

    // ── A8 补强：标签大小写 / 未闭合变体 ──────────────────────────────

    @Test
    fun sanitize_uppercaseScriptTag_stripped() {
        val html = "<SCRIPT>alert('xss')</SCRIPT><p>ok</p>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("uppercase script tag must be stripped", result.contains("<script", ignoreCase = true))
        assertFalse(result.contains("alert"))
        assertTrue(result.contains("<p>ok</p>"))
    }

    @Test
    fun sanitize_unclosedIframeTag_stripped() {
        // 无闭合标签的 iframe（浏览器自动补 </iframe> 并执行内容）
        val html = "<p>before</p><iframe src=\"https://evil.com\">after"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("unclosed iframe must be stripped", result.contains("<iframe"))
        assertFalse(result.contains("evil.com"))
        assertTrue(result.contains("<p>before</p>"))
        assertTrue("content after unclosed iframe must survive", result.contains("after"))
    }

    // ── A8 补强：svg 标签内载荷 ───────────────────────────────────────

    @Test
    fun sanitize_svgOnloadHandler_stripped() {
        val html = "<svg onload=\"alert(1)\"><circle r=\"1\"/></svg>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("svg onload handler must be stripped", result.contains("onload"))
        assertFalse(result.contains("alert"))
        assertTrue("svg shape must survive", result.contains("<circle"))
    }

    @Test
    fun sanitize_svgSmilAnimateHrefValues_stripped() {
        // SMIL mXSS 向量：<animate attributeName="href" values="javascript:..."> 可注入 href
        val html = "<svg><a><animate attributeName=\"href\" values=\"javascript:alert(1)\"/></a></svg>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("SMIL animate tag must be stripped", result.contains("<animate"))
        assertFalse(result.contains("javascript:"))
        assertFalse(result.contains("alert"))
    }

    @Test
    fun sanitize_svgSmilSetAttributeNameEvent_stripped() {
        // SMIL <set> 可把事件名写入 attributeName（onmouseover 经浏览器触发）
        val html = "<svg><set attributeName=\"onmouseover\" to=\"alert(1)\"/></svg>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("SMIL set tag must be stripped", result.contains("<set"))
        assertFalse(result.contains("onmouseover"))
    }

    // ── A8 补强：HTML 实体 / 编码绕过 ────────────────────────────────

    @Test
    fun sanitize_entityEncodedJavascriptHref_stripped() {
        // 属性值字符引用在浏览器解析时先解码：jav&#x61;script: ≡ javascript:
        val html = "<a href=\"jav&#x61;script:alert(1)\">x</a>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("entity-encoded javascript: href must be stripped", result.contains("jav"))
        assertFalse(result.contains("alert"))
    }

    @Test
    fun sanitize_entityEncodedJavascriptSrcUnquoted_stripped() {
        val html = "<img src=&#106;avascript:alert(1)>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("entity-encoded unquoted src must be stripped", result.contains("avascript"))
        assertFalse(result.contains("alert"))
    }

    @Test
    fun sanitize_namedEntityColonHref_stripped() {
        // &colon; 解码为 ':'：javascript&colon;alert(1) ≡ javascript:alert(1)
        val html = "<a href=\"javascript&colon;alert(1)\">x</a>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("&colon; scheme bypass must be stripped", result.contains("javascript"))
        assertFalse(result.contains("alert"))
    }

    @Test
    fun sanitize_newlineInJavascriptScheme_stripped() {
        // WHATWG URL 解析会先移除 tab/换行：java\nscript: ≡ javascript:
        val html = "<a href=\"java\nscript:alert(1)\">x</a>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("newline-in-scheme bypass must be stripped", result.contains("script:"))
        assertFalse(result.contains("alert"))
    }

    @Test
    fun sanitize_encodedTabObfuscatedScheme_stripped() {
        // &#x09; 解码为 tab，与换行同理可混淆 scheme 判定
        val html = "<a href=\"java&#x09;script:alert(1)\">x</a>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("encoded-tab scheme bypass must be stripped", result.contains("script:"))
        assertFalse(result.contains("alert"))
    }

    @Test
    fun sanitize_encodedAmpersandSafeUrl_preserved() {
        // 回归：合法查询串的实体转义不得被误剥
        val html = "<a href=\"https://example.com/?a=1&amp;b=2\">x</a>"

        val result = HtmlSanitizer.sanitize(html)

        assertTrue("safe url with entity ampersand must survive", result.contains("href=\"https://example.com/?a=1&amp;b=2\""))
    }

    // ── A8 补强：其余危险协议变体（vbscript/blob/file/data） ──────────

    @Test
    fun sanitize_vbscriptProtocol_stripped() {
        val html = "<a href=\"vbscript:msgbox(1)\">x</a>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("vbscript: href must be stripped", result.contains("vbscript:"))
    }

    @Test
    fun sanitize_blobUrlSrc_stripped() {
        val html = "<img src=\"blob:https://evil.example/uuid\">"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("blob: src must be stripped", result.contains("blob:"))
    }

    @Test
    fun sanitize_fileProtocolHref_stripped() {
        val html = "<a href=\"file:///etc/passwd\">x</a>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("file: href must be stripped", result.contains("file:"))
    }

    @Test
    fun sanitize_unquotedDataImgSrc_stripped() {
        val html = "<img src=data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("unquoted data: src must be stripped", result.contains("data:"))
    }

    @Test
    fun sanitize_singleQuotedJavascriptHref_stripped() {
        val html = "<a href='javascript:alert(1)'>x</a>"

        val result = HtmlSanitizer.sanitize(html)

        assertFalse("single-quoted javascript: href must be stripped", result.contains("javascript:"))
    }

    @Test
    fun sanitize_mailtoHref_preserved() {
        val html = "<a href=\"mailto:test@example.com\">mail</a>"

        val result = HtmlSanitizer.sanitize(html)

        assertTrue("mailto: href must survive", result.contains("mailto:test@example.com"))
    }

    @Test
    fun sanitize_relativeHrefWithColonPreserved() {
        // 非危险 scheme（./docs:guide 的 scheme 判定为 "./docs"）不得误剥
        val html = "<a href=\"./docs:guide\">x</a>"

        val result = HtmlSanitizer.sanitize(html)

        assertTrue("relative href with colon must survive", result.contains("./docs:guide"))
    }
}
