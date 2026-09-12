package com.yumiru11.githubapp.feature.search

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.paging.PagingData
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.fake.GitHubFakes
import com.yumiru11.githubapp.core.testing.screenshot.captureScreenshotDeterministic
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 搜索页截图基准（light / dark 两帧，仓库 Tab 命中态）。
 *
 * 基准 PNG：feature/search/src/test/screenshots/SearchScreen_{light,dark}.png（入库，
 * 只能由 CI "Record screenshots (CI canonical)" workflow 录制）。
 *
 * 用 `captureScreenshotDeterministic`：结果区是 Paging + 下拉刷新（无限动画），且搜索中
 * 顶部有 `LinearWavyProgressIndicator` 形变进度条 —— Roborazzi 的 compose 捕获路径
 * `idle()` 对无限动画永不返回（根因见 [captureScreenshotDeterministic]）。
 * ViewModel 用 mockk 注入固定状态（与 feature:repo 截图先例同款）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SearchScreenScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun searchViewModel(): SearchViewModel =
        mockk(relaxed = true) {
            every { uiState } returns
                MutableStateFlow(
                    SearchUiState.Success(
                        query = "kotlin",
                        activeTab = SearchTab.REPOSITORIES,
                        repositories =
                            flowOf(
                                PagingData.from(
                                    listOf(
                                        GitHubFakes.fakeRepository(
                                            ownerLogin = "octocat",
                                            name = "Hello-World",
                                            language = "Kotlin",
                                            stargazerCount = 1_234,
                                            forkCount = 56,
                                        ),
                                        GitHubFakes.fakeRepository(
                                            ownerLogin = "yumiru11",
                                            name = "AppDev",
                                            language = "Kotlin",
                                            stargazerCount = 321,
                                            forkCount = 12,
                                        ),
                                    ),
                                ),
                            ),
                        users = flowOf(PagingData.empty()),
                        issues = flowOf(PagingData.empty()),
                        pullRequests = flowOf(PagingData.empty()),
                        code = flowOf(PagingData.empty()),
                    ),
                )
            every { input } returns MutableStateFlow("kotlin")
            every { history } returns MutableStateFlow(listOf("compose", "material3"))
            every { isLoggedIn } returns MutableStateFlow(true)
            every { rateLimitWarning } returns MutableStateFlow<SearchRateLimitWarning?>(null)
        }

    @Test
    fun searchScreen_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "SearchScreen_light", darkTheme = false) {
            SearchScreen(
                onBackClick = {},
                viewModel = searchViewModel(),
            )
        }
    }

    @Test
    fun searchScreen_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "SearchScreen_dark", darkTheme = true) {
            SearchScreen(
                onBackClick = {},
                viewModel = searchViewModel(),
            )
        }
    }
}
