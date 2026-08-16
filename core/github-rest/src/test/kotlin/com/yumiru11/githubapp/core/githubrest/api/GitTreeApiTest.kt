package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [GitTreeApi] 集成测试（MockWebServer 模拟 GitHub Git Data API）。
 *
 * 覆盖：根树解析（blob/tree 条目 + size）、ref 直接作 tree_sha 调用、
 * 空树、truncated 标记透传、非 2xx 抛 HttpException、路径拼装正确。
 */
class GitTreeApiTest {
    private lateinit var server: MockWebServer
    private lateinit var gitTreeApi: GitTreeApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit =
            GitHubRestClient.createRetrofit(
                baseUrl = server.url("/"),
                client = GitHubRestClient.createOkHttpClient(GuestTokenProvider(), InMemoryEtagStore(), false),
                json = GitHubRestClient.createJson(),
            )
        gitTreeApi = retrofit.create(GitTreeApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun getTree_validResponse_parsesBlobAndTreeEntries() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "sha": "9fb037999f264ba9a7fc6274d1faef2cf7a2b1b3",
                          "url": "https://api.github.com/repos/octocat/Hello-World/git/trees/9fb037999f264ba9a7fc6274d1faef2cf7a2b1b3",
                          "tree": [
                            { "path": "README.md", "mode": "100644", "type": "blob", "sha": "aabbcc", "size": 14, "url": "https://api.github.com/repos/octocat/Hello-World/git/blobs/aabbcc" },
                            { "path": "src", "mode": "040000", "type": "tree", "sha": "ddeeff", "url": "https://api.github.com/repos/octocat/Hello-World/git/trees/ddeeff" }
                          ],
                          "truncated": false
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val result = gitTreeApi.getTree("octocat", "Hello-World", "main")

            assertEquals("9fb037999f264ba9a7fc6274d1faef2cf7a2b1b3", result.sha)
            assertFalse(result.truncated)
            assertEquals(2, result.tree.size)
            val blob = result.tree[0]
            assertEquals("README.md", blob.path)
            assertEquals("blob", blob.type)
            assertEquals(14L, blob.size)
            val dir = result.tree[1]
            assertEquals("src", dir.path)
            assertEquals("tree", dir.type)
            assertEquals("ddeeff", dir.sha)
            assertEquals(null, dir.size)

            val request = server.takeRequest()
            assertEquals("/repos/octocat/Hello-World/git/trees/main", request.url.encodedPath)
        }

    @Test
    fun getTree_branchNameAsTreeSha_usesRefPath() =
        runTest {
            // GitHub Git Data API 接受分支名作 tree_sha（无需先取 commit）
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"sha": "abc", "tree": [], "truncated": false}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            gitTreeApi.getTree("octocat", "Hello-World", "develop")

            val request = server.takeRequest()
            assertEquals("/repos/octocat/Hello-World/git/trees/develop", request.url.encodedPath)
        }

    @Test
    fun getTree_truncatedFlag_true_isPreserved() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"sha": "abc", "tree": [{"path": "a.kt", "type": "blob"}], "truncated": true}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val result = gitTreeApi.getTree("octocat", "Hello-World", "main")

            assertTrue(result.truncated)
        }

    @Test
    fun getTree_404Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 404 Not Found")
                    .body("""{"message":"Not Found"}""")
                    .build(),
            )

            try {
                gitTreeApi.getTree("octocat", "Missing-Repo", "main")
                org.junit.Assert.fail("404 应抛 HttpException")
            } catch (e: retrofit2.HttpException) {
                assertEquals(404, e.code())
            }
        }

    @Test
    fun getTree_missingFields_haveSafeDefaults() =
        runTest {
            // GitHub 树条目可能缺 mode/url（历史数据）；path/type 缺失也不应崩溃
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"tree": [{"path": "x.txt"}, {}], "truncated": false}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val result = gitTreeApi.getTree("octocat", "Hello-World", "main")

            assertEquals(2, result.tree.size)
            assertEquals("x.txt", result.tree[0].path)
            assertEquals(null, result.tree[0].type)
            assertEquals("", result.tree[1].path)
        }
}
