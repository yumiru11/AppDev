package com.yumiru11.githubapp.feature.home.data

import com.yumiru11.githubapp.core.githubrest.api.UserApi
import com.yumiru11.githubapp.core.githubrest.model.RepositoryDto
import com.yumiru11.githubapp.core.githubrest.model.UserDto
import com.yumiru11.githubapp.feature.home.model.RepoOption
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [UserReposRepository] 映射契约（#89）：DTO → 选择器选项字段一一对应，
 * 请求参数固定首页第一页 per_page=50。
 */
class UserReposRepositoryTest {
    @Test
    fun currentUserRepos_mapsDtoFieldsAndRequestsFirstPage() =
        runTest {
            val api =
                mockk<UserApi> {
                    coEvery { currentUserRepositories(any(), any()) } returns
                        listOf(
                            RepositoryDto(
                                id = 1L,
                                name = "hello-world",
                                fullName = "octocat/hello-world",
                                isPrivate = false,
                                owner = UserDto(login = "octocat", id = 100L),
                                description = "My first repo",
                            ),
                            RepositoryDto(
                                id = 2L,
                                name = "secret",
                                fullName = "octocat/secret",
                                isPrivate = true,
                                owner = UserDto(login = "octocat", id = 100L),
                                description = null,
                            ),
                        )
                }
            val repos = UserReposRepository(api).currentUserRepos()
            assertEquals(
                listOf(
                    RepoOption("octocat", "hello-world", "My first repo", false),
                    RepoOption("octocat", "secret", null, true),
                ),
                repos,
            )
            coVerify(exactly = 1) { api.currentUserRepositories(perPage = 50, page = 1) }
        }
}
