package com.yumiru11.githubapp.feature.issue.data

import com.yumiru11.githubapp.core.database.entity.IssueEntity
import com.yumiru11.githubapp.core.githubrest.model.IssueDto
import com.yumiru11.githubapp.core.githubrest.model.UserDto
import com.yumiru11.githubapp.feature.issue.model.IssueState
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 缓存映射单测（issue #165 / L07）：IssueDto → IssueEntity → Issue（列表可见字段）。
 *
 * 覆盖：远端分页坐标写入、作者/PR 判别、state 解析，以及 toDomain 只还原列表字段
 * （正文/标签不落缓存，故为空）。
 */
class IssueCacheMapperTest {
    @Test
    fun toEntity_copiesListFieldsAndPagingCoordinates() {
        val entity = dto().toEntity(owner = OWNER, repo = REPO, filter = "open", page = 3, position = 7, cachedAt = 42L)

        assertEquals(OWNER, entity.owner)
        assertEquals(REPO, entity.repo)
        assertEquals("open", entity.filter)
        assertEquals(1347L, entity.issueId)
        assertEquals(42, entity.number)
        assertEquals("Bug report", entity.title)
        assertEquals("open", entity.state)
        assertEquals("octocat", entity.authorLogin)
        assertEquals("https://avatars.githubusercontent.com/u/1", entity.authorAvatarUrl)
        assertEquals(3, entity.commentCount)
        assertFalse(entity.isPullRequest)
        assertEquals("2026-09-06T00:00:00Z", entity.updatedAt)
        assertEquals(3, entity.page)
        assertEquals(7, entity.position)
        assertEquals(42L, entity.cachedAt)
    }

    @Test
    fun toEntity_pullRequestPayload_marksRowAsPullRequest() {
        val entity =
            dto(isPr = true).toEntity(owner = OWNER, repo = REPO, filter = "open", page = 1, position = 0, cachedAt = 0L)

        assertTrue(entity.isPullRequest)
    }

    @Test
    fun toEntity_missingAuthor_leavesAuthorColumnsNull() {
        val entity =
            dto(withAuthor = false).toEntity(owner = OWNER, repo = REPO, filter = "open", page = 1, position = 0, cachedAt = 0L)

        assertNull(entity.authorLogin)
        assertNull(entity.authorAvatarUrl)
    }

    @Test
    fun toDomain_mapsListVisibleFields() {
        val entity = entity()

        val issue = entity.toDomain()

        assertEquals(1347L, issue.id)
        assertEquals(42, issue.number)
        assertEquals("Bug report", issue.title)
        assertEquals(IssueState.OPEN, issue.state)
        assertEquals("octocat", issue.author?.login)
        assertEquals(3, issue.commentCount)
        assertFalse(issue.isPullRequest)
    }

    @Test
    fun toDomain_closedState_isParsed() {
        assertEquals(IssueState.CLOSED, entity(state = "closed").toDomain().state)
    }

    @Test
    fun toDomain_withoutAuthor_producesNullAuthor() {
        assertNull(entity(authorLogin = null).toDomain().author)
    }

    @Test
    fun toDomain_dropsNonListFieldsBecauseCacheDoesNotStoreThem() {
        // 正文/标签/Assignees 不进列表缓存 → 缓存还原的 Issue 在这些字段上必须为空
        val issue = entity().toDomain()

        assertNull(issue.body)
        assertTrue(issue.labels.isEmpty())
        assertTrue(issue.assignees.isEmpty())
        assertNull(issue.milestone)
    }

    private fun dto(
        isPr: Boolean = false,
        withAuthor: Boolean = true,
    ): IssueDto =
        IssueDto(
            id = 1347L,
            number = 42,
            title = "Bug report",
            state = "open",
            user = if (withAuthor) UserDto(id = 1L, login = "octocat", avatarUrl = "https://avatars.githubusercontent.com/u/1") else null,
            comments = 3,
            updatedAt = "2026-09-06T00:00:00Z",
            htmlUrl = "https://github.com/octocat/Hello-World/issues/42",
            pullRequest = if (isPr) JsonObject(emptyMap()) else null,
        )

    private fun entity(
        state: String = "open",
        authorLogin: String? = "octocat",
    ): IssueEntity =
        IssueEntity(
            owner = OWNER,
            repo = REPO,
            filter = "open",
            issueId = 1347L,
            number = 42,
            title = "Bug report",
            state = state,
            authorLogin = authorLogin,
            authorAvatarUrl = null,
            commentCount = 3,
            isPullRequest = false,
            updatedAt = "2026-09-06T00:00:00Z",
            htmlUrl = "https://github.com/octocat/Hello-World/issues/42",
            page = 1,
            position = 0,
            cachedAt = 0L,
        )

    private companion object {
        const val OWNER = "octocat"
        const val REPO = "Hello-World"
    }
}
