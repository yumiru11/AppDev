package com.yumiru11.githubapp.feature.profile

import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubrest.model.RepositoryDto
import com.yumiru11.githubapp.core.githubrest.model.UserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [UserDto.toUser] / [RepositoryDto.toRepository] 纯映射单测：
 * 全字段、null 字段、默认统计值、布尔字段。
 */
class ProfileMappersTest {
    // --- toUser ---

    @Test
    fun toUser_fullDto_mapsAllFields() {
        val dto =
            UserDto(
                login = "octocat",
                id = 1L,
                name = "The Octocat",
                avatarUrl = "https://avatars.example/u/1.png",
                htmlUrl = "https://github.com/octocat",
                bio = "Hello world",
                type = "User",
                publicRepos = 42,
                followers = 100,
                following = 7,
            )

        val user = dto.toUser()

        assertEquals(
            User(
                login = "octocat",
                name = "The Octocat",
                avatarUrl = "https://avatars.example/u/1.png",
                bio = "Hello world",
                url = "https://github.com/octocat",
                publicRepos = 42,
                followers = 100,
                following = 7,
            ),
            user,
        )
    }

    @Test
    fun toUser_nullOptionals_mapNull() {
        val dto = UserDto(login = "octocat", id = 1L)

        val user = dto.toUser()

        assertEquals("octocat", user.login)
        assertNull(user.name)
        assertNull(user.avatarUrl)
        assertNull(user.bio)
        assertNull(user.url)
    }

    @Test
    fun toUser_defaultStats_zero() {
        val user = UserDto(login = "octocat", id = 1L).toUser()

        assertEquals(0, user.publicRepos)
        assertEquals(0, user.followers)
        assertEquals(0, user.following)
    }

    @Test
    fun toUser_stats_mapsCounts() {
        val user =
            UserDto(
                login = "octocat",
                id = 1L,
                publicRepos = 12,
                followers = 345,
                following = 6,
            ).toUser()

        assertEquals(12, user.publicRepos)
        assertEquals(345, user.followers)
        assertEquals(6, user.following)
    }

    // --- toRepository ---

    @Test
    fun toRepository_fullDto_mapsAllFields() {
        val dto =
            RepositoryDto(
                id = 100L,
                name = "Hello-World",
                fullName = "octocat/Hello-World",
                isPrivate = false,
                owner = UserDto(login = "octocat", id = 1L),
                description = "My first repository",
                htmlUrl = "https://github.com/octocat/Hello-World",
                stargazersCount = 80,
                forksCount = 9,
                language = "Java",
                defaultBranch = "master",
            )

        val repo = dto.toRepository()

        assertEquals(
            Repository(
                ownerLogin = "octocat",
                name = "Hello-World",
                description = "My first repository",
                isPrivate = false,
                stargazerCount = 80,
                forkCount = 9,
                language = "Java",
                defaultBranch = "master",
            ),
            repo,
        )
    }

    @Test
    fun toRepository_privateRepo_mapsIsPrivateTrue() {
        val dto =
            RepositoryDto(
                id = 100L,
                name = "Secret",
                fullName = "octocat/Secret",
                isPrivate = true,
                owner = UserDto(login = "octocat", id = 1L),
            )

        assertTrue(dto.toRepository().isPrivate)
    }

    @Test
    fun toRepository_publicRepo_mapsIsPrivateFalse() {
        val dto =
            RepositoryDto(
                id = 100L,
                name = "Public",
                fullName = "octocat/Public",
                isPrivate = false,
                owner = UserDto(login = "octocat", id = 1L),
            )

        assertFalse(dto.toRepository().isPrivate)
    }

    @Test
    fun toRepository_nullOptionals_mapNull() {
        val dto =
            RepositoryDto(
                id = 100L,
                name = "Repo",
                fullName = "octocat/Repo",
                isPrivate = false,
                owner = UserDto(login = "octocat", id = 1L),
            )

        val repo = dto.toRepository()

        assertNull(repo.description)
        assertNull(repo.language)
        assertNull(repo.defaultBranch)
    }

    @Test
    fun toRepository_defaultCounts_zero() {
        val dto =
            RepositoryDto(
                id = 100L,
                name = "Repo",
                fullName = "octocat/Repo",
                isPrivate = false,
                owner = UserDto(login = "octocat", id = 1L),
            )

        val repo = dto.toRepository()

        assertEquals(0, repo.stargazerCount)
        assertEquals(0, repo.forkCount)
    }

    @Test
    fun toRepository_counts_mapsStats() {
        val dto =
            RepositoryDto(
                id = 100L,
                name = "Repo",
                fullName = "octocat/Repo",
                isPrivate = false,
                owner = UserDto(login = "octocat", id = 1L),
                stargazersCount = 1024,
                forksCount = 512,
            )

        val repo = dto.toRepository()

        // REST stargazersCount → domain stargazerCount（命名差异）
        assertEquals(1024, repo.stargazerCount)
        assertEquals(512, repo.forkCount)
    }
}
