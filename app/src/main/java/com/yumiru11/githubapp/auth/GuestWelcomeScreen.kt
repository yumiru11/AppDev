package com.yumiru11.githubapp.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.R

/**
 * 游客模式空状态 / 登录引导占位页（T2 首个 Roborazzi 截图基准的被测画面）。
 *
 * 为 T3/T4 打底的**最小实现**，不做过度设计：
 * - 全部文案走 [stringResource]（en + zh-rCN，禁硬编码）
 * - 全部颜色走 [MaterialTheme.colorScheme]，零硬编码十六进制
 * - 无 emoji 图标（空态仅文本 + 按钮）
 */
@Composable
fun GuestWelcomeScreen(
    onSignIn: () -> Unit = {},
    onBrowseAsGuest: () -> Unit = {},
) {
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
            Text(
                text = stringResource(R.string.guest_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.guest_welcome_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.guest_sign_in))
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onBrowseAsGuest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.guest_browse_as_guest))
            }
        }
    }
}
