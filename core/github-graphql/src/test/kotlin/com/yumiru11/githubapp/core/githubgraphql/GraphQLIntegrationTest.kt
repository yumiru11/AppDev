package com.yumiru11.githubapp.core.githubgraphql

import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.yumiru11.githubapp.core.githubgraphql.generated.ViewerQuery
import com.yumiru11.githubapp.core.githubgraphql.generated.ViewerRepositoriesQuery
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.auth.TokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * GraphQL 通道集成测试（MockWebServer 模拟 /graphql，验收允许 mockwebserver 通道）。
 *
 * 覆盖：viewer 全链（共享 OkHttp 请求头 + 解析）、GraphQL errors、HTTP 错误、分页游标传递。
 */
class GraphQLIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var apolloClient: com.apollographql.apollo.ApolloClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // 共享 OkHttp（与 REST 通道同款拦截器链）：验证 GraphQL 也带统一头/Auth
        val okHttpClient =
            GitHubRestClient.createOkHttpClient(
                tokenProvider = TokenProvider { "gql-token" },
                etagStore = InMemoryEtagStore(),
                debugLogging = false,
            )
        apolloClient =
            GitHubApolloClientFactory.create(
                serverUrl = server.url("/graphql").toString(),
                okHttpClient = okHttpClient,
            )
    }

    @After
    fun tearDown() {
        server.close()
        apolloClient.close()
    }

    @Test
    fun viewerQuery_validResponse_parsesFieldsAndSendsSharedHeaders() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {"data":{"viewer":{
                          "__typename":"User",
                          "login":"octocat","name":"The Octocat",
                          "avatarUrl":"https://avatars.githubusercontent.com/u/1",
                          "bio":"GitHub mascot","url":"https://github.com/octocat"
                        }}}
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val response = apolloClient.query(ViewerQuery()).fetchPolicy(FetchPolicy.NetworkOnly).execute()

            assertNull(response.exception)
            val viewer = response.data?.viewer
            assertEquals("octocat", viewer?.login)
            assertEquals("The Octocat", viewer?.name)
            assertEquals("https://github.com/octocat", viewer?.url)

            // 共享 OkHttp 链：GraphQL POST 同样携带统一头与 Auth
            val recorded = server.takeRequest()
            assertEquals("Bearer gql-token", recorded.headers["Authorization"])
            assertEquals("application/vnd.github+json", recorded.headers["Accept"])
            assertEquals("2022-11-28", recorded.headers["X-GitHub-Api-Version"])
            // 请求体是 GraphQL 文档 + operationName
            val body = recorded.body?.utf8().orEmpty()
            assertTrue("请求体应含 Viewer 操作名", body.contains("Viewer"))
            assertTrue("请求体应含 query 字段", body.contains("\"query\""))
        }

    @Test
    fun viewerQuery_graphqlErrorsPayload_reportedInResponse() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"data":null,"errors":[{"message":"Bad credentials","type":"UNAUTHORIZED"}]}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val response = apolloClient.query(ViewerQuery()).fetchPolicy(FetchPolicy.NetworkOnly).execute()

            // GraphQL 错误随 200 返回：data 为空、errors 携带 message（归一化在 core:github-data 处理）
            assertNull(response.data)
            assertTrue("响应应包含 GraphQL errors", response.hasErrors())
            assertEquals("Bad credentials", response.errors?.first()?.message)
        }

    @Test
    fun viewerQuery_http401_throwsApolloHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 401 Unauthorized")
                    .body("{}")
                    .build(),
            )

            // Apollo 5 execute() 不抛网络异常，错误收敛到 response.exception（归一化在 core:github-data）
            val response = apolloClient.query(ViewerQuery()).fetchPolicy(FetchPolicy.NetworkOnly).execute()

            val exception = response.exception
            assertTrue(
                "非 2xx 应产生 ApolloHttpException，实际：$exception",
                exception is ApolloHttpException,
            )
            assertEquals(401, (exception as ApolloHttpException).statusCode)
        }

    @Test
    fun viewerRepositoriesQuery_afterCursor_sendsCursorAndParsesPageInfo() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {"data":{"viewer":{"__typename":"User","repositories":{
                          "__typename":"RepositoryConnection",
                          "pageInfo":{"__typename":"PageInfo","hasNextPage":true,"endCursor":"CURSOR_B"},
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
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val response =
                apolloClient
                    .query(ViewerRepositoriesQuery(first = 2, after = Optional.present("CURSOR_A")))
                    .fetchPolicy(FetchPolicy.NetworkOnly)
                    .execute()

            // 游标随 variables 传递到服务端
            val recorded = server.takeRequest()
            assertTrue(
                "请求体应携带分页游标 CURSOR_A",
                recorded.body
                    ?.utf8()
                    .orEmpty()
                    .contains("CURSOR_A"),
            )

            val repositories = response.data?.viewer?.repositories
            assertNotNull(repositories)
            assertEquals(true, repositories?.pageInfo?.hasNextPage)
            assertEquals("CURSOR_B", repositories?.pageInfo?.endCursor)

            val node = repositories?.nodes?.single()
            assertEquals("Hello-World", node?.name)
            // DateTime 标量 → java.time.Instant（自定义 InstantAdapter）
            assertEquals(Instant.parse("2026-08-01T00:00:00Z"), node?.updatedAt)
            assertEquals("octocat", node?.owner?.login)
        }
}
