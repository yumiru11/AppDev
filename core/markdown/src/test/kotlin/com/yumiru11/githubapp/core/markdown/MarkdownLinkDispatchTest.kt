package com.yumiru11.githubapp.core.markdown

import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [dispatchMarkdownLink] 分流逻辑纯函数测试（无 Compose/Android 依赖，纯 JVM）。
 *
 * 覆盖「URL → ParsedUrl → 应调 onInternalLink 的类型」：内部链接回传具体子类型，
 * 外部链接回传 [ParsedUrl.External]，由上层决定导航或 CustomTabs。
 */
class MarkdownLinkDispatchTest {
    @Test
    fun dispatchMarkdownLink_githubIssueUrl_invokesOnInternalLinkWithIssue() {
        val received = mutableListOf<ParsedUrl>()
        dispatchMarkdownLink("https://github.com/owner/repo/issues/42") { received += it }

        assertEquals(listOf(ParsedUrl.Issue(owner = "owner", repo = "repo", number = 42)), received)
    }

    @Test
    fun dispatchMarkdownLink_githubPullRequestUrl_invokesOnInternalLinkWithPullRequest() {
        val received = mutableListOf<ParsedUrl>()
        dispatchMarkdownLink("https://github.com/owner/repo/pull/7") { received += it }

        assertEquals(listOf(ParsedUrl.PullRequest(owner = "owner", repo = "repo", number = 7)), received)
    }

    @Test
    fun dispatchMarkdownLink_githubRepoUrl_invokesOnInternalLinkWithRepo() {
        val received = mutableListOf<ParsedUrl>()
        dispatchMarkdownLink("https://github.com/owner/repo") { received += it }

        assertEquals(listOf(ParsedUrl.Repo(owner = "owner", repo = "repo")), received)
    }

    @Test
    fun dispatchMarkdownLink_githubUserUrl_invokesOnInternalLinkWithUser() {
        val received = mutableListOf<ParsedUrl>()
        dispatchMarkdownLink("https://github.com/octocat") { received += it }

        assertEquals(listOf(ParsedUrl.User(login = "octocat")), received)
    }

    @Test
    fun dispatchMarkdownLink_externalUrl_invokesOnInternalLinkWithExternal() {
        val received = mutableListOf<ParsedUrl>()
        dispatchMarkdownLink("https://example.com/some/page") { received += it }

        assertEquals(listOf(ParsedUrl.External(url = "https://example.com/some/page")), received)
    }

    @Test
    fun dispatchMarkdownLink_issueReference_invokesOnInternalLinkWithIssueRef() {
        val received = mutableListOf<ParsedUrl>()
        dispatchMarkdownLink("#123") { received += it }

        assertEquals(listOf<ParsedUrl>(ParsedUrl.IssueRef(owner = null, repo = null, number = 123)), received)
    }
// ── resolveMarkdownUrl：仓库上下文相对链接解析（2026-08-14 真机走查：README 相对链接跳浏览器）──

    private val repoUrl = "https://github.com/owner/repo"

    @Test
    fun resolveMarkdownUrl_relativeFilePath_resolvesToBlobUrl() {
        assertEquals(
            "https://github.com/owner/repo/blob/HEAD/docs/guide.md",
            resolveMarkdownUrl("docs/guide.md", repoUrl),
        )
    }

    @Test
    fun resolveMarkdownUrl_relativeFilePathWithLeadingSlash_resolvesToBlobUrl() {
        assertEquals(
            "https://github.com/owner/repo/blob/HEAD/docs/guide.md",
            resolveMarkdownUrl("/docs/guide.md", repoUrl),
        )
    }

    @Test
    fun resolveMarkdownUrl_dotSlashPrefix_resolvesToBlobUrl() {
        assertEquals(
            "https://github.com/owner/repo/blob/HEAD/CONTRIBUTING.md",
            resolveMarkdownUrl("./CONTRIBUTING.md", repoUrl),
        )
    }

    @Test
    fun resolveMarkdownUrl_anchorOnly_resolvesToRepoPageAnchor() {
        assertEquals(
            "https://github.com/owner/repo#installation",
            resolveMarkdownUrl("#installation", repoUrl),
        )
    }

    @Test
    fun resolveMarkdownUrl_absoluteGitHubUrl_staysUntouched() {
        assertEquals(
            "https://github.com/other/proj/issues/3",
            resolveMarkdownUrl("https://github.com/other/proj/issues/3", repoUrl),
        )
    }

    @Test
    fun resolveMarkdownUrl_externalUrl_staysUntouched() {
        assertEquals(
            "https://example.com/page",
            resolveMarkdownUrl("https://example.com/page", repoUrl),
        )
    }

    @Test
    fun resolveMarkdownUrl_noBaseRepo_returnsRawUrl() {
        assertEquals("docs/guide.md", resolveMarkdownUrl("docs/guide.md", null))
    }
}
