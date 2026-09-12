package com.yumiru11.githubapp.feature.repo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.yumiru11.githubapp.core.data.model.Release
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.datastore.draft.DraftAutoSaver
import com.yumiru11.githubapp.core.datastore.draft.DraftRepository
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.ui.RepoDetailActions
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 仓库详情「深链初始视图」屏幕级测试（TREE / RELEASE 路由参数落地）。
 *
 * 覆盖 spec-audit §10 P2 的消费端：
 * - `showFiles` → 初始分区是「文件」（根树内容可见，而非 README）
 * - `treePath` → 文件树自动展开到目标目录（子项可见，证明逐级展开真实发生）
 * - `showReleases` → 初始分区是「Releases」（Release 卡片可见）
 * - `releaseTag` → 匹配的 Release 详情被自动展开（回调带正确 id）
 *
 * ViewModel 侧用 mockk 注入固定状态；[RepoFilesViewModel] 用真实实例 + MockK 桩
 * [RepoRepository]（屏幕已形参化 filesViewModel，见 RepoDetailScreen 注释）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RepoDetailInitialViewTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val repo =
        Repository(ownerLogin = "octocat", name = "Hello-World", defaultBranch = "main")

    private fun successState(releasesState: ReleasesState = ReleasesState.Idle) =
        RepoDetailUiState.Success(
            repo = repo,
            readmeState = ReadmeState.Empty,
            releasesState = releasesState,
        )

    private fun detailViewModel(state: RepoDetailUiState): RepoDetailViewModel =
        mockk(relaxed = true) {
            every { uiState } returns MutableStateFlow(state)
            every { events } returns emptyFlow()
        }

    private fun filesViewModel(repoRepository: RepoRepository): RepoFilesViewModel =
        RepoFilesViewModel(
            savedStateHandle = SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World")),
            repoRepository = repoRepository,
            drafts = DraftAutoSaver(mockk<DraftRepository>(relaxed = true)),
        )

    private fun setScreen(
        viewModel: RepoDetailViewModel,
        filesViewModel: RepoFilesViewModel,
        showFiles: Boolean = false,
        treePath: String = "",
        showReleases: Boolean = false,
        releaseTag: String = "",
    ) {
        composeRule.setContent {
            AppTheme {
                Box(modifier = Modifier.size(width = 411.dp, height = 891.dp)) {
                    RepoDetailScreen(
                        owner = "octocat",
                        repo = "Hello-World",
                        initialShowFiles = showFiles,
                        initialTreePath = treePath,
                        initialShowReleases = showReleases,
                        initialReleaseTag = releaseTag,
                        viewModel = viewModel,
                        filesViewModel = filesViewModel,
                        actions = RepoDetailActions(),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun repoDetailScreen_initialShowFiles_landsOnFilesSection() {
        val repoRepository =
            mockk<RepoRepository>(relaxed = true) {
                coEvery { getTree(any(), any(), any()) } returns
                    Result.success(
                        listOf(GitTreeNode(name = "src", path = "src", sha = "sha-src", isDirectory = true)),
                    )
            }
        setScreen(
            viewModel = detailViewModel(successState()),
            filesViewModel = filesViewModel(repoRepository),
            showFiles = true,
        )

        // 文件分区内容（根树条目）可见 → 初始 tab 是「文件」而不是 README
        composeRule.onNodeWithText("src").assertIsDisplayed()
    }

    @Test
    fun repoDetailScreen_initialTreePath_autoExpandsToDirectory() {
        val repoRepository =
            mockk<RepoRepository>(relaxed = true) {
                coEvery { getTree(any(), any(), any()) } returns
                    Result.success(
                        listOf(GitTreeNode(name = "src", path = "src", sha = "sha-src", isDirectory = true)),
                    )
                coEvery { getChildTree("octocat", "Hello-World", "sha-src", "src") } returns
                    Result.success(
                        listOf(GitTreeNode(name = "Main.kt", path = "src/Main.kt", sha = "sha-main", isDirectory = false)),
                    )
            }
        setScreen(
            viewModel = detailViewModel(successState()),
            filesViewModel = filesViewModel(repoRepository),
            showFiles = true,
            treePath = "src",
        )

        // 子项可见 = expandTreePath 真实按需拉取并展开了目标目录
        composeRule.onNodeWithText("Main.kt").assertIsDisplayed()
        composeRule.onNodeWithText("src").assertIsDisplayed()
    }

    @Test
    fun repoDetailScreen_initialShowReleases_landsOnReleasesSection() {
        setScreen(
            viewModel =
                detailViewModel(
                    successState(
                        releasesState = ReleasesState.Loaded(listOf(Release(id = 7L, tagName = "v1.0.0"))),
                    ),
                ),
            filesViewModel = filesViewModel(mockk<RepoRepository>(relaxed = true)),
            showReleases = true,
        )

        // Release 卡片可见 → 初始 tab 是「Releases」而不是 README
        composeRule.onNodeWithText("v1.0.0").assertIsDisplayed()
    }

    @Test
    fun repoDetailScreen_initialReleaseTag_expandsMatchingReleaseDetail() {
        val viewModel =
            detailViewModel(
                successState(
                    releasesState = ReleasesState.Loaded(listOf(Release(id = 7L, tagName = "v1.0.0"))),
                ),
            )
        setScreen(
            viewModel = viewModel,
            filesViewModel = filesViewModel(mockk<RepoRepository>(relaxed = true)),
            showReleases = true,
            releaseTag = "v1.0.0",
        )

        // tag 命中 → 自动请求展开对应 Release 详情（loadReleaseDetail 带正确 id）
        verify { viewModel.loadReleaseDetail(7L) }
    }

    @Test
    fun repoDetailScreen_initialReleaseTagNotInList_staysOnList() {
        val viewModel =
            detailViewModel(
                successState(
                    releasesState = ReleasesState.Loaded(listOf(Release(id = 7L, tagName = "v1.0.0"))),
                ),
            )
        setScreen(
            viewModel = viewModel,
            filesViewModel = filesViewModel(mockk<RepoRepository>(relaxed = true)),
            showReleases = true,
            releaseTag = "v9.9.9",
        )

        // 未命中：停在列表（卡片仍可见），不误展开任何 Release
        composeRule.onNodeWithText("v1.0.0").assertIsDisplayed()
        verify(exactly = 0) { viewModel.loadReleaseDetail(any()) }
    }
}
