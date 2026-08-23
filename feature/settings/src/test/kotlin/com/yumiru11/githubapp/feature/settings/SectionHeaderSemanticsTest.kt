package com.yumiru11.githubapp.feature.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [SectionHeader] 语义断言（#87 缺陷 #19）：分组头暴露 heading() 语义，
 * TalkBack 可按节跳转；文本正常渲染。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SectionHeaderSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sectionHeader_rendersGroupText() {
        composeRule.setContent { AppTheme { SectionHeader(text = "外观") } }
        composeRule.onNodeWithText("外观").assertIsDisplayed()
    }

    @Test
    fun sectionHeader_exposesHeadingSemantics() {
        composeRule.setContent { AppTheme { SectionHeader(text = "通用") } }
        composeRule
            .onNodeWithText("通用")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun sectionHeader_allGroups_exposeHeadingSemantics() {
        val groups = listOf("外观", "开发者", "通用")
        composeRule.setContent {
            AppTheme {
                androidx.compose.foundation.layout.Column {
                    groups.forEach { SectionHeader(text = it) }
                }
            }
        }
        groups.forEach { group ->
            composeRule
                .onNodeWithText(group)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        }
    }
}
