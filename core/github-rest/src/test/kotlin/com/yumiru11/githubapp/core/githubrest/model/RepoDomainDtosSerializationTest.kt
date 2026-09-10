package com.yumiru11.githubapp.core.githubrest.model

import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L04/L05/L06/L09 新增与扩展 DTO 的序列化双向测试。
 *
 * 既有 API 测试只覆盖【反序列化】方向；本文件补序列化方向（write$Self），
 * 保证 github-rest 覆盖率稳健高于 0.80 阈值（与 GithubRestModelSerializationTest 同目的）。
 */
class RepoDomainDtosSerializationTest {
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
    fun createRepositoryRequest_snakeCaseNames_areUsedOnWire() {
        val request =
            CreateRepositoryRequest(
                name = "new-repo",
                description = "demo",
                isPrivate = true,
                autoInit = true,
                gitignoreTemplate = "Android",
                licenseTemplate = "mit",
            )

        val encoded = json.encodeToString(CreateRepositoryRequest.serializer(), request)

        assertTrue(encoded.contains("\"private\":true"))
        assertTrue(encoded.contains("\"auto_init\":true"))
        assertTrue(encoded.contains("\"gitignore_template\":\"Android\""))
        assertTrue(encoded.contains("\"license_template\":\"mit\""))
        assertRoundTrips(request, CreateRepositoryRequest.serializer())
    }

    @Test
    fun createRepositoryRequest_nullTemplates_areOmitted() {
        val encoded =
            json.encodeToString(
                CreateRepositoryRequest.serializer(),
                CreateRepositoryRequest(name = "x"),
            )

        assertTrue(!encoded.contains("gitignore_template"))
        assertTrue(!encoded.contains("license_template"))
        assertTrue(!encoded.contains("description"))
    }

    @Test
    fun createReleaseRequest_roundTripsWithTargetCommitish() {
        assertRoundTrips(
            CreateReleaseRequest(
                tagName = "v1.0.0",
                targetCommitish = "release/1.x",
                name = "1.0.0",
                body = "notes",
                draft = true,
                prerelease = true,
            ),
            CreateReleaseRequest.serializer(),
        )
    }

    @Test
    fun createReleaseRequest_optionalFieldsNull_areOmitted() {
        val encoded =
            json.encodeToString(
                CreateReleaseRequest.serializer(),
                CreateReleaseRequest(tagName = "v2"),
            )

        assertTrue(!encoded.contains("target_commitish"))
        // 注意：tag_name 自身含 "name" 子串，必须匹配带引号的字段名
        assertTrue(!encoded.contains("\"name\""))
        assertTrue(!encoded.contains("\"body\""))
        // Json 配置 encodeDefaults=false：draft/prerelease 为默认 false 时不序列化（GitHub 服务端默认同为 false）
        assertTrue(!encoded.contains("\"draft\""))
        assertTrue(!encoded.contains("\"prerelease\""))
    }

    @Test
    fun releaseDto_roundTripsIncludingAssets() {
        val release =
            ReleaseDto(
                id = 5,
                tagName = "v1.0.0",
                name = "First",
                body = "notes",
                htmlUrl = "https://github.com/o/r/releases/tag/v1.0.0",
                publishedAt = "2026-09-01T00:00:00Z",
                prerelease = true,
                draft = false,
                author = UserDto(login = "octocat", id = 1),
                targetCommitish = "main",
                assets =
                    listOf(
                        ReleaseAssetDto(
                            id = 9,
                            name = "app.apk",
                            browserDownloadUrl = "https://example.com/app.apk",
                            size = 1024,
                            downloadCount = 4,
                            contentType = "application/vnd.android.package-archive",
                            createdAt = "2026-09-01T00:00:00Z",
                        ),
                    ),
            )

        assertRoundTrips(release, ReleaseDto.serializer())
        assertEquals(1, release.assets.size)
    }

    @Test
    fun releaseAssetDto_defaults_whenOptionalFieldsMissing() {
        val asset = json.decodeFromString(ReleaseAssetDto.serializer(), """{"id":1,"name":"a.bin"}""")

        assertNull(asset.browserDownloadUrl)
        assertNull(asset.contentType)
        assertNull(asset.createdAt)
        assertEquals(0L, asset.size)
        assertEquals(0, asset.downloadCount)
    }

    @Test
    fun releaseDto_missingAssets_defaultsToEmptyList() {
        val release =
            json.decodeFromString(
                ReleaseDto.serializer(),
                """{"id":1,"tag_name":"v1"}""",
            )

        assertTrue(release.assets.isEmpty())
        assertNull(release.targetCommitish)
    }

    @Test
    fun topicsDto_roundTrips() {
        assertRoundTrips(TopicsDto(names = listOf("kotlin", "android")), TopicsDto.serializer())
        assertTrue(json.decodeFromString(TopicsDto.serializer(), "{}").names.isEmpty())
    }

    @Test
    fun commitDetailDto_roundTripsIncludingPatch() {
        val dto =
            CommitDetailDto(
                sha = "abc",
                commit =
                    CommitInfoDto(
                        message = "fix: x",
                        author = CommitAuthorDto(name = "Octo", email = "o@example.com", date = "2026-09-01T00:00:00Z"),
                        committer = CommitAuthorDto(name = "GitHub", email = "noreply@github.com"),
                    ),
                author = UserDto(login = "octocat", id = 1),
                stats = CommitStatsDto(additions = 5, deletions = 2, total = 7),
                files =
                    listOf(
                        CommitFileDto(
                            filename = "a.kt",
                            status = "modified",
                            additions = 5,
                            deletions = 2,
                            changes = 7,
                            patch = "@@ -1 +1 @@",
                            previousFilename = "old.kt",
                        ),
                    ),
            )

        assertRoundTrips(dto, CommitDetailDto.serializer())
        assertEquals("old.kt", dto.files[0].previousFilename)
    }

    @Test
    fun commitDetailDto_missingOptionalFields_parsesWithDefaults() {
        val dto =
            json.decodeFromString(
                CommitDetailDto.serializer(),
                """{"sha":"s","commit":{}}""",
            )

        assertNull(dto.commit.message)
        assertNull(dto.author)
        assertNull(dto.stats)
        assertTrue(dto.files.isEmpty())
    }

    @Test
    fun commitStatsDto_roundTrips() {
        assertRoundTrips(CommitStatsDto(additions = 1, deletions = 2, total = 3), CommitStatsDto.serializer())
    }
}
