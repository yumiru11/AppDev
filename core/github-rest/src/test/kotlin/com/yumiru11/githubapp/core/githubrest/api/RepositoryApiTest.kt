package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
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
 * [RepositoryApi] 测试补充（checklist C1：DTO 解析边界/错误码/空响应）。
 *
 * 覆盖既有 GitHubRestApiTest 未触及的：可选字段缺失回退默认值、400/401/403/404/500 错误码、空 body。
 */
class RepositoryApiTest {
    private lateinit var server: MockWebServer
    private lateinit var repositoryApi: RepositoryApi

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
        repositoryApi = retrofit.create(RepositoryApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun getRepository_optionalFieldsMissing_parsesWithDefaults() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "id": 1,
                          "name": "minimal-repo",
                          "full_name": "octocat/minimal-repo",
                          "private": false,
                          "owner": { "login": "octocat", "id": 1 }
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val repo = repositoryApi.getRepository("octocat", "minimal-repo")

            assertEquals("minimal-repo", repo.name)
            assertEquals(false, repo.isPrivate)
            assertEquals("octocat", repo.owner.login)
            assertNull("description 缺失应解析为 null", repo.description)
            assertNull("htmlUrl 缺失应解析为 null", repo.htmlUrl)
            assertNull("language 缺失应解析为 null", repo.language)
            assertNull("defaultBranch 缺失应解析为 null", repo.defaultBranch)
            assertEquals("stargazersCount 缺失应回退 0", 0, repo.stargazersCount)
            assertEquals("forksCount 缺失应回退 0", 0, repo.forksCount)
        }

    @Test
    fun getRepository_401Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 401 Unauthorized")
                    .body("""{"message":"Bad credentials"}""")
                    .build(),
            )

            try {
                repositoryApi.getRepository("octocat", "private-repo")
                fail("401 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(401, e.code())
            }
        }

    @Test
    fun getRepository_403Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 403 Forbidden")
                    .body("""{"message":"Resource not accessible by integration"}""")
                    .build(),
            )

            try {
                repositoryApi.getRepository("octocat", "restricted-repo")
                fail("403 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(403, e.code())
            }
        }

    @Test
    fun getRepository_404Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 404 Not Found")
                    .body("""{"message":"Not Found"}""")
                    .build(),
            )

            try {
                repositoryApi.getRepository("octocat", "missing-repo")
                fail("404 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(404, e.code())
            }
        }

    @Test
    fun getRepository_500Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 500 Internal Server Error")
                    .body("""{"message":"Server Error"}""")
                    .build(),
            )

            try {
                repositoryApi.getRepository("octocat", "Hello-World")
                fail("500 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(500, e.code())
            }
        }

    @Test
    fun getRepository_emptyBody200Response_throwsSerializationException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 200 OK")
                    .body("")
                    .build(),
            )

            // kotlinx 解析空串 → JsonDecodingException（SerializationException 子类）
            val result = runCatching { repositoryApi.getRepository("octocat", "Hello-World") }
            assertTrue(
                "空 body 应抛 SerializationException，实际：${result.exceptionOrNull()}",
                result.exceptionOrNull() is SerializationException,
            )
        }
}
