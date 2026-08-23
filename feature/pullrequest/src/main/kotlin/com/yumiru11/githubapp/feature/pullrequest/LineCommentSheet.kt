@file:Suppress("LongMethod") // 行评论 sheet 装配（标题+会话卡+解析入口+输入区+按钮）聚合展示逻辑，拆分反损可读性（PullRequestTabContent 同款先例）

package com.yumiru11.githubapp.feature.pullrequest

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yumiru11.githubapp.core.markdown.MarkdownViewer
import com.yumiru11.githubapp.core.ui.time.relativeTimeText
import com.yumiru11.githubapp.feature.pullrequest.model.LineCommentAnchor
import com.yumiru11.githubapp.feature.pullrequest.model.LineCommentTarget
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewComment
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewThread

/** 行内评论 BottomSheet（T16）：新增/回复评论 + 会话解析/解除（按权限） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LineCommentSheet(
    target: LineCommentTarget,
    canResolve: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (LineCommentAnchor, String, Long?) -> Unit,
    onToggleResolve: (ReviewThread) -> Unit,
    modifier: Modifier = Modifier,
) {
    var commentText by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.pull_request_review_comment_at, target.anchor.path, target.anchor.line),
                style = MaterialTheme.typography.titleMedium,
            )
            if (target.comments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                target.comments.forEach { comment ->
                    LineCommentCard(comment = comment)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (canResolve && target.thread != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                    ) {
                        TextButton(onClick = { onToggleResolve(target.thread) }) {
                            Text(
                                text =
                                    stringResource(
                                        if (target.thread.isResolved) {
                                            R.string.pull_request_line_comment_unresolve
                                        } else {
                                            R.string.pull_request_line_comment_resolve
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                label = {
                    Text(
                        text =
                            stringResource(
                                if (target.thread != null) {
                                    R.string.pull_request_line_comment_reply
                                } else {
                                    R.string.pull_request_line_comment_new
                                },
                            ),
                    )
                },
                placeholder = { Text(text = stringResource(R.string.pull_request_line_comment_placeholder)) },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onSubmit(target.anchor, commentText, target.comments.firstOrNull()?.id) },
                    enabled = commentText.isNotBlank(),
                ) {
                    Text(text = stringResource(R.string.submit))
                }
            }
        }
    }
}

/** 会话内单条评论卡：作者 + 相对时间 + Markdown 原生正文 */
@Composable
private fun LineCommentCard(
    comment: ReviewComment,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                comment.author?.let { author ->
                    AsyncImage(
                        model = author.avatarUrl,
                        contentDescription = author.login,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = comment.author?.login.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                comment.createdAt?.let { createdAt ->
                    Text(
                        text = relativeTimeText(createdAt) ?: createdAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            MarkdownViewer(
                markdown = comment.body.orEmpty(),
                baseRepoUrl = null,
                scrollable = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
