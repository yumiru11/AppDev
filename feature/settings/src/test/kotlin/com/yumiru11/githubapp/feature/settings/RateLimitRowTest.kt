package com.yumiru11.githubapp.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.githubrest.http.RateLimitSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [RateLimitRow] 渲染测试（GATE-2）。
 *
 * 钉死两条契约：快照可用时展示真实「剩余 / 上限 + 重置分钟」；快照缺失时展示「暂无数据」
 * 占位（空态，不是崩溃/空白）。文案全部取自资源表，测试用 context.getString 同源比对。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RateLimitRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun rateLimitRow_snapshotWithRemaining_rendersUsageAndReset() {
        val snapshot =
            RateLimitSnapshot(
                limit = 5000,
                remaining = 1234,
                resetEpochSeconds = System.currentTimeMillis() / 1000 + ONE_HOUR_SECONDS,
                resource = "core",
            )
        val expectedResetMinutes = snapshot.resetInMinutes()

        composeRule.setContent {
            AppTheme { RateLimitRow(snapshot) }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.settings_rate_limit_usage, 1234, 5000))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.settings_rate_limit_reset, expectedResetMinutes))
            .assertIsDisplayed()
    }

    @Test
    fun rateLimitRow_noSnapshot_rendersFallbackPlaceholder() {
        composeRule.setContent {
            AppTheme { RateLimitRow(null) }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.settings_rate_limit_no_data))
            .assertIsDisplayed()
    }

    private companion object {
        const val ONE_HOUR_SECONDS = 3_600L
    }
}
