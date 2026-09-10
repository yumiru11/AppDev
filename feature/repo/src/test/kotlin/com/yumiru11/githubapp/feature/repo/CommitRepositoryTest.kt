package com.yumiru11.githubapp.feature.repo

import com.yumiru11.githubapp.core.githubrest.api.CommitApi
import com.yumiru11.githubapp.core.githubrest.model.CommitAuthorDto
import com.yumiru11.githubapp.core.githubrest.model.CommitDetailDto
import com.yumiru11.githubapp.core.githubrest.model.CommitFileDto
import com.yumiru11.githubapp.core.githubrest.model.CommitInfoDto
import com.yumiru11.githubapp.core.githubrest.model.CommitStatsDto
import com.yumiru11.githubapp.core.githubrest.model.UserDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
 * [CommitRepository] 单测（L09）。
 *
 * 覆盖：单页解析（元信息/统计/文件/patch 缺省）、跨页累积（首页满 100 → 续拉第 2 页）、
 * 页数上限（3 页 = 300 文件，GitHub 单提交上限）、状态字符串映射、
 * 404/403/网络错误 → Result.failure、短 SHA 取 7 位。
 */
class CommitRepositoryTest {
    private val api = mockk<CommitApi>()
    private val repository = CommitRepository(api)

    private fun fileDto(
        name: String,
        status: String = "modified",
        patch: String? = "@@ -1 +1 @@",
    ): CommitFileDto = CommitFileDto(filename = name, status = status, additions = 1, deletions = 1, patch = patch)

    private fun detailDto(
        files: List<CommitFileDto>,
        stats: CommitStatsDto? = CommitStatsDto(additions = 12, deletions = 3, total = 15),
    ): CommitDetailDto =
        CommitDetailDto(
            sha = "abc123def4567890",
            commit =
                CommitInfoDto(
                    message = "fix: something",
                    author = CommitAuthorDto(name = "Octo", email = "octo@example.com", date = "2026-09-01T10:00:00Z"),
                ),
            author = UserDto(login = "octocat", id = 1, avatarUrl = "https://a.example/u"),
            stats = stats,
            files = files,
        )

    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "error".toResponseBody("text/plain".toMediaType())))

    @Test
    fun getCommit_singlePage_mapsAllFields() =
        runTest {
            coEvery { api.getCommit("octocat", "Hello-World", "abc123def4567890", 100, 1) } returns
                detailDto(
                    files =
                        listOf(
                            fileDto("src/Main.kt", status = "added"),
                            fileDto("logo.png", status = "removed", patch = null),
                        ),
                )

            val detail = repository.getCommit("octocat", "Hello-World", "abc123def4567890").getOrThrow()

            assertEquals("abc123def4567890", detail.sha)
            assertEquals("abc123def4567890".take(7), detail.shortSha)
            assertEquals("fix: something", detail.message)
            assertEquals("Octo", detail.authorName)
            assertEquals("octo@example.com", detail.authorEmail)
            assertEquals("2026-09-01T10:00:00Z", detail.authorDate)
            assertEquals("octocat", detail.authorLogin)
            assertEquals(12, detail.additions)
            assertEquals(3, detail.deletions)
            assertEquals(2, detail.files.size)
            assertEquals(CommitFileStatus.ADDED, detail.files[0].status)
            assertEquals(CommitFileStatus.REMOVED, detail.files[1].status)
            assertEquals(null, detail.files[1].patch)
        }

    @Test
    fun getCommit_firstPageFull_requestsSecondPageAndConcatenates() =
        runTest {
            val firstPage = (1..100).map { fileDto("f$it.kt") }
            coEvery { api.getCommit(any(), any(), any(), 100, 1) } returns detailDto(files = firstPage)
            coEvery { api.getCommit(any(), any(), any(), 100, 2) } returns detailDto(files = listOf(fileDto("last.kt")))

            val detail = repository.getCommit("octocat", "Hello-World", "main").getOrThrow()

            assertEquals(101, detail.files.size)
            assertEquals("last.kt", detail.files.last().filename)
            coVerify(exactly = 1) { api.getCommit("octocat", "Hello-World", "main", 100, 2) }
        }

    @Test
    fun getCommit_allPagesFull_stopsAtThreePages() =
        runTest {
            val fullPage = (1..100).map { fileDto("f.kt") }
            coEvery { api.getCommit(any(), any(), any(), 100, any()) } returns detailDto(files = fullPage)

            val detail = repository.getCommit("octocat", "Hello-World", "main").getOrThrow()

            assertEquals(300, detail.files.size)
            coVerify(exactly = 1) { api.getCommit(any(), any(), any(), 100, 3) }
            coVerify(exactly = 0) { api.getCommit(any(), any(), any(), 100, 4) }
        }

    @Test
    fun getCommit_secondPageFails_returnsFailure() =
        runTest {
            val fullPage = (1..100).map { fileDto("f.kt") }
            coEvery { api.getCommit(any(), any(), any(), 100, 1) } returns detailDto(files = fullPage)
            coEvery { api.getCommit(any(), any(), any(), 100, 2) } throws IOException("page 2 down")

            val result = repository.getCommit("octocat", "Hello-World", "main")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
        }

    @Test
    fun getCommit_404Response_returnsFailureWithHttpException() =
        runTest {
            coEvery { api.getCommit(any(), any(), any(), any(), any()) } throws httpException(404)

            val result = repository.getCommit("octocat", "Hello-World", "deadbeef")

            assertEquals(404, (result.exceptionOrNull() as HttpException).code())
        }

    @Test
    fun getCommit_403Response_returnsFailureWithHttpException() =
        runTest {
            coEvery { api.getCommit(any(), any(), any(), any(), any()) } throws httpException(403)

            val result = repository.getCommit("octocat", "private-repo", "main")

            assertEquals(403, (result.exceptionOrNull() as HttpException).code())
        }

    @Test
    fun getCommit_missingOptionalFields_defaultsToEmptyAndZero() =
        runTest {
            coEvery { api.getCommit(any(), any(), any(), any(), any()) } returns
                CommitDetailDto(sha = "s", commit = CommitInfoDto(message = null), stats = null, files = emptyList())

            val detail = repository.getCommit("octocat", "Hello-World", "s").getOrThrow()

            assertEquals("", detail.message)
            assertEquals(0, detail.additions)
            assertEquals(0, detail.deletions)
            assertEquals(null, detail.authorLogin)
            assertTrue(detail.files.isEmpty())
        }

    @Test
    fun commitFileStatus_fromRaw_mapsKnownValuesAndFallsBackToUnknown() {
        assertEquals(CommitFileStatus.ADDED, CommitFileStatus.fromRaw("added"))
        assertEquals(CommitFileStatus.REMOVED, CommitFileStatus.fromRaw("removed"))
        assertEquals(CommitFileStatus.MODIFIED, CommitFileStatus.fromRaw("MODIFIED"))
        assertEquals(CommitFileStatus.RENAMED, CommitFileStatus.fromRaw("renamed"))
        assertEquals(CommitFileStatus.UNKNOWN, CommitFileStatus.fromRaw(null))
        assertEquals(CommitFileStatus.UNKNOWN, CommitFileStatus.fromRaw("copied"))
    }
}
