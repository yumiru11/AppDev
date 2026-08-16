package com.yumiru11.githubapp.core.githubdata.map

import com.yumiru11.githubapp.core.data.model.SearchCodeItem
import com.yumiru11.githubapp.core.data.model.SearchIssue
import com.yumiru11.githubapp.core.githubrest.model.CodeSearchItemDto
import com.yumiru11.githubapp.core.githubrest.model.IssueDto

/**
 * 搜索 DTO → 领域模型映射（/search 各端点 items 子集）。
 *
 * [IssueDto.toSearchIssue] 的 repoFullName 从 repository_url
 * （https://api.github.com/repos/{owner}/{repo}）提取；缺失/畸形时回退 null
 * （UI 不展示归属仓库行）。
 */
internal fun IssueDto.toSearchIssue(): SearchIssue =
    SearchIssue(
        id = id,
        number = number,
        title = title,
        state = state,
        isPullRequest = pullRequest != null,
        authorLogin = user?.login,
        repoFullName = repositoryUrl?.repoFullNameFromApiUrl(),
        htmlUrl = htmlUrl,
    )

internal fun CodeSearchItemDto.toSearchCodeItem(): SearchCodeItem =
    SearchCodeItem(
        name = name,
        path = path,
        repoFullName = repository?.fullName.orEmpty(),
        htmlUrl = htmlUrl,
    )

/** 从 GitHub API 仓库 URL（…/repos/{owner}/{repo}）提取 owner/name；畸形返回 null */
internal fun String.repoFullNameFromApiUrl(): String? {
    val segments = removeSuffix("/").split('/')
    val reposIndex = segments.indexOf("repos")
    val owner = segments.getOrNull(reposIndex + 1)
    val repo = segments.getOrNull(reposIndex + 2)
    return if (reposIndex >= 0 && !owner.isNullOrEmpty() && !repo.isNullOrEmpty()) {
        "$owner/$repo"
    } else {
        null
    }
}
