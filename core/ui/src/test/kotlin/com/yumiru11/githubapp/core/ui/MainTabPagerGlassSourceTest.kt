package com.yumiru11.githubapp.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.navigation.AppRoute
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [MainTabPager] 的 backdrop blur 几何契约（issue #83）。
 *
 * Haze 的 `hazeEffect` 只对**与玻璃矩形相交**的内容采样，所以底栏玻璃要真糊到东西，
 * 容器必须同时满足两条（都被本测试锁住，防止回归到 commit 6157420 修掉的那个状态）：
 *
 * 1. 分区内容节点 **full-bleed**（铺到屏幕底、与底栏矩形相交）——容器不能自己
 *    `.padding(paddingValues)` 把内容整块停在玻璃栏上沿，那是 FEEDBACK #17
 *    「毛玻璃看不出效果」的几何根因；
 * 2. 玻璃栏高度以 `PaddingValues` 数值**下发给页面**，由页面在自己的滚动容器
 *    `contentPadding` 里避让（静止态不遮内容，滚动时内容物理穿过玻璃 → 穿越感）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MainTabPagerGlassSourceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mainTabPager_pageContent_reachesScreenBottom() {
        composeRule.setContent {
            MainTabPager(
                selectedTab = AppRoute.HOME,
                onTabSelected = {},
                homePage = { Box(modifier = Modifier.testTag(PAGE_TAG).fillMaxSize()) },
            )
        }
        val pageBottom = composeRule.onNodeWithTag(PAGE_TAG).getBoundsInRoot().bottom
        val screenBottom = composeRule.onRoot().getBoundsInRoot().bottom

        // 内容底边铺到屏幕底（穿过底栏矩形），而不是停在底栏上沿
        assertTrue(
            "page content must reach the screen bottom: page=$pageBottom screen=$screenBottom",
            pageBottom >= screenBottom - TOLERANCE,
        )
    }

    @Test
    fun mainTabPager_pagePadding_reportsBottomBarHeightToPage() {
        var reportedBottom = Dp.Unspecified
        composeRule.setContent {
            MainTabPager(
                selectedTab = AppRoute.HOME,
                onTabSelected = {},
                homePage = { padding ->
                    reportedBottom = padding.calculateBottomPadding()
                    Box(modifier = Modifier.testTag(PAGE_TAG).fillMaxSize())
                },
            )
        }
        composeRule.onNodeWithTag(PAGE_TAG).assertExists()

        // 底栏高度作为 contentPadding 数值下发（页面自己避让），容器不替页面吃掉了它
        assertTrue(
            "bottom bar height must be handed to the page as content padding, got $reportedBottom",
            reportedBottom > MIN_BOTTOM_BAR,
        )
    }

    private companion object {
        const val PAGE_TAG = "glass-source-page"
        val TOLERANCE = 1.dp
        val MIN_BOTTOM_BAR = 40.dp
    }
}
