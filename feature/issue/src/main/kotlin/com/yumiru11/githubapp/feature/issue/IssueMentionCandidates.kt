package com.yumiru11.githubapp.feature.issue

import com.yumiru11.githubapp.core.editor.assembleMentionCandidates
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem

/**
 * Issue 评论输入框的 `@mention` 候选（SPEC-3）。
 *
 * 候选源 = **本屏状态里已有的人**，不额外发请求：Issue 作者 + Assignees + 评论作者。
 * 时间线事件（closed/labeled/…）的 actor 不计入——本票把「参与者」界定为**写过内容的人**，
 * 状态变更事件的 actor 是操作记录而非内容贡献（且含 bot）。
 *
 * 去重 / 排序 / 截断统一委托 core:editor 的 [assembleMentionCandidates]——
 * 与编辑器侧协作者候选共用同一份规则。
 */
internal fun mentionCandidatesFor(state: IssueDetailUiState.Success): List<String> =
    assembleMentionCandidates(
        buildList {
            state.issue.author
                ?.login
                ?.let(::add)
            state.issue.assignees.mapTo(this) { it.login }
            state.timeline.mapNotNullTo(this) { (it as? IssueTimelineItem.Comment)?.author?.login }
        },
    )
