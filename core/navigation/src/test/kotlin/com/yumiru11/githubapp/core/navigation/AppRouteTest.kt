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
    fun fromParsedUrl_issueRefWithoutContext_returnsNull() {
        // `#123` 无 owner/repo 语境：fromParsedUrl 是纯函数、无屏幕语境，单靠引用无法定位仓库。
        // 显式返回 null 由调用方兜底（README 纯锚点先被 bridge 忽略；深链见 MainActivity 落浏览器）
        assertNull(
            AppRoute.fromParsedUrl(ParsedUrl.IssueRef(null, null, 123)),
        )
    }

    @Test
    fun fromParsedUrl_issueRefWithContext_buildsIssueRoute() {
        // 引用自带 owner/repo 语境时可直接定位 Issue 详情
        assertEquals(
            AppRoute.Issue("owner", "repo", 123),
            AppRoute.fromParsedUrl(ParsedUrl.IssueRef("owner", "repo", 123)),
        )
    }

    @Test
    fun fromParsedUrl_releaseWithoutTag_landsOnReleasesList() {
        // /releases 无 tag：落 Releases 分区但不展开详情
        assertEquals(
            AppRoute.Repo("owner", "repo", showReleases = true),
            AppRoute.fromParsedUrl(ParsedUrl.Release("owner", "repo", null)),
        )
    }

    @Test
    fun fromParsedUrl_releaseWithTag_carriesTagForExpansion() {
        // /releases/tag/{tag}：落 Releases 分区并携带 tag 供分区展开
        assertEquals(
            AppRoute.Repo("owner", "repo", showReleases = true, releaseTag = "v1.2.0"),
            AppRoute.fromParsedUrl(ParsedUrl.Release("owner", "repo", "v1.2.0")),
        )
    }

    @Test
    fun fromParsedUrl_tree_buildsRepoRouteInFilesView() {
        // /tree/{ref}/{path}：复用仓库详情路由，初始落「文件」分区并自动展开到 path
        assertEquals(
            AppRoute.Repo(
                owner = "owner",
                repo = "repo",
                ref = "main",
                showFiles = true,
                treePath = "src/main",
            ),
            AppRoute.fromParsedUrl(ParsedUrl.Tree("owner", "repo", "main", "src/main")),
        )
    }

    @Test
    fun fromParsedUrl_treeWithoutPath_landsOnFilesRoot() {
        // /tree/{ref} 只有分支：仍要直接看到文件浏览（treePath 空 = 只到根树，不展开）
        assertEquals(
            AppRoute.Repo("owner", "repo", "develop", showFiles = true),
            AppRoute.fromParsedUrl(ParsedUrl.Tree("owner", "repo", "develop", "")),
        )
    }

    @Test
    fun fromParsedUrl_search_carriesQuery() {
        assertEquals(
            AppRoute.Search("kotlin coroutines"),
            AppRoute.fromParsedUrl(ParsedUrl.Search("kotlin coroutines")),
        )
    }

    @Test
    fun fromParsedUrl_searchWithEmptyQuery_navigatesToSearchEntry() {
        // `github.com/search`（无 q）：普通搜索入口，用户自行输入
        assertEquals(
            AppRoute.Search(),
            AppRoute.fromParsedUrl(ParsedUrl.Search("")),
        )
    }
}
