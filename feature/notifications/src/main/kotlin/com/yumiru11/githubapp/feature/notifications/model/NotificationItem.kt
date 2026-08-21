package com.yumiru11.githubapp.feature.notifications.model

import androidx.compose.runtime.Immutable

/**
 * 通知条目（领域模型；DTO → domain 映射见 NotificationsPagingSource）。
 *
 * 只读 UI 模型，标注 [Immutable]：行组件参数稳定，
 * 滚动/翻页/过滤切换时跳过行级重组（#86）。
 *
 * @param reason GitHub 通知原因（mention/assign/subscribed/review_requested 等），
 *   UI 层映射本地化文案（ViewModel 不产英文）
 * @param htmlUrl 内容页链接（如 …/issues/1347），应用内导航解析基准
 */
@Immutable
data class NotificationItem(
    val id: String,
    val repoFullName: String,
    val subjectTitle: String,
    val subjectType: String,
    val reason: String,
    val unread: Boolean,
    val updatedAt: String?,
    val htmlUrl: String?,
)

/**
 * 通知过滤（T19 验收第 3 条：全部/参与/提及）。
 *
 * 服务端参数映射（见 NotificationsPagingSource）：
 * - [ALL]：all=true（含已读）
 * - [PARTICIPATING]：participating=true（仅参与）
 * - [MENTION]：all=true + 客户端按 reason ∈ {mention, team_mention} 过滤
 *   （GitHub API 无 mention 参数，仅 all/participating）
 */
enum class NotificationFilter {
    ALL,
    PARTICIPATING,
    MENTION,
}
