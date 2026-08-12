package com.yumiru11.githubapp.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * T4 登录页 UI 骨架（静态版，不含 OAuth 逻辑）。
 *
 * - 全部文案走 [stringResource]（en + zh-rCN，禁硬编码）
 * - 全部颜色走 [MaterialTheme.colorScheme]，零硬编码十六进制
 * - 回调驱动：onSignIn / onBrowseAsGuest / onSavePat，不自己导航
 * - 底部「开发者模式」可折叠展开项（PAT 输入 + 保存）
 */
@Composable
fun LoginScreen(
    onSignIn: () -> Unit = {},
    onBrowseAsGuest: () -> Unit = {},
    onSavePat: (String) -> Unit = {},
) {
    var developerModeExpanded by remember { mutableStateOf(false) }
    var patValue by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 品牌标题区
            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))

            // 主按钮：使用 GitHub 登录
            Button(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.login_github_button))
            }
            Spacer(modifier = Modifier.height(12.dp))

            // 副按钮：以游客身份浏览
            OutlinedButton(
                onClick = onBrowseAsGuest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.login_guest_button))
            }

            Spacer(modifier = Modifier.height(48.dp))

            // 底部「开发者模式」可折叠展开项
            DeveloperModeSection(
                expanded = developerModeExpanded,
                onToggle = { developerModeExpanded = !developerModeExpanded },
                patValue = patValue,
                onPatChange = { patValue = it },
                onSave = { onSavePat(patValue) },
            )
        }
    }
}

/**
 * 开发者模式折叠项：点击展开/收起 → PAT 输入框 + 保存按钮 + 说明文字。
 */
@Composable
private fun DeveloperModeSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    patValue: String,
    onPatChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 折叠标题行（点击展开/收起）
        IconButton(onClick = onToggle) {
            Icon(
                imageVector =
                    if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                contentDescription = stringResource(R.string.login_developer_mode),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.login_developer_mode),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 展开内容
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OutlinedTextField(
                    value = patValue,
                    onValueChange = onPatChange,
                    label = { Text(stringResource(R.string.login_pat_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.login_pat_save))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.login_pat_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
