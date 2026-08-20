@file:Suppress("LongMethod") // 创建页表单装配（标题/正文/标签/提交/错误态）为单体流程，拆分反损可读性

package com.yumiru11.githubapp.feature.issue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumiru11.githubapp.feature.issue.model.IssueErrorType

/**
 * 创建 Issue 页（T14 验收 1：标题/正文/标签全流程）。
 *
 * 标题必填；正文与标签（逗号分隔）可选。提交后 [CreateIssueViewModel] 调 REST 创建，
 * 成功 emit Created → [onCreated] 返回列表页（下拉刷新可见新 Issue）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateIssueScreen(
    owner: String,
    repo: String,
    onBackClick: () -> Unit,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateIssueViewModel = hiltViewModel(),
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var labels by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                CreateIssueEvent.Created -> onCreated()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.issue_create_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.issue_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "$owner/$repo",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(text = stringResource(R.string.issue_create_title_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(text = stringResource(R.string.issue_create_body_label)) },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = labels,
                onValueChange = { labels = it },
                label = { Text(text = stringResource(R.string.issue_create_labels_label)) },
                supportingText = { Text(text = stringResource(R.string.issue_create_labels_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.createIssue(title, body, labels) },
                enabled = title.isNotBlank() && uiState != CreateIssueUiState.Submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.issue_create_submit))
            }
            if (uiState is CreateIssueUiState.Error) {
                Text(
                    text = stringResource((uiState as CreateIssueUiState.Error).errorType.toRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** [IssueErrorType] → 字符串资源 id（创建页错误文案） */
internal fun IssueErrorType.toRes(): Int =
    when (this) {
        IssueErrorType.NOT_FOUND -> R.string.issue_error_not_found
        IssueErrorType.NETWORK -> R.string.issue_error_network
        IssueErrorType.UNKNOWN -> R.string.issue_error_unknown
    }
