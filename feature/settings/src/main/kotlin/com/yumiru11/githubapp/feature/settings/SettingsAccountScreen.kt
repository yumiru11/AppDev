package com.yumiru11.githubapp.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumiru11.githubapp.core.designsystem.component.AppSettingsGroup
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubrest.http.RateLimitSnapshot

/**
 * 账号与开发者（**L3 叶子页**）：登录方式 / 个人访问令牌（PAT）/ REST-only 提示 / 剩余配额。
 *
 * PAT 保存是**唯一需要键盘输入的设置项**：默认收起成一行（尾部显示「已配置 / 未配置」），
 * 点开才展开输入框 + 明文开关 + 保存按钮——不把一行输入框常驻在列表里。
 */
@Composable
fun SettingsAccountScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var patExpanded by remember { mutableStateOf(false) }
    val isPat = uiState.authState is AuthState.PAT

    SettingsPageScaffold(
        title = stringResource(R.string.settings_account_entry),
        onBackClick = onBackClick,
    ) {
        item {
            AppSettingsGroup(title = stringResource(R.string.settings_account_group)) {
                item(
                    headlineContent = { Text(stringResource(R.string.settings_account_state)) },
                    supportingContent = { Text(accountLabel(uiState.authState)) },
                )
                item(
                    onClick = { patExpanded = !patExpanded },
                    headlineContent = { Text(stringResource(R.string.settings_pat)) },
                    supportingContent = {
                        Text(
                            if (isPat) {
                                stringResource(R.string.settings_pat_configured)
                            } else {
                                stringResource(R.string.settings_pat_not_configured)
                            },
                        )
                    },
                )
                if (patExpanded) {
                    item(
                        headlineContent = {
                            PatEditor(onSave = { pat -> viewModel.savePat(pat) })
                        },
                    )
                }
                if (isPat) {
                    item(
                        headlineContent = { Text(stringResource(R.string.settings_rest_only_notice)) },
                    )
                }
            }
        }
        item {
            AppSettingsGroup(title = stringResource(R.string.settings_group_quota)) {
                item(
                    headlineContent = { Text(stringResource(R.string.settings_rate_limit)) },
                    supportingContent = { RateLimitSupporting(rateLimit = uiState.rateLimit) },
                )
            }
        }
    }
}

/** 登录方式文案（不暴露 token 本身）。 */
@Composable
private fun accountLabel(state: AuthState): String =
    when (state) {
        is AuthState.PAT -> stringResource(R.string.settings_account_pat)
        is AuthState.Anonymous -> stringResource(R.string.settings_account_anonymous)
        else -> stringResource(R.string.settings_account_signed_in)
    }

/** PAT 输入（展开态）：密码框 + 明文开关 + 保存。 */
@Composable
private fun PatEditor(onSave: (String) -> Unit) {
    var patValue by remember { mutableStateOf("") }
    var showPat by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppDimens.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spacing.s),
    ) {
        OutlinedTextField(
            value = patValue,
            onValueChange = { patValue = it },
            label = { Text(stringResource(R.string.settings_pat_hint)) },
            visualTransformation =
                if (showPat) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            trailingIcon = {
                TextButton(onClick = { showPat = !showPat }) {
                    Text(
                        text =
                            if (showPat) {
                                stringResource(R.string.settings_pat_hide)
                            } else {
                                stringResource(R.string.settings_pat_show)
                            },
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSave(patValue) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.settings_pat_save))
        }
        Text(
            text = stringResource(R.string.settings_pat_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 配额：有快照 = 进度条 + 剩余/上限 + 重置时间；无快照 = 空态占位（非错误）。 */
@Composable
private fun RateLimitSupporting(rateLimit: RateLimitSnapshot?) {
    if (rateLimit == null) {
        Text(stringResource(R.string.settings_rate_limit_no_data))
        return
    }
    val fraction = if (rateLimit.limit > 0) rateLimit.remaining.toFloat() / rateLimit.limit else 0f

    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spacing.xs)) {
        Text(stringResource(R.string.settings_rate_limit_usage, rateLimit.remaining, rateLimit.limit))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.settings_rate_limit_reset, rateLimit.resetInMinutes()))
    }
}
