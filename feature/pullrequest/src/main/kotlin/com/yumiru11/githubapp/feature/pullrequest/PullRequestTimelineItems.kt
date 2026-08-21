package com.yumiru11.githubapp.feature.pullrequest

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yumiru11.githubapp.core.markdown.MarkdownViewer
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestReviewState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineEventType
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineItem
import com.yumiru11.githubapp.core.ui.time.relativeTimeText
import java.util.Locale

/** 评论条目：列表项风格（非重卡片），作者头像 + 登录名 + 相对时间 + Markdown 正文 */
@Composable
internal fun CommentItem(
    item: PullRequestTimelineItem.Comment,
    onInternalLink: (ParsedUrl) -> Unit,
    baseRepoUrl: String,
) {
    TimelineCard {
        TimelineHeader(
            avatarUrl = item.author?.avatarUrl,
            login = item.author?.login,
            timestamp = item.createdAt,
        )
        if (!item.body.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            MarkdownViewer(
                markdown = item.body,
                onInternalLink = onInternalLink,
                baseRepoUrl = baseRepoUrl,
                scrollable = false,
            )
        }
    }
}

/** Review 卡片：approve/comment/request-changes（状态色 + 状态文案 + 正文） */
@Composable
internal fun ReviewCard(
    item: PullRequestTimelineItem.Review,
    onInternalLink: (ParsedUrl) -> Unit,
    baseRepoUrl: String,
) {
    val stateText = reviewStateText(item.state)
    val stateColor = reviewStateColor(item.state)
    TimelineCard {
        TimelineHeader(
            avatarUrl = item.author?.avatarUrl,
            login = item.author?.login,
            timestamp = item.submittedAt,
            trailingText = stateText,
            trailingColor = stateColor,
        )
        if (!item.body.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            MarkdownViewer(
                markdown = item.body,
                onInternalLink = onInternalLink,
                baseRepoUrl = baseRepoUrl,
                scrollable = false,
            )
        }
    }
}

/** 行内评论条目：path:line 定位 + 正文 */
@Composable
internal fun ReviewCommentItem(
    item: PullRequestTimelineItem.ReviewComment,
    onInternalLink: (ParsedUrl) -> Unit,
    baseRepoUrl: String,
) {
    TimelineCard {
        TimelineHeader(
            avatarUrl = item.author?.avatarUrl,
            login = item.author?.login,
            timestamp = item.createdAt,
        )
        val path = item.path
        val line = item.line
        if (!path.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text =
                    if (line != null) {
                        stringResource(R.string.pull_request_review_comment_at, path, line)
                    } else {
                        path
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!item.body.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            MarkdownViewer(
                markdown = item.body,
                onInternalLink = onInternalLink,
                baseRepoUrl = baseRepoUrl,
                scrollable = false,
            )
        }
    }
}

/** 提交引用条目：缩写 SHA + 消息 */
@Composable
internal fun CommitReferenceItem(item: PullRequestTimelineItem.CommitReference) {
    val sha = item.sha?.take(SHORT_SHA_LENGTH).orEmpty()
    val message =
        item.message
            ?.lineSequence()
            ?.firstOrNull()
            .orEmpty()
    TimelineCard {
        TimelineHeader(
            avatarUrl = item.author?.avatarUrl,
            login = item.author?.login,
            timestamp = item.createdAt,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.pull_request_event_committed, item.author?.login.orEmpty(), sha),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (message.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 事件项：按类型分发——交叉引用/关联 PR 独立成项，其余走通用事件文本 */
@Composable
internal fun EventItem(event: PullRequestTimelineItem.Event) {
    when (event.type) {
        PullRequestTimelineEventType.CROSS_REFERENCED -> {
            CrossReferenceItem(event)
        }

        PullRequestTimelineEventType.CONNECTED, PullRequestTimelineEventType.LINKED -> {
            LinkedPrItem(event)
        }

        else -> {
            EventTextItem(event)
        }
    }
}

/** 通用事件文本（closed/merged/labeled/review_requested/head_ref_force_pushed 等） */
@Suppress("CyclomaticComplexMethod") // 事件类型 → 文案映射分支天然多（20+ 类型），枚举化收益大于拆分（T3 先例）
@Composable
private fun EventTextItem(event: PullRequestTimelineItem.Event) {
    val actor = event.actor?.login.orEmpty()
    val text =
        when (event.type) {
            PullRequestTimelineEventType.CLOSED -> {
                stringResource(R.string.pull_request_event_closed, actor)
            }

            PullRequestTimelineEventType.REOPENED -> {
                stringResource(R.string.pull_request_event_reopened, actor)
            }

            PullRequestTimelineEventType.MERGED -> {
                stringResource(R.string.pull_request_event_merged, actor)
            }

            PullRequestTimelineEventType.LABELED -> {
                stringResource(R.string.pull_request_event_labeled, actor)
            }

            PullRequestTimelineEventType.UNLABELED -> {
                stringResource(R.string.pull_request_event_unlabeled, actor)
            }

            PullRequestTimelineEventType.ASSIGNED -> {
                stringResource(R.string.pull_request_event_assigned, actor)
            }

            PullRequestTimelineEventType.LOCKED -> {
                stringResource(R.string.pull_request_event_locked, actor)
            }

            PullRequestTimelineEventType.REVIEW_REQUESTED -> {
                stringResource(R.string.pull_request_event_review_requested, actor)
            }

            PullRequestTimelineEventType.REVIEW_REQUEST_REMOVED -> {
                stringResource(R.string.pull_request_event_review_request_removed, actor)
            }

            PullRequestTimelineEventType.HEAD_REF_FORCE_PUSHED -> {
                stringResource(R.string.pull_request_event_head_ref_force_pushed, actor, event.ref.orEmpty())
            }

            PullRequestTimelineEventType.HEAD_REF_DELETED -> {
                stringResource(R.string.pull_request_event_head_ref_deleted, actor, event.ref.orEmpty())
            }

            PullRequestTimelineEventType.BASE_REF_CHANGED -> {
                stringResource(R.string.pull_request_event_base_ref_changed, actor)
            }

            PullRequestTimelineEventType.READY_FOR_REVIEW -> {
                stringResource(R.string.pull_request_event_ready_for_review, actor)
            }

            PullRequestTimelineEventType.CONVERTED_TO_DRAFT -> {
                stringResource(R.string.pull_request_event_converted_to_draft, actor)
            }

            PullRequestTimelineEventType.COMMENTED,
            PullRequestTimelineEventType.REVIEWED,
            PullRequestTimelineEventType.COMMITTED,
            PullRequestTimelineEventType.UNKNOWN,
            -> {
                stringResource(R.string.pull_request_event_default, actor, event.type.name.lowercase(Locale.ROOT))
            }

            // 由 EventItem 分发到 CrossReferenceItem/LinkedPrItem，此处不可达
            PullRequestTimelineEventType.CROSS_REFERENCED,
            PullRequestTimelineEventType.CONNECTED,
            PullRequestTimelineEventType.LINKED,
            -> {
                ""
            }
        }
    EventText(text = text)
}

/** 交叉引用项：mention 到其他 issue/PR */
@Composable
private fun CrossReferenceItem(event: PullRequestTimelineItem.Event) {
    val actor = event.actor?.login.orEmpty()
    val text =
        event.sourceIssue?.let { stringResource(R.string.pull_request_event_cross_referenced_number, actor, it.number) }
            ?: stringResource(R.string.pull_request_event_cross_referenced, actor)
    EventText(text = text)
}

/** 关联 PR 项：connected/linked */
@Composable
private fun LinkedPrItem(event: PullRequestTimelineItem.Event) {
    val actor = event.actor?.login.orEmpty()
    val text =
        event.linkedPullRequest?.let { stringResource(R.string.pull_request_event_linked_number, actor, it.number) }
            ?: stringResource(R.string.pull_request_event_linked, actor)
    EventText(text = text)
}

/** 事件文本统一样式 */
@Composable
private fun EventText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Review 状态 → 本地化文案 */
@Composable
private fun reviewStateText(state: PullRequestReviewState): String =
    when (state) {
        PullRequestReviewState.APPROVED -> stringResource(R.string.pull_request_review_approved)
        PullRequestReviewState.CHANGES_REQUESTED -> stringResource(R.string.pull_request_review_changes_requested)
        PullRequestReviewState.COMMENTED -> stringResource(R.string.pull_request_review_commented)
        PullRequestReviewState.DISMISSED -> stringResource(R.string.pull_request_review_dismissed)
        PullRequestReviewState.UNKNOWN -> stringResource(R.string.pull_request_review_unknown)
    }

/** Review 状态 → 语义色（Approved=primary / Changes requested=error / 其余=onSurfaceVariant） */
@Composable
private fun reviewStateColor(state: PullRequestReviewState): androidx.compose.ui.graphics.Color =
    when (state) {
        PullRequestReviewState.APPROVED -> MaterialTheme.colorScheme.primary
        PullRequestReviewState.CHANGES_REQUESTED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

/** 时间线卡片容器（评论/Review/行内评论/提交引用共用） */
@Composable
private fun TimelineCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            content()
        }
    }
}

/** 时间线头部：头像 + 登录名 + 相对时间（可选尾部状态文本） */
@Composable
private fun TimelineHeader(
    avatarUrl: String?,
    login: String?,
    timestamp: String?,
    trailingText: String? = null,
    trailingColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = login,
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = login.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
            )
            timestamp?.let {
                Text(
                    text = relativeTimeText(it) ?: it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailingText != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelMedium,
                color = trailingColor,
            )
        }
    }
}

private const val SHORT_SHA_LENGTH = 7
