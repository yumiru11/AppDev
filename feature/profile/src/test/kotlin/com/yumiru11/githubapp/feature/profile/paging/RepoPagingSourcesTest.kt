package com.yumiru11.githubapp.feature.profile.paging

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.api.UserApi
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
import retrofit2.HttpException

/**
 * Repositories/Starred PagingSource 单测（MockWebServer 模拟 GitHub API，零真实网络）。
 *
 * 覆盖：DTO → 领域模型全字段映射、append 页码推进（prevKey/nextKey）、带 key 的
 * refresh/prepend、空页终止、HTTP 错误 → LoadResult.Error、公开/私有端点分流。
 */
class RepoPagingSourcesTest {
    private lateinit var server: MockWebServer
    private lateinit var api: UserApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = createApi(server)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun repositoriesPagingSource_firstPage_mapsAllDomainFields() =
        runTest {
            server.enqueue(jsonResponse("[${repositoryJson("repo-1", private = true)}]"))

            val result = RepositoriesPagingSource(api, login = null).load(refreshParams())

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(1, page.data.size)
            val repo = page.data.first()
            assertEquals("octocat", repo.ownerLogin)
            assertEquals("repo-1", repo.name)
            assertEquals("desc of repo-1", repo.description)
            assertTrue(repo.isPrivate)
            assertEquals(100, repo.stargazerCount)
            assertEquals(5, repo.forkCount)
            assertEquals("Kotlin", repo.language)
            assertEquals("main", repo.defaultBranch)
            assertEquals("octocat/repo-1", repo.fullName)

            val request = server.takeRequest()
            assertEquals("/user/repos", request.url.encodedPath)
            assertEquals("1", request.url.queryParameter("page"))
            assertEquals("30", request.url.queryParameter("per_page"))
        }

    @Test
    fun repositoriesPagingSource_append_advancesPageKey() =
        runTest {
            val repos = (1..30).joinToString(",") { repositoryJson("repo-$it") }
            server.enqueue(jsonResponse("[$repos]"))

            val result = RepositoriesPagingSource(api, login = null).load(appendParams(key = 2))

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(30, page.data.size)
            assertEquals(1, page.prevKey)
            assertEquals(3, page.nextKey)
            assertEquals("2", server.takeRequest().url.queryParameter("page"))
        }

    @Test
    fun starredPagingSource_append_advancesPageKey() =
        runTest {
            val repos = (1..30).joinToString(",") { repositoryJson("star-$it") }
            server.enqueue(jsonResponse("[$repos]"))

            val result = StarredPagingSource(api, login = null).load(appendParams(key = 2))

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(1, page.prevKey)
            assertEquals(3, page.nextKey)
            assertEquals("2", server.takeRequest().url.queryParameter("page"))
        }

    @Test
    fun repositoriesPagingSource_refreshWithKey_requestsKeyedPage() =
        runTest {
            val repos = (1..30).joinToString(",") { repositoryJson("repo-$it") }
            server.enqueue(jsonResponse("[$repos]"))

            val result =
                RepositoriesPagingSource(api, login = null)
                    .load(
                        PagingSource.LoadParams.Refresh(
                            key = 3,
                            loadSize = 30,
                            placeholdersEnabled = false,
                        ),
                    )

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(2, page.prevKey)
            assertEquals(4, page.nextKey)
            assertEquals("3", server.takeRequest().url.queryParameter("page"))
        }

    @Test
    fun repositoriesPagingSource_prependKey2_returnsPrevKey1() =
        runTest {
            server.enqueue(jsonResponse("[${repositoryJson("repo-1")}]"))

            val result =
                RepositoriesPagingSource(api, login = null)
                    .load(
                        PagingSource.LoadParams.Prepend(
                            key = 2,
                            loadSize = 30,
                            placeholdersEnabled = false,
                        ),
                    )

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(1, page.prevKey)
            assertNull(page.nextKey)
            assertEquals("2", server.takeRequest().url.queryParameter("page"))
        }

    @Test
    fun repositoriesPagingSource_emptyPage_returnsNullKeys() =
        runTest {
            server.enqueue(jsonResponse("[]"))

            val result = RepositoriesPagingSource(api, login = null).load(refreshParams())

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(0, page.data.size)
            assertNull(page.prevKey)
            assertNull(page.nextKey)
        }

    @Test
    fun starredPagingSource_emptyPage_returnsNullKeys() =
        runTest {
            server.enqueue(jsonResponse("[]"))

            val result = StarredPagingSource(api, login = null).load(refreshParams())

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(0, page.data.size)
            assertNull(page.prevKey)
            assertNull(page.nextKey)
        }

    @Test
    fun starredPagingSource_http500_returnsLoadErrorWithHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 500 Internal Server Error")
                    .body("{}")
                    .build(),
            )

            val result = StarredPagingSource(api, login = null).load(refreshParams())

            assertTrue(result is LoadResult.Error)
            assertTrue((result as LoadResult.Error).throwable is HttpException)
        }

    @Test
    fun starredPagingSource_login_requestsPublicEndpoint() =
        runTest {
            server.enqueue(jsonResponse("[]"))

            StarredPagingSource(api, login = "torvalds").load(refreshParams())

            assertEquals("/users/torvalds/starred", server.takeRequest().url.encodedPath)
        }

    private fun refreshParams(): PagingSource.LoadParams.Refresh<Int> =
        PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false)

    private fun appendParams(key: Int): PagingSource.LoadParams.Append<Int> =
        PagingSource.LoadParams.Append(key = key, loadSize = 30, placeholdersEnabled = false)
}

/** 构造指向 MockWebServer 的 UserApi（复用 core:github-rest 工厂，零真实网络） */
private fun createApi(server: MockWebServer): UserApi {
    val retrofit =
        GitHubRestClient.createRetrofit(
            baseUrl = server.url("/"),
            client =
                GitHubRestClient.createOkHttpClient(
                    tokenProvider = GuestTokenProvider(),
                    etagStore = InMemoryEtagStore(),
                    debugLogging = false,
                ),
            json = GitHubRestClient.createJson(),
        )
    return retrofit.create(UserApi::class.java)
}

private fun jsonResponse(body: String): MockResponse =
    MockResponse
        .Builder()
        .body(body)
        .addHeader("Content-Type", "application/json")
        .build()

/** 构造 GitHub 仓库 JSON 对象（字段为 RepositoryDto 子集） */
private fun repositoryJson(
    name: String,
    private: Boolean = false,
): String =
    """
    {
      "id": 1,
      "name": "$name",
      "full_name": "octocat/$name",
      "private": $private,
      "owner": { "login": "octocat", "id": 1, "avatar_url": "https://a/u/1", "html_url": "https://github.com/octocat" },
      "description": "desc of $name",
      "html_url": "https://github.com/octocat/$name",
      "stargazers_count": 100,
      "forks_count": 5,
      "language": "Kotlin",
      "default_branch": "main"
    }
    """.trimIndent()
