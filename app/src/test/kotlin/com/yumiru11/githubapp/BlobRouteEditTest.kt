package com.yumiru11.githubapp

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.yumiru11.githubapp.core.datastore.draft.DraftAutoSaver
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.feature.repo.FileCommitResult
import com.yumiru11.githubapp.feature.repo.FileContentData
import com.yumiru11.githubapp.feature.repo.FileEditState
import com.yumiru11.githubapp.feature.repo.FileKind
import com.yumiru11.githubapp.feature.repo.FileViewState
import com.yumiru11.githubapp.feature.repo.RepoFilesViewModel
import com.yumiru11.githubapp.feature.repo.RepoRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * BLOB 深链编辑态消费回归（缺陷修复守卫）。
 *
 * 缺陷：BlobRoute 此前只渲染 FileViewerScreen，从不消费 RepoFilesUiState.editState ——
 * 深链打开文件后点「编辑」，ViewModel 已进入编辑态而界面仍停在查看器（编辑器不可达，
 * CI editor.png 探针「点了 Edit 后 30s 内编辑态未出现」FAILED 的根因）；且 editable
 * 硬编码 true，游客会看到一个点了没反应的死按钮。
 *
 * 本测试直接渲染 BlobRoute + 真实 RepoFilesViewModel（Mock 仓库与草稿），覆盖：
 * (a) 登录态 + 文件已加载 → 点 Edit → 编辑态出现（顶栏 Commit 动作可见）；
 * (b) 游客 → 不渲染 Edit 入口；
 * (c) PR #234 草稿流程在此路径可用（恢复事件被消费 + 丢弃回基线 + 提交清草稿）。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BlobRouteEditTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val navController: NavHostController = mockk(relaxed = true)

    @Test
    fun blobRoute_loggedInLoadedFile_tapEdit_showsEditScreenWithCommitAction() {
        val viewModel = viewModel(repoRepository(), draftSaver())

        setBlobRoute(isLoggedIn = true, viewModel = viewModel)
        awaitFileLoaded(viewModel)

        // 查看器就绪且登录态 → Edit 入口存在（点击前先确认入口真的渲染了）
        composeRule.onNodeWithContentDescription("Edit").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Edit").performClick()
        composeRule.waitForIdle()

        // 缺陷回归断言：editState 被 BlobRoute 消费 → 编辑器顶栏 Commit 动作出现
        assertTrue(viewModel.uiState.value.editState is FileEditState.Editing)
        composeRule.onNodeWithText("Commit").assertIsDisplayed()
    }

    @Test
    fun blobRoute_guestLoadedFile_rendersNoEditAction() {
        val viewModel = viewModel(repoRepository(), draftSaver())

        setBlobRoute(isLoggedIn = false, viewModel = viewModel)
        awaitFileLoaded(viewModel)

        // 查看器正常渲染（标题 = 路径），但游客没有编辑入口（editable 不再硬编码 true）
        composeRule.onNodeWithText(FILE_PATH).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Edit").assertDoesNotExist()
    }

    @Test
    fun blobRoute_storedDraft_tapEdit_consumesRestoreEventAndDiscardReturnsToBaseline() {
        val drafts = draftSaver(storedDraft = DRAFT_TEXT)
        val viewModel = viewModel(repoRepository(), drafts)

        setBlobRoute(isLoggedIn = true, viewModel = viewModel)
        awaitFileLoaded(viewModel)

        composeRule.onNodeWithContentDescription("Edit").performClick()
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            hasText("Unsaved draft restored")
        }

        // 草稿回填到编辑区
        assertEquals(DRAFT_TEXT, (viewModel.uiState.value.editState as FileEditState.Editing).text)

        // Snackbar 的「Discard draft」动作 → 删草稿并把编辑区恢复为远端基线
        composeRule.onNodeWithText("Discard draft").performClick()
        composeRule.waitForIdle()

        verify(atLeast = 1) { drafts.discard(any()) }
        assertEquals(BASE_TEXT, (viewModel.uiState.value.editState as FileEditState.Editing).text)
    }

    @Test
    fun blobRoute_commitSucceeds_clearsDraftExitsEditAndShowsCommittedSnackbar() {
        val drafts = draftSaver()
        val viewModel = viewModel(repoRepository(), drafts)

        setBlobRoute(isLoggedIn = true, viewModel = viewModel)
        awaitFileLoaded(viewModel)

        composeRule.onNodeWithContentDescription("Edit").performClick()
        composeRule.waitForIdle()

        viewModel.commitEdit(message = "chore(test): blob route commit", newBranchName = null, newFilePath = null)
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            hasText("Committed $FILE_PATH")
        }

        // 提交成功 → 草稿清空、编辑态退出（查看器路径与仓库详情一致）
        verify(atLeast = 1) { drafts.discard(any()) }
        assertTrue(viewModel.uiState.value.editState is FileEditState.Idle)
    }

    private fun setBlobRoute(
        isLoggedIn: Boolean,
        viewModel: RepoFilesViewModel,
    ) {
        composeRule.setContent {
            val lifecycleOwner = remember { BlobRouteTestLifecycleOwner() }
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                AppTheme {
                    BlobRoute(
                        owner = OWNER,
                        repo = REPO,
                        ref = REF,
                        path = FILE_PATH,
                        isLoggedIn = isLoggedIn,
                        navController = navController,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }

    /** 深链文件加载完成再断言（LaunchedEffect → ViewModel 异步链路，避免探针抢跑）。 */
    private fun awaitFileLoaded(viewModel: RepoFilesViewModel) {
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            viewModel.uiState.value.fileState is FileViewState.Loaded
        }
        composeRule.waitForIdle()
    }

    private fun hasText(text: String): Boolean = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun viewModel(
        repoRepository: RepoRepository,
        drafts: DraftAutoSaver,
    ): RepoFilesViewModel =
        RepoFilesViewModel(
            savedStateHandle = SavedStateHandle(mapOf("owner" to OWNER, "repo" to REPO, "ref" to REF)),
            repoRepository = repoRepository,
            drafts = drafts,
        )

    private fun repoRepository(content: FileContentData = fileContent()): RepoRepository =
        mockk {
            coEvery { getFileContent(any(), any(), any(), any()) } returns Result.success(content)
            coEvery { getTree(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { updateFileContent(any(), any(), any(), any(), any(), any(), any()) } returns
                Result.success(FileCommitResult.Success(commitSha = "commit-sha", contentSha = "content-sha"))
        }

    /** 草稿调度器 Mock：读返回 [storedDraft]，写/丢弃只记录调用（断言用）。 */
    private fun draftSaver(storedDraft: String? = null): DraftAutoSaver =
        mockk(relaxed = true) {
            coEvery { load(any()) } returns storedDraft
        }

    private fun fileContent(): FileContentData =
        FileContentData(
            fileName = "Main.kt",
            path = FILE_PATH,
            size = BASE_TEXT.length.toLong(),
            kind = FileKind.CODE,
            text = BASE_TEXT,
            sha = "blob-sha-1",
        )

    private companion object {
        const val OWNER = "octocat"
        const val REPO = "Hello-World"
        const val REF = "main"
        const val FILE_PATH = "src/Main.kt"
        const val BASE_TEXT = "fun main() {}"
        const val DRAFT_TEXT = "fun main() { println(\"draft\") }"
        const val WAIT_TIMEOUT_MILLIS = 10_000L
    }
}

/** 常驻 RESUMED 的测试 LifecycleOwner（collectAsStateWithLifecycle 依赖）。 */
private class BlobRouteTestLifecycleOwner : LifecycleOwner {
    private val registry: LifecycleRegistry =
        LifecycleRegistry.createUnsafe(this).apply {
            currentState = Lifecycle.State.RESUMED
        }

    override val lifecycle: Lifecycle = registry
}
