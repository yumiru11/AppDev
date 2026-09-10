package com.yumiru11.githubapp.feature.home.data

import com.yumiru11.githubapp.core.githubrest.api.SearchApi
import com.yumiru11.githubapp.core.githubrest.api.TrendApi
import com.yumiru11.githubapp.core.githubrest.api.TrendSource
import com.yumiru11.githubapp.core.githubrest.model.RepositoryDto
import com.yumiru11.githubapp.core.githubrest.model.SearchRepositoriesResponse
import com.yumiru11.githubapp.core.githubrest.model.TrendDto
import com.yumiru11.githubapp.core.githubrest.model.UserDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

/**
 * [TrendRepository] 单测（L08）：镜像优先 → 搜索回退 → 6h 缓存，全链路静默降级。
 *
 * 时钟注入（[NOW_MILLIS]）：缓存窗口与回退查询串的日期都可确定性断言；
 * 超时用虚拟时间（runTest）驱动，不真等 5s。
 */
class TrendRepositoryTest {
    private var nowMillis: Long = NOW_MILLIS

    private fun repository(
        trendApi: TrendApi,
        searchApi: SearchApi = mockk(),
    ): TrendRepository = TrendRepository(trendApi, searchApi, now = { nowMillis })

    private fun trendDto(
        owner: String = "JetBrains",
        repo: String = "kotlin",
        stars: Int = 51_234,
    ): TrendDto =
        TrendDto(
            owner = owner,
            repo = repo,
            description = "The Kotlin Programming Language.",
            language = "Kotlin",
            stars = stars,
            forks = 6_789,
        )

    private fun searchResponse(): SearchRepositoriesResponse =
        SearchRepositoriesResponse(
            totalCount = 1,
            items =
                listOf(
                    RepositoryDto(
                        id = 1,
                        name = "kotlin",
                        fullName = "JetBrains/kotlin",
                        isPrivate = false,
                        owner = UserDto(login = "JetBrains", id = 1),
                        description = "Search fallback result",
                        htmlUrl = "https://github.com/JetBrains/kotlin",
                        stargazersCount = 51_234,
                        forksCount = 6_789,
                        language = "Kotlin",
                    ),
                ),
        )

    @Test
    fun trending_mirrorReturnsItems_mapsItemsAndSkipsSearchFallback() =
        runTest {
            val trendApi = mockk<TrendApi> { coEvery { trending(any()) } returns listOf(trendDto()) }
            val searchApi = mockk<SearchApi>()

            val items = repository(trendApi, searchApi).trending()

            assertEquals(1, items.size)
            assertEquals("JetBrains/kotlin", items.single().fullName)
            assertEquals(51_234, items.single().stars)
            assertEquals("https://github.com/JetBrains/kotlin", items.single().url)
            coVerify(exactly = 0) { searchApi.searchRepositories(any(), any(), any(), any()) }
        }

    @Test
    fun trending_mirrorThrows_fallsBackToSearchWithCreatedQuery() =
        runTest {
            val trendApi = mockk<TrendApi> { coEvery { trending(any()) } throws IOException("mirror down") }
            val searchApi =
                mockk<SearchApi> {
                    coEvery { searchRepositories(any(), any(), any(), any()) } returns searchResponse()
                }

            val items = repository(trendApi, searchApi).trending()

            assertEquals("JetBrains/kotlin", items.single().fullName)
            assertEquals("Search fallback result", items.single().description)
            // 回退查询串：近 7 天新建 + 星标 > 100，按 stars 排序（§2.5 拍板）
            coVerify(exactly = 1) {
                searchApi.searchRepositories(
                    query = "created:>2026-08-30 stars:>100",
                    page = 1,
                    perPage = 5,
                    sort = "stars",
                )
            }
        }

    @Test
    fun trending_mirrorTimesOut_fallsBackToSearch() =
        runTest {
            val trendApi =
                mockk<TrendApi> {
                    coEvery { trending(any()) } coAnswers {
                        delay(MIRROR_HANG_MILLIS)
                        listOf(trendDto())
                    }
                }
            val searchApi =
                mockk<SearchApi> {
                    coEvery { searchRepositories(any(), any(), any(), any()) } returns searchResponse()
                }

            // 虚拟时间：5s 请求超时先于镜像 hang 触发 → 静默降级不抛
            val items = repository(trendApi, searchApi).trending()

            assertEquals("JetBrains/kotlin", items.single().fullName)
            coVerify(exactly = 1) { searchApi.searchRepositories(any(), any(), any(), any()) }
        }

    @Test
    fun trending_mirrorReturnsEmpty_fallsBackToSearch() =
        runTest {
            val trendApi = mockk<TrendApi> { coEvery { trending(any()) } returns emptyList() }
            val searchApi =
                mockk<SearchApi> {
                    coEvery { searchRepositories(any(), any(), any(), any()) } returns searchResponse()
                }

            val items = repository(trendApi, searchApi).trending()

            assertEquals(1, items.size)
        }

    @Test
    fun trending_mirrorReturnsOnlyDirtyRows_fallsBackToSearch() =
        runTest {
            // owner 缺失的脏数据被过滤后为空 → 视同镜像失败，交回退兜底
            val trendApi =
                mockk<TrendApi> {
                    coEvery { trending(any()) } returns listOf(trendDto(owner = "", repo = ""))
                }
            val searchApi =
                mockk<SearchApi> {
                    coEvery { searchRepositories(any(), any(), any(), any()) } returns searchResponse()
                }

            val items = repository(trendApi, searchApi).trending()

            assertEquals("JetBrains/kotlin", items.single().fullName)
        }

    @Test
    fun trending_bothSourcesFail_returnsEmptyListWithoutThrowing() =
        runTest {
            val trendApi = mockk<TrendApi> { coEvery { trending(any()) } throws IOException("mirror down") }
            val searchApi =
                mockk<SearchApi> {
                    coEvery { searchRepositories(any(), any(), any(), any()) } throws IOException("search down")
                }

            // 契约：永不抛出（调用方取消除外）→ UI 静默隐藏小节
            val items = repository(trendApi, searchApi).trending()

            assertTrue(items.isEmpty())
        }

    @Test
    fun trending_withinCacheWindow_returnsCachedItemsWithoutRefetch() =
        runTest {
            val trendApi = mockk<TrendApi> { coEvery { trending(any()) } returns listOf(trendDto()) }
            val repository = repository(trendApi)

            repository.trending()
            nowMillis += CACHE_TTL_MILLIS - 1
            val second = repository.trending()

            assertEquals(1, second.size)
            coVerify(exactly = 1) { trendApi.trending(any()) }
        }

    @Test
    fun trending_afterCacheWindowExpires_refetchesMirror() =
        runTest {
            val trendApi = mockk<TrendApi> { coEvery { trending(any()) } returns listOf(trendDto()) }
            val repository = repository(trendApi)

            repository.trending()
            nowMillis += CACHE_TTL_MILLIS
            repository.trending()

            coVerify(exactly = 2) { trendApi.trending(any()) }
        }

    @Test
    fun trending_bothSourcesFailAfterSuccess_returnsStaleCache() =
        runTest {
            val trendApi = mockk<TrendApi> { coEvery { trending(any()) } returns listOf(trendDto()) }
            val searchApi = mockk<SearchApi>()
            val repository = repository(trendApi, searchApi)
            repository.trending()

            // 缓存过期 + 两路全挂：有旧数据看旧数据（仍不抛）
            nowMillis += CACHE_TTL_MILLIS
            coEvery { trendApi.trending(any()) } throws IOException("mirror down")
            coEvery { searchApi.searchRepositories(any(), any(), any(), any()) } throws IOException("search down")

            val stale = repository.trending()

            assertEquals("JetBrains/kotlin", stale.single().fullName)
        }

    @Test
    fun trending_customPeriod_requestsThatPeriodMirrorUrl() =
        runTest {
            val trendApi = mockk<TrendApi> { coEvery { trending(any()) } returns listOf(trendDto()) }

            repository(trendApi).trending(TrendSource.Period.MONTHLY)

            coVerify(exactly = 1) {
                trendApi.trending(TrendSource.mirrorUrl(TrendSource.Period.MONTHLY))
            }
        }

    private companion object {
        /** 2026-09-06T10:00:00Z（回退查询串日期基准 → created:>2026-08-30） */
        val NOW_MILLIS: Long = Instant.parse("2026-09-06T10:00:00Z").toEpochMilli()
        const val CACHE_TTL_MILLIS = 6 * 60 * 60 * 1_000L
        const val MIRROR_HANG_MILLIS = 60_000L
    }
}
