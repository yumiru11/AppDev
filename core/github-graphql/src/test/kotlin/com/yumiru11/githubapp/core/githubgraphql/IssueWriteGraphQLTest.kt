package com.yumiru11.githubapp.core.githubgraphql

import com.apollographql.apollo.api.Optional
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.yumiru11.githubapp.core.githubgraphql.generated.IssueWriteContextQuery
import com.yumiru11.githubapp.core.githubgraphql.generated.UpdateIssueMutation
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.auth.TokenProvider
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

/**
 * Issue 写操作 GraphQL 通道集成测试（T14，MockWebServer）。
 *
 * 覆盖：IssueWriteContextQuery（viewer login + viewerPermission + issue node id 解析）、
 * UpdateIssueMutation（body 更新 + 请求体携带 id/body 变量）。
 */
class IssueWriteGraphQLTest {
    private lateinit var server: MockWebServer
    private lateinit var apolloClient: com.apollographql.apollo.ApolloClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
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
    fun issueWriteContextQuery_validResponse_parsesPermissionAndNodeId() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {"data":{
                          "viewer":{"__typename":"User","login":"octocat"},
                          "repository":{
                            "__typename":"Repository",
                            "viewerPermission":"WRITE",
                            "issue":{"__typename":"Issue","id":"I_kwDOA"}
                          }
                        }}
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val response =
                apolloClient
                    .query(IssueWriteContextQuery(owner = "octocat", name = "Hello-World", number = 42))
                    .fetchPolicy(FetchPolicy.NetworkOnly)
                    .execute()

            assertNull(response.exception)
            val data = response.data
            assertEquals("octocat", data?.viewer?.login)
            assertEquals("WRITE", data?.repository?.viewerPermission?.rawValue)
            assertEquals("I_kwDOA", data?.repository?.issue?.id)

            val recorded = server.takeRequest()
            val body = recorded.body?.utf8().orEmpty()
            assertTrue("请求体应含 IssueWriteContext 操作名", body.contains("IssueWriteContext"))
            assertTrue("请求体应携带 number 变量", body.contains("42"))
        }

    @Test
    fun issueWriteContextQuery_repositoryWithoutIssue_parsesNullIssue() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {"data":{
                          "viewer":{"__typename":"User","login":"octocat"},
                          "repository":{"__typename":"Repository","viewerPermission":"READ","issue":null}
                        }}
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val response =
                apolloClient
                    .query(IssueWriteContextQuery(owner = "octocat", name = "Hello-World", number = 999))
                    .fetchPolicy(FetchPolicy.NetworkOnly)
                    .execute()

            assertNull(response.exception)
            assertEquals(
                "READ",
                response.data
                    ?.repository
                    ?.viewerPermission
                    ?.rawValue,
            )
            assertNull("issue 不存在应为 null", response.data?.repository?.issue)
        }

    @Test
    fun updateIssueMutation_validResponse_sendsIdAndBodyVariables() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {"data":{"updateIssue":{
                          "__typename":"UpdateIssuePayload",
                          "issue":{
                            "__typename":"Issue",
                            "id":"I_kwDOA","number":42,"title":"t",
                            "body":"- [x] done","state":"OPEN"
                          }
                        }}}
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val response =
                apolloClient
                    .mutation(
                        UpdateIssueMutation(
                            id = "I_kwDOA",
                            body = Optional.present("- [x] done"),
                        ),
                    ).execute()

            assertNull(response.exception)
            val issue = response.data?.updateIssue?.issue
            assertEquals("I_kwDOA", issue?.id)
            assertEquals("- [x] done", issue?.body)
            assertEquals("OPEN", issue?.state?.rawValue)

            val recorded = server.takeRequest()
            val body = recorded.body?.utf8().orEmpty()
            assertTrue("请求体应含 UpdateIssue 操作名", body.contains("UpdateIssue"))
            assertTrue("请求体应携带 node id", body.contains("I_kwDOA"))
            assertTrue("请求体应携带新 body", body.contains("- [x] done"))
        }

    @Test
    fun updateIssueMutation_graphqlErrors_reportedInResponse() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {"data":null,"errors":[{"message":"Could not resolve to a node with the global id of 'bad'","type":"NOT_FOUND"}]}
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val response =
                apolloClient
                    .mutation(UpdateIssueMutation(id = "bad", body = Optional.present("x")))
                    .execute()

            assertNull(response.data)
            assertTrue("应包含 GraphQL errors", response.hasErrors())
            assertEquals("Could not resolve to a node with the global id of 'bad'", response.errors?.first()?.message)
        }
}
