package com.yumiru11.githubapp.feature.profile

import androidx.paging.PagingData
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.testing.fake.GitHubFakes
import com.yumiru11.githubapp.feature.profile.model.GistItem
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

/**
 * 个人页 / Gists 页截图测试共用夹具（确定性、离线自足）。
 *
 * ## 相对时间（铁律）
 *
 * Gist 行把 `createdAt` 交给 `relativeTimeText` 渲染成相对文案；夹具**不得**写死绝对
 * 时间戳（跨日历静默过期，见 #246）；[profileIsoDaysAgo] 运行时按"当前时刻减 N 天"生成。
 *
 * ## 头像一律 null
 *
 * `fakeUser(avatarUrl = null)`：资料头不触网、不出网络像素差异（不覆盖 Coil 解码）。
 *
 * ## 为什么 mock ViewModel
 *
 * 与 [RepoDetailInitialViewTest]（feature:repo）同款：屏幕 viewModel 形参已为测试开放，
 * 截图只关心「给定状态 → 像素」；mock 掉 VM 的四路 Paging/资料加载副作用保证离屏确定性。
 */
internal fun profileIsoDaysAgo(days: Long): String =
    Instant
        .now()
        .minusSeconds(days * SECONDS_PER_DAY)
        .toString()

private const val SECONDS_PER_DAY = 86_400L

/** 本人主页用户（统计四件套齐全；avatarUrl = null 不触网）。 */
internal fun profileScreenshotUser(): User =
    GitHubFakes
        .fakeUser(
            login = "yumiru11",
            name = "Yumiru11",
            avatarUrl = null,
            bio = "Building a lightweight Material You GitHub client.",
        ).copy(
            publicRepos = 42,
            followers = 128,
            following = 37,
            starredCount = 12,
        )

/** Repos Tab 列表两条。 */
internal fun profileScreenshotRepos() =
    listOf(
        GitHubFakes.fakeRepository(ownerLogin = "yumiru11", name = "AppDev", stargazerCount = 321, forkCount = 12),
        GitHubFakes.fakeRepository(ownerLogin = "yumiru11", name = "dotfiles", language = "Shell", stargazerCount = 8, forkCount = 1),
    )

/** Followers Tab 列表两条（他人主页用户，avatarUrl = null）。 */
internal fun profileScreenshotFollowers(): List<User> =
    listOf(
        GitHubFakes.fakeUser(login = "octocat", name = "The Octocat", avatarUrl = null),
        GitHubFakes.fakeUser(login = "hubot", name = "Hubot", avatarUrl = null),
    )

/** Gists 列表三条（相对时间戳，跨日历稳定）。 */
internal fun gistItems(): List<GistItem> =
    listOf(
        GistItem(
            id = "gist-1",
            fileName = "screenshot-fixtures.md",
            language = "Markdown",
            description = "截图夹具说明（相对时间 + 固定内容）",
            createdAt = profileIsoDaysAgo(2),
            htmlUrl = "https://gist.github.com/octocat/gist-1",
        ),
        GistItem(
            id = "gist-2",
            fileName = "ComposeSnippets.kt",
            language = "Kotlin",
            description = "Compose 容器/动画片段",
            createdAt = profileIsoDaysAgo(5),
            htmlUrl = "https://gist.github.com/octocat/gist-2",
        ),
        GistItem(
            id = "gist-3",
            fileName = "ci-notes.txt",
            language = null,
            description = null,
            createdAt = profileIsoDaysAgo(12),
            htmlUrl = "https://gist.github.com/octocat/gist-3",
        ),
    )

/** 个人页 ViewModel 桩：Success（本人）+ 四路 Paging 固定数据 + 空事件流。 */
internal fun profileScreenshotViewModel(): ProfileViewModel =
    mockk(relaxed = true) {
        every { uiState } returns
            MutableStateFlow(
                ProfileUiState.Success(user = profileScreenshotUser(), isSelf = true),
            )
        every { events } returns emptyFlow()
        every { repositories } returns flowOf(PagingData.from(profileScreenshotRepos()))
        every { starred } returns flowOf(PagingData.empty())
        every { followers } returns flowOf(PagingData.from(profileScreenshotFollowers()))
        every { following } returns flowOf(PagingData.empty())
    }

/** Gists ViewModel 桩：固定三条 gist（相对时间戳）。 */
internal fun gistsScreenshotViewModel(): GistsViewModel =
    mockk(relaxed = true) {
        every { gists } returns flowOf(PagingData.from(gistItems()))
    }
