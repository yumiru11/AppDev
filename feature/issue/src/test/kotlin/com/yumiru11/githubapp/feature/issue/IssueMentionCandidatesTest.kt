package com.yumiru11.githubapp.feature.issue

import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueState
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineEventType
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem
import com.yumiru11.githubapp.feature.issue.model.IssueUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue 评论输入框 `@mention` 候选装配测试（SPEC-3）。
 *
 * 断言的是**候选来源集合**（作者 + assignees + 评论作者）与归一化契约
 * （去重/大小写/排序由 core:editor 的 `assembleMentionCandidates` 提供，
 * 其自身规则见 core:editor 的 `MentionCandidatesTest`）。
 */
class IssueMentionCandidatesTest {
    @Test
    fun mentionCandidatesFor_authorAssigneesAndCommenters_dedupesIgnoringCaseAndSorts() {
        val state =
            successState(
                issue = issue(author = "Octocat", assignees = listOf("hubot", "Octocat")),
                timeline =
                    listOf(
                        comment("Hubot"),
                        comment(null),
                        event(actor = "dependabot"),
                    ),
            )

        assertEquals(listOf("hubot", "Octocat"), mentionCandidatesFor(state))
    }

    @Test
    fun mentionCandidatesFor_onlyEventActors_returnsEmpty() {
        val state =
            successState(
                issue = issue(author = null, assignees = emptyList()),
                timeline = listOf(event(actor = "octocat")),
            )

        assertTrue("事件 actor 是操作记录，不是内容参与者", mentionCandidatesFor(state).isEmpty())
    }

    @Test
    fun mentionCandidatesFor_noParticipantsAtAll_returnsEmpty() {
        val state = successState(issue = issue(author = null, assignees = emptyList()), timeline = emptyList())

        assertTrue(mentionCandidatesFor(state).isEmpty())
    }

    @Test
    fun mentionCandidatesFor_assigneeWithBlankLogin_isDropped() {
        val state =
            successState(
                issue = issue(author = null, assignees = listOf("  ", "octocat")),
                timeline = emptyList(),
            )

        assertEquals(listOf("octocat"), mentionCandidatesFor(state))
    }

    private fun successState(
        issue: Issue,
        timeline: List<IssueTimelineItem>,
    ): IssueDetailUiState.Success = IssueDetailUiState.Success(issue = issue, timeline = timeline)

    private fun issue(
        author: String?,
        assignees: List<String>,
    ): Issue =
        Issue(
            id = 1L,
            number = 42,
            title = "Bug report",
            state = IssueState.OPEN,
            author = author?.let { IssueUser(login = it) },
            assignees = assignees.map { IssueUser(login = it) },
        )

    private fun comment(login: String?): IssueTimelineItem.Comment =
        IssueTimelineItem.Comment(id = 10L, author = login?.let { IssueUser(login = it) })

    private fun event(actor: String?): IssueTimelineItem.Event =
        IssueTimelineItem.Event(
            id = 11L,
            type = IssueTimelineEventType.CLOSED,
            actor = actor?.let { IssueUser(login = it) },
        )
}
