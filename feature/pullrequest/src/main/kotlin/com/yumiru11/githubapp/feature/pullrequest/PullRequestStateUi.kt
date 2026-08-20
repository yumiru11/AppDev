package com.yumiru11.githubapp.feature.pullrequest

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
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestErrorType

/** 加载中：居中圆形进度条 */
@Composable
internal fun PullRequestLoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** 错误态：错误文案 + 重试按钮 */
@Composable
internal fun PullRequestErrorContent(
    errorType: PullRequestErrorType,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = pullRequestErrorMessage(errorType),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.pull_request_retry))
            }
        }
    }
}

/** 错误类型 → 本地化文案（ViewModel 只产类型，不产英文） */
@Composable
internal fun pullRequestErrorMessage(errorType: PullRequestErrorType): String =
    when (errorType) {
        PullRequestErrorType.NOT_FOUND -> stringResource(R.string.pull_request_error_not_found)
        PullRequestErrorType.NETWORK -> stringResource(R.string.pull_request_error_network)
        PullRequestErrorType.UNKNOWN -> stringResource(R.string.pull_request_error_unknown)
    }
