package com.yumiru11.githubapp.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.component.CardGroup
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.githubauth.auth.AuthState

/**
 * 开发者分组（ui-design §3.6，#87 分组卡化）：PAT 输入（折叠项，明文开关）、REST-only
 * 降级提示（PAT 态展示）、剩余配额占位（待 API 接线）。
 */
@Composable
internal fun DeveloperSettingsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    var expanded by remember { mutableStateOf(false) }

    CardGroup {
        item {
            PatEntrySection(
                expanded = expanded,
                onToggle = { expanded = !expanded },
                onSave = viewModel::savePat,
            )
        }
        if (uiState.authState is AuthState.PAT) {
            item { RestOnlyNotice() }
        }
        item { RateLimitRow() }
    }
}

/** PAT 输入折叠项：点击展开/收起 → 输入框（明文开关）+ 保存按钮 + 说明文字。 */
@Composable
private fun PatEntrySection(
    expanded: Boolean,
    onToggle: () -> Unit,
    onSave: (String) -> Unit,
) {
    var patValue by remember { mutableStateOf("") }
    var showPat by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_pat),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggle) {
            Icon(
                imageVector =
                    if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                contentDescription = stringResource(R.string.settings_pat),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    AnimatedVisibility(visible = expanded) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onSave(patValue) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.settings_pat_save))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_pat_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** REST-only 降级提示卡（PAT 态展示，ADR-0003）。 */
@Composable
private fun RestOnlyNotice() {
    Surface(
        shape = RoundedCornerShape(AppDimens.cornerMedium),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_rest_only_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/** 剩余配额占位行（待 REST 通道配额 API 接线）。 */
@Composable
private fun RateLimitRow() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_rate_limit),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.settings_rate_limit_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
