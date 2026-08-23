package com.yumiru11.githubapp.core.navigation

import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppRouteTest {
    // ---- 路由表常量 ----

    @Test
    fun routeTable_homeRoute_isHome() {
        assertEquals("home", AppRoute.HOME)
    }

    @Test
    fun routeTable_repoRoute_hasOwnerAndRepoPlaceholders() {
        assertEquals("repo/{owner}/{repo}", AppRoute.REPO)
    }

    @Test
    fun routeTable_issueRoute_hasOwnerRepoNumberPlaceholders() {
        assertEquals("issue/{owner}/{repo}/{number}", AppRoute.ISSUE)
    }

    @Test
    fun routeTable_prRoute_hasOwnerRepoNumberPlaceholders() {
        assertEquals("pr/{owner}/{repo}/{number}", AppRoute.PR)
    }

    @Test
    fun routeTable_commitRoute_hasOwnerRepoShaPlaceholders() {
        assertEquals("commit/{owner}/{repo}/{sha}", AppRoute.COMMIT)
    }

    @Test
    fun routeTable_discussionRoute_hasOwnerRepoNumberPlaceholders() {
        assertEquals("discussion/{owner}/{repo}/{number}", AppRoute.DISCUSSION)
    }

    @Test
    fun routeTable_blobRoute_hasOwnerRepoRefPathPlaceholders() {
        assertEquals("blob/{owner}/{repo}/{ref}/{path}", AppRoute.BLOB)
    }

    @Test
    fun routeTable_userRoute_hasLoginPlaceholder() {
        assertEquals("user/{login}", AppRoute.USER)
    }

    @Test
    fun routeTable_searchRoute_isSearch() {
        assertEquals("search", AppRoute.SEARCH)
    }

    @Test
    fun routeTable_profileRoute_isProfile() {
        assertEquals("profile", AppRoute.PROFILE)
    }

    @Test
    fun routeTable_externalRoute_isExternal() {
        assertEquals("external", AppRoute.EXTERNAL)
    }

    // ---- fromParsedUrl 映射 ----

    @Test
    fun fromParsedUrl_repo_buildsRepoRoute() {
        assertEquals(
            "repo/owner/repo",
            AppRoute.fromParsedUrl(ParsedUrl.Repo("owner", "repo")),
        )
    }

    @Test
    fun fromParsedUrl_issue_buildsIssueRoute() {
        assertEquals(
            "issue/owner/repo/123",
            AppRoute.fromParsedUrl(ParsedUrl.Issue("owner", "repo", 123)),
        )
    }

    @Test
    fun fromParsedUrl_pullRequest_buildsPrRoute() {
        assertEquals(
            "pr/owner/repo/456",
            AppRoute.fromParsedUrl(ParsedUrl.PullRequest("owner", "repo", 456)),
        )
    }

    @Test
    fun fromParsedUrl_commit_buildsCommitRoute() {
        val sha = "0123456789abcdef0123456789abcdef01234567"
        assertEquals(
            "commit/owner/repo/$sha",
            AppRoute.fromParsedUrl(ParsedUrl.Commit("owner", "repo", sha)),
        )
    }

    @Test
    fun fromParsedUrl_commitWithoutOwnerRepo_returnsNull() {
        // 裸 sha 无 owner/repo 语境，无法导航
        val sha = "0123456789abcdef0123456789abcdef01234567"
        assertNull(
            AppRoute.fromParsedUrl(ParsedUrl.Commit(null, null, sha)),
        )
    }

    @Test
    fun fromParsedUrl_discussion_buildsDiscussionRoute() {
        assertEquals(
            "discussion/owner/repo/5",
            AppRoute.fromParsedUrl(ParsedUrl.Discussion("owner", "repo", 5)),
        )
    }

    @Test
    fun fromParsedUrl_blob_buildsBlobRoute() {
        assertEquals(
            "blob/owner/repo/main/src/Main.kt",
            AppRoute.fromParsedUrl(ParsedUrl.Blob("owner", "repo", "main", "src/Main.kt")),
        )
    }

    @Test
    fun fromParsedUrl_user_buildsUserRoute() {
        assertEquals(
            "user/login",
            AppRoute.fromParsedUrl(ParsedUrl.User("login")),
        )
    }

    @Test
    fun fromParsedUrl_external_returnsNull() {
        assertNull(
            AppRoute.fromParsedUrl(ParsedUrl.External("https://example.com")),
        )
    }

    @Test
    fun fromParsedUrl_issueRef_returnsNull() {
        // IssueRef 无 owner/repo 语境，无法映射到具体路由
        assertNull(
            AppRoute.fromParsedUrl(ParsedUrl.IssueRef(null, null, 123)),
        )
    }

    @Test
    fun fromParsedUrl_release_returnsNull() {
        // Release 不在路由表内
        assertNull(
            AppRoute.fromParsedUrl(ParsedUrl.Release("owner", "repo", null)),
        )
    }

    @Test
    fun fromParsedUrl_tree_returnsNull() {
        // Tree 不在路由表内
        assertNull(
            AppRoute.fromParsedUrl(ParsedUrl.Tree("owner", "repo", "main", "")),
        )
    }

    @Test
    fun fromParsedUrl_search_returnsNull() {
        // Search 无参数，无对应路由
        assertNull(
            AppRoute.fromParsedUrl(ParsedUrl.Search("query")),
        )
    }
}
