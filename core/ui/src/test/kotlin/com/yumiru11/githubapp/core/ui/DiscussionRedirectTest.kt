package com.yumiru11.githubapp.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Discussion 深链外部兜底的 URL 构造（spec-audit §10 P2：不再挂占位屏，显式走浏览器）。
 *
 * 组合行为（打开浏览器 + 回退上一页）在 AppNavHost 的 Discussion destination 内；
 * 这里锁死外部目的地 URL 的形态（GitHub Discussions 标准路径）。
 */
class DiscussionRedirectTest {
    @Test
    fun discussionBrowserUrl_buildsCanonicalDiscussionUrl() {
        assertEquals(
            "https://github.com/octocat/Hello-World/discussions/7",
            discussionBrowserUrl("octocat", "Hello-World", 7),
        )
    }

    @Test
    fun discussionBrowserUrl_preservesOwnerAndRepoVerbatim() {
        assertEquals(
            "https://github.com/yumiru11/AppDev/discussions/123",
            discussionBrowserUrl("yumiru11", "AppDev", 123),
        )
    }
}
