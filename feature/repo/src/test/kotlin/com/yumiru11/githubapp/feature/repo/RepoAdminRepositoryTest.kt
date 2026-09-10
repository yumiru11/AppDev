package com.yumiru11.githubapp.feature.repo

import com.yumiru11.githubapp.core.githubdata.error.GitHubError
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.githubrest.api.RepositoryApi
import com.yumiru11.githubapp.core.githubrest.api.UserApi
import com.yumiru11.githubapp.core.githubrest.model.CreateRepositoryRequest
import com.yumiru11.githubapp.core.githubrest.model.RepositoryDto
import com.yumiru11.githubapp.core.githubrest.model.UserDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * [RepoAdminRepository] 单测（L04：创建/删除仓库）。
 *
 * 覆盖：创建成功（DTO → 领域模型 / 请求体字段）、422 → Validation、403 → Forbidden、
 * 网络错误 → Network；删除 204 成功、403 → Forbidden、404 → NotFound、非 HTTP 异常透传归一化；
 * CancellationException 必须原样抛出（协程取消语义）。
 */
class RepoAdminRepositoryTest {
    private val userApi = mockk<UserApi>()
    private val repositoryApi = mockk<RepositoryApi>()
    private val repository = RepoAdminRepository(userApi, repositoryApi)

    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "error".toResponseBody("text/plain".toMediaType())))

    private fun repoDto(name: String = "new-repo"): RepositoryDto =
        RepositoryDto(
            id = 9,
            name = name,
            fullName = "octocat/$name",
            isPrivate = true,
            owner = UserDto(login = "octocat", id = 1),
            description = "demo",
            defaultBranch = "main",
            stargazersCount = 0,
            forksCount = 0,
        )

    private fun errorOf(result: Result<*>): GitHubError = ((result.exceptionOrNull() as GitHubRequestException).error)

    // ---- 创建仓库 ----

    @Test
    fun createRepository_success_mapsDtoToDomainAndSendsBody() =
        runTest {
            val bodySlot = slot<CreateRepositoryRequest>()
            coEvery { userApi.createRepository(capture(bodySlot)) } returns repoDto()

            val result =
                repository.createRepository(
                    name = "new-repo",
                    description = "demo",
                    isPrivate = true,
                    autoInit = true,
                )

            assertTrue(result.isSuccess)
            val repo = result.getOrThrow()
            assertEquals("octocat", repo.ownerLogin)
            assertEquals("new-repo", repo.name)
            assertTrue(repo.isPrivate)
            assertEquals("main", repo.defaultBranch)

            assertEquals("new-repo", bodySlot.captured.name)
            assertEquals("demo", bodySlot.captured.description)
            assertTrue(bodySlot.captured.isPrivate)
            assertTrue(bodySlot.captured.autoInit)
        }

    @Test
    fun createRepository_templates_passedThrough() =
        runTest {
            val bodySlot = slot<CreateRepositoryRequest>()
            coEvery { userApi.createRepository(capture(bodySlot)) } returns repoDto()

            repository.createRepository(
                name = "new-repo",
                description = null,
                isPrivate = false,
                autoInit = false,
                gitignoreTemplate = "Android",
                licenseTemplate = "mit",
            )

            assertEquals("Android", bodySlot.captured.gitignoreTemplate)
            assertEquals("mit", bodySlot.captured.licenseTemplate)
        }

    @Test
    fun createRepository_422Response_normalizesToValidation() =
        runTest {
            coEvery { userApi.createRepository(any()) } throws httpException(422)

            val result = repository.createRepository("taken", null, false, false)

            assertEquals(GitHubError.Validation, errorOf(result))
        }

    @Test
    fun createRepository_403Response_normalizesToForbidden() =
        runTest {
            coEvery { userApi.createRepository(any()) } throws httpException(403)

            val result = repository.createRepository("repo", null, false, false)

            assertEquals(GitHubError.Forbidden, errorOf(result))
        }

    @Test
    fun createRepository_ioError_normalizesToNetwork() =
        runTest {
            coEvery { userApi.createRepository(any()) } throws IOException("offline")

            val result = repository.createRepository("repo", null, false, false)

            assertTrue(errorOf(result) is GitHubError.Network)
        }

    @Test
    fun createRepository_cancellation_rethrown() =
        runTest {
            coEvery { userApi.createRepository(any()) } throws CancellationException("cancelled")

            var thrown: Throwable? = null
            try {
                repository.createRepository("repo", null, false, false)
            } catch (e: CancellationException) {
                thrown = e
            }

            assertTrue("取消异常必须原样抛出", thrown is CancellationException)
        }

    // ---- 删除仓库 ----

    @Test
    fun deleteRepository_204Response_returnsSuccess() =
        runTest {
            coEvery { repositoryApi.deleteRepository("octocat", "Hello-World") } returns Response.success(Unit)

            val result = repository.deleteRepository("octocat", "Hello-World")

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { repositoryApi.deleteRepository("octocat", "Hello-World") }
        }

    @Test
    fun deleteRepository_403Response_normalizesToForbidden() =
        runTest {
            coEvery { repositoryApi.deleteRepository(any(), any()) } returns
                Response.error(403, "no".toResponseBody("text/plain".toMediaType()))

            val result = repository.deleteRepository("octocat", "Hello-World")

            assertEquals(GitHubError.Forbidden, errorOf(result))
        }

    @Test
    fun deleteRepository_404Response_normalizesToNotFound() =
        runTest {
            coEvery { repositoryApi.deleteRepository(any(), any()) } returns
                Response.error(404, "gone".toResponseBody("text/plain".toMediaType()))

            val result = repository.deleteRepository("octocat", "Hello-World")

            assertEquals(GitHubError.NotFound, errorOf(result))
        }

    @Test
    fun deleteRepository_ioError_normalizesToNetwork() =
        runTest {
            coEvery { repositoryApi.deleteRepository(any(), any()) } throws IOException("offline")

            val result = repository.deleteRepository("octocat", "Hello-World")

            assertTrue(errorOf(result) is GitHubError.Network)
        }

    @Test
    fun asGitHubRequestException_alreadyNormalized_isPassthrough() {
        val existing = GitHubRequestException(GitHubError.Forbidden, IOException("cause"))

        val normalized = existing.asGitHubRequestException()

        assertTrue("已归一化的异常不应二次包装", normalized === existing)
    }

    @Test
    fun asGitHubRequestException_rawHttpException_wrapsWithCause() {
        val raw = httpException(422)

        val normalized = raw.asGitHubRequestException()

        assertEquals(GitHubError.Validation, normalized.error)
        assertTrue(normalized.cause === raw)
    }
}
