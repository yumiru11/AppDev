package com.yumiru11.githubapp.feature.repo

import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [reposListContentPadding] 的数值断言（P0：仓库分区末行被底栏压字）。
 *
 * ## 为什么走「纯函数」退路
 *
 * `ReposScreen` 的底栏高度来自宿主（[ReposScreen] 参数 `bottomContentPadding`），本屏
 * `paddingValues.calculateBottomPadding()` 恒为 0——列表底部预留的**全部**信息都在
 * `bottomContentPadding` 这个数里。把它收敛成纯函数后，可以用纯数值断言锁住
 * 「三键（48dp inset）/手势（24dp inset）两种导航栏下都留够」这条契约，
 * 不必拉起 Hilt + Paging + 触网的整屏组合（`ReposViewModel` 的分页源是 final 类
 * `ViewerRepositoriesPagingSource`，纯 JVM 测试无法注入可控 PagingSource）。
 *
 * 整屏几何由 `MainTabPagerInsetsTest` 在同一 worktree 内用**真实三键导航栏 inset**
 * 做端到端断言（末项底边 ≤ 底栏上沿），两者互补：
 * - 本测试：数值口径（含系统导航栏 inset 的失败模式立刻可见）
 * - `MainTabPagerInsetsTest`：容器实测几何 + 丢形参的灵敏度证明
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReposContentPaddingTest {
    @Test
    fun reposListContentPadding_threeButtonNav_reservesBottomBarPlusNavBarInset() {
        val padding = reposListContentPadding(topPadding = 0.dp, bottomContentPadding = THREE_BUTTON_BOTTOM_BAR)

        assertEquals(
            "三键导航栏下底部预留 = 底栏总高（80dp 内容 + 48dp inset）+ 列表自身呼吸位",
            THREE_BUTTON_BOTTOM_BAR + AppDimens.contentPadding,
            padding.calculateBottomPadding(),
        )
        assertTrue(
            "底部预留必须≥底栏总高，否则末行落进底栏矩形",
            padding.calculateBottomPadding() >= THREE_BUTTON_BOTTOM_BAR,
        )
    }

    @Test
    fun reposListContentPadding_gestureNav_reservesBottomBarPlusGestureInset() {
        val padding = reposListContentPadding(topPadding = 0.dp, bottomContentPadding = GESTURE_BOTTOM_BAR)

        // 修三键不能坏手势：手势条 inset（24dp）同样必须计入
        assertEquals(
            "手势导航下底部预留 = 底栏总高（80dp 内容 + 24dp 手势条 inset）+ 列表自身呼吸位",
            GESTURE_BOTTOM_BAR + AppDimens.contentPadding,
            padding.calculateBottomPadding(),
        )
    }

    @Test
    fun reposListContentPadding_zeroBottomContentPadding_reproducesP0Shortfall() {
        // 反向护栏：复刻修复前「宿主丢形参 → bottomContentPadding 默认 0.dp」的等效行为。
        // 三键导航栏下底栏总高 128dp，此时底部预留只剩 16dp —— 末行必然被压住。
        val degraded = reposListContentPadding(topPadding = 0.dp, bottomContentPadding = 0.dp)

        assertEquals(
            "丢形参时底部预留退化成只剩列表自身呼吸位",
            AppDimens.contentPadding,
            degraded.calculateBottomPadding(),
        )
        assertTrue(
            "该退化值远小于三键导航栏下的底栏总高（$THREE_BUTTON_BOTTOM_BAR）——即 P0 缺陷",
            degraded.calculateBottomPadding() < THREE_BUTTON_BOTTOM_BAR,
        )
    }

    @Test
    fun reposListContentPadding_topPadding_offsetsByCornerSmall() {
        val padding = reposListContentPadding(topPadding = 120.dp, bottomContentPadding = 0.dp)

        // 顶部避让顶栏玻璃总高 + 卡片间距（保持既有实现口径不变）
        assertEquals(120.dp + AppDimens.cornerSmall, padding.calculateTopPadding())
    }

    private companion object {
        /** M3 NavigationBar 内容 80dp + 三键导航栏 inset 48dp */
        val THREE_BUTTON_BOTTOM_BAR = 128.dp

        /** M3 NavigationBar 内容 80dp + 手势条 inset 24dp */
        val GESTURE_BOTTOM_BAR = 104.dp
    }
}
