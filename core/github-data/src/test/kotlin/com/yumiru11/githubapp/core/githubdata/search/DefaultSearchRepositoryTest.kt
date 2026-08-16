package com.yumiru11.githubapp.core.githubdata.search

import com.yumiru11.githubapp.core.data.model.SearchCodeItem
import com.yumiru11.githubapp.core.data.model.SearchIssue
import com.yumiru11.githubapp.core.githubdata.error.GitHubError
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.githubrest.api.SearchApi
import com.yumiru11.githubapp.core.githubrest.model.CodeSearchItemDto
import com.yumiru11.githubapp.core.githubrest.model.IssueDto
import com.yumiru11.githubapp.core.githubrest.model.RepositoryDto
import com.yumiru11.githubapp.core.githubrest.model.SearchCodeResponse
import com.yumiru11.githubapp.core.githubrest.model.SearchIssuesResponse
import com.yumiru11.githubapp.core.githubrest.model.SearchRepositoriesResponse
import com.yumiru11.githubapp.core.githubrest.model.SearchUsersResponse
import com.yumiru11.githubapp.core.githubrest.model.UserDto
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

/**
 * DefaultSearchRepository 单测（MockK 桩 SearchApi）。
 *
 * 覆盖：四类搜索 DTO → 领域映射（含 PR `is:pr` qualifier 追加与
 * repository_url → owner/name 解析）、429 → RateLimited、网络错误 → Network、
 * 未知错误 → Unknown、空页。
 */
class DefaultSearchRepositoryTest {
    private val searchApi = mockk<SearchApi>()
    private val repository = DefaultSearchRepository(searchApi)

    @Test
    fun searchRepositories_success_mapsItemsToDomain() =
        runTest {
            coEvery { searchApi.searchRepositories(any(), any(), any()) } returns
                SearchRepositoriesResponse(
                    totalCount = 1,
                    items = listOf(repositoryDto()),
                )

            val result = repository.searchRepositories(query = "kotlin", page = 1, perPage = 30)

            assertEquals(1, result.size)
            val item = result.single()
            assertEquals("octocat", item.ownerLogin)
            assertEquals("Hello-World", item.name)
            assertEquals("My first repo", item.description)
            assertEquals(1234, item.stargazerCount)
            assertEquals("Kotlin", item.language)
        }

    @Test
    fun searchRepositories_emptyPage_returnsEmptyList() =
        runTest {
            coEvery { searchApi.searchRepositories(any(), any(), any()) } returns
                SearchRepositoriesResponse(totalCount = 0, items = emptyList())

            val result = repository.searchRepositories(query = "nothing", page = 1, perPage = 30)

            assertTrue(result.isEmpty())
        }

    @Test
    fun searchUsers_success_mapsToDomain() =
        runTest {
            coEvery { searchApi.searchUsers(any(), any(), any()) } returns
                SearchUsersResponse(
                    totalCount = 1,
                    items = listOf(UserDto(login = "octocat", id = 1, name = "The Octocat")),
                )

            val result = repository.searchUsers(query = "octocat", page = 1, perPage = 30)

            val user = result.single()
            assertEquals("octocat", user.login)
            assertEquals("The Octocat", user.name)
        }

    @Test
    fun searchIssues_success_mapsStateAndRepoFromRepositoryUrl() =
        runTest {
            coEvery { searchApi.searchIssues(any(), any(), any()) } returns
                SearchIssuesResponse(items = listOf(issueDto(pullRequest = null)))

            val result = repository.searchIssues(query = "bug", page = 1, perPage = 30)

            val issue = result.single()
            assertEquals(42, issue.number)
            assertEquals("Bug report", issue.title)
            assertEquals("open", issue.state)
            assertFalse(issue.isPullRequest)
            assertEquals("octocat/Hello-World", issue.repoFullName)
            assertEquals("octocat", issue.authorLogin)
        }

    @Test
    fun searchIssues_prItem_setsIsPullRequest() =
        runTest {
            coEvery { searchApi.searchIssues(any(), any(), any()) } returns
                SearchIssuesResponse(items = listOf(issueDto(pullRequest = """{"url": "x"}""")))

            val result = repository.searchIssues(query = "bug", page = 1, perPage = 30)

            assertTrue(result.single().isPullRequest)
        }

    @Test
    fun searchIssues_malformedRepositoryUrl_returnsNullRepoFullName() =
        runTest {
            coEvery { searchApi.searchIssues(any(), any(), any()) } returns
                SearchIssuesResponse(items = listOf(issueDto(pullRequest = null, repositoryUrl = "not-a-url")))

            val result = repository.searchIssues(query = "bug", page = 1, perPage = 30)

            assertNull(result.single().repoFullName)
        }

    @Test
    fun searchPullRequests_appendsIsPrQualifierToQuery() =
        runTest {
            val querySlot = slot<String>()
            coEvery {
                searchApi.searchIssues(capture(querySlot), any(), any())
            } returns SearchIssuesResponse(items = listOf(issueDto(pullRequest = """{"url": "x"}""")))

            val result = repository.searchPullRequests(query = "bug", page = 1, perPage = 30)

            assertEquals("bug is:pr", querySlot.captured)
            assertTrue(result.single().isPullRequest)
        }

    @Test
    fun searchCode_success_mapsPathAndRepository() =
        runTest {
            coEvery { searchApi.searchCode(any(), any(), any()) } returns
                SearchCodeResponse(
                    items =
                        listOf(
                            CodeSearchItemDto(
                                name = "Main.kt",
                                path = "src/Main.kt",
                                repository = repositoryDto(),
                            ),
                        ),
                )

            val result = repository.searchCode(query = "fun main", page = 1, perPage = 30)

            val item: SearchCodeItem = result.single()
            assertEquals("Main.kt", item.name)
            assertEquals("src/Main.kt", item.path)
            assertEquals("octocat/Hello-World", item.repoFullName)
        }

    @Test
    fun searchRepositories_http429_throwsRateLimited() =
        runTest {
            coEvery { searchApi.searchRepositories(any(), any(), any()) } throws httpException(429)

            val error = assertThrowsGitHubRequestException { repository.searchRepositories("kotlin", 1, 30) }

            assertTrue(error is GitHubError.RateLimited)
        }

    @Test
    fun searchUsers_ioException_throwsNetwork() =
        runTest {
            coEvery { searchApi.searchUsers(any(), any(), any()) } throws IOException("connection reset")

            val error = assertThrowsGitHubRequestException { repository.searchUsers("octocat", 1, 30) }

            assertTrue(error is GitHubError.Network)
        }

    @Test
    fun searchIssues_unknownException_throwsUnknown() =
        runTest {
            coEvery { searchApi.searchIssues(any(), any(), any()) } throws IllegalStateException("boom")

            val error = assertThrowsGitHubRequestException { repository.searchIssues("bug", 1, 30) }

            assertTrue(error is GitHubError.Unknown)
        }

    @Test
    fun searchCode_http401_throwsUnauthorized() =
        runTest {
            coEvery { searchApi.searchCode(any(), any(), any()) } throws httpException(401)

            val error = assertThrowsGitHubRequestException { repository.searchCode("fun", 1, 30) }

            assertEquals(GitHubError.Unauthorized, error)
        }

    private fun httpException(code: Int): HttpException {
        val body = """{"message":"error"}""".toResponseBody("application/json".toMediaType())
        val rawResponse =
            okhttp3.Response
                .Builder()
                .request(
                    okhttp3.Request
                        .Builder()
                        .url("http://localhost/")
                        .build(),
                ).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(code)
                .message("error")
                .body(body)
                .build()
        return HttpException(retrofit2.Response.error<Any>(body, rawResponse))
    }

    private suspend fun assertThrowsGitHubRequestException(block: suspend () -> Unit): GitHubError {
        try {
            block()
        } catch (e: GitHubRequestException) {
            return e.error
        }
        throw AssertionError("必须抛 GitHubRequestException")
    }

    private fun repositoryDto(): RepositoryDto =
        RepositoryDto(
            id = 1,
            name = "Hello-World",
            fullName = "octocat/Hello-World",
            isPrivate = false,
            owner = UserDto(login = "octocat", id = 1),
            description = "My first repo",
            htmlUrl = "https://github.com/octocat/Hello-World",
            stargazersCount = 1234,
            forksCount = 56,
            language = "Kotlin",
            defaultBranch = "main",
        )

    private fun issueDto(
        pullRequest: String?,
        repositoryUrl: String = "https://api.github.com/repos/octocat/Hello-World",
    ): IssueDto {
        val dto =
            IssueDto(
                id = 100,
                number = 42,
                title = "Bug report",
                state = "open",
                user = UserDto(login = "octocat", id = 1),
                htmlUrl = "https://github.com/octocat/Hello-World/issues/42",
                repositoryUrl = repositoryUrl,
            )
        return if (pullRequest != null) {
            dto.copy(pullRequest = Json.parseToJsonElement(pullRequest) as JsonObject)
        } else {
            dto
        }
    }
}
