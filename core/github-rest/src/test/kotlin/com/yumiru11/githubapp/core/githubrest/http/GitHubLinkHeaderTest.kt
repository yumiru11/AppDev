package com.yumiru11.githubapp.core.githubrest.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * GitHub Link 响应头解析（#166 / UI21）。
 *
 * **纯 JVM 测试**（不加 Robolectric）：这是字符串协议解析，没有任何 Android 依赖；
 * 而且被 Robolectric 沙箱加载的类不产出 JaCoCo 覆盖数据（实测），纯 JVM 才能进覆盖率门禁。
 */
class GitHubLinkHeaderTest {
    private val base = "https://api.github.com/user/starred?per_page=1"

    @Test
    fun lastPage_nextAndLast_returnsLastPageNumber() {
        val header =
            "<$base&page=2>; rel=\"next\", <$base&page=42>; rel=\"last\""

        assertEquals(42, GitHubLinkHeader.lastPage(header))
    }

    @Test
    fun lastPage_onlyNext_returnsNull() {
        assertEquals(null, GitHubLinkHeader.lastPage("<$base&page=2>; rel=\"next\""))
    }

    @Test
    fun lastPage_lastListedFirst_stillFound() {
        // GitHub 不保证 rel 顺序，且 last 常与 next/prev/first 同现
        val header =
            "<$base&page=42>; rel=\"last\", <$base&page=1>; rel=\"first\", <$base&page=2>; rel=\"next\""

        assertEquals(42, GitHubLinkHeader.lastPage(header))
    }

    @Test
    fun lastPage_nullOrBlank_returnsNull() {
        assertNull(GitHubLinkHeader.lastPage(null))
        assertNull(GitHubLinkHeader.lastPage(""))
        assertNull(GitHubLinkHeader.lastPage("   "))
    }

    @Test
    fun lastPage_lastWithoutPageParam_returnsNull() {
        assertNull(GitHubLinkHeader.lastPage("<https://api.github.com/user/starred>; rel=\"last\""))
    }

    @Test
    fun lastPage_malformedHeader_returnsNullWithoutThrowing() {
        assertNull(GitHubLinkHeader.lastPage("garbage"))
        assertNull(GitHubLinkHeader.lastPage("<no-closing-bracket; rel=\"last\""))
    }

    @Test
    fun totalCount_withLastPage_multipliesByPerPage() {
        val header = "<$base&page=7>; rel=\"last\""

        assertEquals(7, GitHubLinkHeader.totalCount(header, perPage = 1, currentPageSize = 1))
        assertEquals(1400, GitHubLinkHeader.totalCount(header, perPage = 200, currentPageSize = 200))
    }

    @Test
    fun totalCount_withoutLinkHeader_usesCurrentPageSize() {
        // 只有一页：没有 Link 头，总数就是本次返回的条数
        assertEquals(0, GitHubLinkHeader.totalCount(null, perPage = 1, currentPageSize = 0))
        assertEquals(1, GitHubLinkHeader.totalCount(null, perPage = 1, currentPageSize = 1))
    }

    @Test
    fun totalCount_lastPageSmallerThanCurrentPageSize_neverUnderReports() {
        // 防御性：perPage 与 last page 的组合理论上不该小于实际返回条数
        assertEquals(5, GitHubLinkHeader.totalCount("<$base&page=1>; rel=\"last\"", perPage = 1, currentPageSize = 5))
    }
}
