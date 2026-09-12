package com.yumiru11.githubapp.feature.repo

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yumiru11.githubapp.core.editor.FileFindState
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.screenshot.captureScreenshotDeterministic
import com.yumiru11.githubapp.core.ui.RepoDetailActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 文件查看器截图基准（light / dark 两帧，代码文件形态）。
 *
 * 基准 PNG：feature/repo/src/test/screenshots/FileViewerScreen_{light,dark}.png（入库，
 * 只能由 CI "Record screenshots (CI canonical)" workflow 录制）。
 *
 * 内容用未知扩展名（grammar = null 纯文本），规避 TextMate 语法资产加载路径（FileEdit 先例）。
 * 用 `captureScreenshotDeterministic`：Sora 编辑器 + 悬浮工具层的组合下，Roborazzi 的
 * compose 捕获路径 `idle()` 可能不收敛（根因见 [captureScreenshotDeterministic]）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class FileViewerScreenScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fileViewerScreen_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "FileViewerScreen_light", darkTheme = false) {
            FileViewerScreen(
                fileState = FileViewState.Loaded(fileViewerContentData()),
                selectedPath = "docs/notes.txt",
                ref = "main",
                viewModel = repoFilesScreenshotViewModel(),
                actions = RepoDetailActions(),
                baseRepoUrl = "https://github.com/octocat/Hello-World",
                findState = FileFindState(),
                isFindOpen = false,
                editable = true,
                onClose = {},
            )
        }
    }

    @Test
    fun fileViewerScreen_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "FileViewerScreen_dark", darkTheme = true) {
            FileViewerScreen(
                fileState = FileViewState.Loaded(fileViewerContentData()),
                selectedPath = "docs/notes.txt",
                ref = "main",
                viewModel = repoFilesScreenshotViewModel(),
                actions = RepoDetailActions(),
                baseRepoUrl = "https://github.com/octocat/Hello-World",
                findState = FileFindState(),
                isFindOpen = false,
                editable = true,
                onClose = {},
            )
        }
    }
}
