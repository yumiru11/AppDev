package com.yumiru11.githubapp.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [SettingsTopBar] 返回可供性断言（UI-3：设置页无返回箭头）。
 *
 * 设置是二级页（导航进入）：顶栏必须暴露可点击的返回导航图标，且
 * contentDescription 走 stringResource（TalkBack 可读）；点击必须回调宿主接线。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SettingsTopBarBackTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsTopBar_backIcon_clickInvokesOnBackClick() {
        var clicks = 0
        val backLabel = RuntimeEnvironment.getApplication().getString(R.string.settings_back)

        composeRule.setContent { AppTheme { SettingsTopBar(onBackClick = { clicks++ }) } }

        composeRule.onNodeWithContentDescription(backLabel).assertIsDisplayed().performClick()
        assertEquals("返回箭头点击必须回调 onBackClick", 1, clicks)
    }

    @Test
    fun settingsTopBar_rendersTitle() {
        val title = RuntimeEnvironment.getApplication().getString(R.string.settings_title)

        composeRule.setContent { AppTheme { SettingsTopBar(onBackClick = {}) } }

        composeRule.onNodeWithText(title).assertIsDisplayed()
    }
}
