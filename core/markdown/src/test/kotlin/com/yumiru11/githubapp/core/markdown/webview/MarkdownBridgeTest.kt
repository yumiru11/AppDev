package com.yumiru11.githubapp.core.markdown.webview

import com.yumiru11.githubapp.core.navigation.link.GitHubLinkParser
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MarkdownBridge 单元测试（纯 JVM，无需 Robolectric）。
 *
 * 验证 JS bridge 白名单回调分派：onLinkClick 走 GitHubLinkParser，
 * 其余回调（onCodeCopy/onImageClick/onCheckboxClick/onHeightChanged/onMermaidResult）
 * 转发至回调收集器。
 */
class MarkdownBridgeTest {
    @Test
    fun onLinkClick_externalUrl_dispatchesExternal() {
        val recorder = RecordingBridgeCallback()
        val bridge = MarkdownBridge(recorder, GitHubLinkParser)

        bridge.onLinkClick("https://example.com/path")

        assertEquals(1, recorder.externalClicks.size)
        assertEquals("https://example.com/path", recorder.externalClicks.first())
    }

    @Test
    fun onLinkClick_githubRepoUrl_dispatchesInternalRepo() {
        val recorder = RecordingBridgeCallback()
        val bridge = MarkdownBridge(recorder, GitHubLinkParser)

        bridge.onLinkClick("https://github.com/owner/repo")

        assertEquals(1, recorder.internalRepoClicks.size)
        val parsed = recorder.internalRepoClicks.first()
        assertEquals("owner", parsed.owner)
        assertEquals("repo", parsed.repo)
    }

    @Test
    fun onLinkClick_relativeAnchor_dispatchesExternal() {
        // 相对链接（#section / ./docs）无 owner/repo 上下文时 GitHubLinkParser 视为 External
        val recorder = RecordingBridgeCallback()
        val bridge = MarkdownBridge(recorder, GitHubLinkParser)

        bridge.onLinkClick("#section-anchor")

        assertTrue("relative link without context falls back to external", recorder.externalClicks.isNotEmpty())
    }

    @Test
    fun onCodeCopy_code_forwardedToCallback() {
        val recorder = RecordingBridgeCallback()
        val bridge = MarkdownBridge(recorder, GitHubLinkParser)

        bridge.onCodeCopy("val x = 42")

        assertEquals(1, recorder.copiedCodes.size)
        assertEquals("val x = 42", recorder.copiedCodes.first())
    }

    @Test
    fun onImageClick_src_forwardedToCallback() {
        val recorder = RecordingBridgeCallback()
        val bridge = MarkdownBridge(recorder, GitHubLinkParser)

        bridge.onImageClick("https://example.com/image.png")

        assertEquals(1, recorder.clickedImages.size)
        assertEquals("https://example.com/image.png", recorder.clickedImages.first())
    }

    @Test
    fun onCheckboxClick_indexAndChecked_forwardedToCallback() {
        val recorder = RecordingBridgeCallback()
        val bridge = MarkdownBridge(recorder, GitHubLinkParser)

        bridge.onCheckboxClick(3, true)

        assertEquals(1, recorder.checkboxChanges.size)
        val (index, checked) = recorder.checkboxChanges.first()
        assertEquals(3, index)
        assertTrue(checked)
    }

    @Test
    fun onHeightChanged_height_forwardedToCallback() {
        val recorder = RecordingBridgeCallback()
        val bridge = MarkdownBridge(recorder, GitHubLinkParser)

        bridge.onHeightChanged(420)

        assertEquals(1, recorder.heightChanges.size)
        assertEquals(420, recorder.heightChanges.first())
    }

    @Test
    fun onMermaidResult_renderedAndFailedCounts_forwardedToCallback() {
        val recorder = RecordingBridgeCallback()
        val bridge = MarkdownBridge(recorder, GitHubLinkParser)

        bridge.onMermaidResult(3, 1, true)

        assertEquals(1, recorder.mermaidResults.size)
        val (rendered, failed, engineSupported) = recorder.mermaidResults.first()
        assertEquals(3, rendered)
        assertEquals(1, failed)
        assertTrue("引擎放行时 engineSupported 必须为 true", engineSupported)
    }

    @Test
    fun onMermaidResult_blockedEngine_forwardedAsZeroCountsAndFalse() {
        val recorder = RecordingBridgeCallback()
        val bridge = MarkdownBridge(recorder, GitHubLinkParser)

        bridge.onMermaidResult(0, 0, false)

        assertEquals(1, recorder.mermaidResults.size)
        val (rendered, failed, engineSupported) = recorder.mermaidResults.first()
        assertEquals(0, rendered)
        assertEquals(0, failed)
        assertFalse("门禁拦下时 engineSupported=false 是回退证据", engineSupported)
    }

    @Test
    fun onLinkClick_emptyUrl_noDispatch() {
        val recorder = RecordingBridgeCallback()
        val bridge = MarkdownBridge(recorder, GitHubLinkParser)

        bridge.onLinkClick("")

        assertTrue(recorder.externalClicks.isEmpty())
        assertTrue(recorder.internalRepoClicks.isEmpty())
    }

    /** 收集 bridge 回调的 fake 实现（用于断言分派结果） */
    private class RecordingBridgeCallback : MarkdownBridgeCallback {
        val externalClicks = mutableListOf<String>()
        val internalRepoClicks = mutableListOf<ParsedUrl.Repo>()
        val copiedCodes = mutableListOf<String>()
        val clickedImages = mutableListOf<String>()
        val checkboxChanges = mutableListOf<Pair<Int, Boolean>>()
        val heightChanges = mutableListOf<Int>()
        val mermaidResults = mutableListOf<Triple<Int, Int, Boolean>>()

        override fun onExternalLink(url: String) {
            externalClicks.add(url)
        }

        override fun onInternalLink(parsed: ParsedUrl) {
            if (parsed is ParsedUrl.Repo) {
                internalRepoClicks.add(parsed)
            }
        }

        override fun onCodeCopy(code: String) {
            copiedCodes.add(code)
        }

        override fun onImageClick(src: String) {
            clickedImages.add(src)
        }

        override fun onCheckboxClick(
            index: Int,
            checked: Boolean,
        ) {
            checkboxChanges.add(index to checked)
        }

        override fun onHeightChanged(heightPx: Int) {
            heightChanges.add(heightPx)
        }

        override fun onMermaidResult(
            rendered: Int,
            failed: Int,
            engineSupported: Boolean,
        ) {
            mermaidResults.add(Triple(rendered, failed, engineSupported))
        }
    }
}
