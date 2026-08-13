@file:OptIn(ExperimentalFoundationApi::class)

package com.yumiru11.githubapp.core.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.yumiru11.githubapp.core.ui.AppBottomBar
import com.yumiru11.githubapp.core.ui.AppTopBar
import com.yumiru11.githubapp.core.ui.HomePager
import com.yumiru11.githubapp.core.ui.HomeTabs

/**
 * 首页：AppTopBar + HomeTabs + HomePager + AppBottomBar。
 *
 * 点击顶栏通知铃铛 → 导航到 AppRoute.NOTIFICATION（T19 全屏 slide-in 通知页，
 * 替换 T3 占位 NotificationPanel 弹出面板，docs/ui-design.md §3.4）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit,
    onTabSelected: (String) -> Unit,
    selectedTab: String,
    blurEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { 4 })

    Scaffold(
        topBar = {
            AppTopBar(
                onSearchClick = onSearchClick,
                onNotificationClick = onNotificationClick,
                onProfileClick = onProfileClick,
                blurEnabled = blurEnabled,
            )
        },
        bottomBar = {
            AppBottomBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                blurEnabled = blurEnabled,
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            HomeTabs(pagerState = pagerState)
            HomePager(
                pagerState = pagerState,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
