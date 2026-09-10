package com.yumiru11.githubapp.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 铃铛未读角标「99+ 上限」边界纯函数单测（issue #85 / audit 缺陷 #14，issue #168 / UI27）。
 *
 * 覆盖 0 / 1 / 99 / 100 / 999 五个边界：≤99 原样显示，>99 一律收敛。
 */
class BadgeCountBoundaryTest {
    @Test
    fun badgeCountOverflows_zeroUnread_isNotOverflow() {
        assertFalse(badgeCountOverflows(0))
    }

    @Test
    fun badgeCountOverflows_singleUnread_isNotOverflow() {
        assertFalse(badgeCountOverflows(1))
    }

    @Test
    fun badgeCountOverflows_exactlyMax_isNotOverflow() {
        assertFalse(badgeCountOverflows(99))
    }

    @Test
    fun badgeCountOverflows_oneAboveMax_isOverflow() {
        assertTrue(badgeCountOverflows(100))
    }

    @Test
    fun badgeCountOverflows_farAboveMax_isOverflow() {
        assertTrue(badgeCountOverflows(999))
    }
}

/**
 * 角标显示文本与语义描述的一致性断言（issue #168 / UI27）：
 * >99 时屏幕显示 99+，TalkBack 也播报 99+（不再报 100/999 这种与屏幕打架的数字）。
 *
 * 文案来自资源（values / values-zh-rCN 成对），此处按默认 locale（en）断言。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppTopBarBadgeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appTopBar_zeroUnread_hidesBadgeAndKeepsGenericBellDescription() {
        setContentWithUnreadCount(0)
        composeRule.onNodeWithContentDescription("Notifications").assertIsDisplayed()
        composeRule.onNodeWithText("0").assertDoesNotExist()
    }

    @Test
    fun appTopBar_singleUnread_showsOneAndSingularDescription() {
        setContentWithUnreadCount(1)
        composeRule.onNodeWithText("1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("1 unread notification").assertIsDisplayed()
    }

    @Test
    fun appTopBar_ninetyNineUnread_showsExactCountAndPluralDescription() {
        setContentWithUnreadCount(99)
        composeRule.onNodeWithText("99").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("99 unread notifications").assertIsDisplayed()
    }

    @Test
    fun appTopBar_oneHundredUnread_capsBadgeAndDescriptionAtNinetyNinePlus() {
        setContentWithUnreadCount(100)
        composeRule.onNodeWithText("99+").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("99+ unread notifications").assertIsDisplayed()
    }

    @Test
    fun appTopBar_nineHundredNinetyNineUnread_capsBadgeAndDescriptionAtNinetyNinePlus() {
        setContentWithUnreadCount(999)
        composeRule.onNodeWithText("99+").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("99+ unread notifications").assertIsDisplayed()
    }

    private fun setContentWithUnreadCount(unreadCount: Int) {
        composeRule.setContent {
            AppTheme {
                AppTopBar(
                    onSearchClick = {},
                    onNotificationClick = {},
                    onProfileClick = {},
                    unreadCount = unreadCount,
                )
            }
        }
    }
}
