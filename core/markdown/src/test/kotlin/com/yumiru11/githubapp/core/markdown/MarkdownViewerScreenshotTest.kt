package com.yumiru11.githubapp.core.markdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * MarkdownViewer 截图基准测试（Robolectric Native Graphics，纯 JVM 免模拟器）。
 *
 * 基准 PNG 输出到 `core/markdown/src/test/screenshots/`（入库；build/ 不进版本库）。
 * record：`./gradlew :core:markdown:recordRoborazziDebug`
 * verify：`./gradlew :core:markdown:verifyRoborazziDebug`
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MarkdownViewerScreenshotTest : ScreenshotTest() {

    /** 浅色主题：标题 + 代码块 + 引用 */
    @Test
    fun markdownViewer_lightTheme_withHeadingsCodeBlockQuote_matchesBaseline() {
        captureScreenshot("MarkdownViewer_light", darkTheme = false) {
            MarkdownViewer(
                markdown = SAMPLE_MARKDOWN,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }

    /** 深色主题：标题 + 代码块 + 引用 */
    @Test
    @Config(qualifiers = "night")
    fun markdownViewer_darkTheme_withHeadingsCodeBlockQuote_matchesBaseline() {
        captureScreenshot("MarkdownViewer_dark", darkTheme = true) {
            MarkdownViewer(
                markdown = SAMPLE_MARKDOWN,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }

    /** 浅色主题：GitHub Alert（NOTE） */
    @Test
    fun markdownViewer_lightTheme_withGitHubAlert_matchesBaseline() {
        captureScreenshot("MarkdownViewer_alert_light", darkTheme = false) {
            MarkdownViewer(
                markdown = SAMPLE_ALERT,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }

    /** 深色主题：GitHub Alert（NOTE） */
    @Test
    @Config(qualifiers = "night")
    fun markdownViewer_darkTheme_withGitHubAlert_matchesBaseline() {
        captureScreenshot("MarkdownViewer_alert_dark", darkTheme = true) {
            MarkdownViewer(
                markdown = SAMPLE_ALERT,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}

private val SAMPLE_MARKDOWN = """
# AppDev

轻量级 **Android GitHub 客户端** · Material You 设计

## 功能特性

- 仓库浏览 / 文件树 / README
- Issue / PR / Review / 行内评论
- 通知、搜索、代码编辑（Sora Editor）

## 代码示例

```kotlin
suspend fun fetchRepo(owner: String, name: String): Repo {
    return withContext(Dispatchers.IO) { api.repo(owner, name) }
}
```

## 引用

> 环境：Pixel 8 / Android 15 / 屏幕刷新率 120Hz
> 版本：v0.1.0

**加粗** · *斜体* · ~~删除线~~ · `行内代码`
""".trimIndent()

private val SAMPLE_ALERT = """
> [!NOTE]
> 这是一条 **Note** 告警，使用 M3 primaryContainer 色。

> [!WARNING]
> 这是一条 **Warning** 告警，使用 surfaceContainerHighest 色。

> [!CAUTION]
> 这是一条 **Caution** 告警，使用 errorContainer 色。

普通引用（非告警）：

> 这是一段普通引用文本，不包含 `[!TYPE]` 标记。
""".trimIndent()
