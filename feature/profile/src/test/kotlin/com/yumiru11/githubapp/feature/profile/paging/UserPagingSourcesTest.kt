package com.yumiru11.githubapp.feature.profile.paging

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.api.UserApi
import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import io.mockk.coEvery
import io.mockk.mockk
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
import java.io.IOException

/**
 * Followers/Following PagingSource 单测（MockWebServer 模拟 GitHub API，零真实网络）。
 *
 * 覆盖：append 页码推进（prevKey/nextKey）、空页终止、HTTP 错误 → LoadResult.Error
 * （含非 HTTP 的 IOException 传播）、公开/私有端点分流。
 */
class UserPagingSourcesTest {
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
    fun followersPagingSource_append_advancesPageKey() =
        runTest {
            val users = (1..30).joinToString(",") { userJson("follower-$it") }
            server.enqueue(jsonResponse("[$users]"))

            val result = FollowersPagingSource(api, login = null).load(appendParams(key = 2))

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(30, page.data.size)
            assertEquals("follower-1", page.data.first().login)
            assertEquals(1, page.prevKey)
            assertEquals(3, page.nextKey)
            assertEquals("2", server.takeRequest().url.queryParameter("page"))
        }

    @Test
    fun followingPagingSource_append_advancesPageKey() =
        runTest {
            val users = (1..30).joinToString(",") { userJson("following-$it") }
            server.enqueue(jsonResponse("[$users]"))

            val result = FollowingPagingSource(api, login = null).load(appendParams(key = 2))

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(1, page.prevKey)
            assertEquals(3, page.nextKey)
            assertEquals("2", server.takeRequest().url.queryParameter("page"))
        }

    @Test
    fun followersPagingSource_emptyPage_returnsNullKeys() =
        runTest {
            server.enqueue(jsonResponse("[]"))

            val result = FollowersPagingSource(api, login = "octocat").load(refreshParams())

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(0, page.data.size)
            assertNull(page.prevKey)
            assertNull(page.nextKey)
        }

    @Test
    fun followingPagingSource_emptyPage_returnsNullKeys() =
        runTest {
            server.enqueue(jsonResponse("[]"))

            val result = FollowingPagingSource(api, login = null).load(refreshParams())

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(0, page.data.size)
            assertNull(page.prevKey)
            assertNull(page.nextKey)
        }

    @Test
    fun followersPagingSource_http500_returnsLoadErrorWithHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 500 Internal Server Error")
                    .body("{}")
                    .build(),
            )

            val result = FollowersPagingSource(api, login = null).load(refreshParams())

            assertTrue(result is LoadResult.Error)
            assertTrue((result as LoadResult.Error).throwable is HttpException)
        }

    @Test
    fun followingPagingSource_http500_returnsLoadErrorWithHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 500 Internal Server Error")
                    .body("{}")
                    .build(),
            )

            val result = FollowingPagingSource(api, login = null).load(refreshParams())

            assertTrue(result is LoadResult.Error)
            assertTrue((result as LoadResult.Error).throwable is HttpException)
        }

    @Test
    fun followingPagingSource_login_requestsPublicEndpoint() =
        runTest {
            server.enqueue(jsonResponse("[]"))

            FollowingPagingSource(api, login = "torvalds").load(refreshParams())

            assertEquals("/users/torvalds/following", server.takeRequest().url.encodedPath)
        }

    @Test
    fun followersPagingSource_self_requestsPrivateEndpoint() =
        runTest {
            server.enqueue(jsonResponse("[]"))

            FollowersPagingSource(api, login = null).load(refreshParams())

            assertEquals("/user/followers", server.takeRequest().url.encodedPath)
        }

    @Test
    fun followersPagingSource_ioException_returnsLoadErrorWithCause() =
        runTest {
            val failingApi =
                mockk<UserApi> {
                    coEvery { currentUserFollowers(any(), any()) } throws IOException("network down")
                }

            val result = FollowersPagingSource(failingApi, login = null).load(refreshParams())

            assertTrue(result is LoadResult.Error)
            assertTrue((result as LoadResult.Error).throwable is IOException)
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

/** 构造 GitHub 用户 JSON 对象（字段为 UserDto 子集） */
private fun userJson(login: String): String =
    """
    {
      "login": "$login",
      "id": 1,
      "name": "The Octocat",
      "avatar_url": "https://avatars.githubusercontent.com/u/1",
      "html_url": "https://github.com/$login",
      "bio": "GitHub mascot",
      "type": "User",
      "public_repos": 8,
      "followers": 9000,
      "following": 10
    }
    """.trimIndent()
