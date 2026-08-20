package com.yumiru11.githubapp.feature.repo

import com.yumiru11.githubapp.core.githubrest.api.RepoManagementApi
import com.yumiru11.githubapp.core.githubrest.model.ReleaseDto
import com.yumiru11.githubapp.core.githubrest.model.RepositoryDto
import com.yumiru11.githubapp.core.githubrest.model.SubscriptionDto
import com.yumiru11.githubapp.core.githubrest.model.SubscriptionRequest
import com.yumiru11.githubapp.core.githubrest.model.TagCommitDto
import com.yumiru11.githubapp.core.githubrest.model.TagDto
import com.yumiru11.githubapp.core.githubrest.model.UserDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * RepoManagementRepository 单测（T12：Star/Watch/Fork + Releases/Tags/Languages）。
 *
 * 覆盖：状态检查端点状态码语义（204/404/401 → false）、写操作路由（PUT/DELETE）、
 * Fork 错误码透传、DTO → 领域模型映射。
 */
class RepoManagementRepositoryTest {
    private val api = mockk<RepoManagementApi>()
    private val repository = RepoManagementRepository(api)

    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "error".toResponseBody("text/plain".toMediaType())))

    // ---- Star 状态检查 ----

    @Test
    fun isStarred_204Response_returnsTrue() =
        runTest {
            coEvery { api.isStarred("octocat", "Hello-World") } returns Response.success(Unit)

            val result = repository.isStarred("octocat", "Hello-World")

            assertTrue(result)
        }

    @Test
    fun isStarred_404Response_returnsFalse() =
        runTest {
            // Response<Unit> 语义：404 → error response → isSuccessful=false
            coEvery { api.isStarred(any(), any()) } returns Response.error(404, "error".toResponseBody("text/plain".toMediaType()))

            val result = repository.isStarred("octocat", "Hello-World")

            assertFalse(result)
        }

    @Test
    fun isStarred_401Response_returnsFalse() =
        runTest {
            // 401/403（游客/令牌失效）→ 按未星标处理，不抛错
            coEvery { api.isStarred(any(), any()) } returns Response.error(401, "error".toResponseBody("text/plain".toMediaType()))

            val result = repository.isStarred("octocat", "Hello-World")

            assertFalse(result)
        }

    // ---- Star/Unstar 写操作 ----

    @Test
    fun setStarred_true_callsStar() =
        runTest {
            coEvery { api.star("octocat", "Hello-World") } returns Response.success(Unit)

            repository.setStarred("octocat", "Hello-World", starred = true)

            coVerify(exactly = 1) { api.star("octocat", "Hello-World") }
            coVerify(exactly = 0) { api.unstar(any(), any()) }
        }

    @Test
    fun setStarred_false_callsUnstar() =
        runTest {
            coEvery { api.unstar("octocat", "Hello-World") } returns Response.success(Unit)

            repository.setStarred("octocat", "Hello-World", starred = false)

            coVerify(exactly = 1) { api.unstar("octocat", "Hello-World") }
            coVerify(exactly = 0) { api.star(any(), any()) }
        }

    // ---- Watch 状态检查 ----

    @Test
    fun isWatching_200Subscribed_returnsTrue() =
        runTest {
            coEvery { api.getSubscription("octocat", "Hello-World") } returns SubscriptionDto(subscribed = true)

            val result = repository.isWatching("octocat", "Hello-World")

            assertTrue(result)
        }

    @Test
    fun isWatching_200NotSubscribed_returnsFalse() =
        runTest {
            coEvery { api.getSubscription(any(), any()) } returns SubscriptionDto(subscribed = false)

            val result = repository.isWatching("octocat", "Hello-World")

            assertFalse(result)
        }

    @Test
    fun isWatching_404Response_returnsFalse() =
        runTest {
            coEvery { api.getSubscription(any(), any()) } throws httpException(404)

            val result = repository.isWatching("octocat", "Hello-World")

            assertFalse(result)
        }

    @Test
    fun isWatching_403Response_returnsFalse() =
        runTest {
            coEvery { api.getSubscription(any(), any()) } throws httpException(403)

            val result = repository.isWatching("octocat", "Hello-World")

            assertFalse(result)
        }

    // ---- Watch/Unwatch 写操作 ----

    @Test
    fun setWatching_true_callsWatchWithSubscribedTrue() =
        runTest {
            coEvery { api.watch(any(), any(), any()) } returns SubscriptionDto(subscribed = true)

            repository.setWatching("octocat", "Hello-World", watching = true)

            coVerify(exactly = 1) {
                api.watch("octocat", "Hello-World", SubscriptionRequest(subscribed = true))
            }
            coVerify(exactly = 0) { api.unwatch(any(), any()) }
        }

    @Test
    fun setWatching_false_callsUnwatch() =
        runTest {
            coEvery { api.unwatch("octocat", "Hello-World") } returns Response.success(Unit)

            repository.setWatching("octocat", "Hello-World", watching = false)

            coVerify(exactly = 1) { api.unwatch("octocat", "Hello-World") }
            coVerify(exactly = 0) { api.watch(any(), any(), any()) }
        }

    // ---- Fork ----

    @Test
    fun fork_success_mapsToRepository() =
        runTest {
            coEvery { api.fork("octocat", "Hello-World") } returns
                RepositoryDto(
                    id = 1,
                    name = "Hello-World",
                    fullName = "octocat/Hello-World",
                    isPrivate = false,
                    owner = UserDto(login = "octocat", id = 1),
                    description = "desc",
                    stargazersCount = 10,
                    forksCount = 2,
                    language = "Kotlin",
                    defaultBranch = "main",
                )

            val result = repository.fork("octocat", "Hello-World")

            assertTrue(result.isSuccess)
            val repo = result.getOrThrow()
            assertEquals("octocat", repo.ownerLogin)
            assertEquals("Hello-World", repo.name)
            assertEquals(10, repo.stargazerCount)
        }

    @Test
    fun fork_403Response_returnsFailure() =
        runTest {
            coEvery { api.fork(any(), any()) } throws httpException(403)

            val result = repository.fork("octocat", "Hello-World")

            assertTrue(result.isFailure)
            assertEquals(403, (result.exceptionOrNull() as HttpException).code())
        }

    @Test
    fun fork_422Response_returnsFailure() =
        runTest {
            coEvery { api.fork(any(), any()) } throws httpException(422)

            val result = repository.fork("octocat", "Hello-World")

            assertTrue(result.isFailure)
            assertEquals(422, (result.exceptionOrNull() as HttpException).code())
        }

    // ---- Releases ----

    @Test
    fun getReleases_success_mapsToDomain() =
        runTest {
            coEvery { api.listReleases("octocat", "Hello-World") } returns
                listOf(
                    ReleaseDto(
                        id = 1,
                        tagName = "v1.0.0",
                        name = "First release",
                        body = "Initial release",
                        htmlUrl = "https://github.com/octocat/Hello-World/releases/tag/v1.0.0",
                        publishedAt = "2026-01-15T10:30:00Z",
                        prerelease = false,
                        draft = false,
                        author = UserDto(login = "octocat", id = 1),
                    ),
                )

            val result = repository.getReleases("octocat", "Hello-World")

            assertTrue(result.isSuccess)
            val release = result.getOrThrow().single()
            assertEquals("v1.0.0", release.tagName)
            assertEquals("octocat", release.authorLogin)
            assertEquals("2026-01-15T10:30:00Z", release.publishedAt.toString())
        }

    @Test
    fun getReleases_invalidPublishedAt_parsesNull() =
        runTest {
            coEvery { api.listReleases(any(), any()) } returns
                listOf(
                    ReleaseDto(
                        id = 1,
                        tagName = "v1.0.0",
                        publishedAt = "not-a-date",
                    ),
                )

            val result = repository.getReleases("octocat", "Hello-World")

            assertTrue(result.isSuccess)
            assertEquals(null, result.getOrThrow().single().publishedAt)
        }

    @Test
    fun getRelease_success_mapsToDomain() =
        runTest {
            coEvery { api.getRelease("octocat", "Hello-World", 42) } returns
                ReleaseDto(
                    id = 42,
                    tagName = "v2.0.0",
                    name = "Second release",
                    body = "Changelog",
                    prerelease = true,
                )

            val result = repository.getRelease("octocat", "Hello-World", 42)

            assertTrue(result.isSuccess)
            val release = result.getOrThrow()
            assertEquals(42, release.id)
            assertEquals("v2.0.0", release.tagName)
            assertTrue(release.prerelease)
        }

    // ---- Tags ----

    @Test
    fun getTags_success_mapsToDomain() =
        runTest {
            coEvery { api.listTags("octocat", "Hello-World") } returns
                listOf(
                    TagDto(
                        name = "v1.0.0",
                        commit = TagCommitDto(sha = "c5b97d5ae6c19d5c5df71a34c7fbeeda2479ccbc"),
                    ),
                )

            val result = repository.getTags("octocat", "Hello-World")

            assertTrue(result.isSuccess)
            val tag = result.getOrThrow().single()
            assertEquals("v1.0.0", tag.name)
            assertEquals("c5b97d5ae6c19d5c5df71a34c7fbeeda2479ccbc", tag.commitSha)
        }

    // ---- Languages ----

    @Test
    fun getLanguages_success_returnsMap() =
        runTest {
            coEvery { api.getLanguages("octocat", "Hello-World") } returns
                mapOf("Kotlin" to 102400L, "Java" to 51200L)

            val result = repository.getLanguages("octocat", "Hello-World")

            assertTrue(result.isSuccess)
            assertEquals(102400L, result.getOrThrow()["Kotlin"])
            assertEquals(51200L, result.getOrThrow()["Java"])
        }
}
