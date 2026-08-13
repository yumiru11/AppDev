package com.yumiru11.githubapp.feature.issue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.feature.issue.model.IssueErrorType

/** 加载中：居中圆形进度条 */
@Composable
internal fun IssueLoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** 错误态：错误文案 + 重试按钮 */
@Composable
internal fun IssueErrorContent(
    errorType: IssueErrorType,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = issueErrorMessage(errorType),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.issue_retry))
            }
        }
    }
}

/** 错误类型 → 本地化文案（ViewModel 只产类型，不产英文） */
@Composable
internal fun issueErrorMessage(errorType: IssueErrorType): String =
    when (errorType) {
        IssueErrorType.NOT_FOUND -> stringResource(R.string.issue_error_not_found)
        IssueErrorType.NETWORK -> stringResource(R.string.issue_error_network)
        IssueErrorType.UNKNOWN -> stringResource(R.string.issue_error_unknown)
    }
