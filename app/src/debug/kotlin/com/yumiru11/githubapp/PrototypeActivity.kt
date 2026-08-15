/*
 * PROTOTYPE ONLY (debug variant) — 真机 A/B 对照入口。
 * 仅在 prototype/readme-comparison 分支存在；拍板后随分支废弃，永不进 release。
 */
package com.yumiru11.githubapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.prototype.readmecomparison.ReadmeComparisonScreen

class PrototypeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // AppTheme：跟随系统深浅色 + 提供 ExtendedColors（GitHubAlertCard 五色语义，
            // 裸 MaterialTheme 无 Provider 时取默认浅色 → 深色下 alert 不变，2026-08-16 验证）。
            AppTheme(themeMode = com.yumiru11.githubapp.core.datastore.model.ThemeMode.SYSTEM) {
                PrototypeBackground()
            }
        }
    }
}

/** 深色背景 = 近黑 + 6% 主题色（用户拍板：两边背景发灰，要黑色融入一点主题色）。 */
@Composable
private fun PrototypeBackground() {
    val scheme = MaterialTheme.colorScheme
    val bg =
        if (isSystemInDarkTheme()) {
            lerp(Color(0xFF0B0B0D), scheme.primary, 0.06f)
        } else {
            scheme.surface
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(bg),
    ) {
        ReadmeComparisonScreen(modifier = Modifier.fillMaxSize())
    }
}
