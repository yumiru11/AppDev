package com.yumiru11.githubapp.feature.repo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

/**
 * 文件编辑屏幕截图基准测试（T22，light / dark 各一张）。
 *
 * 基准 PNG：feature/repo/src/test/screenshots/FileEditScreen_{light,dark}.png（入库）。
 * 内容用未知扩展名（grammar = null 纯文本），规避 TextMate 语法资产加载路径（同 Issue 截图先例）。
 */
class FileEditScreenScreenshotTest : ScreenshotTest() {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModel =
        RepoFilesViewModel(
            SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World")),
            mockk<RepoRepository>(relaxed = true),
        )

    @Test
    fun fileEditScreen_lightTheme_matchesBaseline() {
        captureScreenshot(name = "FileEditScreen_light", darkTheme = false) {
            Box(modifier = Modifier.size(width = 411.dp, height = 891.dp)) {
                FileEditScreen(
                    editState = FileEditState.Editing(isNew = false, text = SAMPLE_CODE, sha = "blob-1", isMarkdown = false),
                    filePath = "notes.txt",
                    baseRepoUrl = "https://github.com/octocat/Hello-World",
                    defaultRef = "main",
                    viewModel = viewModel,
                    onClose = {},
                )
            }
        }
    }

    @Test
    fun fileEditScreen_darkTheme_matchesBaseline() {
        captureScreenshot(name = "FileEditScreen_dark", darkTheme = true) {
            Box(modifier = Modifier.size(width = 411.dp, height = 891.dp)) {
                FileEditScreen(
                    editState = FileEditState.Editing(isNew = false, text = SAMPLE_CODE, sha = "blob-1", isMarkdown = false),
                    filePath = "notes.txt",
                    baseRepoUrl = "https://github.com/octocat/Hello-World",
                    defaultRef = "main",
                    viewModel = viewModel,
                    onClose = {},
                )
            }
        }
    }

    private companion object {
        const val SAMPLE_CODE = "// T22 file editor\nfun main() {\n    println(\"hello\")\n}\n"
    }
}
