package com.yumiru11.githubapp.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.yumiru11.githubapp.core.designsystem.token.LocalAppSurfaces
import com.yumiru11.githubapp.core.designsystem.token.resolveAppSurfaces

/**
 * 把「背景图/OLED/高对比」状态解析成容器色并经 [LocalAppSurfaces] 下发。
 *
 * **必须放在 [AppTheme] 之内**：解析要用到当前 [MaterialTheme.colorScheme]（动态取色/seed
 * 主题切换后容器色要跟着变）。调用链见 app 层 AppThemeHost。
 *
 * @param backgroundImageEnabled 是否设置了全局背景图（非空 URI 即视为开启）
 * @param oled OLED 纯黑模式（强制不透明）
 * @param highContrast 高对比模式（强制不透明）
 */
@Composable
fun ProvideAppSurfaces(
    backgroundImageEnabled: Boolean,
    oled: Boolean,
    highContrast: Boolean,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces =
        remember(scheme, backgroundImageEnabled, oled, highContrast) {
            resolveAppSurfaces(
                colorScheme = scheme,
                backgroundImageEnabled = backgroundImageEnabled,
                oled = oled,
                highContrast = highContrast,
            )
        }
    CompositionLocalProvider(LocalAppSurfaces provides surfaces, content = content)
}
