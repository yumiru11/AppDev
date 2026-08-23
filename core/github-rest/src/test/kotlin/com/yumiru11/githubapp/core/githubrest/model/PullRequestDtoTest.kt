package com.yumiru11.githubapp.core.githubrest.model

import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PullRequestDto] 及其嵌套 DTO 的序列化往返与 data class 契约测试（T15 覆盖率修复）。
 *
 * 纯单测：kotlinx-serialization JSON 往返，无需 MockWebServer。
 * Json 配置复用 [GitHubRestClient.createJson]（SnakeCase + ignoreUnknownKeys + isLenient），
 * 与运行时 Retrofit 转换器一致，保证「全字段构造 → encode → decode → == 原实例」稳定。
 *
 * 覆盖目标（修复 CI 0.70 → ≥0.80 的缺口）：
 * - 序列化方向（write$Self/encodeToString）——既有 PullRequestApiTest 只测反序列化，序列化行全红；
 * - 从未被实例化的嵌套 DTO（PullRequestRepoDto / TeamDto / PullRequestReviewDto /
 *   PullRequestReviewCommentDto / CrossReferenceSourceDto 链路）；
 * - data class 契约：equals/hashCode/toString/copy/componentN；
 * - 默认值分支（缺省参数构造路径）。
 */
class PullRequestDtoTest {
    private val json: Json = GitHubRestClient.createJson()

    private fun <T> assertRoundTrips(
        value: T,
        serializer: KSerializer<T>,
    ) {
        val encoded = json.encodeToString(serializer, value)
        val decoded = json.decodeFromString(serializer, encoded)
        assertEquals(value, decoded)
        assertEquals(value.hashCode(), decoded.hashCode())
        assertEquals(value.toString(), decoded.toString())
        assertTrue("往返应保持实例等价", setOf(value, decoded).size == 1)
    }

    @Test
    fun pullRequestDto_allFields_roundTripsAndExercisesContract() {
        val user = UserDto(login = "octocat", id = 1, name = "Octo Cat")
        val milestone = MilestoneDto(title = "v1.0", state = "open", description = "desc")
        val label = LabelDto(name = "bug", color = "d73a4a")
        val team = TeamDto(name = "Core", slug = "core")
        val head =
            PullRequestBranchDto(
                label = "octocat:feat",
                ref = "feat",
                sha = "abc123",
                repo = PullRequestRepoDto(name = "Hello", fullName = "octocat/Hello"),
            )
        val base = PullRequestBranchDto(label = "octocat:main", ref = "main", sha = "def456")
        val pull =
            PullRequestDto(
                id = 10L,
                number = 42,
                title = "Add feature",
                state = "open",
                nodeId = "PR_10",
                body = "Body text",
                user = user,
                labels = listOf(label),
                assignees = listOf(user),
                milestone = milestone,
                comments = 3,
                reviewComments = 2,
                commits = 5,
                additions = 100,
                deletions = 20,
                changedFiles = 4,
                createdAt = "2026-08-01T00:00:00Z",
                updatedAt = "2026-08-02T00:00:00Z",
                closedAt = null,
                mergedAt = null,
                htmlUrl = "https://github.com/octocat/Hello/pull/42",
                mergeable = true,
                mergeableState = "clean",
                draft = false,
                head = head,
                base = base,
                requestedReviewers = listOf(user),
                requestedTeams = listOf(team),
            )
        assertRoundTrips(pull, PullRequestDto.serializer())

        // data class 契约：copy / componentN / equals 差异分支
        assertEquals("Renamed", pull.copy(title = "Renamed").title)
        assertEquals(10L, pull.component1())
        assertEquals(42, pull.component2())
        assertEquals("Add feature", pull.component3())
        assertEquals("open", pull.component4())
        assertEquals("PR_10", pull.component5())
        assertEquals("Body text", pull.component6())
        assertEquals(user, pull.component7())
        assertEquals(listOf(label), pull.component8())
        assertFalse("equals 应拒绝非同类对象", pull.equals("not a pr"))
        assertFalse("equals 应区分差异字段", pull.equals(pull.copy(number = 43)))
    }

    @Test
    fun pullRequestDto_defaults_constructsAndRoundTrips() {
        // 缺省参数构造路径（既有反序列化走全参构造，缺省分支未覆盖）
        val minimal = PullRequestDto(id = 1L, number = 2, title = "t", state = "open")
        assertRoundTrips(minimal, PullRequestDto.serializer())
        assertEquals(emptyList<UserDto>(), minimal.assignees)
        assertEquals(false, minimal.draft)
        assertEquals(0, minimal.additions)
    }

    @Test
    fun pullRequestBranchDto_withRepo_roundTrips() {
        assertRoundTrips(
            PullRequestBranchDto(
                label = "octocat:feat",
                ref = "feature",
                sha = "abc123",
                repo = PullRequestRepoDto(name = "Hello", fullName = "octocat/Hello"),
            ),
            PullRequestBranchDto.serializer(),
        )
        assertRoundTrips(PullRequestBranchDto(), PullRequestBranchDto.serializer())
    }

    @Test
    fun pullRequestRepoDto_roundTrips() {
        assertRoundTrips(
            PullRequestRepoDto(name = "Hello", fullName = "octocat/Hello"),
            PullRequestRepoDto.serializer(),
        )
        assertRoundTrips(PullRequestRepoDto(), PullRequestRepoDto.serializer())
    }

    @Test
    fun teamDto_roundTrips() {
        assertRoundTrips(TeamDto(name = "Core", slug = "core"), TeamDto.serializer())
        assertRoundTrips(TeamDto(), TeamDto.serializer())
    }

    @Test
    fun pullRequestCommitDto_roundTrips() {
        val author = CommitAuthorDto(name = "Octo", email = "o@x.com", date = "2026-08-01T00:00:00Z")
        assertRoundTrips(
            PullRequestCommitDto(
                sha = "abc123",
                commit = PullRequestCommitDetailDto(message = "msg", author = author, committer = author),
                author = UserDto(login = "octocat", id = 1),
                committer = UserDto(login = "torvalds", id = 2),
                htmlUrl = "https://x/commit/abc123",
                files =
                    listOf(
                        PullRequestCommitFileDto(
                            filename = "a.kt",
                            status = "modified",
                            additions = 1,
                            deletions = 2,
                            changes = 3,
                        ),
                    ),
            ),
            PullRequestCommitDto.serializer(),
        )
        assertRoundTrips(
            PullRequestCommitDto(sha = "abc123"),
            PullRequestCommitDto.serializer(),
        )
    }

    @Test
    fun pullRequestCommitFileDto_roundTrips() {
        assertRoundTrips(
            PullRequestCommitFileDto(
                filename = "a.kt",
                status = "added",
                additions = 5,
                deletions = 0,
                changes = 5,
            ),
            PullRequestCommitFileDto.serializer(),
        )
        assertRoundTrips(
            PullRequestCommitFileDto(filename = "a.kt"),
            PullRequestCommitFileDto.serializer(),
        )
    }

    @Test
    fun pullRequestFileDto_roundTrips() {
        assertRoundTrips(
            PullRequestFileDto(
                filename = "README.md",
                status = "added",
                additions = 5,
                deletions = 0,
                changes = 5,
                patch = "@@ -0,0 +1,5 @@\n+Hi",
                rawUrl = "https://x/raw",
                blobUrl = "https://x/blob",
            ),
            PullRequestFileDto.serializer(),
        )
        assertRoundTrips(
            PullRequestFileDto(filename = "README.md"),
            PullRequestFileDto.serializer(),
        )
    }

    @Test
    fun pullRequestReviewDto_roundTrips() {
        assertRoundTrips(
            PullRequestReviewDto(
                id = 99L,
                user = UserDto(login = "rev", id = 2),
                body = "LGTM",
                state = "APPROVED",
                submittedAt = "2026-08-02T00:00:00Z",
                commitId = "sha",
                htmlUrl = "https://x/review/99",
            ),
            PullRequestReviewDto.serializer(),
        )
        assertRoundTrips(PullRequestReviewDto(id = 99L), PullRequestReviewDto.serializer())
    }

    @Test
    fun pullRequestReviewCommentDto_roundTrips() {
        assertRoundTrips(
            PullRequestReviewCommentDto(
                id = 7L,
                user = UserDto(login = "dev", id = 3),
                body = "note",
                path = "src/Main.kt",
                line = 10,
                position = 5,
                createdAt = "2026-08-02T00:00:00Z",
                htmlUrl = "https://x/c/7",
                inReplyToId = 6L,
            ),
            PullRequestReviewCommentDto.serializer(),
        )
        assertRoundTrips(
            PullRequestReviewCommentDto(id = 7L),
            PullRequestReviewCommentDto.serializer(),
        )
    }

    @Test
    fun checkRunDto_roundTrips() {
        assertRoundTrips(
            CheckRunDto(
                id = 1L,
                name = "CI",
                status = "completed",
                conclusion = "success",
                startedAt = "2026-08-02T00:00:00Z",
                completedAt = "2026-08-02T00:05:00Z",
                output = CheckRunOutputDto(title = "All green", summary = "ok", text = "details"),
                app = CheckRunAppDto(name = "GitHub Actions"),
                htmlUrl = "https://x/runs/1",
                detailsUrl = "https://x/details",
            ),
            CheckRunDto.serializer(),
        )
        assertRoundTrips(CheckRunDto(id = 1L), CheckRunDto.serializer())
    }

    @Test
    fun checkRunsResponseDto_roundTrips() {
        assertRoundTrips(
            CheckRunsResponseDto(
                totalCount = 2,
                checkRuns = listOf(CheckRunDto(id = 1L, name = "CI")),
            ),
            CheckRunsResponseDto.serializer(),
        )
        assertRoundTrips(CheckRunsResponseDto(), CheckRunsResponseDto.serializer())
    }

    @Test
    fun combinedStatusDto_roundTrips() {
        assertRoundTrips(
            CombinedStatusDto(
                state = "success",
                totalCount = 1,
                statuses =
                    listOf(
                        CombinedStatusItemDto(
                            state = "success",
                            context = "CI",
                            description = "ok",
                            targetUrl = "https://x",
                            createdAt = "2026-08-02T00:00:00Z",
                        ),
                    ),
            ),
            CombinedStatusDto.serializer(),
        )
        assertRoundTrips(CombinedStatusDto(), CombinedStatusDto.serializer())
    }
}
