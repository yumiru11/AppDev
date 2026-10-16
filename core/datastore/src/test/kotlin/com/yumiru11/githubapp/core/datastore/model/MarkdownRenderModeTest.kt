package com.yumiru11.githubapp.core.datastore.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Markdown 渲染策略枚举的持久化回退与回退许可语义。 */
class MarkdownRenderModeTest {
    @Test
    fun fromName_knownName_returnsThatMode() {
        assertEquals(MarkdownRenderMode.WEBVIEW_ONLY, MarkdownRenderMode.fromName("WEBVIEW_ONLY"))
    }

    @Test
    fun fromName_unknownOrNull_fallsBackToNativePreferred() {
        assertEquals(MarkdownRenderMode.NATIVE_PREFERRED, MarkdownRenderMode.fromName("SOMETHING_ELSE"))
        assertEquals(MarkdownRenderMode.NATIVE_PREFERRED, MarkdownRenderMode.fromName(null))
    }

    @Test
    fun allowsWebViewFallback_onlyNativeOnlyModeDisallows() {
        assertTrue(MarkdownRenderMode.NATIVE_PREFERRED.allowsWebViewFallback)
        assertFalse(MarkdownRenderMode.NATIVE_ONLY.allowsWebViewFallback)
        assertTrue(MarkdownRenderMode.WEBVIEW_PREFERRED.allowsWebViewFallback)
        assertTrue(MarkdownRenderMode.WEBVIEW_ONLY.allowsWebViewFallback)
    }
}
