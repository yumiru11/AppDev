package com.yumiru11.githubapp.feature.home.data

import com.apollographql.apollo.ApolloClient
import com.yumiru11.githubapp.core.githubrest.api.SearchApi
import com.yumiru11.githubapp.core.githubrest.model.IssueDto
import com.yumiru11.githubapp.core.githubrest.model.LabelDto
import com.yumiru11.githubapp.core.githubrest.model.SearchIssuesResponse
import com.yumiru11.githubapp.core.githubrest.model.SearchRepositoriesResponse
import com.yumiru11.githubapp.core.githubrest.model.SearchUsersResponse
import com.yumiru11.githubapp.core.githubrest.model.UserDto
import com.yumiru11.githubapp.feature.home.model.HomeIssueFilter
import com.yumiru11.githubapp.feature.home.model.HomePullFilter
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * [HomeSectionsRepository] 单测（纯 JVM，假 [SearchApi] + MockK 桩 ApolloClient）。
 *
 * 覆盖：五个分区各自的检索式（关键词错了 = 分区内容全错，是本模块最容易静默劣化的地方）、
 * DTO → 投影模型映射（repositoryUrl → owner/repo、标签、PR 识别、计数）、以及 API 异常透传
 * （ViewM 层的 Error 态依赖它抛出）。
 */
class HomeSectionsRepositoryTest {
    private val queries = mutableListOf<Pair<String, Int>>()
    private val issuesResponse = mutableListOf<IssueDto>()
    private val usersResponse = mutableListOf<UserDto>()
    private lateinit var repository: HomeSectionsRepository

    private val fakeApi =
        object : SearchApi {
            /** 置 true 后下一次检索抛 IOException（验证异常透传到 VM 的 Error 态）。 */
            var failIssues = false

            override suspend fun searchRepositories(
                query: String,
                page: Int,
                perPage: Int,
                sort: String?,
            ): SearchRepositoriesResponse = SearchRepositoriesResponse()

            override suspend fun searchUsers(
                query: String,
                page: Int,
                perPage: Int,
            ): SearchUsersResponse {
                queries += query to page
                return SearchUsersResponse(items = usersResponse.toList())
            }

            override suspend fun searchCode(
                query: String,
                page: Int,
                perPage: Int,
            ): com.yumiru11.githubapp.core.githubrest.model.SearchCodeResponse =
                com.yumiru11.githubapp.core.githubrest.model
                    .SearchCodeResponse()

            override suspend fun searchIssues(
                query: String,
                page: Int,
                perPage: Int,
            ): SearchIssuesResponse {
                queries += query to page
                if (failIssues) throw IOException("network down")
                return SearchIssuesResponse(items = issuesResponse.toList())
            }
        }

    @Before
    fun setUp() {
        queries.clear()
        issuesResponse.clear()
        usersResponse.clear()
        repository = HomeSectionsRepository(fakeApi, mockk<ApolloClient>(relaxed = true))
    }

    private fun issueDto(
        number: Int = 1,
        pullRequest: Boolean = false,
    ): IssueDto =
        IssueDto(
            id = number.toLong(),
            number = number,
            title = "Title #$number",
            state = "open",
            labels = listOf(LabelDto(name = "bug", color = "d73a4a")),
            comments = 3,
            updatedAt = "2026-10-01T00:00:00Z",
            pullRequest = if (pullRequest) kotlinx.serialization.json.JsonObject(emptyMap()) else null,
            repositoryUrl = "https://api.github.com/repos/octocat/Hello-World",
        )

    @Test
    fun issues_assignedFilter_usesAssigneeQueryAndMapsProjection() =
        runTest {
            issuesResponse += issueDto(number = 7)
            val items = repository.issues(HomeIssueFilter.ASSIGNED, page = 2)

            assertEquals("is:issue is:open assignee:@me", queries.single().first)
            assertEquals(2, queries.single().second)
            val item = items.single()
            assertEquals(7, item.number)
            assertEquals("octocat/Hello-World", item.repoFullName)
            assertEquals(3, item.commentCount)
            assertEquals("d73a4a", item.labels.single().colorHex)
            assertEquals(false, item.isPullRequest)
        }

    @Test
    fun issues_createdFilter_usesAuthorQuery() =
        runTest {
            repository.issues(HomeIssueFilter.CREATED)
            assertEquals("is:issue is:open author:@me", queries.single().first)
        }

    @Test
    fun issues_mentionedFilter_usesMentionsQuery() =
        runTest {
            repository.issues(HomeIssueFilter.MENTIONED)
            assertEquals("is:issue is:open mentions:@me", queries.single().first)
        }

    @Test
    fun pulls_eachFilter_usesItsOwnQuery() =
        runTest {
            repository.pulls(HomePullFilter.INVOLVED)
            repository.pulls(HomePullFilter.REVIEW)
            repository.pulls(HomePullFilter.RECENT)
            assertEquals(
                listOf("is:pr is:open involves:@me", "is:pr is:open review-requested:@me", "is:pr is:open sort:updated"),
                queries.map { it.first },
            )
        }

    @Test
    fun pullRequestItem_isFlaggedAsPullRequest() =
        runTest {
            issuesResponse += issueDto(pullRequest = true)
            assertTrue(repository.pulls(HomePullFilter.INVOLVED).single().isPullRequest)
        }

    @Test
    fun hotIssues_usesCommentThresholdQuery() =
        runTest {
            repository.hotIssues()
            assertEquals("is:issue is:open comments:>5", queries.single().first)
        }

    @Test
    fun suggestedUsers_mapsLoginNameAvatarAndFollowers() =
        runTest {
            usersResponse +=
                UserDto(
                    login = "octocat",
                    id = 1L,
                    name = "The Octocat",
                    avatarUrl = "https://avatars/octocat.png",
                    followers = 42,
                )
            val user = repository.suggestedUsers().single()
            assertEquals("octocat", user.login)
            assertEquals("The Octocat", user.name)
            assertEquals("https://avatars/octocat.png", user.avatarUrl)
            assertEquals(42, user.followers)
            assertEquals("followers:>5000 type:user", queries.single().first)
        }

    @Test
    fun itemWithoutRepositoryUrl_leavesRepoFullNameEmpty() =
        runTest {
            issuesResponse += issueDto().copy(repositoryUrl = null)
            assertEquals("", repository.issues(HomeIssueFilter.ASSIGNED).single().repoFullName)
        }

    @Test
    fun apiFailure_propagatesToCaller() =
        runTest {
            fakeApi.failIssues = true
            val outcome = runCatching { repository.hotIssues() }
            assertTrue(outcome.exceptionOrNull() is IOException)
        }
}
