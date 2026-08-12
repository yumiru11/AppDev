package com.yumiru11.githubapp.core.markdown

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * MarkdownViewer 链接点击接线组件测试（Robolectric compose-test，纯 JVM 免模拟器）。
 *
 * 验证 renderer 0.38.1 的 LinkAnnotation → LocalUriHandler 通路把点击交到
 * [MarkdownViewer] 的 onInternalLink 回调，且 URL 已按 GitHubLinkParser 解析。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MarkdownViewerLinkClickTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun markdownViewer_linkClick_internalIssueUrl_invokesOnInternalLinkWithIssue() {
        val received = mutableListOf<ParsedUrl>()
        composeRule.setContent {
            MarkdownViewer(
                markdown = "[issue 42](https://github.com/owner/repo/issues/42)",
                onInternalLink = { received += it },
            )
        }

        composeRule.onNodeWithText("issue 42").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(ParsedUrl.Issue(owner = "owner", repo = "repo", number = 42)), received)
    }

    @Test
    fun markdownViewer_linkClick_externalUrl_invokesOnInternalLinkWithExternal() {
        val received = mutableListOf<ParsedUrl>()
        composeRule.setContent {
            MarkdownViewer(
                markdown = "[external](https://example.com/page)",
                onInternalLink = { received += it },
            )
        }

        composeRule.onNodeWithText("external").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(ParsedUrl.External(url = "https://example.com/page")), received)
    }
}
