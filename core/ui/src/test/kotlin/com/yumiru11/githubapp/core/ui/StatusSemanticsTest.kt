package com.yumiru11.githubapp.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.yumiru11.githubapp.core.designsystem.component.GitHubStatus
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * GitHub 状态播报文案映射（issue #168 / UI26）。
 *
 * 断言 6 个状态各自解析到本地化短语（默认 en 资源），覆盖 when 全分支；
 * 文案本体在 values / values-zh-rCN 成对维护（zh：「该项已开启」等）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class StatusSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun gitHubStatusStateDescription_allStatuses_resolveToLocalizedPhrases() {
        composeRule.setContent {
            AppTheme {
                Column {
                    GitHubStatus.entries.forEach { status ->
                        Text(text = gitHubStatusStateDescription(status))
                    }
                }
            }
        }

        EXPECTED_EN_PHRASES.forEach { phrase ->
            composeRule.onNodeWithText(phrase).assertExists()
        }
    }

    private companion object {
        /** 与 values/strings.xml 的 state_description_* 一一对应（en 默认资源）。 */
        val EXPECTED_EN_PHRASES =
            listOf(
                "This item is open",
                "This item is closed",
                "This pull request is merged",
                "Draft pull request",
                "No merge conflicts",
                "Has merge conflicts",
            )
    }
}
