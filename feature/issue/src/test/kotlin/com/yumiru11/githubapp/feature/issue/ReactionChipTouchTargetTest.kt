package com.yumiru11.githubapp.feature.issue

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 反应 chip 触区断言（issue #168 / UI24，ui-audit #15）。
 *
 * 两条都要成立，缺一不可：
 * 1. **命中区 ≥ 48dp**（M3 无障碍基线，ui-design §8）——测 clickable 节点本身的布局尺寸
 * 2. **视觉不膨胀**——胶囊本体 ≤ [REACTION_CHIP_MAX_VISUAL_HEIGHT]，即 48dp 只加在命中层
 *
 * 只断言尺寸会被「把 48dp 直接加到胶囊上」的实现骗过（旧实现正是如此），所以再补一条
 * **真实触点**断言：点击命中区**下沿**（视觉胶囊之外）仍必须触发 toggle。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReactionChipTouchTargetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reactionChip_plainState_hitAreaIsAtLeastFortyEightDp() {
        var toggles = 0
        composeRule.setContent {
            AppTheme {
                ReactionChip(
                    content = "+1",
                    count = 3,
                    reacted = false,
                    reactedStateText = "Reacted",
                    onToggle = { toggles++ },
                )
            }
        }

        // DpRect 没有 width/height 成员（会撞 Modifier.width/height 扩展）→ 用边界相减
        val hit = composeRule.onNodeWithTag(REACTION_CHIP_HIT_TAG).getUnclippedBoundsInRoot()
        val hitWidth = hit.right - hit.left
        val hitHeight = hit.bottom - hit.top
        assertTrue("hit width $hitWidth < 48dp", hitWidth >= 48.dp)
        assertTrue("hit height $hitHeight < 48dp", hitHeight >= 48.dp)
    }

    @Test
    fun reactionChip_plainState_visualPillStaysCompact() {
        composeRule.setContent {
            AppTheme {
                ReactionChip(
                    content = "+1",
                    count = 3,
                    reacted = false,
                    reactedStateText = "Reacted",
                    onToggle = {},
                )
            }
        }

        // clickable 会 merge 子树 → 胶囊节点必须走 unmerged tree 才找得到
        val pill =
            composeRule
                .onNodeWithTag(REACTION_CHIP_PILL_TAG, useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        val pillHeight = pill.bottom - pill.top
        assertTrue(
            "visual pill should not be inflated to the 48dp touch target: $pillHeight",
            pillHeight <= REACTION_CHIP_MAX_VISUAL_HEIGHT,
        )
    }

    @Test
    fun reactionChip_clickBelowVisualPill_stillToggles() {
        var toggles = 0
        composeRule.setContent {
            AppTheme {
                Column {
                    ReactionChip(
                        content = "+1",
                        count = 0,
                        reacted = false,
                        reactedStateText = "Reacted",
                        onToggle = { toggles++ },
                    )
                }
            }
        }

        val hit = composeRule.onNodeWithTag(REACTION_CHIP_HIT_TAG).getUnclippedBoundsInRoot()
        val pill =
            composeRule
                .onNodeWithTag(REACTION_CHIP_PILL_TAG, useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        // 胶囊底边与命中区底边之间那段「看得见但没有胶囊」的区域
        val gapToBottom = hit.bottom - pill.bottom
        assertTrue("test premise: hit area must extend below the pill", gapToBottom > 0.dp)

        composeRule
            .onNodeWithTag(REACTION_CHIP_HIT_TAG)
            .performTouchInput { click(Offset(centerX, bottom - 1f)) }

        assertEquals(1, toggles)
    }

    @Test
    fun reactionChip_reactedState_stillKeepsFortyEightDpHitArea() {
        var toggles = 0
        composeRule.setContent {
            AppTheme {
                ReactionChip(
                    content = "heart",
                    count = 2,
                    reacted = true,
                    reactedStateText = "Reacted",
                    onToggle = { toggles++ },
                )
            }
        }

        val hit = composeRule.onNodeWithTag(REACTION_CHIP_HIT_TAG).getUnclippedBoundsInRoot()
        assertTrue("hit height ${hit.bottom - hit.top} < 48dp", hit.bottom - hit.top >= 48.dp)

        composeRule.onNodeWithTag(REACTION_CHIP_HIT_TAG).performTouchInput { click(center) }
        assertEquals(1, toggles)
    }
}
