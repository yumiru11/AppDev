package com.yumiru11.githubapp.core.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.textmate.theme.Theme
import dev.textmate.theme.ThemeReader
import java.io.ByteArrayInputStream

/**
 * GitHub 半融合 TextMate 主题（C 方案）：
 * - 语法 token 颜色直接使用 VS Code Dark+ / Light+ 原始值（GitHub 原色系）
 * - `editor.background` 强制透明，代码块容器背景由 M3 surface 决定
 */
object GitHubTextMateTheme {
    const val DARK_TOKEN_ASSET = "themes/dark_plus.json"
    const val LIGHT_TOKEN_ASSET = "themes/light_plus.json"
    private const val DARK_FOREGROUND = "#D4D4D4"
    private const val LIGHT_FOREGROUND = "#000000"

    /** 纯函数：生成透明背景 + VS Code 默认前景色的主题颜色覆盖 JSON。 */
    fun buildColorsJson(isDark: Boolean): String {
        val foreground = if (isDark) DARK_FOREGROUND else LIGHT_FOREGROUND
        return """
            {
              "name": "GitHub transparent",
              "colors": {
                "editor.foreground": "$foreground",
                "editor.background": "#00000000"
              }
            }
            """.trimIndent()
    }

    @Composable
    fun rememberGitHubTextMateTheme(isDark: Boolean): Theme {
        val context = LocalContext.current
        val scheme = MaterialTheme.colorScheme
        return remember(context, isDark) {
            try {
                val tokenAsset = if (isDark) DARK_TOKEN_ASSET else LIGHT_TOKEN_ASSET
                context.assets.open(tokenAsset).use { tokenStream ->
                    ByteArrayInputStream(buildColorsJson(isDark).toByteArray(Charsets.UTF_8)).use { colorsStream ->
                        ThemeReader.readTheme(tokenStream, colorsStream)
                    }
                }
            } catch (_: Exception) {
                // 资产损坏/缺失时退回 M3 派生主题，代码块仍可读（与 TextMateCodeBlock 兜底策略一致）。
                buildM3Theme(name = if (isDark) "M3 Dark fallback" else "M3 Light fallback", scheme = scheme)
            }
        }
    }
}
