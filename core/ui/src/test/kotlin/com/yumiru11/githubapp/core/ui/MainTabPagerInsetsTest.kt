package com.yumiru11.githubapp.core.ui

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.testing.insets.SystemBarInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 底栏分区页底部避让契约的 insets 回归测试（P0：仓库分区末行被底栏压字）。
 *
 * ## 为什么必须用几何断言、不能靠截图基线
 *
 * `MainTabPager` 的 Scaffold 显式 `contentWindowInsets = WindowInsets(0.dp)`（**有意设计**），
 * 且 Scaffold **不会**给 content 自动加 padding——底栏高度只能靠宿主把 page lambda 的
 * `PaddingValues` 转成各页的 `bottomContentPadding`。丢形参**编译通过、首屏截图也看不出来**
 * （CI 32 帧全是首屏、无「列表滚到底」帧），所以唯一能锁住它的是几何数值断言。
 *
 * ## 断言什么（三键导航栏 = 缺陷唯一复现条件）
 *
 * 用 [SystemBarInsets] 注入**真实的三键导航栏 inset（48dp）**——Robolectric 根部窗口
 * insets 恒为 0（`WindowInsets.navigationBars` 在 sdk 35/28、gesture/三键 qualifier 下
 * 实测都是 0.dp），不注入就永远测不到「含系统导航栏」的底栏总高：
 *
 * 1. 容器下发给分区页的底部预留 == 底栏**实测总高**（NavigationBar 内容 80dp + 导航栏 inset）；
 * 2. 分区页把该值落到滚动容器 `contentPadding` 后，列表能滚到「末项底边 ≤ 底栏上沿」
 *    ——即末项完整滚出底栏，不被底栏 / 系统导航栏裁切；
 * 3. 手势导航（24dp）与三键导航（48dp）**都**成立。
 *
 * 反向护栏见 [mainTabPager_zeroBottomPadding_lastItemIsCoveredByBottomBar]：它复刻修复前
 * `reposPage` 丢形参的等效行为（底部预留 0dp），必须失败——证明正向断言真的锁住了东西。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MainTabPagerInsetsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun mainTabPager_threeButtonNavigation_reportsBottomBarHeightIncludingNavBarInset() {
        val geometry = renderPagerUnderInsets(SystemBarInsets.THREE_BUTTON)

        assertEquals(
            "三键导航栏下底栏总高必须含 navigationBars inset（80dp 内容 + 48dp inset）",
            NAV_BAR_CONTENT_HEIGHT + SystemBarInsets.THREE_BUTTON.navigationBarDp.dp,
            geometry.reportedBottomPadding,
        )
        assertTrue(
            "高 = 底栏实测总高：reported=${geometry.reportedBottomPadding} bar=${geometry.barHeight}",
            geometry.reportedBottomPadding >= geometry.barHeight,
        )
    }

    @Test
    fun mainTabPager_gestureNavigation_reportsBottomBarHeightIncludingGestureInset() {
        val geometry = renderPagerUnderInsets(SystemBarInsets.GESTURE)

        // 修三键不能坏手势：手势条 inset 同样必须计入
        assertEquals(
            "手势导航下底栏总高必须含手势条 inset",
            NAV_BAR_CONTENT_HEIGHT + SystemBarInsets.GESTURE.navigationBarDp.dp,
            geometry.reportedBottomPadding,
        )
        assertTrue(
            "手势导航下下发值不得小于底栏实测总高",
            geometry.reportedBottomPadding >= geometry.barHeight,
        )
    }

    @Test
    fun mainTabPager_pageContentPadding_lastItemClearOfBottomBar_threeButton() {
        val geometry = renderPagerUnderInsets(SystemBarInsets.THREE_BUTTON)

        assertTrue(
            "末项底边必须停在底栏上沿之上（不被底栏 / 系统导航栏裁切）：" +
                "lastItemBottom=${geometry.lastItemBottom} barTop=${geometry.barTop}",
            geometry.lastItemBottom <= geometry.barTop,
        )
        // 末项还必须真的在屏内（否则断言可能只是「滚不到底」的假绿）
        assertTrue(
            "末项底边必须在屏幕内：lastItemBottom=${geometry.lastItemBottom} screen=${geometry.screenBottom}",
            geometry.lastItemBottom <= geometry.screenBottom,
        )
    }

    @Test
    fun mainTabPager_pageContentPadding_lastItemClearOfBottomBar_gesture() {
        val geometry = renderPagerUnderInsets(SystemBarInsets.GESTURE)

        assertTrue(
            "手势导航下末项同样不得被底栏裁切：" +
                "lastItemBottom=${geometry.lastItemBottom} barTop=${geometry.barTop}",
            geometry.lastItemBottom <= geometry.barTop,
        )
    }

    @Test
    fun mainTabPager_zeroBottomPadding_lastItemIsCoveredByBottomBar() {
        // 反向护栏：复刻修复前 reposPage 丢形参的等效行为（bottomContentPadding = 0.dp）。
        // 这条必须失败，否则说明上面的正向断言什么都没锁住。
        val geometry = renderPagerUnderInsets(SystemBarInsets.THREE_BUTTON, forwardPadding = false)

        assertTrue(
            "底部预留 0dp 时末项必然落进底栏矩形（灵敏度证明）：" +
                "lastItemBottom=${geometry.lastItemBottom} barTop=${geometry.barTop}",
            geometry.lastItemBottom > geometry.barTop,
        )
    }

    /**
     * 渲染与生产同构的容器：`MainTabPager` 的 Scaffold 结构（`contentWindowInsets` 归零 +
     * [AppBottomBar] 作 bottomBar）+ 一个拿 `bottomContentPadding` 作列表 contentPadding 的分区页。
     *
     * @param forwardPadding false = 复刻丢形参（页面收到 0dp）
     */
    private fun renderPagerUnderInsets(
        spec: SystemBarInsets.InsetsSpec,
        forwardPadding: Boolean = true,
    ): PagerGeometry {
        var reportedBottomPadding: Dp = Dp.Unspecified
        var composeView: View? = null

        composeRule.setContent {
            composeView = LocalView.current
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                // 与 MainTabPager 同款：容器不叠加 inset，故底栏高度不在 Scaffold padding 里
                contentWindowInsets = WindowInsets(0.dp),
                bottomBar = {
                    Box(modifier = Modifier.testTag(BAR_TAG)) {
                        AppBottomBar(selectedTab = MainTab.REPOS, onTabSelected = {})
                    }
                },
            ) { paddingValues ->
                reportedBottomPadding = paddingValues.calculateBottomPadding()
                val pagePadding = if (forwardPadding) reportedBottomPadding else 0.dp
                Box(modifier = Modifier.testTag(PAGE_TAG).fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.testTag(LIST_TAG).fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                bottom = pagePadding + AppDimens.contentPadding,
                            ),
                    ) {
                        repeat(ROW_COUNT) { index ->
                            item(key = "row-$index") {
                                Box(
                                    modifier =
                                        Modifier
                                            .testTag(rowTag(index))
                                            .fillMaxWidth()
                                            .height(ROW_HEIGHT),
                                )
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        SystemBarInsets.applyTo(checkNotNull(composeView) { "LocalView 未捕获到 ComposeView" }, spec)
        composeRule.waitForIdle()

        // 滚到列表末端：末项底边应停在 contentPadding 的底部边界（= 底栏上沿）
        composeRule.onNodeWithTag(LIST_TAG).performScrollToIndex(ROW_COUNT - 1)
        composeRule.waitForIdle()

        val barBounds = composeRule.onNodeWithTag(BAR_TAG).getBoundsInRoot()
        val lastRowBounds = composeRule.onNodeWithTag(rowTag(ROW_COUNT - 1)).getBoundsInRoot()
        val rootBounds = composeRule.onRoot().getBoundsInRoot()
        // 实测值留痕：截图基线看不到系统栏，这些数值是 insets 修复唯一的可核对证据
        System.out.println(
            "INSETS reportedBottom=$reportedBottomPadding barTop=${barBounds.top} " +
                "barHeight=${barBounds.bottom - barBounds.top} " +
                "lastItemBottom=${lastRowBounds.bottom} screenBottom=${rootBounds.bottom}",
        )
        return PagerGeometry(
            reportedBottomPadding = reportedBottomPadding,
            barTop = barBounds.top,
            barHeight = barBounds.bottom - barBounds.top,
            lastItemBottom = lastRowBounds.bottom,
            screenBottom = rootBounds.bottom,
        )
    }

    private data class PagerGeometry(
        val reportedBottomPadding: Dp,
        val barTop: Dp,
        val barHeight: Dp,
        val lastItemBottom: Dp,
        val screenBottom: Dp,
    )

    private fun rowTag(index: Int): String = "insets-row-$index"

    private companion object {
        const val PAGE_TAG = "insets-page"
        const val LIST_TAG = "insets-list"
        const val BAR_TAG = "insets-bar"

        /** 行数足够多，保证能真正滚到底（行数不足时 contentPadding 无观测差异） */
        const val ROW_COUNT = 60

        /** M3 NavigationBar 容器内容高（不含 insets） */
        val NAV_BAR_CONTENT_HEIGHT = 80.dp

        val ROW_HEIGHT = 64.dp
    }
}
