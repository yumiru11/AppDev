@file:Suppress("CyclomaticComplexMethod")
// feedActionText 对事件类型 × GitHub action 枚举做穷尽映射，分支天然多
// （同 NotificationsScreen.reasonLabel 与原 HomeScreen 先例）；精准抑制。

package com.yumiru11.githubapp.feature.home.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.ui.time.relativeTimeText
import com.yumiru11.githubapp.feature.home.R
import com.yumiru11.githubapp.feature.home.model.FeedEventType
import com.yumiru11.githubapp.feature.home.model.FeedItem
import java.time.Instant
import java.time.ZoneId

/** GitHub 事件 action 字面量 */
private const val ACTION_OPENED = "opened"
private const val ACTION_CLOSED = "closed"
private const val ACTION_REOPENED = "reopened"
private const val ACTION_EDITED = "edited"
private const val ACTION_DELETED = "deleted"

/** 动态流单行卡片（T10/#86 预览基线）：头像 + 动作文案 + 标题 + 仓库与时间。 */
@Composable
internal fun FeedRow(
    item: FeedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            AsyncImage(
                model = item.actorAvatarUrl,
                contentDescription = item.actorLogin,
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feedActionText(item),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.title.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.repoFullName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // #84：相对时间优先（"3 小时前"），解析失败/未来时间回退绝对日期（缺陷 #11 时间显示统一）
                    val date = feedTimestampText(item.createdAt)
                    if (date.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 事件类型 + action → 本地化动作文案（ViewModel 只传原始值，不产英文） */
@Composable
private fun feedActionText(item: FeedItem): String =
    when (item.type) {
        FeedEventType.ISSUE -> {
            when (item.action) {
                ACTION_OPENED -> stringResource(R.string.feed_action_issue_opened, item.actorLogin, item.number ?: 0)
                ACTION_CLOSED -> stringResource(R.string.feed_action_issue_closed, item.actorLogin, item.number ?: 0)
                ACTION_REOPENED -> stringResource(R.string.feed_action_issue_reopened, item.actorLogin, item.number ?: 0)
                else -> stringResource(R.string.feed_action_issue_other, item.actorLogin, item.number ?: 0)
            }
        }

        FeedEventType.ISSUE_COMMENT -> {
            when (item.action) {
                ACTION_EDITED -> stringResource(R.string.feed_action_issue_comment_edited, item.actorLogin, item.number ?: 0)
                ACTION_DELETED -> stringResource(R.string.feed_action_issue_comment_deleted, item.actorLogin, item.number ?: 0)
                else -> stringResource(R.string.feed_action_issue_comment, item.actorLogin, item.number ?: 0)
            }
        }

        FeedEventType.PULL_REQUEST -> {
            when (item.action) {
                ACTION_OPENED -> stringResource(R.string.feed_action_pr_opened, item.actorLogin, item.number ?: 0)
                ACTION_CLOSED -> stringResource(R.string.feed_action_pr_closed, item.actorLogin, item.number ?: 0)
                ACTION_REOPENED -> stringResource(R.string.feed_action_pr_reopened, item.actorLogin, item.number ?: 0)
                else -> stringResource(R.string.feed_action_pr_other, item.actorLogin, item.number ?: 0)
            }
        }

        FeedEventType.PUSH -> {
            stringResource(R.string.feed_action_push, item.actorLogin, item.commitCount ?: 0)
        }

        FeedEventType.STAR -> {
            stringResource(R.string.feed_action_star, item.actorLogin)
        }

        FeedEventType.FORK -> {
            stringResource(R.string.feed_action_fork, item.actorLogin)
        }
    }

/** ISO-8601 时间戳 → 本地日期（yyyy-MM-dd）；解析失败返回空串（UI 隐藏时间）。相对时间不可用时的回退 */
private fun formatDate(isoTimestamp: String?): String {
    if (isoTimestamp.isNullOrBlank()) return ""
    return runCatching {
        Instant
            .parse(isoTimestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
    }.getOrDefault("")
}

/** feed 行时间戳：相对时间优先，回退绝对日期（#84 缺陷 #11） */
@Composable
private fun feedTimestampText(isoTimestamp: String?): String = isoTimestamp?.let { relativeTimeText(it) } ?: formatDate(isoTimestamp)

@Preview(name = "Light", showBackground = true)
@Composable
private fun FeedRowPreviewLight() {
    AppTheme(darkTheme = false) {
        FeedRow(
            item =
                FeedItem(
                    id = "1",
                    type = FeedEventType.PULL_REQUEST,
                    actorLogin = "octocat",
                    actorAvatarUrl = null,
                    repoFullName = "yumiru11/AppDev",
                    action = "opened",
                    title = "perf(list): Paging itemKey 迁移与模型稳定性标注",
                    number = 86,
                    commitCount = null,
                    createdAt = "2026-08-21T08:30:00Z",
                    htmlUrl = null,
                ),
            onClick = {},
        )
    }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun FeedRowPreviewDark() {
    AppTheme(darkTheme = true) {
        FeedRow(
            item =
                FeedItem(
                    id = "1",
                    type = FeedEventType.STAR,
                    actorLogin = "yumiru11",
                    actorAvatarUrl = null,
                    repoFullName = "yumiru11/AppDev",
                    action = null,
                    title = "",
                    number = null,
                    commitCount = null,
                    createdAt = "2026-08-21T08:30:00Z",
                    htmlUrl = null,
                ),
            onClick = {},
        )
    }
}
