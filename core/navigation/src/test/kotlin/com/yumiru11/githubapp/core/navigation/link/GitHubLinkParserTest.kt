package com.yumiru11.githubapp.core.navigation.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubLinkParserTest {
    // ---- 绝对链接 ----

    @Test
    fun parseUrl_absoluteRepoUrl_returnsRepo() {
        assertEquals(
            ParsedUrl.Repo("owner", "repo"),
            GitHubLinkParser.parseUrl("https://github.com/owner/repo"),
        )
    }

    @Test
    fun parseUrl_absoluteIssueUrl_returnsIssue() {
        assertEquals(
            ParsedUrl.Issue("owner", "repo", 123),
            GitHubLinkParser.parseUrl("https://github.com/owner/repo/issues/123"),
        )
    }

    @Test
    fun parseUrl_absolutePullUrl_returnsPullRequest() {
        assertEquals(
            ParsedUrl.PullRequest("owner", "repo", 456),
            GitHubLinkParser.parseUrl("https://github.com/owner/repo/pull/456"),
        )
    }

    @Test
    fun parseUrl_absoluteCommitUrl_returnsCommit() {
        val sha = "0123456789abcdef0123456789abcdef01234567"
        assertEquals(
            ParsedUrl.Commit("owner", "repo", sha),
            GitHubLinkParser.parseUrl("https://github.com/owner/repo/commit/$sha"),
        )
    }

    @Test
    fun parseUrl_absoluteBlobUrl_returnsBlob() {
        assertEquals(
            ParsedUrl.Blob("owner", "repo", "main", "src/Main.kt"),
            GitHubLinkParser.parseUrl("https://github.com/owner/repo/blob/main/src/Main.kt"),
        )
    }

    @Test
    fun parseUrl_absoluteTreeUrl_returnsTree() {
        assertEquals(
            ParsedUrl.Tree("owner", "repo", "main", ""),
            GitHubLinkParser.parseUrl("https://github.com/owner/repo/tree/main"),
        )
    }

    @Test
    fun parseUrl_absoluteTreeUrlWithPath_returnsTreeWithPath() {
        assertEquals(
            ParsedUrl.Tree("owner", "repo", "main", "src"),
            GitHubLinkParser.parseUrl("https://github.com/owner/repo/tree/main/src"),
        )
    }

    @Test
    fun parseUrl_absoluteReleasesUrl_returnsReleaseWithoutTag() {
        assertEquals(
            ParsedUrl.Release("owner", "repo", null),
            GitHubLinkParser.parseUrl("https://github.com/owner/repo/releases"),
        )
    }

    @Test
    fun parseUrl_absoluteReleaseTagUrl_returnsReleaseWithTag() {
        assertEquals(
            ParsedUrl.Release("owner", "repo", "v1.0.0"),
            GitHubLinkParser.parseUrl("https://github.com/owner/repo/releases/tag/v1.0.0"),
        )
    }

    @Test
    fun parseUrl_absoluteDiscussionsUrl_returnsDiscussion() {
        assertEquals(
            ParsedUrl.Discussion("owner", "repo", 5),
            GitHubLinkParser.parseUrl("https://github.com/owner/repo/discussions/5"),
        )
    }

    @Test
    fun parseUrl_absoluteUserUrl_returnsUser() {
        assertEquals(
            ParsedUrl.User("user"),
            GitHubLinkParser.parseUrl("https://github.com/user"),
        )
    }

    // ---- 相对链接 ----

    @Test
    fun parseUrl_relativeRepoUrl_returnsRepo() {
        assertEquals(
            ParsedUrl.Repo("owner", "repo"),
            GitHubLinkParser.parseUrl("owner/repo"),
        )
    }

    @Test
    fun parseUrl_relativeRepoUrlWithLeadingSlash_returnsRepo() {
        assertEquals(
            ParsedUrl.Repo("octocat", "Hello-World"),
            GitHubLinkParser.parseUrl("/octocat/Hello-World"),
        )
    }

    @Test
    fun parseUrl_relativeIssueRefUrl_returnsIssue() {
        assertEquals(
            ParsedUrl.Issue("owner", "repo", 123),
            GitHubLinkParser.parseUrl("owner/repo#123"),
        )
    }

    @Test
    fun parseUrl_issueNumberOnly_returnsIssueRef() {
        assertEquals(
            ParsedUrl.IssueRef(null, null, 123),
            GitHubLinkParser.parseUrl("#123"),
        )
    }

    @Test
    fun parseUrl_relativeBlobUrl_returnsExternal() {
        // ../blob/main/file 无 owner/repo 语境，无法归属到 Blob，按 External 处理
        assertTrue(
            GitHubLinkParser.parseUrl("../blob/main/file") is ParsedUrl.External,
        )
    }

    // ---- 协议相对 ----

    @Test
    fun parseUrl_protocolRelativeUrl_returnsRepo() {
        assertEquals(
            ParsedUrl.Repo("owner", "repo"),
            GitHubLinkParser.parseUrl("//github.com/owner/repo"),
        )
    }

    // ---- 提及 ----

    @Test
    fun parseUrl_mentionUsername_returnsUser() {
        assertEquals(
            ParsedUrl.User("username"),
            GitHubLinkParser.parseUrl("@username"),
        )
    }

    @Test
    fun parseUrl_mentionOrgTeam_returnsExternal() {
        // @org/team 无对应路由，按 External 处理
        assertTrue(
            GitHubLinkParser.parseUrl("@org/team") is ParsedUrl.External,
        )
    }

    // ---- 裸 sha ----

    @Test
    fun parseUrl_bareSha_returnsCommit() {
        val sha = "0123456789abcdef0123456789abcdef01234567"
        assertEquals(
            ParsedUrl.Commit(null, null, sha),
            GitHubLinkParser.parseUrl(sha),
        )
    }

    // ---- 非法 / 外部 ----

    @Test
    fun parseUrl_nonGithubDomain_returnsExternal() {
        assertTrue(
            GitHubLinkParser.parseUrl("https://example.com/owner/repo") is ParsedUrl.External,
        )
    }

    @Test
    fun parseUrl_emptyString_returnsExternal() {
        assertTrue(
            GitHubLinkParser.parseUrl("") is ParsedUrl.External,
        )
    }

    @Test
    fun parseUrl_githubUnrecognizedPath_returnsExternal() {
        assertTrue(
            GitHubLinkParser.parseUrl("https://github.com/owner/repo/unknown/thing") is ParsedUrl.External,
        )
    }

    // ---- 边界 ----

    @Test
    fun parseUrl_ownerWithHyphen_returnsRepo() {
        assertEquals(
            ParsedUrl.Repo("my-owner", "my-repo"),
            GitHubLinkParser.parseUrl("https://github.com/my-owner/my-repo"),
        )
    }

    @Test
    fun parseUrl_uppercasePathSegments_returnsRepo() {
        assertEquals(
            ParsedUrl.Repo("Owner", "Repo"),
            GitHubLinkParser.parseUrl("https://github.com/Owner/Repo"),
        )
    }

    @Test
    fun parseUrl_repoUrlWithQuery_returnsRepo() {
        assertEquals(
            ParsedUrl.Repo("owner", "repo"),
            GitHubLinkParser.parseUrl("https://github.com/owner/repo?tab=readme-ov-file"),
        )
    }

    @Test
    fun parseUrl_issueUrlWithTrailingSlash_returnsIssue() {
        assertEquals(
            ParsedUrl.Issue("owner", "repo", 123),
            GitHubLinkParser.parseUrl("https://github.com/owner/repo/issues/123/"),
        )
    }

    @Test
    fun parseUrl_issueNumberNonNumeric_returnsExternal() {
        assertTrue(
            GitHubLinkParser.parseUrl("https://github.com/owner/repo/issues/abc") is ParsedUrl.External,
        )
    }

    @Test
    fun parseUrl_issueListUrl_returnsIssueList() {
        // 回归：列表页此前返回 External → CustomTabs 隐式 VIEW 命中本应用自身
        // intent-filter，弹「打开方式」选择器自循环（CI 实拍 C 板第 2 帧）
        assertEquals(
            ParsedUrl.IssueList("yumiru11", "AppDev"),
            GitHubLinkParser.parseUrl("https://github.com/yumiru11/AppDev/issues"),
        )
    }

    @Test
    fun parseUrl_commitShaTooShort_returnsExternal() {
        assertTrue(
            GitHubLinkParser.parseUrl("https://github.com/owner/repo/commit/abc123") is ParsedUrl.External,
        )
    }

    @Test
    fun parseUrl_singleSegment_returnsExternal() {
        // 单段既非 @mention 也非裸 sha，无法归属
        assertTrue(
            GitHubLinkParser.parseUrl("justaword") is ParsedUrl.External,
        )
    }
}
