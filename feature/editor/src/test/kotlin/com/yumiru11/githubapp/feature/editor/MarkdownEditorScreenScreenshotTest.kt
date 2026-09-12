package com.yumiru11.githubapp.feature.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.screenshot.captureScreenshotDeterministic
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Markdown 编辑器页截图基准（light / dark 两帧，编辑态 + 示例正文）。
 *
 * 基准 PNG：feature/editor/src/test/screenshots/MarkdownEditorScreen_{light,dark}.png（入库，
 * 只能由 CI "Record screenshots (CI canonical)" workflow 录制）。
 *
 * 用 `captureScreenshotDeterministic`：Sora 编辑器 + Markdown 工具栏组合下与 feature:repo 截图
 * 统一捕获路径，规避 Roborazzi compose 捕获的 `idle()` 挂死面（根因见
 * [captureScreenshotDeterministic]）。ViewModel 由屏幕内部按 initialContent 构造，
 * 无需注入（预览态默认关闭，WebView 预览不参与首帧）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class MarkdownEditorScreenScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun markdownEditorScreen_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "MarkdownEditorScreen_light", darkTheme = false) {
            MarkdownEditorScreen(
                initialContent = SAMPLE_MARKDOWN,
                onClose = {},
            )
        }
    }

    @Test
    fun markdownEditorScreen_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "MarkdownEditorScreen_dark", darkTheme = true) {
            MarkdownEditorScreen(
                initialContent = SAMPLE_MARKDOWN,
                onClose = {},
            )
        }
    }

    private companion object {
        val SAMPLE_MARKDOWN =
            """
            ## 截图基线扩展

            - 覆盖 Home / RepoDetail / FileViewer / Search
            - 覆盖 Profile / Gists / Settings / Branches
            - 编辑器页本身也在名单里

            > 相对时间夹具 + 冻结时钟 = 跨日历稳定。
            """.trimIndent()
    }
}
