package com.yumiru11.githubapp.feature.home

import androidx.paging.PagingData
import com.yumiru11.githubapp.feature.home.model.FeedEventType
import com.yumiru11.githubapp.feature.home.model.FeedItem
import com.yumiru11.githubapp.feature.home.model.TrendItem
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

/**
 * 首页截图测试共用夹具（确定性、离线自足）。
 *
 * ## 相对时间（铁律）
 *
 * FeedRow 把 `createdAt` 交给 `relativeTimeText` 渲染成 "1 day ago" 这类相对文案。
 * 夹具**不得**写死绝对时间戳（会随日历推进静默过期，见 #246 日历时间炸弹修复）；
 * [homeIsoDaysAgo] 在测试运行时按"当前时刻减 N 天"生成，渲染文案跨日历恒定。
 *
 * ## 头像一律 null
 *
 * FeedRow 的 `AsyncImage` 在夹具里不给 URL（actorAvatarUrl = null）：不触网、不出
 * 网络像素差异；头像组件的布局分支由 contentDescription/尺寸本身覆盖。
 *
 * ## 为什么 mock ViewModel
 *
 * 与 [RepoDetailInitialViewTest] 同款做法（该测试已在本仓库合入）：屏幕的 viewModel
 * 形参已为测试开放，截图只关心「给定状态 → 像素」；mock 掉 VM 的加载副作用才能保证
 * 离屏确定性（不依赖仓库 mock 的协程时序）。
 */
internal fun homeIsoDaysAgo(days: Long): String =
    Instant
        .now()
        .minusSeconds(days * SECONDS_PER_DAY)
        .toString()

private const val SECONDS_PER_DAY = 86_400L

/** 动态流三条不同事件类型（Issue / PR / Push），覆盖动作文案与行内时间分支。 */
internal fun homeFeedItems(): List<FeedItem> =
    listOf(
        FeedItem(
            id = "feed-1",
            type = FeedEventType.ISSUE,
            actorLogin = "yumiru11",
            actorAvatarUrl = null,
            repoFullName = "octocat/Hello-World",
            action = "opened",
            title = "Home 截图基线首帧",
            number = 201,
            commitCount = null,
            createdAt = homeIsoDaysAgo(1),
            htmlUrl = "https://github.com/octocat/Hello-World/issues/201",
        ),
        FeedItem(
            id = "feed-2",
            type = FeedEventType.PULL_REQUEST,
            actorLogin = "octocat",
            actorAvatarUrl = null,
            repoFullName = "octocat/Hello-World",
            action = "closed",
            title = "test(ui): 补齐缺失屏幕截图基线",
            number = 249,
            commitCount = null,
            createdAt = homeIsoDaysAgo(2),
            htmlUrl = "https://github.com/octocat/Hello-World/pull/249",
        ),
        FeedItem(
            id = "feed-3",
            type = FeedEventType.PUSH,
            actorLogin = "hubot",
            actorAvatarUrl = null,
            repoFullName = "yumiru11/AppDev",
            action = null,
            title = "fix(repo): BlobRoute 消费 editState",
            number = null,
            commitCount = 3,
            createdAt = homeIsoDaysAgo(3),
            htmlUrl = null,
        ),
    )

/** Trending 小节（feed 尾部）两条。 */
internal fun homeTrendingItems(): List<TrendItem> =
    listOf(
        TrendItem(
            fullName = "octocat/Hello-World",
            description = "My first repository on GitHub!",
            language = "Kotlin",
            stars = 12_345,
            forks = 678,
            url = "https://github.com/octocat/Hello-World",
        ),
        TrendItem(
            fullName = "rikkahub/rikkahub",
            description = "Native Android LLM chat client",
            language = "Kotlin",
            stars = 4_321,
            forks = 210,
            url = "https://github.com/rikkahub/rikkahub",
        ),
    )

/** 首页 ViewModel 桩：Success（feed 三条 + trending 两条），retry 走 relaxed 空实现。 */
internal fun homeScreenshotViewModel(): HomeViewModel =
    mockk(relaxed = true) {
        every { uiState } returns
            MutableStateFlow(
                HomeUiState.Success(feed = flowOf(PagingData.from(homeFeedItems()))),
            )
        every { trending } returns MutableStateFlow(homeTrendingItems())
    }

/** 仓库选择器 ViewModel 桩：首帧不展开选择器，只需兜住 `uiState` 收集。 */
internal fun homePickerScreenshotViewModel(): RepoPickerViewModel =
    mockk(relaxed = true) {
        every { uiState } returns MutableStateFlow<RepoPickerUiState>(RepoPickerUiState.Loading)
    }
