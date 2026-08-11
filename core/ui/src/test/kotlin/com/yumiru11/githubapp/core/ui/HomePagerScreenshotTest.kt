@file:OptIn(ExperimentalFoundationApi::class)

package com.yumiru11.githubapp.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.rememberPagerState
import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HomePager 截图基准测试（light / dark / 第二页 三态）。
 *
 * 基准 PNG：core/ui/src/test/screenshots/HomePager_{light,dark,page2}.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HomePagerScreenshotTest : ScreenshotTest() {
    @Test
    fun homePager_lightTheme_matchesBaseline() {
        captureScreenshot(name = "HomePager_light", darkTheme = false) {
            val pagerState = rememberPagerState(pageCount = { 4 })
            HomePager(pagerState = pagerState)
        }
    }

    @Test
    fun homePager_darkTheme_matchesBaseline() {
        captureScreenshot(name = "HomePager_dark", darkTheme = true) {
            val pagerState = rememberPagerState(pageCount = { 4 })
            HomePager(pagerState = pagerState)
        }
    }
}
