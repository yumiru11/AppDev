@file:Suppress("LongMethod", "LongParameterList")
// 表单页装配（顶栏 + 六个字段 + 提交 + 事件 Snackbar）结构固有；各字段参数天然多（FileEditScreen 先例）

package com.yumiru11.githubapp.feature.repo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumiru11.githubapp.core.ui.AppSnackbarHost

/**
 * 新建 Release 表单页（L05）。
 *
 * 字段：Tag（必填）/ 目标分支 / 标题 / 说明 / 草稿开关 / 预发布开关。
 * 成功后 [onCreated] 由宿主导航回仓库详情（重进触发 Releases 刷新）；失败经 Snackbar 呈现。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseCreateScreen(
    onBackClick: () -> Unit = {},
    onCreated: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ReleaseCreateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message =
                when (event) {
                    ReleaseCreateEvent.ValidationFailed -> resources.getString(R.string.repo_release_snackbar_validation)
                    ReleaseCreateEvent.PermissionDenied -> resources.getString(R.string.repo_release_snackbar_forbidden)
                    ReleaseCreateEvent.Failed -> resources.getString(R.string.repo_release_snackbar_failed)
                }
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(uiState.created) {
        if (uiState.created != null) onCreated()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.repo_release_create_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.repo_release_create_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.tagName,
                onValueChange = viewModel::onTagChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.repo_release_tag)) },
                placeholder = { Text(text = stringResource(R.string.repo_release_tag_hint)) },
                isError = uiState.tagError,
                supportingText =
                    if (uiState.tagError) {
                        { Text(text = stringResource(R.string.repo_release_tag_error)) }
                    } else {
                        null
                    },
                singleLine = true,
            )

            OutlinedTextField(
                value = uiState.targetCommitish,
                onValueChange = viewModel::onTargetChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.repo_release_target)) },
                placeholder = { Text(text = stringResource(R.string.repo_release_target_hint)) },
                singleLine = true,
            )

            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.repo_release_name)) },
                singleLine = true,
            )

            OutlinedTextField(
                value = uiState.body,
                onValueChange = viewModel::onBodyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.repo_release_body)) },
                minLines = 4,
                maxLines = 8,
            )

            SwitchRow(
                title = stringResource(R.string.repo_release_form_draft),
                checked = uiState.draft,
                onCheckedChange = viewModel::onDraftChange,
            )

            SwitchRow(
                title = stringResource(R.string.repo_release_form_prerelease),
                checked = uiState.prerelease,
                onCheckedChange = viewModel::onPrereleaseChange,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = viewModel::submit,
                enabled = uiState.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(
                    text =
                        stringResource(
                            if (uiState.isSubmitting) {
                                R.string.repo_release_create_submitting
                            } else {
                                R.string.repo_release_create_submit
                            },
                        ),
                )
            }
        }
    }
}

/** 开关行（标题 + Switch）。 */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
