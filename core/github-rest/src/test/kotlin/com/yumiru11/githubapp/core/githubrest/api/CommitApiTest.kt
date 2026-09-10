package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import com.yumiru11.githubapp.core.githubrest.model.CommitDetailDto
import com.yumiru11.githubapp.core.githubrest.model.CommitFileDto
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
 * [CommitApi] 测试（L09：COMMIT 详情页）。
 *
 * 覆盖：路径与分页 query、提交元信息/统计/文件列表解析、patch 缺省（二进制文件）、
 * 404/403 错误码。
 */
class CommitApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: CommitApi

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
        api = retrofit.create(CommitApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun getCommit_validResponse_parsesMetaStatsAndFiles() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "sha": "abc123def456",
                          "commit": {
                            "message": "fix: something\n\nbody text",
                            "author": { "name": "Octo", "email": "octo@example.com", "date": "2026-09-01T10:00:00Z" }
                          },
                          "author": { "login": "octocat", "id": 1, "avatar_url": "https://a.example/u" },
                          "stats": { "additions": 12, "deletions": 3, "total": 15 },
                          "files": [
                            {
                              "filename": "src/Main.kt",
                              "status": "modified",
                              "additions": 10,
                              "deletions": 2,
                              "changes": 12,
                              "patch": "@@ -1,3 +1,4 @@\n-old\n+new\n"
                            },
                            {
                              "filename": "logo.png",
                              "status": "added",
                              "additions": 0,
                              "deletions": 0,
                              "changes": 0
                            }
                          ]
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val commit = api.getCommit("octocat", "Hello-World", "abc123def456", perPage = 100, page = 1)

            assertEquals("abc123def456", commit.sha)
            assertEquals("fix: something\n\nbody text", commit.commit.message)
            assertEquals("Octo", commit.commit.author?.name)
            assertEquals("octocat", commit.author?.login)
            assertEquals(12, commit.stats?.additions)
            assertEquals(2, commit.files.size)
            assertEquals("src/Main.kt", commit.files[0].filename)
            assertTrue(commit.files[0].patch?.contains("+new") == true)
            assertNull("二进制文件无 patch", commit.files[1].patch)

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/repos/octocat/Hello-World/commits/abc123def456", request.url.encodedPath)
            assertEquals("100", request.url.queryParameter("per_page"))
            assertEquals("1", request.url.queryParameter("page"))
        }

    @Test
    fun getCommit_pageTwo_passesPagingQuery() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"sha":"s","commit":{"message":"m"},"files":[]}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            api.getCommit("octocat", "Hello-World", "main", perPage = 100, page = 2)

            val request = server.takeRequest()
            assertEquals("100", request.url.queryParameter("per_page"))
            assertEquals("2", request.url.queryParameter("page"))
        }

    @Test
    fun getCommit_missingOptionalFields_parsesWithDefaults() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"sha":"s","commit":{"message":"only message"}}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val commit = api.getCommit("octocat", "Hello-World", "s", perPage = 100, page = 1)

            assertNull(commit.author)
            assertNull(commit.stats)
            assertTrue(commit.files.isEmpty())
        }

    @Test
    fun getCommit_404Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 404 Not Found")
                    .body("""{"message":"No commit found for SHA"}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val result = runCatching { api.getCommit("octocat", "Hello-World", "deadbeef", 100, 1) }

            val exception = result.exceptionOrNull()
            assertTrue("404 应抛 HttpException，实际：$exception", exception is HttpException)
            assertEquals(404, (exception as HttpException).code())
        }

    @Test
    fun getCommit_403Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 403 Forbidden")
                    .body("""{"message":"Forbidden"}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val result = runCatching { api.getCommit("octocat", "private", "s", 100, 1) }

            assertEquals(403, (result.exceptionOrNull() as HttpException).code())
        }

    /** DTO 直接构造（编译期保障字段名与类型：CommitDetailDto/CommitFileDto 的生产可见性）。 */
    @Test
    fun commitDetailDto_directConstruction_keepsFieldMapping() {
        val dto =
            CommitDetailDto(
                sha = "s",
                commit =
                    com.yumiru11.githubapp.core.githubrest.model
                        .CommitInfoDto(message = "m"),
                files = listOf(CommitFileDto(filename = "a.kt", additions = 1, deletions = 2)),
            )

        assertEquals("s", dto.sha)
        assertEquals(1, dto.files[0].additions)
    }
}
