package com.yumiru11.githubapp.feature.home.model

import androidx.compose.runtime.Immutable

/**
 * 首页动态条目（领域模型；DTO → domain 映射见 FeedPagingSource）。
 *
 * 仅承载本票展示的 6 类事件（Issues/IssueComment/PullRequest/Push/Star/Fork），
 * 未知事件类型在分页源侧过滤（不为所有 GitHub 事件类型建模型）。
 *
 * 只读 UI 模型，标注 [Immutable]：行组件参数稳定，
 * 滚动/翻页/无关状态变化时跳过行级重组（#86）。
 *
 * @param type 事件类型（UI 层映射本地化动作文案，ViewModel 不产英文）
 * @param action GitHub 事件动作（opened/closed/reopened 等；Push/Star/Fork 为 null）
 * @param title 内容标题（issue/PR 标题或 push 首条提交信息；Star/Fork 为空串）
 * @param number issue/PR 编号（Push/Star/Fork 为 null）
 * @param commitCount push 提交数（仅 PUSH 非 null）
 * @param htmlUrl 内容页链接（应用内导航解析基准，T10 验收第 4 条）
 */
@Immutable
data class FeedItem(
    val id: String,
    val type: FeedEventType,
    val actorLogin: String,
    val actorAvatarUrl: String?,
    val repoFullName: String,
    val action: String?,
    val title: String,
    val number: Int?,
    val commitCount: Int?,
    val createdAt: String?,
    val htmlUrl: String?,
)

/**
 * 首页动态事件类型（T10 展示用 6 类）。
 */
enum class FeedEventType {
    /** Issue 开/关/更新（IssuesEvent） */
    ISSUE,

    /** Issue 评论（IssueCommentEvent） */
    ISSUE_COMMENT,

    /** Pull Request 开/关/更新（PullRequestEvent） */
    PULL_REQUEST,

    /** 推送提交（PushEvent） */
    PUSH,

    /** Star（WatchEvent） */
    STAR,

    /** Fork（ForkEvent） */
    FORK,
}
