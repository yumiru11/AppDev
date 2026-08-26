@file:Suppress("LongMethod", "CyclomaticComplexMethod")
// 表单屏幕装配（顶栏/输入/分支选择/错误态）结构固有，拆散反损可读性（MergeBox/FileEditScreen 先例）

package com.yumiru11.githubapp.feature.pullrequest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumiru11.githubapp.feature.pullrequest.R
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestErrorType

/**
 * 创建 PR 页（T23：标题/描述 + base/head 分支选择，REST 写优先）。
 *
 * - 顶栏「Create」按钮：标题非空、head != base、有推送权限、非提交中才可用
 * - base 默认默认分支；head 默认第一个非 base 分支（仓库仅一个分支时无法创建）
 * - 创建成功 → [onCreated]（宿主导航打开新 PR 详情）；失败 → Snackbar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRequestCreateScreen(
    owner: String,
    repo: String,
    onBackClick: () -> Unit,
    onCreated: (owner: String, repo: String, number: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PullRequestCreateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PullRequestCreateEvent.Created -> onCreated(owner, repo, event.number)
                is PullRequestCreateEvent.Failed -> snackbarHostState.showSnackbar(createPrErrorText(context, event.errorType))
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.pull_request_create_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.pull_request_back),
                        )
                    }
                },
                actions = {
                    val form = uiState as? PullRequestCreateUiState.Form
                    val canSubmit =
                        form != null &&
                            form.canCreate &&
                            !form.isSubmitting &&
                            form.title.isNotBlank() &&
                            form.headBranch.isNotBlank() &&
                            form.headBranch != form.baseBranch
                    TextButton(onClick = viewModel::submit, enabled = canSubmit) {
                        Text(text = stringResource(R.string.pull_request_create_submit))
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
                is PullRequestCreateUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is PullRequestCreateUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = createPrErrorText(context, state.errorType),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = viewModel::retry) {
                            Text(text = stringResource(R.string.pull_request_retry))
                        }
                    }
                }

                is PullRequestCreateUiState.Form -> {
                    CreatePullRequestForm(
                        state = state,
                        onTitleChange = viewModel::updateTitle,
                        onBodyChange = viewModel::updateBody,
                        onBaseChange = viewModel::selectBase,
                        onHeadChange = viewModel::selectHead,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatePullRequestForm(
    state: PullRequestCreateUiState.Form,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onBaseChange: (String) -> Unit,
    onHeadChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.pull_request_create_title_label)) },
            singleLine = true,
            enabled = !state.isSubmitting,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = state.body,
            onValueChange = onBodyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.pull_request_create_body_label)) },
            minLines = 5,
            enabled = !state.isSubmitting,
        )
        Spacer(modifier = Modifier.height(16.dp))
        BranchDropdown(
            label = stringResource(R.string.pull_request_create_base),
            branches = state.branches,
            selected = state.baseBranch,
            onSelect = onBaseChange,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        BranchDropdown(
            label = stringResource(R.string.pull_request_create_head),
            branches = state.branches,
            selected = state.headBranch,
            onSelect = onHeadChange,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.headBranch.isNotBlank() && state.headBranch == state.baseBranch) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.pull_request_create_same_branch_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (!state.canCreate) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.pull_request_create_no_permission_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 分支选择下拉（MergeBox 同款 Box + OutlinedButton + DropdownMenu 模式）。 */
@Composable
private fun BranchDropdown(
    label: String,
    branches: List<RepositoryBranch>,
    selected: String,
    onSelect: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled && branches.isNotEmpty() && selected.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = selected.ifBlank { stringResource(R.string.pull_request_create_select_branch) },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = { Text(text = branch.name) },
                    onClick = {
                        onSelect(branch.name)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** 创建 PR 错误类型 → 本地化文案（非 Composable：事件收集场景不可用 stringResource）。 */
private fun createPrErrorText(
    context: android.content.Context,
    errorType: PullRequestErrorType,
): String =
    when (errorType) {
        PullRequestErrorType.NOT_FOUND -> context.getString(R.string.pull_request_error_not_found)
        PullRequestErrorType.NETWORK -> context.getString(R.string.pull_request_error_network)
        PullRequestErrorType.UNKNOWN -> context.getString(R.string.pull_request_error_unknown)
    }
