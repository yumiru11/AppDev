package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import com.yumiru11.githubapp.core.githubrest.model.GitRefCreateRequest
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * [GitRefApi] 测试（T22 建分支端点；T23 分支列表）。
 *
 * 覆盖：getBranch/createRef 的路径与请求体、listBranches 的 per_page 参数与响应解析
 * （name/sha/protected）、非 2xx 抛 HttpException。
 */
class GitRefApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: GitRefApi

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
        api = retrofit.create(GitRefApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun getBranch_headsPrefixedPath_parsesRefObject() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """{"ref":"refs/heads/main","object":{"sha":"abc123","type":"commit","url":"u"}}""",
                ),
            )

            val dto = api.getBranch("octocat", "Hello-World", "heads/main")

            assertEquals("refs/heads/main", dto.ref)
            assertEquals("abc123", dto.`object`.sha)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            // Retrofit @Path 将 ref="heads/main" 的 '/' 编码为 %2F（T22 既有发送行为）
            assertTrue(request.url.encodedPath.contains("/repos/octocat/Hello-World/git/ref/heads/"))
            assertTrue(request.url.encodedPath.contains("heads%2Fmain"))
        }

    @Test
    fun createRef_sendsFullRefAndSha_parsesResponse() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """{"ref":"refs/heads/feat-x","object":{"sha":"abc123","type":"commit","url":"u"}}""",
                ),
            )

            val dto = api.createRef("octocat", "Hello-World", GitRefCreateRequest(ref = "refs/heads/feat-x", sha = "abc123"))

            assertEquals("refs/heads/feat-x", dto.ref)
            val request = server.takeRequest()
            assertEquals("POST", request.method.toString())
            assertEquals("/repos/octocat/Hello-World/git/refs", request.url.encodedPath)
            val body = request.body?.utf8()
            assertTrue(body?.contains("\"ref\":\"refs/heads/feat-x\"") == true)
            assertTrue(body?.contains("\"sha\":\"abc123\"") == true)
        }

    @Test
    fun listBranches_defaultParams_sendsPathAndPerPage() =
        runTest {
            server.enqueue(jsonResponse("[]"))

            val branches = api.listBranches("octocat", "Hello-World")

            assertEquals(0, branches.size)
            val request = server.takeRequest()
            assertEquals("/repos/octocat/Hello-World/branches", request.url.encodedPath)
            assertEquals("100", request.url.queryParameter("per_page"))
        }

    @Test
    fun listBranches_response_parsesNameShaAndProtected() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """
                    [
                      {"name":"main","commit":{"sha":"abc123","url":"u"},"protected":true},
                      {"name":"dev","commit":{"sha":"def456","url":"u"},"protected":false}
                    ]
                    """.trimIndent(),
                ),
            )

            val branches = api.listBranches("octocat", "Hello-World")

            assertEquals(2, branches.size)
            assertEquals("main", branches[0].name)
            assertEquals("abc123", branches[0].commit?.sha)
            assertTrue(branches[0].`protected`)
            assertEquals("dev", branches[1].name)
            assertEquals("def456", branches[1].commit?.sha)
        }

    @Test
    fun listBranches_httpError_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 404 Not Found")
                    .body("""{"message":"Not Found"}""")
                    .build(),
            )

            try {
                api.listBranches("octocat", "Hello-World")
                fail("expected HttpException")
            } catch (e: HttpException) {
                assertEquals(404, e.code())
            }
        }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse
            .Builder()
            .body(body)
            .addHeader("Content-Type", "application/json")
            .build()
}
