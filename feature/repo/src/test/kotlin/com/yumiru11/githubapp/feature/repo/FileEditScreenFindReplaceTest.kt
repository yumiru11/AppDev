package com.yumiru11.githubapp.feature.repo

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import com.yumiru11.githubapp.core.datastore.draft.DraftAutoSaver
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.designsystem.token.CodeEditorPreferences
import com.yumiru11.githubapp.core.designsystem.token.CodeEditorPreferencesWriter
import com.yumiru11.githubapp.core.designsystem.token.LocalCodeEditorPreferences
import com.yumiru11.githubapp.core.designsystem.token.LocalCodeEditorPreferencesWriter
import com.yumiru11.githubapp.core.editor.LineEnding
import com.yumiru11.githubapp.core.editor.TextFileFormat
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 文件编辑页查找/替换交互测试（EDITOR-1）。
 *
 * 覆盖「替换流程」端到端路径：输入查询词 → Sora 异步回灌匹配（按钮可用）→ 输入替换词 →
 * 「全部替换」→ 编辑器文本变更经 [RepoFilesViewModel.onEditorTextChanged] 回灌状态。
 * 另覆盖换行开关：点击图标必须把选择经写入端写回（不持有第二份状态）。
 *
 * 用真实 [RepoFilesViewModel]（桩仓库）+ 真实 Sora 编辑器：查找/替换必须落在真实编辑器缓冲上。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class FileEditScreenFindReplaceTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun replaceAll_multipleMatches_replacesAllAndSyncsEditingText() {
        val viewModel = editingViewModel("a b a b a")
        // 快照在组合外读取：lint 禁止 Composition 期间读 StateFlow.value
        val editState = viewModel.uiState.value.editState
        composeRule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalCodeEditorPreferences provides CodeEditorPreferences()) {
                    FileEditScreen(
                        editState = editState,
                        filePath = "Main.kt",
                        baseRepoUrl = "https://github.com/octocat/Hello-World",
                        defaultRef = "main",
                        viewModel = viewModel,
                        onClose = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(FileFindReplaceBarTags.QUERY).performTextInput("a")
        // 先确认输入真的进了状态（CI 上曾出现"输入未落 → 后面干等结果"的假超时）
        assertEquals("a", viewModel.uiState.value.findState.query)
        // Sora 查找是异步的：等匹配回灌后再点替换（按钮以 hasMatches 为可用判据）
        awaitMatches(viewModel)
        composeRule.onNodeWithTag(FileFindReplaceBarTags.REPLACE).performTextInput("X")
        composeRule.onNodeWithText("Replace all").performClick()
        composeRule.waitForIdle()

        assertEquals("X b X b X", (viewModel.uiState.value.editState as FileEditState.Editing).text)
    }

    @Test
    fun replace_selectedMatch_replacesOnlyThatMatch() {
        val viewModel = editingViewModel("a b a b a")
        // 快照在组合外读取：lint 禁止 Composition 期间读 StateFlow.value
        val editState = viewModel.uiState.value.editState
        composeRule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalCodeEditorPreferences provides CodeEditorPreferences()) {
                    FileEditScreen(
                        editState = editState,
                        filePath = "Main.kt",
                        baseRepoUrl = "https://github.com/octocat/Hello-World",
                        defaultRef = "main",
                        viewModel = viewModel,
                        onClose = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(FileFindReplaceBarTags.QUERY).performTextInput("a")
        assertEquals("a", viewModel.uiState.value.findState.query)
        awaitMatches(viewModel)
        composeRule.onNodeWithTag(FileFindReplaceBarTags.REPLACE).performTextInput("X")
        composeRule.onNodeWithText("Replace").performClick()
        composeRule.waitForIdle()

        assertEquals("X b a b a", (viewModel.uiState.value.editState as FileEditState.Editing).text)
    }

    @Test
    fun replaceAll_withNoMatches_keepsTextUnchanged() {
        val viewModel = editingViewModel("a b a b a")
        // 快照在组合外读取：lint 禁止 Composition 期间读 StateFlow.value
        val editState = viewModel.uiState.value.editState
        composeRule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalCodeEditorPreferences provides CodeEditorPreferences()) {
                    FileEditScreen(
                        editState = editState,
                        filePath = "Main.kt",
                        baseRepoUrl = "https://github.com/octocat/Hello-World",
                        defaultRef = "main",
                        viewModel = viewModel,
                        onClose = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(FileFindReplaceBarTags.QUERY).performTextInput("zzz")
        assertEquals("zzz", viewModel.uiState.value.findState.query)
        // 无匹配：替换动作保持禁用（不给「点了没反应」的死按钮）
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Replace all").assertIsNotEnabled()
        assertEquals("a b a b a", (viewModel.uiState.value.editState as FileEditState.Editing).text)
    }

    @Test
    fun softWrapToggle_offPreference_writesEnabledPreferenceThroughWriter() {
        val writes = mutableListOf<Boolean>()
        val writer =
            object : CodeEditorPreferencesWriter {
                override fun setCodeSoftWrap(enabled: Boolean) {
                    writes += enabled
                }

                override fun setMarkdownSoftWrap(enabled: Boolean) = Unit
            }
        val viewModel = editingViewModel("a b a")
        // 快照在组合外读取：lint 禁止 Composition 期间读 StateFlow.value
        val editState = viewModel.uiState.value.editState
        composeRule.setContent {
            AppTheme {
                CompositionLocalProvider(
                    LocalCodeEditorPreferences provides CodeEditorPreferences(codeSoftWrap = false),
                    LocalCodeEditorPreferencesWriter provides writer,
                ) {
                    FileEditScreen(
                        editState = editState,
                        filePath = "Main.kt",
                        baseRepoUrl = "https://github.com/octocat/Hello-World",
                        defaultRef = "main",
                        viewModel = viewModel,
                        onClose = {},
                    )
                }
            }
        }

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.repo_file_soft_wrap_off))
            .performClick()

        assertEquals(listOf(true), writes)
    }

    @Test
    fun textFormatIndicator_crlfFile_showsCrlfAndUtf8() {
        val viewModel = editingViewModel("a b a b a", format = TextFileFormat(lineEnding = LineEnding.CRLF))
        // 快照在组合外读取：lint 禁止 Composition 期间读 StateFlow.value
        val editState = viewModel.uiState.value.editState
        composeRule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalCodeEditorPreferences provides CodeEditorPreferences()) {
                    FileEditScreen(
                        editState = editState,
                        filePath = "Main.kt",
                        baseRepoUrl = "https://github.com/octocat/Hello-World",
                        defaultRef = "main",
                        viewModel = viewModel,
                        onClose = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("CRLF · UTF-8").assertExists()
    }

    /**
     * 等 Sora 异步查找回灌（匹配数 > 0）。
     *
     * Sora 在后台线程扫描完成后经 `postInLifecycle` 回主线程派发；Robolectric 的主 looper 是
     * 暂停态，必须**显式排空**才会执行队列里的回灌。（CI 实测：`waitUntil` 自身的轮询不驱动
     * looper，10s 超时偶发；这里改成「waitForIdle + 小睡」的有界轮询，失败信息自带状态。）
     */
    private fun awaitMatches(viewModel: RepoFilesViewModel) {
        val deadline = System.currentTimeMillis() + ASYNC_SEARCH_TIMEOUT_MS
        while (!viewModel.uiState.value.findState.hasMatches && System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            Thread.sleep(SEARCH_POLL_INTERVAL_MS)
        }
        assertTrue(
            "Sora 查找结果未在 ${ASYNC_SEARCH_TIMEOUT_MS}ms 内回灌：findState=${viewModel.uiState.value.findState}",
            viewModel.uiState.value.findState.hasMatches,
        )
    }

    /** 真实 VM（桩仓库）+ 已加载文件 + 进入编辑态 + 展开查找栏。 */
    private fun editingViewModel(
        text: String,
        format: TextFileFormat = TextFileFormat.DEFAULT,
    ): RepoFilesViewModel {
        val repoRepository = mockk<RepoRepository>(relaxed = true)
        val node = GitTreeNode(name = "Main.kt", path = "Main.kt", sha = "sha-1", isDirectory = false)
        coEvery { repoRepository.getTree(any(), any(), any()) } returns Result.success(listOf(node))
        coEvery { repoRepository.getFileContent(any(), any(), any(), any(), any()) } returns
            Result.success(FileContentData("Main.kt", "Main.kt", text.length.toLong(), FileKind.CODE, text, "blob-1", format))
        // 草稿读盘必须显式桩成 null：relaxed mock 对可空 String 返回 ""，
        // 会被 restoreDraft 当成"恢复了空草稿"而清空编辑文本（本测试踩过）
        val drafts = mockk<DraftAutoSaver>(relaxed = true) { coEvery { load(any()) } returns null }
        val viewModel =
            RepoFilesViewModel(
                savedStateHandle = SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World")),
                repoRepository = repoRepository,
                drafts = drafts,
            )
        viewModel.loadRootTree("main")
        viewModel.openFile(node, "main")
        viewModel.startEdit()
        viewModel.openFind()
        return viewModel
    }

    private companion object {
        /** Sora 查找回灌等待上限（本地 < 1s；CI 慢机留 30s 余量）。 */
        const val ASYNC_SEARCH_TIMEOUT_MS = 30_000L

        /** 轮询间隔（每轮先排空 looper 再小睡）。 */
        const val SEARCH_POLL_INTERVAL_MS = 50L
    }
}
