package com.yumiru11.githubapp.core.githubrest.model

import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * github-rest 其余 model DTO 的序列化往返与辅助方法测试（T15 覆盖率加固）。
 *
 * 既有 API 测试只【反序列化】（MockWebServer 响应 → DTO），导致各 DTO 的
 * 序列化方向（write$Self/encodeToString）与含逻辑的辅助方法（FileContentDto/ReadmeDto
 * 的 decode*）长期处于红区。本文件补齐该方向，使 github-rest 覆盖率稳健高于 0.80 阈值，
 * 避免恰好压线带来的 CI 抖动。
 *
 * Json 配置复用 [GitHubRestClient.createJson]（SnakeCase + ignoreUnknownKeys + isLenient）。
 * UserDto / LabelDto / MilestoneDto 在 main 已存在且已被各自测试覆盖，此处仅作为嵌套对象
 * 随父 DTO 往返，不再单独补测。
 */
class GithubRestModelSerializationTest {
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
    fun repositoryDto_roundTripsWithNestedOwner() {
        val repo =
            RepositoryDto(
                id = 1L,
                name = "Hello-World",
                fullName = "octocat/Hello-World",
                isPrivate = false,
                owner = UserDto(login = "octocat", id = 1, name = "Octo"),
                description = "demo",
                htmlUrl = "https://github.com/octocat/Hello-World",
                stargazersCount = 10,
                forksCount = 2,
                language = "Kotlin",
                defaultBranch = "main",
            )
        assertRoundTrips(repo, RepositoryDto.serializer())
        assertRoundTrips(
            RepositoryDto(id = 2L, name = "x", fullName = "o/x", isPrivate = true, owner = UserDto(login = "u", id = 9)),
            RepositoryDto.serializer(),
        )
    }

    @Test
    fun notificationDto_roundTrips() {
        assertRoundTrips(
            NotificationDto(
                id = "n1",
                repository = NotificationRepositoryDto(fullName = "octocat/Hello", htmlUrl = "https://x/r"),
                subject =
                    NotificationSubjectDto(
                        title = "New comment",
                        url = "https://api/x",
                        latestCommentUrl = "https://api/x/c",
                        type = "Issue",
                    ),
                reason = "mention",
                unread = true,
                updatedAt = "2026-08-01T00:00:00Z",
                htmlUrl = "https://x/n",
            ),
            NotificationDto.serializer(),
        )
        assertRoundTrips(
            NotificationDto(
                id = "n2",
                repository = NotificationRepositoryDto(),
                subject = NotificationSubjectDto(title = "t", url = "u", type = "PR"),
                reason = "subscribed",
            ),
            NotificationDto.serializer(),
        )
    }

    @Test
    fun eventDto_roundTripsWithPayload() {
        assertRoundTrips(
            EventDto(
                id = "e1",
                type = "PushEvent",
                actor = EventActorDto(login = "octocat", avatarUrl = "https://x/a.png"),
                repo = EventRepoDto(name = "octocat/Hello"),
                payload =
                    EventPayloadDto(
                        action = "created",
                        commits = listOf(EventCommitDto(message = "init", sha = "abc")),
                        size = 1,
                    ),
                createdAt = "2026-08-01T00:00:00Z",
            ),
            EventDto.serializer(),
        )
        assertRoundTrips(
            EventDto(
                id = "e2",
                type = "IssuesEvent",
                actor = EventActorDto(login = "dev"),
                repo = EventRepoDto(name = "o/r"),
                payload =
                    EventPayloadDto(
                        action = "opened",
                        issue = EventIssueDto(title = "bug", number = 5, htmlUrl = "https://x/i/5"),
                        pullRequest = EventPullRequestDto(title = "feat", number = 6, htmlUrl = "https://x/pr/6"),
                        comment = EventCommentDto(body = "hi", htmlUrl = "https://x/c"),
                    ),
            ),
            EventDto.serializer(),
        )
    }

    @Test
    fun fileContentDto_roundTripsAndDecodes() {
        val file =
            FileContentDto(
                name = "Main.kt",
                path = "src/Main.kt",
                sha = "abc",
                size = 11L,
                type = "file",
                content = "SGVsbG8gV29ybGQ=", // "Hello World" base64
                encoding = "base64",
                downloadUrl = "https://x/raw",
            )
        assertRoundTrips(file, FileContentDto.serializer())
        assertEquals("Hello World", file.decodeContent())
        assertEquals("Hello World", String(file.decodeBytes()!!))
        // 无内容/无编码时解码应安全返回 null
        assertNull(FileContentDto(name = "x", path = "x").decodeContent())
        assertNull(FileContentDto(name = "x", path = "x").decodeBytes())
    }

    @Test
    fun gitTreeDto_roundTrips() {
        assertRoundTrips(
            GitTreeResponseDto(
                sha = "treeSha",
                truncated = false,
                tree =
                    listOf(
                        TreeItemDto(path = "README.md", mode = "100644", type = "blob", sha = "b1", size = 14L),
                        TreeItemDto(path = "src", mode = "040000", type = "tree", sha = "t1"),
                    ),
            ),
            GitTreeResponseDto.serializer(),
        )
        assertRoundTrips(GitTreeResponseDto(), GitTreeResponseDto.serializer())
    }

    @Test
    fun readmeDto_roundTripsAndDecodes() {
        val readme =
            ReadmeDto(
                name = "README.md",
                path = "README.md",
                sha = "r",
                size = 11L,
                htmlUrl = "https://x/readme",
                downloadUrl = "https://x/raw",
                type = "file",
                content = "SGVsbG8gV29ybGQ=",
                encoding = "base64",
            )
        assertRoundTrips(readme, ReadmeDto.serializer())
        assertEquals("Hello World", readme.decodeContent())
        assertNull(ReadmeDto(name = "x", path = "x", sha = "s").decodeContent())
    }

    @Test
    fun searchRepositoriesResponse_roundTrips() {
        assertRoundTrips(
            SearchRepositoriesResponse(
                totalCount = 1,
                incompleteResults = false,
                items =
                    listOf(
                        RepositoryDto(id = 1L, name = "x", fullName = "o/x", isPrivate = false, owner = UserDto(login = "u", id = 1)),
                    ),
            ),
            SearchRepositoriesResponse.serializer(),
        )
        assertRoundTrips(SearchRepositoriesResponse(), SearchRepositoriesResponse.serializer())
    }

    @Test
    fun searchIssuesResponse_roundTrips() {
        assertRoundTrips(
            SearchIssuesResponse(
                totalCount = 1,
                items =
                    listOf(
                        IssueDto(
                            id = 1L,
                            number = 2,
                            title = "t",
                            state = "open",
                            repositoryUrl = "https://api/x",
                        ),
                    ),
            ),
            SearchIssuesResponse.serializer(),
        )
        assertRoundTrips(SearchIssuesResponse(), SearchIssuesResponse.serializer())
    }

    @Test
    fun searchUsersResponse_roundTrips() {
        assertRoundTrips(
            SearchUsersResponse(
                totalCount = 1,
                items = listOf(UserDto(login = "octocat", id = 1, name = "Octo")),
            ),
            SearchUsersResponse.serializer(),
        )
        assertRoundTrips(SearchUsersResponse(), SearchUsersResponse.serializer())
    }

    @Test
    fun searchCodeResponse_roundTrips() {
        assertRoundTrips(
            SearchCodeResponse(
                totalCount = 1,
                items =
                    listOf(
                        CodeSearchItemDto(
                            name = "Main.kt",
                            path = "src/Main.kt",
                            sha = "abc",
                            htmlUrl = "https://x/c",
                            repository =
                                RepositoryDto(
                                    id = 1L,
                                    name = "x",
                                    fullName = "o/x",
                                    isPrivate = false,
                                    owner = UserDto(login = "u", id = 1),
                                ),
                        ),
                    ),
            ),
            SearchCodeResponse.serializer(),
        )
        assertRoundTrips(SearchCodeResponse(), SearchCodeResponse.serializer())
    }

    @Test
    fun codeSearchItemDto_roundTrips() {
        assertRoundTrips(
            CodeSearchItemDto(name = "Main.kt", path = "src/Main.kt", sha = "abc", htmlUrl = "https://x/c"),
            CodeSearchItemDto.serializer(),
        )
        assertRoundTrips(CodeSearchItemDto(name = "x", path = "p"), CodeSearchItemDto.serializer())
    }

    @Test
    fun markdownRenderRequest_roundTrips() {
        assertRoundTrips(
            MarkdownRenderRequest(text = "# Hi", mode = "gfm", context = "octocat/Hello"),
            MarkdownRenderRequest.serializer(),
        )
        assertRoundTrips(MarkdownRenderRequest(text = "plain"), MarkdownRenderRequest.serializer())
    }
}
