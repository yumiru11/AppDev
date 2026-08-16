package com.yumiru11.githubapp.core.githubdata.paging

import androidx.paging.PagingSource
import com.yumiru11.githubapp.core.githubdata.error.GitHubError
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.githubgraphql.GitHubApolloClientFactory
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.test.assertIs

/**
 * ViewerRepositoriesPagingSource 测试（直接调 PagingSource.load + GraphQL cursor 分页）。
 *
 * 覆盖：首屏加载、翻页游标传递、末页 nextKey 为空、GraphQL 错误转 LoadResult.Error。
 */
class ViewerRepositoriesPagingSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var pagingSource: ViewerRepositoriesPagingSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val apolloClient =
            GitHubApolloClientFactory.create(
                serverUrl = server.url("/graphql").toString(),
                okHttpClient = OkHttpClient(),
            )
        pagingSource = ViewerRepositoriesPagingSource(apolloClient)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun load_firstPage_mapsNodesAndReturnsEndCursorAsNextKey() =
        runTest {
            server.enqueue(connectionResponse(endCursor = "CURSOR_A", hasNextPage = true))

            val page =
                assertIs<PagingSource.LoadResult.Page<String, *>>(
                    pagingSource.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 2, placeholdersEnabled = false)),
                )

            assertEquals(1, (page.data as List<*>).size)
            assertEquals("CURSOR_A", page.nextKey)
            assertNull(page.prevKey)
        }

    @Test
    fun load_append_passesPreviousEndCursorAndStopsAtLastPage() =
        runTest {
            server.enqueue(connectionResponse(endCursor = "CURSOR_A", hasNextPage = true))
            server.enqueue(connectionResponse(endCursor = "CURSOR_B", hasNextPage = false))

            pagingSource.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 2, placeholdersEnabled = false))
            val second =
                assertIs<PagingSource.LoadResult.Page<String, *>>(
                    pagingSource.load(PagingSource.LoadParams.Append(key = "CURSOR_A", loadSize = 2, placeholdersEnabled = false)),
                )

            // 翻页请求体携带上一页游标
            server.takeRequest() // 首屏请求
            assertTrue(
                "翻页请求应携带 CURSOR_A",
                server
                    .takeRequest()
                    .body
                    ?.utf8()
                    .orEmpty()
                    .contains("CURSOR_A"),
            )
            // hasNextPage=false 时 nextKey 必须为空（末页）
            assertNull(second.nextKey)
        }

    @Test
    fun load_graphqlErrors_returnsLoadResultError() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"data":null,"errors":[{"message":"Bad credentials"}]}""")
                    .build(),
            )

            val result =
                pagingSource.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 2, placeholdersEnabled = false))

            assertIs<PagingSource.LoadResult.Error<String, *>>(result)
        }

    @Test
    fun load_firstPage_requestDoesNotCarryAnyCursor() =
        runTest {
            server.enqueue(connectionResponse(endCursor = "CURSOR_A", hasNextPage = true))

            pagingSource.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 2, placeholdersEnabled = false))

            val requestBody =
                server
                    .takeRequest()
                    .body
                    ?.utf8()
                    .orEmpty()
            // 首屏（key=null）不得携带任何游标，否则会跳过第一页数据
            assertFalse("首屏请求不应携带游标", requestBody.contains("CURSOR"))
        }

    @Test
    fun load_emptyFirstPage_returnsEmptyPageWithNullNextKey() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {"data":{"viewer":{"__typename":"User","repositories":{
                          "__typename":"RepositoryConnection",
                          "pageInfo":{"__typename":"PageInfo","hasNextPage":false,"endCursor":null},
                          "nodes":[]
                        }}}}
                        """.trimIndent(),
                    ).build(),
            )

            val page =
                assertIs<PagingSource.LoadResult.Page<String, *>>(
                    pagingSource.load(
                        PagingSource.LoadParams.Refresh(key = null, loadSize = 2, placeholdersEnabled = false),
                    ),
                )

            assertEquals(0, (page.data as List<*>).size)
            assertNull(page.nextKey)
        }

    @Test
    fun load_nullNodes_returnsEmptyPage() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {"data":{"viewer":{"__typename":"User","repositories":{
                          "__typename":"RepositoryConnection",
                          "pageInfo":{"__typename":"PageInfo","hasNextPage":false,"endCursor":null}
                        }}}}
                        """.trimIndent(),
                    ).build(),
            )

            val page =
                assertIs<PagingSource.LoadResult.Page<String, *>>(
                    pagingSource.load(
                        PagingSource.LoadParams.Refresh(key = null, loadSize = 2, placeholdersEnabled = false),
                    ),
                )

            // nodes 字段缺失 → null → orEmpty 兜底为空页而非错误
            assertEquals(0, (page.data as List<*>).size)
            assertNull(page.nextKey)
        }

    @Test
    fun load_refresh_restartsFromFirstPageAndDropsStaleCursor() =
        runTest {
            server.enqueue(connectionResponse(endCursor = "CURSOR_A", hasNextPage = true))
            server.enqueue(connectionResponse(endCursor = "CURSOR_B", hasNextPage = true))
            server.enqueue(connectionResponse(endCursor = "CURSOR_C", hasNextPage = false))

            // 首屏 → 旧游标 CURSOR_A；刷新（key=null）→ 新游标 CURSOR_B；基于新游标翻页
            pagingSource.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 2, placeholdersEnabled = false))
            pagingSource.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 2, placeholdersEnabled = false))
            pagingSource.load(PagingSource.LoadParams.Append(key = "CURSOR_B", loadSize = 2, placeholdersEnabled = false))

            server.takeRequest() // 首屏请求
            val refreshRequest =
                server
                    .takeRequest()
                    .body
                    ?.utf8()
                    .orEmpty()
            val appendRequest =
                server
                    .takeRequest()
                    .body
                    ?.utf8()
                    .orEmpty()
            // 刷新必须从第一页重新开始（不携带旧游标），且后续翻页使用刷新后的新游标
            assertFalse("刷新请求不应携带旧游标", refreshRequest.contains("CURSOR_A"))
            assertTrue("刷新后追加应携带新游标 CURSOR_B", appendRequest.contains("CURSOR_B"))
        }

    @Test
    fun load_graphqlErrors_carriesNormalizedGraphQlError() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"data":null,"errors":[{"message":"Bad credentials"}]}""")
                    .build(),
            )

            val result =
                pagingSource.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 2, placeholdersEnabled = false))

            val error = assertIs<PagingSource.LoadResult.Error<String, *>>(result)
            val exception = assertIs<GitHubRequestException>(error.throwable)
            assertEquals(GitHubError.GraphQl(listOf("Bad credentials")), exception.error)
        }

    @Test
    fun load_http500_carriesServerError() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 500 Internal Server Error")
                    .body("{}")
                    .build(),
            )

            val result =
                pagingSource.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 2, placeholdersEnabled = false))

            val error = assertIs<PagingSource.LoadResult.Error<String, *>>(result)
            val exception = assertIs<GitHubRequestException>(error.throwable)
            assertEquals(GitHubError.Server(500), exception.error)
        }

    private fun connectionResponse(
        endCursor: String,
        hasNextPage: Boolean,
    ): MockResponse =
        MockResponse
            .Builder()
            .body(
                """
                {"data":{"viewer":{"__typename":"User","repositories":{
                  "__typename":"RepositoryConnection",
                  "pageInfo":{"__typename":"PageInfo","hasNextPage":$hasNextPage,"endCursor":"$endCursor"},
                  "nodes":[{
                    "__typename":"Repository",
                    "id":"R_kwDOA","name":"Hello-World","isPrivate":false,
                    "description":"first repo","stargazerCount":3,
                    "updatedAt":"2026-08-01T00:00:00Z",
                    "owner":{"__typename":"User","login":"octocat"},
                    "primaryLanguage":{"__typename":"Language","name":"Kotlin"}
                  }]
                }}}}
                """.trimIndent(),
            ).build()
}
