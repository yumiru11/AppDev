package com.yumiru11.githubapp.feature.pullrequest

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yumiru11.githubapp.core.designsystem.component.AppErrorState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestErrorType

/**
 * 错误态：错误类型文案 + 重试。
 *
 * 设计系统 Batch 2：收敛到共享 [AppErrorState]（Alert 插图 + 标题 + 按钮），
 * 不再手搓 Box + 原文色文案 + 裸按钮；文案仍由本处按错误类型本地化。
 */
@Composable
internal fun PullRequestErrorContent(
    errorType: PullRequestErrorType,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppErrorState(
        title = pullRequestErrorMessage(errorType),
        actionLabel = stringResource(R.string.pull_request_retry),
        onAction = onRetry,
        modifier = modifier,
    )
}

/** 错误类型 → 本地化文案（ViewModel 只产类型，不产英文） */
@Composable
internal fun pullRequestErrorMessage(errorType: PullRequestErrorType): String =
    when (errorType) {
        PullRequestErrorType.NOT_FOUND -> stringResource(R.string.pull_request_error_not_found)
        PullRequestErrorType.NETWORK -> stringResource(R.string.pull_request_error_network)
        PullRequestErrorType.UNKNOWN -> stringResource(R.string.pull_request_error_unknown)
    }
