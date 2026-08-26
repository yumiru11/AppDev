@file:Suppress("LongMethod", "CyclomaticComplexMethod")
// 屏幕装配（顶栏/列表/新建与删除对话框/Snackbar）结构固有，拆散反损可读性（FileEditScreen 先例）

package com.yumiru11.githubapp.feature.repo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Delete
import com.yumiru11.githubapp.feature.repo.R

/**
 * 分支管理页（T23，plan.md §7.5：列出/切换/创建/删除）。
 *
 * - 列表：默认分支排首 + 当前分支高亮；点击非当前分支 = 切换（宿主重载文件树并返回）
 * - 新建：顶栏按钮（仅可推送会话）；对话框输入分支名，基于默认分支创建
 * - 删除：行尾按钮（仅非默认、非受保护、可推送会话）；确认对话框防误删
 * - 全部文案 stringResource 本地化；错误 → Snackbar/错误态（ViewModel 不产文案）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchesScreen(
    owner: String,
    repo: String,
    currentRef: String?,
    onBackClick: () -> Unit,
    onBranchSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BranchesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message =
                when (event) {
                    is BranchEvent.Created -> context.getString(R.string.repo_branch_snackbar_created, event.name)
                    is BranchEvent.Deleted -> context.getString(R.string.repo_branch_snackbar_deleted, event.name)
                    is BranchEvent.Failed -> branchErrorText(context, event.errorType)
                }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                // owner/repo 路由契约参数在此展示（与 PR 列表页顶栏同风格）
                title = { Text(text = "$owner/$repo") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.repo_branch_back),
                        )
                    }
                },
                actions = {
                    if (uiState is BranchesUiState.Success && (uiState as BranchesUiState.Success).canPush) {
                        IconButton(
                            onClick = { showCreateDialog = true },
                            enabled = !(uiState as BranchesUiState.Success).isBusy,
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Add,
                                contentDescription = stringResource(R.string.repo_branch_new),
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when (val state = uiState) {
                is BranchesUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is BranchesUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = branchErrorText(context, state.errorType),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = viewModel::retry) {
                            Text(text = stringResource(R.string.repo_branch_retry))
                        }
                    }
                }

                is BranchesUiState.Success -> {
                    if (state.branches.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.repo_branch_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        BranchList(
                            branches = state.branches,
                            defaultBranch = state.defaultBranch,
                            currentRef = currentRef,
                            canPush = state.canPush,
                            isBusy = state.isBusy,
                            onBranchSelected = { name -> onBranchSelected(name) },
                            onDeleteClick = { name -> pendingDelete = name },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog && uiState is BranchesUiState.Success) {
        CreateBranchDialog(
            defaultBranch = (uiState as BranchesUiState.Success).defaultBranch,
            onConfirm = { name ->
                viewModel.createBranch(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(text = stringResource(R.string.repo_branch_delete)) },
            text = {
                Text(
                    text = stringResource(R.string.repo_branch_delete_confirm, name),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBranch(name)
                        pendingDelete = null
                    },
                ) {
                    Text(text = stringResource(R.string.repo_branch_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(text = stringResource(R.string.repo_branch_cancel))
                }
            },
        )
    }
}

@Composable
private fun BranchList(
    branches: List<Branch>,
    defaultBranch: String?,
    currentRef: String?,
    canPush: Boolean,
    isBusy: Boolean,
    onBranchSelected: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(branches, key = { it.name }) { branch ->
            val isCurrent = branch.name == currentRef
            val isDefault = branch.name == defaultBranch
            val canDelete = canPush && !isDefault && !branch.isProtected && !isBusy
            Card(
                onClick = { if (!isCurrent) onBranchSelected(branch.name) },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = branch.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        if (isDefault || branch.isProtected) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text =
                                    if (isDefault) {
                                        stringResource(R.string.repo_branch_default)
                                    } else {
                                        stringResource(R.string.repo_branch_protected)
                                    },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (isCurrent) {
                        Text(
                            text = stringResource(R.string.repo_branch_current),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (canDelete) {
                        IconButton(onClick = { onDeleteClick(branch.name) }) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Delete,
                                contentDescription = stringResource(R.string.repo_branch_delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateBranchDialog(
    defaultBranch: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val trimmed = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.repo_branch_new)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.repo_branch_name)) },
                    placeholder = { Text(text = stringResource(R.string.repo_branch_name_hint)) },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.repo_branch_from_base, defaultBranch ?: "main"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotBlank() && !trimmed.contains(' '),
            ) {
                Text(text = stringResource(R.string.repo_branch_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.repo_branch_cancel))
            }
        },
    )
}

/** 分支错误类型 → 本地化文案（非 Composable：事件收集场景不可用 stringResource）。 */
private fun branchErrorText(
    context: android.content.Context,
    errorType: RepoErrorType,
): String =
    when (errorType) {
        RepoErrorType.FORBIDDEN -> context.getString(R.string.repo_branch_error_forbidden)
        RepoErrorType.NOT_FOUND -> context.getString(R.string.repo_branch_error_not_found)
        RepoErrorType.NETWORK -> context.getString(R.string.repo_branch_error_network)
        RepoErrorType.UNKNOWN -> context.getString(R.string.repo_branch_error_unknown)
    }
