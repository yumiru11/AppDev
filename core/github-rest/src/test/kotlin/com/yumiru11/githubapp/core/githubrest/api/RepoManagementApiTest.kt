package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import com.yumiru11.githubapp.core.githubrest.model.ReleaseDto
import com.yumiru11.githubapp.core.githubrest.model.SubscriptionDto
import com.yumiru11.githubapp.core.githubrest.model.SubscriptionRequest
import com.yumiru11.githubapp.core.githubrest.model.TagCommitDto
import com.yumiru11.githubapp.core.githubrest.model.TagDto
import com.yumiru11.githubapp.core.githubrest.model.UserDto
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * [RepoManagementApi] 测试（T12：Star/Watch/Fork + Releases/Tags/Languages）。
 *
 * 覆盖：状态检查端点状态码语义（204/404）、写操作方法/路径/请求体、Fork 错误码（403/422）、
 * 列表与语言 Map 解析。
 */
class RepoManagementApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: RepoManagementApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
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
        api = retrofit.create(RepoManagementApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    // ---- Star 状态检查 ----

    @Test
    fun isStarred_204Response_returnsSuccess() =
        runTest {
            server.enqueue(MockResponse.Builder().status("HTTP/1.1 204 No Content").build())

            val response = api.isStarred("octocat", "Hello-World")

            assertTrue(response.isSuccessful)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/user/starred/octocat/Hello-World", request.url.encodedPath)
        }

    @Test
    fun isStarred_404Response_returnsErrorResponse() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 404 Not Found")
                    .body("""{"message":"Not Found"}""")
                    .build(),
            )

            // Response<Unit> 返回类型：非 2xx 不抛异常，返回 error response（调用方按 isSuccessful=false 处理）
            val response = api.isStarred("octocat", "Hello-World")

            assertFalse(response.isSuccessful)
            assertEquals(404, response.code())
        }

    // ---- Star/Unstar 写操作 ----

    @Test
    fun star_204Response_returnsSuccess() =
        runTest {
            server.enqueue(MockResponse.Builder().status("HTTP/1.1 204 No Content").build())

            val response = api.star("octocat", "Hello-World")

            assertTrue(response.isSuccessful)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/user/starred/octocat/Hello-World", request.url.encodedPath)
        }

    @Test
    fun unstar_204Response_returnsSuccess() =
        runTest {
            server.enqueue(MockResponse.Builder().status("HTTP/1.1 204 No Content").build())

            val response = api.unstar("octocat", "Hello-World")

            assertTrue(response.isSuccessful)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/user/starred/octocat/Hello-World", request.url.encodedPath)
        }

    // ---- Watch 状态检查 ----

    @Test
    fun getSubscription_200Subscribed_parsesSubscribed() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"subscribed":true,"ignored":false}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val subscription = api.getSubscription("octocat", "Hello-World")

            assertEquals(true, subscription.subscribed)
            assertEquals(false, subscription.ignored)
        }

    @Test
    fun getSubscription_404Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 404 Not Found")
                    .body("""{"message":"Not Found"}""")
                    .build(),
            )

            try {
                api.getSubscription("octocat", "Hello-World")
                fail("404 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(404, e.code())
            }
        }

    // ---- Watch/Unwatch 写操作 ----

    @Test
    fun watch_sendsSubscribedTrueBody() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"subscribed":true,"ignored":false}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val subscription = api.watch("octocat", "Hello-World", SubscriptionRequest(subscribed = true))

            assertEquals(true, subscription.subscribed)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/repos/octocat/Hello-World/subscription", request.url.encodedPath)
            assertEquals("""{"subscribed":true}""", request.body?.utf8())
        }

    @Test
    fun unwatch_204Response_returnsSuccess() =
        runTest {
            server.enqueue(MockResponse.Builder().status("HTTP/1.1 204 No Content").build())

            val response = api.unwatch("octocat", "Hello-World")

            assertTrue(response.isSuccessful)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/repos/octocat/Hello-World/subscription", request.url.encodedPath)
        }

    // ---- Fork ----

    @Test
    fun fork_202Response_parsesRepositoryDto() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 202 Accepted")
                    .body(
                        """
                        {
                          "id": 1296269,
                          "name": "Hello-World",
                          "full_name": "octocat/Hello-World",
                          "private": false,
                          "owner": { "login": "octocat", "id": 1 },
                          "stargazers_count": 80,
                          "forks_count": 9,
                          "language": "Kotlin",
                          "default_branch": "main"
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val repo = api.fork("octocat", "Hello-World")

            assertEquals("Hello-World", repo.name)
            assertEquals("octocat", repo.owner.login)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/repos/octocat/Hello-World/forks", request.url.encodedPath)
        }

    @Test
    fun fork_403Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 403 Forbidden")
                    .body("""{"message":"Resource not accessible by integration"}""")
                    .build(),
            )

            try {
                api.fork("octocat", "Hello-World")
                fail("403 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(403, e.code())
            }
        }

    @Test
    fun fork_422Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 422 Unprocessable Entity")
                    .body("""{"message":"Repository already exists"}""")
                    .build(),
            )

            try {
                api.fork("octocat", "Hello-World")
                fail("422 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(422, e.code())
            }
        }

    // ---- Releases ----

    @Test
    fun listReleases_200Response_parsesList() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [
                          {
                            "id": 1,
                            "tag_name": "v1.0.0",
                            "name": "First release",
                            "body": "Initial release",
                            "html_url": "https://github.com/octocat/Hello-World/releases/tag/v1.0.0",
                            "published_at": "2026-01-15T10:30:00Z",
                            "prerelease": false,
                            "draft": false,
                            "author": { "login": "octocat", "id": 1 }
                          }
                        ]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val releases = api.listReleases("octocat", "Hello-World")

            assertEquals(1, releases.size)
            assertEquals("v1.0.0", releases[0].tagName)
            assertEquals("First release", releases[0].name)
            assertEquals("octocat", releases[0].author?.login)
            assertEquals("2026-01-15T10:30:00Z", releases[0].publishedAt)
        }

    @Test
    fun getRelease_200Response_parsesDetail() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "id": 42,
                          "tag_name": "v2.0.0",
                          "name": "Second release",
                          "body": "Changelog",
                          "prerelease": true,
                          "draft": false
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val release = api.getRelease("octocat", "Hello-World", 42)

            assertEquals(42, release.id)
            assertEquals("v2.0.0", release.tagName)
            assertEquals(true, release.prerelease)
            val request = server.takeRequest()
            assertEquals("/repos/octocat/Hello-World/releases/42", request.url.encodedPath)
        }

    // ---- Tags ----

    @Test
    fun listTags_200Response_parsesList() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [
                          {
                            "name": "v1.0.0",
                            "commit": {
                              "sha": "c5b97d5ae6c19d5c5df71a34c7fbeeda2479ccbc",
                              "url": "https://api.github.com/repos/octocat/Hello-World/commits/c5b97d5ae6c19d5c5df71a34c7fbeeda2479ccbc"
                            }
                          }
                        ]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val tags = api.listTags("octocat", "Hello-World")

            assertEquals(1, tags.size)
            assertEquals("v1.0.0", tags[0].name)
            assertEquals("c5b97d5ae6c19d5c5df71a34c7fbeeda2479ccbc", tags[0].commit.sha)
        }

    // ---- Languages ----

    @Test
    fun getLanguages_200Response_parsesMap() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"Kotlin": 102400, "Java": 51200}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val languages = api.getLanguages("octocat", "Hello-World")

            assertEquals(2, languages.size)
            assertEquals(102400L, languages["Kotlin"])
            assertEquals(51200L, languages["Java"])
        }

    // ---- 分支删除（T17 MergeBox）----

    @Test
    fun deleteBranch_204Response_returnsSuccess() =
        runTest {
            server.enqueue(MockResponse.Builder().status("HTTP/1.1 204 No Content").build())

            val response = api.deleteBranch("octocat", "Hello-World", "feature")

            assertTrue(response.isSuccessful)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/repos/octocat/Hello-World/git/refs/heads/feature", request.url.encodedPath)
        }
}
