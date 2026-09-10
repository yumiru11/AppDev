package com.yumiru11.githubapp.feature.profile.paging

import androidx.paging.PagingSource
import com.yumiru11.githubapp.core.githubrest.api.GistApi
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [GistPagingSource] 单测（L11，MockWebServer 模拟 GitHub API，零真实网络）。
 *
 * 覆盖：首页（nextKey 推进）、尾页（不足一页 → nextKey = null）、错误页 → LoadResult.Error、
 * DTO → 领域模型（文件名取 files 键 / 语言 / 描述 / 时间源字段 / 无文件回退 id）。
 */
class GistPagingSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var gistApi: GistApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit =
            GitHubRestClient.createRetrofit(
                baseUrl = server.url("/"),
                client =
                    GitHubRestClient
                        .createOkHttpClient(
                            tokenProvider = GuestTokenProvider(),
                            etagStore = InMemoryEtagStore(),
                            debugLogging = false,
                        ),
                json = GitHubRestClient.createJson(),
            )
        gistApi = retrofit.create(GistApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun gistJson(id: String): String =
        """
        {
          "id": "$id",
          "description": "gist $id",
          "html_url": "https://gist.github.com/octocat/$id",
          "created_at": "2026-08-01T10:00:00Z",
          "files": {
            "Main.kt": { "filename": "Main.kt", "language": "Kotlin" }
          }
        }
        """.trimIndent()

    private fun enqueueJson(body: String) {
        server.enqueue(
            MockResponse
                .Builder()
                .body(body)
                .addHeader("Content-Type", "application/json")
                .build(),
        )
    }

    private fun source(username: String = "octocat"): GistPagingSource = GistPagingSource(gistApi = gistApi, username = username)

    @Test
    fun load_firstPageFullPage_returnsPageWithNextKeyAndMappedItems() =
        runTest {
            val gists = (1..30).joinToString(",") { gistJson("gist-$it") }
            enqueueJson("[$gists]")

            val result = source().load(refreshParams())

            assertTrue(result is PagingSource.LoadResult.Page)
            val page = result as PagingSource.LoadResult.Page
            assertEquals(30, page.data.size)
            assertEquals(2, page.nextKey)
            assertNull(page.prevKey)
            val first = page.data.first()
            assertEquals("gist-1", first.id)
            assertEquals("Main.kt", first.fileName)
            assertEquals("Kotlin", first.language)
            assertEquals("gist gist-1", first.description)
            assertEquals("2026-08-01T10:00:00Z", first.createdAt)
            assertEquals("https://gist.github.com/octocat/gist-1", first.htmlUrl)
            val request = server.takeRequest()
            assertEquals("/users/octocat/gists", request.url.encodedPath)
            assertEquals("30", request.url.queryParameter("per_page"))
            assertEquals("1", request.url.queryParameter("page"))
        }

    @Test
    fun load_tailPageShorterThanLoadSize_returnsNullNextKey() =
        runTest {
            enqueueJson("[${gistJson("only-one")}]")

            val result = source(username = "torvalds").load(refreshParams())

            assertTrue(result is PagingSource.LoadResult.Page)
            val page = result as PagingSource.LoadResult.Page
            assertEquals(1, page.data.size)
            assertNull(page.nextKey)
            assertEquals("/users/torvalds/gists", server.takeRequest().url.encodedPath)
        }

    @Test
    fun load_gistWithoutFiles_fallsBackToIdAsFileName() =
        runTest {
            enqueueJson("""[ { "id": "empty-files", "description": null, "files": {} } ]""")

            val result = source().load(refreshParams())

            val page = result as PagingSource.LoadResult.Page
            assertEquals("empty-files", page.data.single().fileName)
            assertNull(page.data.single().language)
            assertNull(page.data.single().description)
        }

    @Test
    fun load_serverError_returnsLoadError() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(500)
                    .body("{}")
                    .build(),
            )

            val result = source().load(refreshParams())

            assertTrue(result is PagingSource.LoadResult.Error)
        }

    private fun refreshParams(): PagingSource.LoadParams.Refresh<Int> =
        PagingSource.LoadParams.Refresh(
            key = null,
            loadSize = 30,
            placeholdersEnabled = false,
        )
}
