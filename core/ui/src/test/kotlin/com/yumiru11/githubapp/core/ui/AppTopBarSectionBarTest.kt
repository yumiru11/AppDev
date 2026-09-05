package com.yumiru11.githubapp.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [AppTopBar] 玻璃头副行插槽的几何断言（issue #83）。
 *
 * backdrop blur 的采样范围 = **玻璃矩形 ∩ 内容**，所以「小分区条在不在玻璃矩形内」
 * 就是能不能看见穿越感的分水岭：
 *
 * - 在矩形内（本测试锁住的行为）：分区条与顶栏共用同一个 `hazeEffect`，玻璃头
 *   因副行加高，Scaffold 交给内容区的 top inset 随之含两行 → 列表视口能铺到玻璃
 *   背后；而且**玻璃层数不增**（§6.2 ≤2 层上限由此守住）。
 * - 留在内容列里（改前的布局）：列表视口被整体下推一行，滚动内容永远进不了顶栏
 *   矩形 → 每帧采样空背景 → FEEDBACK #17「只糊栏自身、看不出效果」。
 *
 * 像素级模糊本身在 JVM 断言不了（Robolectric 不渲染 RenderEffect），本测试断言的是
 * 它的**几何前提**；观感由真机验收卡兜底。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppTopBarSectionBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appTopBar_sectionBar_liesInsideGlassHeaderRect() {
        setContentWithAndWithoutSection()
        val headerRect = composeRule.onNodeWithTag(HEADER_WITH).getBoundsInRoot()
        val sectionRect = composeRule.onNodeWithTag(SECTION).getBoundsInRoot()

        // 副行整个落在玻璃矩形之内（底部不越界），否则它采样不到、也遮不住内容
        assertTrue(
            "section must start below the glass top edge",
            sectionRect.top > 0.dp,
        )
        assertTrue(
            "section must stay inside the glass rect: sectionBottom=${sectionRect.bottom} glassBottom=${headerRect.bottom}",
            sectionRect.bottom <= headerRect.bottom,
        )
    }

    @Test
    fun appTopBar_sectionBar_growsGlassHeader_byAtLeastSectionHeight() {
        setContentWithAndWithoutSection()
        val withRect = composeRule.onNodeWithTag(HEADER_WITH).getBoundsInRoot()
        val withoutRect = composeRule.onNodeWithTag(HEADER_WITHOUT).getBoundsInRoot()
        // DpRect 没有 height 成员（会撞 Modifier.height 扩展），用 bottom - top 取玻璃头高度
        val withSection = withRect.bottom - withRect.top
        val withoutSection = withoutRect.bottom - withoutRect.top

        // 玻璃头因副行加高（内容区 top inset 随之含两行），否则列表不会为分区条避让
        assertTrue(
            "glass header should grow with the section bar: $withSection vs $withoutSection",
            withSection - withoutSection >= SECTION_HEIGHT,
        )
    }

    /** 一次组合内放两个变体（setContent 每个测试只能调用一次）。 */
    private fun setContentWithAndWithoutSection() {
        composeRule.setContent {
            Column {
                Box(modifier = Modifier.testTag(HEADER_WITH)) {
                    AppTopBar(
                        onSearchClick = {},
                        onNotificationClick = {},
                        onProfileClick = {},
                        sectionBar = {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(SECTION_HEIGHT)
                                        .testTag(SECTION),
                            )
                        },
                    )
                }
                Box(modifier = Modifier.testTag(HEADER_WITHOUT)) {
                    AppTopBar(
                        onSearchClick = {},
                        onNotificationClick = {},
                        onProfileClick = {},
                    )
                }
            }
        }
    }

    private companion object {
        const val SECTION = "glass-section-bar"
        const val HEADER_WITH = "glass-header-with-section"
        const val HEADER_WITHOUT = "glass-header-without-section"
        val SECTION_HEIGHT = 48.dp
    }
}
