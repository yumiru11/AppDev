package com.yumiru11.githubapp.feature.repo

import com.yumiru11.githubapp.core.data.model.Release
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 深链初始视图的纯逻辑（RepoDetailScreen 的 internal 顶层函数）。
 *
 * 屏幕装配（参数 → 分区 / 展开）由 [RepoDetailInitialViewTest] 做组合级断言；
 * 这里锁死两个判定函数本身的行为边界（含反例：前缀不算命中、两提示同真时的优先级）。
 */
class RepoDetailDeepLinkTest {
    private fun release(
        id: Long,
        tagName: String,
    ) = Release(id = id, tagName = tagName)

    // ---- repoDetailInitialTab（TREE → 文件 / RELEASE → Releases） ----

    @Test
    fun repoDetailInitialTab_showFiles_returnsFilesTab() {
        assertEquals(1, repoDetailInitialTab(showFiles = true, showReleases = false))
    }

    @Test
    fun repoDetailInitialTab_showReleases_returnsReleasesTab() {
        assertEquals(2, repoDetailInitialTab(showFiles = false, showReleases = true))
    }

    @Test
    fun repoDetailInitialTab_bothHints_prefersFiles() {
        // 解析器一次只产出一个提示；同真时文件优先（TREE 语义更强，携带路径定位）
        assertEquals(1, repoDetailInitialTab(showFiles = true, showReleases = true))
    }

    @Test
    fun repoDetailInitialTab_noHint_returnsReadme() {
        assertEquals(0, repoDetailInitialTab(showFiles = false, showReleases = false))
    }

    // ---- findReleaseIdByTag（RELEASE 深链的 tag 精确匹配） ----

    @Test
    fun findReleaseIdByTag_exactMatch_returnsId() {
        val releases = listOf(release(1L, "v1.0.0"), release(2L, "v2.0.0"))
        assertEquals(2L, findReleaseIdByTag(releases, "v2.0.0"))
    }

    @Test
    fun findReleaseIdByTag_noMatch_returnsNull() {
        assertNull(findReleaseIdByTag(listOf(release(1L, "v1.0.0")), "v9.9.9"))
    }

    @Test
    fun findReleaseIdByTag_prefixTag_doesNotMatch() {
        // 精确匹配（GitHub tag 区分大小写、非前缀语义）：v1 ≠ v1.0
        assertNull(findReleaseIdByTag(listOf(release(1L, "v1.0")), "v1"))
    }

    @Test
    fun findReleaseIdByTag_caseSensitiveTag_doesNotMatch() {
        assertNull(findReleaseIdByTag(listOf(release(1L, "v1.0.0")), "V1.0.0"))
    }

    @Test
    fun findReleaseIdByTag_emptyList_returnsNull() {
        assertNull(findReleaseIdByTag(emptyList(), "v1.0.0"))
    }
}
