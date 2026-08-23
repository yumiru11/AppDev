@file:Suppress("LongMethod") // Review sheet 装配（结论选择 + 正文 + 按钮）聚合展示逻辑，拆分反损可读性（LineCommentSheet 同款先例）

package com.yumiru11.githubapp.feature.pullrequest

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewConclusion

/** 对话页「Review changes」入口行（T17）：打开审查 BottomSheet；READ 及以上可见 */
@Composable
internal fun ReviewEntryRow(
    onOpenReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Button(
            onClick = onOpenReview,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
        ) {
            Text(text = stringResource(R.string.pull_request_review_changes))
        }
    }
}

/**
 * Review 提交 BottomSheet（T17）：三种结论 + 正文。
 *
 * 结论权限：APPROVE / REQUEST_CHANGES 需 WRITE（chip 禁用）；COMMENT 需正文非空才能提交。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewSheet(
    canApprove: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (ReviewConclusion, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var conclusion by remember { mutableStateOf<ReviewConclusion?>(null) }
    var body by remember { mutableStateOf("") }
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
                text = stringResource(R.string.pull_request_review_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                FilterChip(
                    selected = conclusion == ReviewConclusion.APPROVE,
                    onClick = { conclusion = ReviewConclusion.APPROVE },
                    enabled = canApprove,
                    label = { Text(text = stringResource(R.string.pull_request_review_approve)) },
                )
                FilterChip(
                    selected = conclusion == ReviewConclusion.REQUEST_CHANGES,
                    onClick = { conclusion = ReviewConclusion.REQUEST_CHANGES },
                    enabled = canApprove,
                    label = { Text(text = stringResource(R.string.pull_request_review_request_changes)) },
                )
                FilterChip(
                    selected = conclusion == ReviewConclusion.COMMENT,
                    onClick = { conclusion = ReviewConclusion.COMMENT },
                    label = { Text(text = stringResource(R.string.pull_request_review_comment_option)) },
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                label = { Text(text = stringResource(R.string.pull_request_review_body_label)) },
                placeholder = { Text(text = stringResource(R.string.pull_request_comment_placeholder)) },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { conclusion?.let { onSubmit(it, body) } },
                    enabled = conclusion != null && (conclusion != ReviewConclusion.COMMENT || body.isNotBlank()),
                ) {
                    Text(text = stringResource(R.string.submit))
                }
            }
        }
    }
}
