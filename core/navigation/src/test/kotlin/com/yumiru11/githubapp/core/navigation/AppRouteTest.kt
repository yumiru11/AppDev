package com.yumiru11.githubapp.core.navigation

import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppRouteTest {
    // ---- startDestination pattern（@SerialName 固定基路径，#90 类型安全路由） ----

    @Test
    fun startDestinationPattern_home_isHome() {
        assertEquals("home", AppRoute.startDestinationPattern<AppRoute.Home>())
    }

    @Test
    fun startDestinationPattern_login_isLogin() {
        assertEquals("login", AppRoute.startDestinationPattern<AppRoute.Login>())
    }

    // ---- 路由对象默认值（带默认值的参数 = optional query） ----

    @Test
    fun repoRoute_refDefaultsToEmpty() {
        // T23：分支切换深链的 ref 为可选 query
        assertEquals("", AppRoute.Repo("owner", "repo").ref)
    }

    @Test
    fun branchesRoute_refDefaultsToEmpty() {
        // 分支页进入时携带当前分支（可选 query）
        assertEquals("", AppRoute.Branches("owner", "repo").ref)
    }

    @Test
    fun blobRoute_pathDefaultsToEmpty() {
        // path 走 query 参数（多段文件路径，T11）
        assertEquals("", AppRoute.Blob("owner", "repo", "main").path)
    }

    // ---- fromParsedUrl 映射：返回类型安全 route 对象 ----

    @Test
    fun fromParsedUrl_repo_buildsRepoRoute() {
        assertEquals(
            AppRoute.Repo("owner", "repo"),
            AppRoute.fromParsedUrl(ParsedUrl.Repo("owner", "repo")),
        )
    }

    @Test
    fun fromParsedUrl_issue_buildsIssueRoute() {
        assertEquals(
            AppRoute.Issue("owner", "repo", 123),
            AppRoute.fromParsedUrl(ParsedUrl.Issue("owner", "repo", 123)),
        )
    }

    @Test
    fun fromParsedUrl_issueList_buildsIssuesRoute() {
        assertEquals(
            AppRoute.Issues("yumiru11", "AppDev"),
            AppRoute.fromParsedUrl(ParsedUrl.IssueList("yumiru11", "AppDev")),
        )
    }

    @Test
    fun fromParsedUrl_pullRequest_buildsPrRoute() {
        assertEquals(
            AppRoute.Pr("owner", "repo", 456),
            AppRoute.fromParsedUrl(ParsedUrl.PullRequest("owner", "repo", 456)),
        )
    }

    @Test
    fun fromParsedUrl_commit_buildsCommitRoute() {
        val sha = "0123456789abcdef0123456789abcdef01234567"
        assertEquals(
            AppRoute.Commit("owner", "repo", sha),
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
            AppRoute.Discussion("owner", "repo", 5),
            AppRoute.fromParsedUrl(ParsedUrl.Discussion("owner", "repo", 5)),
        )
    }

    @Test
    fun fromParsedUrl_blob_buildsBlobRoute() {
        assertEquals(
            AppRoute.Blob("owner", "repo", "main", "src/Main.kt"),
            AppRoute.fromParsedUrl(ParsedUrl.Blob("owner", "repo", "main", "src/Main.kt")),
        )
    }

    @Test
    fun fromParsedUrl_blob_deepMultiSegmentPath_keepsRawPath() {
        // 回归：多段文件路径曾需手工 URLEncoder 编码进 query（单段占位符无法匹配而崩溃，
        // CI 截图 5.11 六段路径首次暴露）；类型安全路由下编码交给 navigation 参数序列化器，
        // 映射层直接承载原始 path
        val path = "app/src/main/java/com/yumiru11/githubapp/MainActivity.kt"
        assertEquals(
            AppRoute.Blob("yumiru11", "AppDev", "main", path),
            AppRoute.fromParsedUrl(ParsedUrl.Blob("yumiru11", "AppDev", "main", path)),
        )
    }

    @Test
    fun fromParsedUrl_blob_pathWithSpace_keepsRawPath() {
        assertEquals(
            AppRoute.Blob("owner", "repo", "main", "My File.kt"),
            AppRoute.fromParsedUrl(ParsedUrl.Blob("owner", "repo", "main", "My File.kt")),
        )
    }

    @Test
    fun fromParsedUrl_user_buildsUserRoute() {
        assertEquals(
            AppRoute.User("login"),
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