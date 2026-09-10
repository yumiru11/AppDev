package com.yumiru11.githubapp.feature.issue.data

import com.yumiru11.githubapp.core.database.entity.IssueEntity
import com.yumiru11.githubapp.core.githubrest.model.IssueDto
import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueState
import com.yumiru11.githubapp.feature.issue.model.IssueUser

// Issue 网络 DTO ↔ Room 分页缓存实体互转（issue #165 / L07）。
//
// 缓存只保存列表行所需字段：正文/标签/Assignees/Milestone/反应不进列表缓存
// （列表不渲染它们，详情页走单独接口实时拉取，见 IssueRepository.getIssue）。
// 因此 IssueEntity.toDomain 产出的 Issue 在这些字段上是空值，仅可用于列表展示。

/** IssueDto → 缓存实体（[page]/[position] 为远端分页坐标，[cachedAt] 为本地写入时间） */
internal fun IssueDto.toEntity(
    owner: String,
    repo: String,
    filter: String,
    page: Int,
    position: Int,
    cachedAt: Long,
): IssueEntity =
    IssueEntity(
        owner = owner,
        repo = repo,
        filter = filter,
        issueId = id,
        number = number,
        title = title,
        state = state,
        authorLogin = user?.login,
        authorAvatarUrl = user?.avatarUrl,
        commentCount = comments,
        isPullRequest = pullRequest != null,
        updatedAt = updatedAt,
        htmlUrl = htmlUrl,
        page = page,
        position = position,
        cachedAt = cachedAt,
    )

/** 缓存实体 → 列表领域模型（仅列表可见字段，见文件 KDoc） */
internal fun IssueEntity.toDomain(): Issue =
    Issue(
        id = issueId,
        number = number,
        title = title,
        state = IssueState.fromRaw(state),
        author = authorLogin?.let { IssueUser(login = it, avatarUrl = authorAvatarUrl) },
        commentCount = commentCount,
        updatedAt = updatedAt,
        htmlUrl = htmlUrl,
        isPullRequest = isPullRequest,
    )
