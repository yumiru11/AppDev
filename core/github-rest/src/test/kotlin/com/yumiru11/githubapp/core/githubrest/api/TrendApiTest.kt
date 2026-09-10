package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.api.TrendSource.Period
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.time.LocalDate

/**
 * [TrendApi] 测试（L08 / ui-design §2.5）：镜像地址构造 + 镜像 JSON 解析 + 无认证头。
 *
 * 镜像站与 api.github.com 不同源，接口走 @Url 全量地址；此处用 MockWebServer 承载
 * 「全量 URL」路径，同时断言请求**不带 Authorization**（第三方 host 不得收到 GitHub 令牌）。
 */
class TrendApiTest {
    private lateinit var server: MockWebServer
    private lateinit var trendApi: TrendApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        trendApi =
            GitHubRestClient
                .createUnauthenticatedRetrofit(baseUrl = TrendSource.MIRROR_BASE_URL.toHttpUrl())
                .create(TrendApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun mirrorJson(): String =
        """
        [
          {
            "owner": "JetBrains",
            "repo": "kotlin",
            "description": "The Kotlin Programming Language.",
            "language": "Kotlin",
            "stars": 51234,
            "new_stars": 321,
            "forks": 6789,
            "builtBy": [ { "username": "someone" } ]
          },
          {
            "owner": "square",
            "repo": "okhttp",
            "language": "Kotlin",
            "stars": 42000,
            "forks": 8900
          }
        ]
        """.trimIndent()

    @Test
    fun mirrorUrl_defaultPeriod_pointsAtDailyAllLanguagesJson() {
        assertEquals(
            "https://raw.githubusercontent.com/Unpublished/GithubTrending/trends/trending_daily-all.json",
            TrendSource.mirrorUrl(),
        )
    }

    @Test
    fun mirrorUrl_weeklyAndMonthly_switchPathSegment() {
        assertTrue(TrendSource.mirrorUrl(Period.WEEKLY).endsWith("/trends/trending_weekly-all.json"))
        assertTrue(TrendSource.mirrorUrl(Period.MONTHLY).endsWith("/trends/trending_monthly-all.json"))
    }

    @Test
    fun fallbackQuery_today_coversLastSevenDaysAndStarFloor() {
        assertEquals(
            "created:>2026-08-30 stars:>100",
            TrendSource.fallbackQuery(LocalDate.of(2026, 9, 6)),
        )
    }

    @Test
    fun trending_validMirrorJson_parsesItemsAndToleratesUnknownFields() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(mirrorJson())
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val items = trendApi.trending(server.url("/trends/trending_daily-all.json").toString())

            assertEquals(2, items.size)
            val first = items.first()
            assertEquals("JetBrains/kotlin", first.fullName)
            assertEquals("The Kotlin Programming Language.", first.description)
            assertEquals("Kotlin", first.language)
            assertEquals(51_234, first.stars)
            assertEquals(6_789, first.forks)
            assertEquals("https://github.com/JetBrains/kotlin", first.url)
            // 可选字段缺失 → null（第二项无 description）
            assertNull(items[1].description)
        }

    @Test
    fun trending_fullUrl_requestGoesToMirrorHostWithoutAuthorizationHeader() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("[]")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            trendApi.trending(server.url("/trends/trending_daily-all.json").toString())

            val recorded = server.takeRequest()
            assertEquals("/trends/trending_daily-all.json", recorded.url.encodedPath)
            // 安全红线：镜像站是第三方静态托管，绝不允许携带 GitHub 令牌
            assertNull(recorded.headers["Authorization"])
        }

    @Test
    fun trending_serverError_throwsHttpException() =
        runTest {
            server.enqueue(MockResponse.Builder().code(500).build())

            // Retrofit 3 非 2xx 抛 HttpException —— 由 TrendRepository 捕获后走搜索回退
            val error =
                runCatching { trendApi.trending(server.url("/trends/trending_daily-all.json").toString()) }
                    .exceptionOrNull()

            assertTrue("应抛 HttpException，实际：$error", error is HttpException)
            assertEquals(500, (error as HttpException).code())
        }
}
