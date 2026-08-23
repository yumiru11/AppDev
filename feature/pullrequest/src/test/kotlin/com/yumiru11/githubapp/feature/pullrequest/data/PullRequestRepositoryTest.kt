package com.yumiru11.githubapp.feature.pullrequest.data

import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.model.IssueEventDto
import com.yumiru11.githubapp.core.githubrest.model.PullRequestReviewCommentDto
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRunConclusion
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRunStatus
import com.yumiru11.githubapp.feature.pullrequest.model.DiffSide
import com.yumiru11.githubapp.feature.pullrequest.model.MergeableState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestReviewState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineEventType
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineItem
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewComment
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewThread
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PullRequestRepository 映射测试（纯 JVM，DTO → 领域模型）。
 *
 * 覆盖：PR 状态映射（open/closed/merged/draft）、Mergeable 映射（true/false/null）、
 * 时间线判别（reviewed→Review / commented 含 path→ReviewComment / commented→Comment /
 * committed→CommitReference / cross-referenced null id→合成负 id）、CheckRun 状态/结论映射。
 */
class PullRequestRepositoryTest {
    @Test
    fun toDomain_openPr_mapsOpenState() {
        val dto = pullRequestDto(state = "open", draft = false, mergedAt = null)

        val domain = dto.toDomain()

        assertEquals(PullRequestState.OPEN, domain.state)
        assertEquals("Add feature", domain.title)
        assertEquals("octocat", domain.author?.login)
        assertEquals("feature", domain.head?.ref)
        assertEquals("main", domain.base?.ref)
    }

    @Test
    fun toDomain_closedWithoutMerge_mapsClosedState() {
        val dto = pullRequestDto(state = "closed", draft = false, mergedAt = null)

        assertEquals(PullRequestState.CLOSED, dto.toDomain().state)
    }

    @Test
    fun toDomain_closedWithMerge_mapsMergedState() {
        val dto = pullRequestDto(state = "closed", draft = false, mergedAt = "2026-08-02T00:00:00Z")

        assertEquals(PullRequestState.MERGED, dto.toDomain().state)
    }

    @Test
    fun toDomain_draft_mapsDraftState() {
        val dto = pullRequestDto(state = "open", draft = true, mergedAt = null)

        assertEquals(PullRequestState.DRAFT, dto.toDomain().state)
    }

    @Test
    fun toDomain_mergeableTrue_mapsMergeable() {
        val dto = pullRequestDto(state = "open", draft = false, mergedAt = null).copy(mergeable = true, mergeableState = "clean")

        assertEquals(MergeableState.MERGEABLE, dto.toDomain().mergeableState)
    }

    @Test
    fun toDomain_mergeableFalse_mapsConflicting() {
        val dto = pullRequestDto(state = "open", draft = false, mergedAt = null).copy(mergeable = false, mergeableState = "dirty")

        assertEquals(MergeableState.CONFLICTING, dto.toDomain().mergeableState)
    }

    @Test
    fun toDomain_mergeableNull_mapsUnknownPending() {
        val dto = pullRequestDto(state = "open", draft = false, mergedAt = null).copy(mergeable = null, mergeableState = "unknown")

        assertEquals(MergeableState.UNKNOWN, dto.toDomain().mergeableState)
        assertNull(dto.toDomain().mergeable)
    }

    @Test
    fun toTimelineItem_reviewed_mapsReviewCard() {
        val dto = timelineEventDto(event = "reviewed", state = "APPROVED", body = "LGTM")

        val item = dto.toTimelineItem(0)

        assertTrue(item is PullRequestTimelineItem.Review)
        item as PullRequestTimelineItem.Review
        assertEquals(PullRequestReviewState.APPROVED, item.state)
        assertEquals("LGTM", item.body)
        assertEquals("reviewer", item.author?.login)
    }

    @Test
    fun toTimelineItem_commentedWithPath_mapsReviewComment() {
        val dto = timelineEventDto(event = "commented", path = "src/Main.kt", line = 10, body = "Inline note")

        val item = dto.toTimelineItem(0)

        assertTrue(item is PullRequestTimelineItem.ReviewComment)
        item as PullRequestTimelineItem.ReviewComment
        assertEquals("src/Main.kt", item.path)
        assertEquals(10, item.line)
        assertEquals("Inline note", item.body)
    }

    @Test
    fun toTimelineItem_commentedWithoutPath_mapsComment() {
        val dto = timelineEventDto(event = "commented", body = "Regular comment")

        val item = dto.toTimelineItem(0)

        assertTrue(item is PullRequestTimelineItem.Comment)
        item as PullRequestTimelineItem.Comment
        assertEquals("Regular comment", item.body)
    }

    @Test
    fun toTimelineItem_committed_mapsCommitReference() {
        val dto = timelineEventDto(event = "committed", sha = "abc123", message = "WIP")

        val item = dto.toTimelineItem(0)

        assertTrue(item is PullRequestTimelineItem.CommitReference)
        item as PullRequestTimelineItem.CommitReference
        assertEquals("abc123", item.sha)
        assertEquals("WIP", item.message)
    }

    @Test
    fun toTimelineItem_crossReferencedNullId_usesSyntheticId() {
        val json =
            """
            {
              "id": null,
              "event": "cross-referenced",
              "created_at": "2026-08-19T12:50:43Z",
              "source": {
                "type": "issue",
                "issue": {
                  "id": 1,
                  "number": 72,
                  "title": "source issue",
                  "state": "open"
                }
              }
            }
            """.trimIndent()

        val dto = GitHubRestClient.createJson().decodeFromString<IssueEventDto>(json)
        val item = dto.toTimelineItem(1)

        assertTrue(item is PullRequestTimelineItem.Event)
        item as PullRequestTimelineItem.Event
        assertEquals(PullRequestTimelineEventType.CROSS_REFERENCED, item.type)
        assertEquals(-2L, item.id)
        assertEquals(72, item.sourceIssue?.number)
    }

    @Test
    fun toTimelineItem_mergedEvent_mapsEventType() {
        val dto = timelineEventDto(event = "merged")

        val item = dto.toTimelineItem(0)

        assertTrue(item is PullRequestTimelineItem.Event)
        assertEquals(PullRequestTimelineEventType.MERGED, (item as PullRequestTimelineItem.Event).type)
    }

    @Test
    fun toTimelineItem_unknownEvent_mapsUnknownType() {
        val dto = timelineEventDto(event = "some_future_event")

        val item = dto.toTimelineItem(0)

        assertTrue(item is PullRequestTimelineItem.Event)
        assertEquals(PullRequestTimelineEventType.UNKNOWN, (item as PullRequestTimelineItem.Event).type)
    }

    @Test
    fun checkRunDto_completedSuccess_mapsConclusion() {
        val dto =
            com.yumiru11.githubapp.core.githubrest.model.CheckRunDto(
                id = 1L,
                name = "CI",
                status = "completed",
                conclusion = "success",
            )

        val domain = dto.toDomain()

        assertEquals(CheckRunStatus.COMPLETED, domain.status)
        assertEquals(CheckRunConclusion.SUCCESS, domain.conclusion)
    }

    @Test
    fun checkRunDto_inProgressNullConclusion_mapsUnknownConclusion() {
        val dto =
            com.yumiru11.githubapp.core.githubrest.model.CheckRunDto(
                id = 2L,
                name = "Lint",
                status = "in_progress",
                conclusion = null,
            )

        val domain = dto.toDomain()

        assertEquals(CheckRunStatus.IN_PROGRESS, domain.status)
        assertEquals(CheckRunConclusion.UNKNOWN, domain.conclusion)
    }

    private fun pullRequestDto(
        state: String,
        draft: Boolean,
        mergedAt: String?,
    ): com.yumiru11.githubapp.core.githubrest.model.PullRequestDto =
        com.yumiru11.githubapp.core.githubrest.model.PullRequestDto(
            id = 1L,
            number = 42,
            title = "Add feature",
            state = state,
            draft = draft,
            mergedAt = mergedAt,
            user =
                com.yumiru11.githubapp.core.githubrest.model
                    .UserDto(login = "octocat", id = 1),
            head =
                com.yumiru11.githubapp.core.githubrest.model
                    .PullRequestBranchDto(ref = "feature", sha = "abc123"),
            base =
                com.yumiru11.githubapp.core.githubrest.model
                    .PullRequestBranchDto(ref = "main"),
        )

    private fun timelineEventDto(
        event: String,
        state: String? = null,
        path: String? = null,
        line: Int? = null,
        sha: String? = null,
        message: String? = null,
        body: String? = null,
    ): IssueEventDto =
        IssueEventDto(
            id = 1L,
            event = event,
            actor =
                com.yumiru11.githubapp.core.githubrest.model
                    .UserDto(login = "reviewer", id = 2),
            body = body,
            state = state,
            path = path,
            line = line,
            sha = sha,
            message = message,
        )

    // ── T16：行内评论与会话映射 ──

    @Test
    fun toDomain_reviewComment_rightSide_mapsAllFields() {
        val dto =
            PullRequestReviewCommentDto(
                id = 1L,
                user =
                    com.yumiru11.githubapp.core.githubrest.model
                        .UserDto(login = "reviewer", id = 2),
                body = "nice",
                path = "README.md",
                line = 10,
                side = "RIGHT",
                commitId = "abc123",
                nodeId = "PRRC_1",
                resolved = true,
                createdAt = "2026-08-01T00:00:00Z",
            )

        val domain = dto.toDomain()

        assertEquals(ReviewComment::class, domain::class)
        assertEquals(1L, domain.id)
        assertEquals("nice", domain.body)
        assertEquals("reviewer", domain.author?.login)
        assertEquals("README.md", domain.path)
        assertEquals(10, domain.anchorLine)
        assertEquals(DiffSide.RIGHT, domain.side)
        assertEquals("abc123", domain.commitId)
        assertTrue(domain.resolved)
        assertEquals("PRRC_1", domain.nodeId)
    }

    @Test
    fun toDomain_reviewComment_leftSide_mapsOriginalLineAsAnchor() {
        val dto =
            PullRequestReviewCommentDto(
                id = 2L,
                line = null,
                originalLine = 7,
                side = "LEFT",
            )

        val domain = dto.toDomain()

        assertEquals(DiffSide.LEFT, domain.side)
        assertEquals(7, domain.anchorLine)
        assertNull(domain.line)
    }

    @Test
    fun toDomain_reviewComment_unknownSide_fallsBackToLine() {
        val dto =
            PullRequestReviewCommentDto(
                id = 3L,
                line = 5,
                side = null,
            )

        val domain = dto.toDomain()

        assertEquals(DiffSide.UNKNOWN, domain.side)
        assertEquals(5, domain.anchorLine)
    }

    @Test
    fun toDomain_reviewComment_resolvedNull_defaultsFalse() {
        val dto = PullRequestReviewCommentDto(id = 4L, resolved = null)

        val domain = dto.toDomain()

        assertTrue(!domain.resolved)
    }

    @Test
    fun toDomain_reviewComment_replyKeepsInReplyToId() {
        val dto = PullRequestReviewCommentDto(id = 5L, inReplyToId = 1L)

        val domain = dto.toDomain()

        assertEquals(1L, domain.inReplyToId)
    }

    @Test
    fun toReviewThreads_rightSideThread_mapsAnchorAndComments() {
        val raw =
            listOf(
                RawReviewThread(
                    id = "THREAD_1",
                    path = "README.md",
                    side = "RIGHT",
                    line = 10,
                    originalLine = null,
                    isResolved = true,
                    commentIds = listOf("PRRC_1", "PRRC_2"),
                ),
            )

        val threads = raw.toReviewThreads()

        assertEquals(1, threads.size)
        val thread = threads.single()
        assertEquals("THREAD_1", thread.id)
        assertEquals(DiffSide.RIGHT, thread.side)
        assertEquals(10, thread.anchorLine)
        assertTrue(thread.isResolved)
        assertEquals(listOf("PRRC_1", "PRRC_2"), thread.commentIds)
    }

    @Test
    fun toReviewThreads_unknownSide_mapsToUnknown() {
        val threads =
            listOf(
                RawReviewThread(
                    id = "T",
                    path = "p",
                    side = null,
                    line = null,
                    originalLine = 3,
                    isResolved = false,
                    commentIds = emptyList(),
                ),
            ).toReviewThreads()

        assertEquals(DiffSide.UNKNOWN, threads.single().side)
        assertEquals(3, threads.single().anchorLine)
    }
}
