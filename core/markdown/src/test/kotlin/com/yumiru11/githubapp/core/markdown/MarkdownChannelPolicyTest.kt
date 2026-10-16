package com.yumiru11.githubapp.core.markdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 渲染通道裁决的纯函数契约：哪些文档必须回退 WebView。
 *
 * 命名规范：methodName_scenario_expectedBehavior。
 */
class MarkdownChannelPolicyTest {
    @Test
    fun serverHtml_withKatexMarkers_requiresWebView() {
        assertTrue(
            MarkdownChannelPolicy.requiresWebView(
                content = "<p>see <span class=\"katex\">x</span></p>",
                isServerHtml = true,
            ),
        )
    }

    @Test
    fun serverHtml_withMermaidContainer_requiresWebView() {
        assertTrue(
            MarkdownChannelPolicy.requiresWebView(
                content = "<div class=\"mermaid\">graph TD; A-->B</div>",
                isServerHtml = true,
            ),
        )
    }

    @Test
    fun serverHtml_withPlainMarkup_staysNative() {
        assertFalse(
            MarkdownChannelPolicy.requiresWebView(
                content = "<h1>Title</h1><p>Body with <code>code</code></p>",
                isServerHtml = true,
            ),
        )
    }

    @Test
    fun offlineMarkdown_withMermaidFence_requiresWebView() {
        assertTrue(
            MarkdownChannelPolicy.requiresWebView(
                content = "before\n\n```mermaid\ngraph TD; A-->B\n```\n\nafter",
                isServerHtml = false,
            ),
        )
    }

    @Test
    fun offlineMarkdown_withInlineMath_requiresWebView() {
        assertTrue(
            MarkdownChannelPolicy.requiresWebView(
                content = "Euler: " + '$' + "e^{i\\pi}+1=0" + '$',
                isServerHtml = false,
            ),
        )
    }

    @Test
    fun offlineMarkdown_withPlainText_staysNative() {
        assertFalse(
            MarkdownChannelPolicy.requiresWebView(
                content = "# Title\n\n- item\n- item",
                isServerHtml = false,
            ),
        )
    }

    @Test
    fun oversizedDocument_requiresWebViewRegardlessOfFormat() {
        val huge = "x".repeat(MarkdownChannelPolicy.MAX_NATIVE_CHARS + 1)
        assertTrue(MarkdownChannelPolicy.requiresWebView(huge, isServerHtml = true))
        assertTrue(MarkdownChannelPolicy.requiresWebView(huge, isServerHtml = false))
    }
}
