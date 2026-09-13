package com.yumiru11.githubapp.feature.editor

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.designsystem.token.CodeEditorPreferences
import com.yumiru11.githubapp.core.designsystem.token.CodeEditorPreferencesWriter
import com.yumiru11.githubapp.core.designsystem.token.LocalCodeEditorPreferences
import com.yumiru11.githubapp.core.designsystem.token.LocalCodeEditorPreferencesWriter
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Markdown 编辑器软换行开关交互测试（EDITOR-1）。
 *
 * 断言：点击顶栏开关 → 经 [LocalCodeEditorPreferencesWriter] 写回**取反**后的偏好
 * （编辑器不持有第二份状态）；描述随状态切换（开 → 点完写 false）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class MarkdownEditorScreenWrapToggleTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun softWrapToggle_defaultOn_writesDisabledPreferenceThroughWriter() {
        val writes = mutableListOf<Boolean>()
        val writer =
            object : CodeEditorPreferencesWriter {
                override fun setCodeSoftWrap(enabled: Boolean) = Unit

                override fun setMarkdownSoftWrap(enabled: Boolean) {
                    writes += enabled
                }
            }
        composeRule.setContent {
            AppTheme {
                CompositionLocalProvider(
                    LocalCodeEditorPreferences provides CodeEditorPreferences(markdownSoftWrap = true),
                    LocalCodeEditorPreferencesWriter provides writer,
                ) {
                    MarkdownEditorScreen(initialContent = "# title", onClose = {})
                }
            }
        }

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.editor_soft_wrap_on))
            .performClick()

        assertEquals(listOf(false), writes)
    }
}
