package com.yumiru11.githubapp.feature.home.model

// 首页各分区（议题 / 拉取请求 / 讨论 / 发现）的列表项模型。
//
// 为什么在 feature:home 自建轻量投影、不在 UI 里直接用 DTO/GraphQL 模型：
// - 这些分区只读展示，字段集合与全量模型差异大（不需要 body/milestone/reactions…）；
// - 列表卡片只需要「标题 + 状态 + 归属 + 计数 + 时间」，投影成模型后 UI 层零解析逻辑。

/** 议题/PR 行（REST /search/issues 结果；PR 由 `pull_request` 字段识别）。 */
data class HomeIssueItem(
    val id: Long,
    val number: Int,
    val title: String,
    val repoFullName: String,
    val state: String,
    val isPullRequest: Boolean,
    val isDraft: Boolean,
    val isMerged: Boolean,
    val commentCount: Int,
    val updatedAt: String?,
    val labels: List<HomeLabel>,
)

/** GitHub label（颜色为 API 的 6 位 hex，无 # 前缀）。 */
data class HomeLabel(
    val name: String,
    val colorHex: String?,
)

/** 建议关注用户行。 */
data class HomeUserItem(
    val login: String,
    val name: String?,
    val avatarUrl: String?,
    val followers: Int,
)

/** 讨论行（GraphQL search type: DISCUSSION）。 */
data class HomeDiscussionItem(
    val id: String,
    val number: Int,
    val title: String,
    val repoFullName: String?,
    val categoryName: String?,
    val isAnswered: Boolean,
    val commentCount: Int,
    val upvotes: Int,
    val updatedAt: String?,
)

/** 议题分区子过滤（对应 M3E HomeIssueFilter）。 */
enum class HomeIssueFilter {
    ASSIGNED,
    CREATED,
    MENTIONED,
}

/** 拉取请求分区子过滤（对应 M3E HomePullFilter）。 */
enum class HomePullFilter {
    INVOLVED,
    REVIEW,
    RECENT,
}
