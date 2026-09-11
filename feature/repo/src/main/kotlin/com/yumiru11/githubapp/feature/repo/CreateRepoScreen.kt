@file:Suppress("LongMethod")
// 表单页装配（顶栏 + 四个字段 + 提交按钮 + 事件 Snackbar）结构固有，拆散反损可读性（FileEditScreen 先例）

package com.yumiru11.githubapp.feature.repo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumiru11.githubapp.core.designsystem.token.AppMotion
import com.yumiru11.githubapp.core.ui.AppSnackbarHost

/**
 * 新建仓库页（L04）。
 *
 * 表单：仓库名（纯函数实时校验）/ 描述 / 私有开关 / 初始化 README 开关。
 * 创建成功 → [onCreated] 携带新仓库 owner/name（宿主导航到仓库详情页）；
 * 失败 → [CreateRepoEvent] 经 Snackbar 呈现（UI 层 stringResource 映射）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRepoScreen(
    onBackClick: () -> Unit = {},
    onCreated: (owner: String, name: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: CreateRepoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message =
                when (event) {
                    CreateRepoEvent.NameTaken -> context.getString(R.string.repo_create_snackbar_name_taken)
                    CreateRepoEvent.PermissionDenied -> context.getString(R.string.repo_create_snackbar_permission_denied)
                    CreateRepoEvent.Failed -> context.getString(R.string.repo_create_snackbar_failed)
                }
            snackbarHostState.showSnackbar(message)
        }
    }

    // 创建成功：一次性导航（created 由 VM 置位，页面随后被弹出）
    LaunchedEffect(uiState.created) {
        uiState.created?.let { created -> onCreated(created.owner, created.name) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.repo_create_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.repo_create_back),
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
            NameField(
                value = uiState.name,
                error = uiState.nameError,
                onValueChange = viewModel::onNameChange,
            )

            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.repo_create_description)) },
                minLines = 3,
                maxLines = 5,
            )

            ToggleRow(
                title = stringResource(R.string.repo_create_private),
                description = stringResource(R.string.repo_create_private_hint),
                checked = uiState.isPrivate,
                onCheckedChange = viewModel::onPrivateChange,
            )

            ToggleRow(
                title = stringResource(R.string.repo_create_auto_init),
                description = stringResource(R.string.repo_create_auto_init_hint),
                checked = uiState.autoInit,
                onCheckedChange = viewModel::onAutoInitChange,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = viewModel::submit,
                enabled = uiState.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(
                    text =
                        stringResource(
                            if (uiState.isSubmitting) R.string.repo_create_submitting else R.string.repo_create_submit,
                        ),
                )
            }
        }
    }
}

/** 仓库名输入（校验错误经 AnimatedVisibility 淡入淡出，动效走 [AppMotion] + MotionScale）。 */
@Composable
private fun NameField(
    value: String,
    error: RepoNameError?,
    onValueChange: (String) -> Unit,
) {
    val duration = AppMotion.scaledDuration(AppMotion.DURATION_SMALL_STATE_CHANGE)
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.repo_create_name)) },
            placeholder = { Text(text = stringResource(R.string.repo_create_name_hint)) },
            isError = error != null,
            singleLine = true,
        )
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(animationSpec = tween(duration, easing = AppMotion.EmphasizedDecelerate)),
            exit = fadeOut(animationSpec = tween(duration, easing = AppMotion.EmphasizedAccelerate)),
        ) {
            Text(
                text = error?.let { stringResource(repoNameErrorRes(it)) }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}

/** 开关行（标题 + 说明 + Switch；M3 组件，无硬编码颜色）。 */
@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 校验错误 → 文案资源（ViewModel 只传类型，不产英文）。 */
internal fun repoNameErrorRes(error: RepoNameError): Int =
    when (error) {
        RepoNameError.EMPTY -> R.string.repo_name_error_empty
        RepoNameError.INVALID_CHARS -> R.string.repo_name_error_invalid_chars
        RepoNameError.ONLY_DOTS -> R.string.repo_name_error_only_dots
        RepoNameError.GIT_SUFFIX -> R.string.repo_name_error_git_suffix
        RepoNameError.TRAILING_UNDERSCORE -> R.string.repo_name_error_trailing_underscore
        RepoNameError.TOO_LONG -> R.string.repo_name_error_too_long
    }
