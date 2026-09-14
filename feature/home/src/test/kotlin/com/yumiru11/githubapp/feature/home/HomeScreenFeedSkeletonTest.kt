package com.yumiru11.githubapp.feature.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.feature.home.model.FeedItem
import com.yumiru11.githubapp.feature.home.ui.FEED_SKELETON_ROWS
import com.yumiru11.githubapp.feature.home.ui.FEED_SKELETON_ROW_TAG
import com.yumiru11.githubapp.feature.home.ui.FEED_SKELETON_TAG
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI-7 首载骨架屏语义断言：首载显示骨架 / 数据到达替换为内容 / 有内容的刷新不显示骨架。
 *
 * 形态红线：骨架只在「列表为空 + refresh 挂起」时出现（[homeFirstLoadPagingData] 用
 * `sourceLoadStates` 显式构造该形态）；一旦判定退化为「任何 Loading 都上骨架」或
 * 「骨架永不出现」，本文件的三个断言必红。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class HomeScreenFeedSkeletonTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val firstFeedTitle = homeFeedItems().first().title

    @Test
    fun homeScreen_feedFirstLoad_showsSkeletonRows() {
        setHomeScreenWithFeed(MutableStateFlow(homeFirstLoadPagingData()))

        composeRule.onNodeWithTag(FEED_SKELETON_TAG).assertIsDisplayed()
        composeRule
            .onAllNodesWithTag(FEED_SKELETON_ROW_TAG, useUnmergedTree = true)
            .assertCountEquals(FEED_SKELETON_ROWS)
        composeRule.onNodeWithText(firstFeedTitle).assertDoesNotExist()
    }

    @Test
    fun homeScreen_feedFirstLoad_dataArrives_replacesSkeletonWithContent() {
        val feed = MutableStateFlow(homeFirstLoadPagingData())
        setHomeScreenWithFeed(feed)
        composeRule.onNodeWithTag(FEED_SKELETON_TAG).assertIsDisplayed()

        feed.value = PagingData.from(homeFeedItems())
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(firstFeedTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(FEED_SKELETON_TAG).assertDoesNotExist()
    }

    @Test
    fun homeScreen_feedRefreshWithExistingRows_keepsRowsAndHidesSkeleton() {
        setHomeScreenWithFeed(MutableStateFlow(homeRefreshingPagingDataWithRows()))

        composeRule.onNodeWithText(firstFeedTitle).assertIsDisplayed()
        composeRule.onNodeWithTag(FEED_SKELETON_TAG).assertDoesNotExist()
    }

    /** 挂载 HomeScreen：feed 状态流可增量推进（首载 → 内容），其余依赖用截图夹具的桩。 */
    private fun setHomeScreenWithFeed(feed: MutableStateFlow<PagingData<FeedItem>>) {
        val viewModel =
            mockk<HomeViewModel>(relaxed = true) {
                every { uiState } returns MutableStateFlow(HomeUiState.Success(feed = feed))
                every { trending } returns MutableStateFlow(emptyList())
            }
        composeRule.setContent {
            AppTheme {
                HomeScreen(
                    onSearchClick = {},
                    onNotificationClick = {},
                    onProfileClick = {},
                    viewModel = viewModel,
                    pickerViewModel = homePickerScreenshotViewModel(),
                )
            }
        }
    }

    /** 有内容 + refresh 仍挂起（下拉刷新中）：骨架不得出现，内容行原地保留。 */
    private fun homeRefreshingPagingDataWithRows(): PagingData<FeedItem> =
        PagingData.from(
            homeFeedItems(),
            LoadStates(
                refresh = LoadState.Loading,
                prepend = LoadState.NotLoading(endOfPaginationReached = false),
                append = LoadState.NotLoading(endOfPaginationReached = false),
            ),
        )
}
