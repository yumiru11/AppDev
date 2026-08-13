package com.yumiru11.githubapp.feature.profile

import androidx.paging.PagingSource
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * ProfileRepository 单测（MockWebServer 模拟 GitHub API，零真实网络）。
 *
 * 覆盖：资料头（self / login 双端点 + 统计字段映射 + 404）；四列表 PagingSource
 * 分页（首页 nextKey / 尾页 null / 错误 → LoadResult.Error）。
 */
class ProfileRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: ProfileRepository

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
        repository = ProfileRepository(userApi = retrofit.create(UserApi::class.java))
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun userJson(
        login: String = "octocat",
        publicRepos: Int = 8,
        followers: Int = 9_000,
        following: Int = 10,
    ): String =
        """
        {
          "login": "$login",
          "id": 1,
          "name": "The Octocat",
          "avatar_url": "https://avatars.githubusercontent.com/u/1",
          "html_url": "https://github.com/$login",
          "bio": "GitHub mascot",
          "type": "User",
          "public_repos": $publicRepos,
          "followers": $followers,
          "following": $following
        }
        """.trimIndent()

    private fun repositoryJson(name: String): String =
        """
        {
          "id": 1,
          "name": "$name",
          "full_name": "octocat/$name",
          "private": false,
          "owner": { "login": "octocat", "id": 1, "avatar_url": "https://a/u/1", "html_url": "https://github.com/octocat" },
          "description": "desc of $name",
          "html_url": "https://github.com/octocat/$name",
          "stargazers_count": 100,
          "forks_count": 5,
          "language": "Kotlin",
          "default_branch": "main"
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

    @Test
    fun getProfile_self_validResponse_mapsDomainWithStats() =
        runTest {
            enqueueJson(userJson())

            val user = repository.getProfile(login = null)

            assertEquals("octocat", user.login)
            assertEquals("The Octocat", user.name)
            assertEquals("GitHub mascot", user.bio)
            assertEquals(8, user.publicRepos)
            assertEquals(9_000, user.followers)
            assertEquals(10, user.following)
            assertEquals("/user", server.takeRequest().url.encodedPath)
        }

    @Test
    fun getProfile_login_validResponse_mapsDomain() =
        runTest {
            enqueueJson(userJson(login = "torvalds"))

            val user = repository.getProfile(login = "torvalds")

            assertEquals("torvalds", user.login)
            assertEquals("/users/torvalds", server.takeRequest().url.encodedPath)
        }

    @Test
    fun getProfile_404Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 404 Not Found")
                    .body("""{"message":"Not Found"}""")
                    .build(),
            )

            try {
                repository.getProfile(login = "ghost-user")
                fail("404 应抛 HttpException（Retrofit 3 语义）")
            } catch (e: HttpException) {
                assertEquals(404, e.code())
            }
        }

    @Test
    fun repositoriesPagingSource_self_firstPage_returnsPageWithNextKey() =
        runTest {
            val repos = (1..30).joinToString(",") { repositoryJson("repo-$it") }
            enqueueJson("[$repos]")

            val result = repository.repositories(login = null).load(refreshParams())

            assertTrue(result is PagingSource.LoadResult.Page)
            val page = result as PagingSource.LoadResult.Page
            assertEquals(30, page.data.size)
            assertEquals("repo-1", page.data.first().name)
            assertEquals(2, page.nextKey)
            assertNull(page.prevKey)
            assertEquals("/user/repos", server.takeRequest().url.encodedPath)
        }

    @Test
    fun repositoriesPagingSource_login_lastPage_returnsPageWithNullNextKey() =
        runTest {
            // 尾页不足一页 → nextKey = null（分页终止）
            enqueueJson("[${repositoryJson("only-one")}]")

            val result = repository.repositories(login = "torvalds").load(refreshParams())

            assertTrue(result is PagingSource.LoadResult.Page)
            val page = result as PagingSource.LoadResult.Page
            assertEquals(1, page.data.size)
            assertNull(page.nextKey)
            assertEquals("/users/torvalds/repos", server.takeRequest().url.encodedPath)
        }

    @Test
    fun starredPagingSource_self_firstPage_returnsPageWithNextKey() =
        runTest {
            val repos = (1..30).joinToString(",") { repositoryJson("star-$it") }
            enqueueJson("[$repos]")

            val result = repository.starred(login = null).load(refreshParams())

            assertTrue(result is PagingSource.LoadResult.Page)
            val page = result as PagingSource.LoadResult.Page
            assertEquals(30, page.data.size)
            assertEquals(2, page.nextKey)
            assertEquals("/user/starred", server.takeRequest().url.encodedPath)
        }

    @Test
    fun followersPagingSource_login_firstPage_returnsPageWithNextKey() =
        runTest {
            val users = (1..30).joinToString(",") { userJson(login = "follower-$it") }
            enqueueJson("[$users]")

            val result = repository.followers(login = "octocat").load(refreshParams())

            assertTrue(result is PagingSource.LoadResult.Page)
            val page = result as PagingSource.LoadResult.Page
            assertEquals(30, page.data.size)
            assertEquals("follower-1", page.data.first().login)
            assertEquals(2, page.nextKey)
            assertEquals("/users/octocat/followers", server.takeRequest().url.encodedPath)
        }

    @Test
    fun followingPagingSource_self_firstPage_returnsPageWithNextKey() =
        runTest {
            val users = (1..30).joinToString(",") { userJson(login = "following-$it") }
            enqueueJson("[$users]")

            val result = repository.following(login = null).load(refreshParams())

            assertTrue(result is PagingSource.LoadResult.Page)
            val page = result as PagingSource.LoadResult.Page
            assertEquals(30, page.data.size)
            assertEquals(2, page.nextKey)
            assertEquals("/user/following", server.takeRequest().url.encodedPath)
        }

    @Test
    fun pagingSource_errorResponse_returnsLoadError() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 500 Internal Server Error")
                    .body("{}")
                    .build(),
            )

            val result = repository.repositories(login = null).load(refreshParams())

            assertTrue(result is PagingSource.LoadResult.Error)
        }

    private fun refreshParams(): PagingSource.LoadParams.Refresh<Int> =
        PagingSource.LoadParams.Refresh(
            key = null,
            loadSize = 30,
            placeholdersEnabled = false,
        )
}
