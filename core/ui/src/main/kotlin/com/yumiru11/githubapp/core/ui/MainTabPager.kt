package com.yumiru11.githubapp.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.navigation.AppRoute
import kotlinx.coroutines.launch

/**
 * 底部 Tab 三分区容器（2026-08-14 真机走查分区重构）。
 *
 * 底部「首页 / 仓库 / 我的」不是独立导航页（原 NavHost 路由整页切换产生「弹窗感」），
 * 而是同一容器内横向分页：点 tab 或左右滑动切换分区，无导航动画、无页面栈。
 *
 * - 顶栏由各分区页自持（首页=搜索+铃铛、我的=标题+设置，保留页差异）
 * - 底栏 [AppBottomBar] 固定在容器底部，tab 与 pager 双向联动
 * - 分区内容由宿主注入（feature 模块无相互依赖）
 *
 * @param selectedTab 当前选中分区路由（AppRoute.HOME / repos / AppRoute.PROFILE）
 * @param onTabSelected tab 点击回调（宿主无需处理，本组件内部已联动 pager；保留参数供外部感知）
 */
@Composable
fun MainTabPager(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    blurEnabled: Boolean = true,
    homePage: @Composable (PaddingValues) -> Unit = {},
    reposPage: @Composable (PaddingValues) -> Unit = {},
    profilePage: @Composable (PaddingValues) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tabIndex = tabIndexFor(selectedTab)
    val pagerState =
        rememberPagerState(initialPage = tabIndex) { TAB_COUNT }
    val scope = rememberCoroutineScope()

    // 外部 tab 状态变化（如顶部头像切到我的）→ 联动 pager 滚动
    LaunchedEffect(tabIndex) {
        if (pagerState.currentPage != tabIndex) {
            pagerState.animateScrollToPage(tabIndex)
        }
    }

    Scaffold(
        modifier = modifier,
        // 分区页各自处理 insets（Home/Profile 有 TopAppBar 自带 statusBars；
        // 容器不再叠加顶部 padding，否则出现「顶栏距状态栏空一段」——2026-08-14 真机走查修复）
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            AppBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { route ->
                    onTabSelected(route)
                    scope.launch {
                        pagerState.animateScrollToPage(tabIndexFor(route))
                    }
                },
                blurEnabled = blurEnabled,
            )
        },
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val pagePadding =
                PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding(),
                )
            when (page) {
                0 -> homePage(pagePadding)
                1 -> reposPage(pagePadding)
                2 -> profilePage(pagePadding)
            }
        }
    }
}

private fun tabIndexFor(route: String): Int =
    when (route) {
        TAB_REPOS -> 1
        AppRoute.PROFILE -> 2
        else -> 0
    }

private const val TAB_COUNT = 3
