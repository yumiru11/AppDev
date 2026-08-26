package com.yumiru11.githubapp.feature.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.component.AppEmptyState
import com.yumiru11.githubapp.core.designsystem.component.AppErrorState
import com.yumiru11.githubapp.core.designsystem.component.AppLoadingState
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons
import com.yumiru11.githubapp.feature.home.R
import com.yumiru11.githubapp.feature.home.RepoPickerUiState
import com.yumiru11.githubapp.feature.home.model.RepoOption

/**
 * 仓库选择器 BottomSheet（#89）：长条按钮 → 选仓库 → 携 {owner}/{repo} 路由。
 *
 * 内容拆出 [RepoPickerSheetContent] 直测（Robolectric 下不依赖弹窗窗口）；
 * 三态复用 #84 共享组件族。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RepoPickerSheet(
    visible: Boolean,
    uiState: RepoPickerUiState,
    onPick: (owner: String, repo: String) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    if (!visible) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        RepoPickerSheetContent(
            uiState = uiState,
            onPick = onPick,
            onRetry = onRetry,
        )
    }
}

/** 选择器内容区：标题 + Loading/Error/Empty/列表四分支。 */
@Composable
internal fun RepoPickerSheetContent(
    uiState: RepoPickerUiState,
    onPick: (owner: String, repo: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            text = stringResource(R.string.repo_picker_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        when (uiState) {
            is RepoPickerUiState.Loading -> {
                AppLoadingState(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                )
            }

            is RepoPickerUiState.Error -> {
                AppErrorState(
                    title = stringResource(R.string.repo_picker_error),
                    actionLabel = stringResource(R.string.repo_picker_retry),
                    onAction = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is RepoPickerUiState.Ready -> {
                if (uiState.repos.isEmpty()) {
                    AppEmptyState(
                        icon = AppDevOcticons.Repo,
                        title = stringResource(R.string.repo_picker_empty),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp),
                    ) {
                        items(items = uiState.repos, key = { it.fullName }) { repo ->
                            RepoPickerRow(
                                repo = repo,
                                onClick = { onPick(repo.owner, repo.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoPickerRow(
    repo: RepoOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AppDevOcticons.Repo,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = repo.fullName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
            if (!repo.description.isNullOrBlank()) {
                Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
