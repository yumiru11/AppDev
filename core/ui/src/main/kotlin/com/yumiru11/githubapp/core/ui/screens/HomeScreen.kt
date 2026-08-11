@file:OptIn(ExperimentalFoundationApi::class)

package com.yumiru11.githubapp.core.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.yumiru11.githubapp.core.ui.AppBottomBar
import com.yumiru11.githubapp.core.ui.AppTopBar
import com.yumiru11.githubapp.core.ui.HomePager
import com.yumiru11.githubapp.core.ui.HomeTabs
import com.yumiru11.githubapp.core.ui.NotificationPanel

/**
 * 首页：AppTopBar + HomeTabs + HomePager + AppBottomBar + 通知面板覆盖。
 *
 * 点击顶栏通知铃铛触发全屏滑入 [NotificationPanel]，不走导航。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    @Suppress("unused") onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit,
    onTabSelected: (String) -> Unit,
    selectedTab: String,
    modifier: Modifier = Modifier,
) {
    var showNotifPanel by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { 4 })

    Scaffold(
        topBar = {
            AppTopBar(
                onSearchClick = onSearchClick,
                onNotificationClick = { showNotifPanel = true },
                onProfileClick = onProfileClick,
            )
        },
        bottomBar = {
            AppBottomBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
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

            NotificationPanel(
                visible = showNotifPanel,
                onDismiss = { showNotifPanel = false },
                onMarkAllRead = { /* T5+ */ },
            )
        }
    }
}
