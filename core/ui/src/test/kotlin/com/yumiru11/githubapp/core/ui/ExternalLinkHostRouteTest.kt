package com.yumiru11.githubapp.core.ui

import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD 单测：routeLink() 纯函数，区分内部/外部链接路由。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class ExternalLinkHostRouteTest {
    @Test
    fun routeLink_externalUrl_returnsOpenExternal() {
        val url = ParsedUrl.External("https://example.com/article")
        val action = routeLink(url)

        assertTrue(action is LinkAction.OpenExternal)
        assertEquals("https://example.com/article", (action as LinkAction.OpenExternal).url)
    }

    @Test
    fun routeLink_repoUrl_returnsDelegateInternal() {
        val url = ParsedUrl.Repo("owner", "repo")
        val action = routeLink(url)

        assertTrue(action is LinkAction.DelegateInternal)
        assertEquals(url, (action as LinkAction.DelegateInternal).parsedUrl)
    }

    @Test
    fun routeLink_issueUrl_returnsDelegateInternal() {
        val url = ParsedUrl.Issue("owner", "repo", 42)
        val action = routeLink(url)

        assertTrue(action is LinkAction.DelegateInternal)
        assertEquals(url, (action as LinkAction.DelegateInternal).parsedUrl)
    }

    @Test
    fun routeLink_pullRequestUrl_returnsDelegateInternal() {
        val url = ParsedUrl.PullRequest("owner", "repo", 1)
        val action = routeLink(url)

        assertTrue(action is LinkAction.DelegateInternal)
        assertEquals(url, (action as LinkAction.DelegateInternal).parsedUrl)
    }

    @Test
    fun routeLink_userUrl_returnsDelegateInternal() {
        val url = ParsedUrl.User("login")
        val action = routeLink(url)

        assertTrue(action is LinkAction.DelegateInternal)
        assertEquals(url, (action as LinkAction.DelegateInternal).parsedUrl)
    }

    @Test
    fun routeLink_commitUrl_returnsDelegateInternal() {
        val sha = "a".repeat(40)
        val url = ParsedUrl.Commit("owner", "repo", sha)
        val action = routeLink(url)

        assertTrue(action is LinkAction.DelegateInternal)
        assertEquals(url, (action as LinkAction.DelegateInternal).parsedUrl)
    }

    @Test
    fun routeLink_searchUrl_returnsDelegateInternal() {
        val url = ParsedUrl.Search("kotlin")
        val action = routeLink(url)

        assertTrue(action is LinkAction.DelegateInternal)
        assertEquals(url, (action as LinkAction.DelegateInternal).parsedUrl)
    }
}
