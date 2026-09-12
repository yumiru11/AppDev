package com.yumiru11.githubapp.feature.repo

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
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
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.testing.insets.SystemBarInsets
import com.yumiru11.githubapp.core.ui.AppBottomBar
import com.yumiru11.githubapp.core.ui.MainTab
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 仓库分区列表底部的**几何**断言（P0：仓库分区末行被底栏压字）。
 *
 * ## 与 [ReposContentPaddingTest] 的分工
 *
 * 那个用纯函数锁数值口径；这个把 [reposListContentPadding]（**生产代码本身**）喂给滚动容器，
 * 与真的 [AppBottomBar] 同屏渲染，注入**真实三键导航栏 inset（48dp）**后断言
 * **末项底边 ≤ 底栏上沿**——即末项能完整滚出底栏，不被底栏与系统导航栏裁切。
 *
 * ⚠️ 为什么不能只靠截图基线：Robolectric / CI 截图渲染不到系统导航栏，且 CI 32 帧全是
 * **首屏**、没有「列表滚到底」帧——丢 `bottomContentPadding` 的缺陷在现有截图链路里
 * 永远不可见，只能靠这里的几何数值断言。
 *
 * [reposListContentPadding_zeroBottomContentPadding_lastItemCoveredByBottomBar] 是灵敏度
 * 证明：复刻修复前「宿主丢形参」的等效行为后该断言必须失败。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReposListInsetsGeometryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun reposListContentPadding_threeButtonNav_lastItemClearOfBottomBar() {
        val geometry = renderReposListUnderThreeButtonNav(bottomContentPadding = THREE_BUTTON_BOTTOM_BAR)

        assertTrue(
            "接住宿主下发的底栏总高后，末项底边必须停在底栏上沿之上：" +
                "lastItemBottom=${geometry.lastItemBottom} barTop=${geometry.barTop}",
            geometry.lastItemBottom <= geometry.barTop,
        )
    }

    @Test
    fun reposListContentPadding_gestureNav_lastItemClearOfBottomBar() {
        val geometry =
            renderReposListUnderThreeButtonNav(
                bottomContentPadding = GESTURE_BOTTOM_BAR,
                spec = SystemBarInsets.GESTURE,
            )

        // 修三键不能坏手势
        assertTrue(
            "手势导航下末项同样不得被底栏裁切：" +
                "lastItemBottom=${geometry.lastItemBottom} barTop=${geometry.barTop}",
            geometry.lastItemBottom <= geometry.barTop,
        )
    }

    @Test
    fun reposListContentPadding_zeroBottomContentPadding_lastItemCoveredByBottomBar() {
        // 反向护栏：复刻修复前 reposPage 丢形参（bottomContentPadding = 0.dp）的等效行为
        val geometry = renderReposListUnderThreeButtonNav(bottomContentPadding = 0.dp)

        assertTrue(
            "底部预留退化成 16dp 时末项必然落进底栏矩形（灵敏度证明）：" +
                "lastItemBottom=${geometry.lastItemBottom} barTop=${geometry.barTop}",
            geometry.lastItemBottom > geometry.barTop,
        )
    }

    /**
     * 渲染「生产 contentPadding 公式 + 真底栏」并滚到末项，返回末项底边与底栏上沿。
     *
     * 本函数**故意**忽略 Scaffold 的 innerPadding（故抑制 UnusedMaterial3ScaffoldPaddingParameter）：
     * 断言目标就是「列表自身的 contentPadding 公式（reposListContentPadding）足以避开底栏」；
     * 若在此消费 innerPadding，等于把待测公式换成 Scaffold 的默认 padding，测试即失去意义
     * （反向护栏 reposListContentPadding_zeroBottomContentPadding_* 同源）。
     */
    @Suppress("UnusedMaterial3ScaffoldPaddingParameter")
    private fun renderReposListUnderThreeButtonNav(
        bottomContentPadding: Dp,
        spec: SystemBarInsets.InsetsSpec = SystemBarInsets.THREE_BUTTON,
    ): ListGeometry {
        var composeView: View? = null

        composeRule.setContent {
            composeView = LocalView.current
            AppTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    // 与 MainTabPager / ReposScreen 同款：容器不叠加 inset
                    contentWindowInsets = WindowInsets(0.dp),
                    bottomBar = {
                        Box(modifier = Modifier.testTag(BAR_TAG)) {
                            AppBottomBar(selectedTab = MainTab.REPOS, onTabSelected = {})
                        }
                    },
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.testTag(LIST_TAG).fillMaxSize(),
                            // 生产代码本身的 contentPadding 公式（不是测试里重写的镜像）
                            contentPadding = reposListContentPadding(topPadding = 0.dp, bottomContentPadding = bottomContentPadding),
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
        }
        composeRule.waitForIdle()
        SystemBarInsets.applyTo(checkNotNull(composeView) { "LocalView 未捕获到 ComposeView" }, spec)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LIST_TAG).performScrollToIndex(ROW_COUNT - 1)
        composeRule.waitForIdle()

        val barBounds = composeRule.onNodeWithTag(BAR_TAG).getBoundsInRoot()
        val lastRowBounds = composeRule.onNodeWithTag(rowTag(ROW_COUNT - 1)).getBoundsInRoot()
        // 末项必须真的在屏内（否则可能是「滚不到底」造成的假绿）
        assertTrue(
            "末项底边须落在屏幕内：lastItemBottom=${lastRowBounds.bottom}",
            lastRowBounds.bottom <= composeRule.onRoot().getBoundsInRoot().bottom,
        )
        System.out.println(
            "REPOS-INSETS bottomContentPadding=$bottomContentPadding " +
                "lastItemBottom=${lastRowBounds.bottom} barTop=${barBounds.top}",
        )
        return ListGeometry(lastItemBottom = lastRowBounds.bottom, barTop = barBounds.top)
    }

    private data class ListGeometry(
        val lastItemBottom: Dp,
        val barTop: Dp,
    )

    private fun rowTag(index: Int): String = "repos-insets-row-$index"

    private companion object {
        const val LIST_TAG = "repos-insets-list"
        const val BAR_TAG = "repos-insets-bar"
        const val ROW_COUNT = 60

        /** M3 NavigationBar 内容 80dp + 三键导航栏 inset 48dp */
        val THREE_BUTTON_BOTTOM_BAR = 128.dp

        /** M3 NavigationBar 内容 80dp + 手势条 inset 24dp */
        val GESTURE_BOTTOM_BAR = 104.dp

        val ROW_HEIGHT = 64.dp
    }
}
