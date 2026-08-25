@file:Suppress("LongMethod", "MatchingDeclarationName")
// - LongMethod：MergeBox 卡片装配（主按钮+方法下拉+标题/备注+删除分支+Update branch）聚合展示逻辑，拆分反损可读性（LineCommentSheet 同款先例）
// - MatchingDeclarationName：文件按组件族命名（MergeBox 卡片 + ConversationActions 数据类 + Review 入口），多顶层声明同文件，拆分反损可读性（T3 先例）

package com.yumiru11.githubapp.feature.pullrequest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.feature.pullrequest.model.MergeableState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestMergeMethod
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestWriteAction

/**
 * 对话页动作区状态/回调聚合（T17；显隐开关由 ViewModel 计算为布尔位，UI 只消费）。
 *
 * 可见性规则：
 * - 打开态：canReview → Review 入口；canMerge → MergeBox
 * - 已合并态：canDeleteHeadBranch → 删除分支入口
 * - canMerge/canDeleteHeadBranch 已含 WRITE + 同仓库 + 非默认分支约束（ViewModel 计算）
 */
internal data class ConversationActions(
    val state: PullRequestState,
    val mergeableState: MergeableState,
    val canReview: Boolean,
    val canMerge: Boolean,
    val canDeleteHeadBranch: Boolean,
    val headSameRepo: Boolean,
    val pendingAction: PullRequestWriteAction? = null,
    val prTitle: String = "",
    val onOpenReview: () -> Unit = {},
    val onMerge: (PullRequestMergeMethod, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    val onUpdateBranch: () -> Unit = {},
    val onDeleteBranch: () -> Unit = {},
) {
    /** 是否有任何可见动作（避免空 item 占位） */
    val hasVisibleContent: Boolean
        get() =
            (state == PullRequestState.OPEN && (canReview || canMerge)) ||
                (state == PullRequestState.MERGED && canDeleteHeadBranch)
}

/** 对话页动作区：Review 入口 + MergeBox（打开态）/ 删除分支（已合并态） */
@Composable
internal fun PullRequestActionItems(
    actions: ConversationActions,
    modifier: Modifier = Modifier,
) {
    if (!actions.hasVisibleContent) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (actions.state == PullRequestState.OPEN) {
            if (actions.canReview) {
                ReviewEntryRow(onOpenReview = actions.onOpenReview)
            }
            if (actions.canMerge) {
                MergeBoxCard(actions = actions)
            }
        } else if (actions.state == PullRequestState.MERGED && actions.canDeleteHeadBranch) {
            MergedBranchActionsRow(actions = actions)
        }
    }
}

/** MergeBox：合并方法 SplitButton（主按钮 + 方法下拉）+ 标题/备注 + 删除分支 + Update branch */
@Composable
private fun MergeBoxCard(
    actions: ConversationActions,
    modifier: Modifier = Modifier,
) {
    var method by rememberSaveable { mutableStateOf(PullRequestMergeMethod.MERGE) }
    var commitTitle by rememberSaveable(actions.prTitle) { mutableStateOf(actions.prTitle) }
    var commitMessage by rememberSaveable { mutableStateOf("") }
    var deleteBranch by rememberSaveable { mutableStateOf(false) }
    var methodMenuExpanded by remember { mutableStateOf(false) }
    val busy = actions.pendingAction != null
    val mergeable = actions.mergeableState == MergeableState.MERGEABLE
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.pull_request_merge_box_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { actions.onMerge(method, commitTitle, commitMessage, deleteBranch) },
                    enabled = mergeable && !busy && commitTitle.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.pull_request_merge_box_title))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box {
                    FilledTonalIconButton(
                        onClick = { methodMenuExpanded = true },
                        enabled = !busy,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.pull_request_merge_select_method),
                        )
                    }
                    DropdownMenu(expanded = methodMenuExpanded, onDismissRequest = { methodMenuExpanded = false }) {
                        PullRequestMergeMethod.entries.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(text = mergeMethodLabel(candidate)) },
                                onClick = {
                                    method = candidate
                                    methodMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            when {
                actions.mergeableState == MergeableState.CONFLICTING -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.pull_request_merge_conflict_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                !mergeable -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.pull_request_merge_checking_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = commitTitle,
                onValueChange = { commitTitle = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.pull_request_merge_commit_title)) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = commitMessage,
                onValueChange = { commitMessage = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.pull_request_merge_commit_message)) },
            )
            if (actions.canDeleteHeadBranch) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = deleteBranch,
                        onCheckedChange = { deleteBranch = it },
                    )
                    Text(text = stringResource(R.string.pull_request_merge_delete_branch))
                }
            }
            if (actions.headSameRepo) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = actions.onUpdateBranch,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (actions.pendingAction == PullRequestWriteAction.UPDATE_BRANCH) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text = stringResource(R.string.pull_request_merge_update_branch))
                }
            }
        }
    }
}

/** 已合并态操作行：删除 head 分支（同仓库 + 非默认分支 + WRITE，见 [ConversationActions.canDeleteHeadBranch]） */
@Composable
private fun MergedBranchActionsRow(
    actions: ConversationActions,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedButton(
            onClick = actions.onDeleteBranch,
            enabled = actions.pendingAction == null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
        ) {
            if (actions.pendingAction == PullRequestWriteAction.DELETE_BRANCH) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = stringResource(R.string.pull_request_delete_branch))
        }
    }
}

/** 合并方法 → 本地化文案 */
@Composable
private fun mergeMethodLabel(method: PullRequestMergeMethod): String =
    when (method) {
        PullRequestMergeMethod.MERGE -> stringResource(R.string.pull_request_merge_method_merge)
        PullRequestMergeMethod.SQUASH -> stringResource(R.string.pull_request_merge_method_squash)
        PullRequestMergeMethod.REBASE -> stringResource(R.string.pull_request_merge_method_rebase)
    }
