package com.yumiru11.githubapp.feature.pullrequest.data

import com.apollographql.apollo.ApolloClient
import com.yumiru11.githubapp.core.githubauth.token.TokenStorage
import com.yumiru11.githubapp.core.githubrest.api.GitRefApi
import com.yumiru11.githubapp.core.githubrest.api.IssueApi
import com.yumiru11.githubapp.core.githubrest.api.PullRequestApi
import com.yumiru11.githubapp.core.githubrest.api.RepoManagementApi
import com.yumiru11.githubapp.core.githubrest.api.RepositoryApi
import com.yumiru11.githubapp.core.githubrest.api.UserApi
import com.yumiru11.githubapp.core.githubrest.model.PullRequestFileDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PR 文件列表缓存的 revision 身份回归（spec-audit §3.6 / plan §4.6）。
 *
 * 缓存 key 必须含 PR head sha：换 revision（PR 推新提交）后不得返回上一个 revision 的列表。
 * 无 head sha（详情未取到 head 的理论边界）时不做任何缓存，直连网络。
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class PullRequestRepositoryCacheTest {
    private val owner = "octocat"
    private val repo = "Hello-World"
    private val number = 7

    private fun repository(pullRequestApi: PullRequestApi): PullRequestRepository =
        PullRequestRepository(
            pullRequestApi = pullRequestApi,
            repositoryApi = mockk(relaxed = true),
            repoManagementApi = mockk(relaxed = true),
            gitRefApi = mockk(relaxed = true),
            issueApi = mockk(relaxed = true),
            apolloClient = mockk(relaxed = true),
            userApi = mockk(relaxed = true),
            tokenStorage = mockk(relaxed = true),
        )

    private fun file(filename: String): PullRequestFileDto = PullRequestFileDto(filename = filename)

    @Test
    fun files_sameHeadSha_reusesCacheWithoutSecondRequest() =
        runTest {
            val api = mockk<PullRequestApi>()
            coEvery { api.listFiles(owner, repo, number) } returns listOf(file("A.kt"))
            val repository = repository(api)

            val first = repository.files(owner, repo, number, "sha-1")
            val second = repository.files(owner, repo, number, "sha-1")

            assertEquals(first, second)
            coVerify(exactly = 1) { api.listFiles(owner, repo, number) }
        }

    @Test
    fun files_differentHeadSha_refetchesAndReturnsNewList() =
        runTest {
            val api = mockk<PullRequestApi>()
            coEvery { api.listFiles(owner, repo, number) } returnsMany
                listOf(listOf(file("A.kt")), listOf(file("B.kt"), file("C.kt")))
            val repository = repository(api)

            val first = repository.files(owner, repo, number, "sha-1")
            val second = repository.files(owner, repo, number, "sha-2")

            assertEquals(listOf("A.kt"), first.map { it.filename })
            assertEquals("新 revision 不得命中旧缓存", listOf("B.kt", "C.kt"), second.map { it.filename })
            coVerify(exactly = 2) { api.listFiles(owner, repo, number) }
        }

    @Test
    fun files_withoutHeadSha_neverServesCache() =
        runTest {
            val api = mockk<PullRequestApi>()
            coEvery { api.listFiles(owner, repo, number) } returns listOf(file("A.kt"))
            val repository = repository(api)

            repository.files(owner, repo, number)
            repository.files(owner, repo, number)

            coVerify(exactly = 2) { api.listFiles(owner, repo, number) }
        }
}
