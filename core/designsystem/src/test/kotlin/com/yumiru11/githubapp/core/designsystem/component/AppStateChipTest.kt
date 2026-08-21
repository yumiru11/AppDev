package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

class GitHubStatusColorRoleTest {
    @Test
    fun gitHubStatusColorRole_open_mapsToSuccess() {
        assertEquals(AppStateColorRole.SUCCESS, gitHubStatusColorRole(GitHubStatus.OPEN))
    }

    @Test
    fun gitHubStatusColorRole_closed_mapsToDanger() {
        assertEquals(AppStateColorRole.DANGER, gitHubStatusColorRole(GitHubStatus.CLOSED))
    }

    @Test
    fun gitHubStatusColorRole_merged_mapsToTertiary() {
        assertEquals(AppStateColorRole.TERTIARY, gitHubStatusColorRole(GitHubStatus.MERGED))
    }

    @Test
    fun gitHubStatusColorRole_draft_mapsToSurfaceVariant() {
        assertEquals(AppStateColorRole.SURFACE_VARIANT, gitHubStatusColorRole(GitHubStatus.DRAFT))
    }

    @Test
    fun gitHubStatusColorRole_mergeable_mapsToSuccess() {
        assertEquals(AppStateColorRole.SUCCESS, gitHubStatusColorRole(GitHubStatus.MERGEABLE))
    }

    @Test
    fun gitHubStatusColorRole_conflicting_mapsToError() {
        assertEquals(AppStateColorRole.ERROR, gitHubStatusColorRole(GitHubStatus.CONFLICTING))
    }
}

/** [AppStateChip] 语义断言（Robolectric compose-test，不建截图基线，#84 决策 Q3） */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppStateChipTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appStateChip_openStatus_rendersLabel() {
        composeRule.setContent { AppTheme { AppStateChip(status = GitHubStatus.OPEN, label = "Open") } }
        composeRule.onNodeWithText("Open").assertIsDisplayed()
    }

    @Test
    fun appStateChip_allRoles_renderLabel() {
        // 单次 setContent 渲染全部状态（compose rule 生命周期限制一次），
        // 同时覆盖全部色角色分支（JaCoCo 行覆盖）
        val labels = listOf("Open", "Closed", "Merged", "Draft", "Mergeable", "Conflicting")
        composeRule.setContent {
            AppTheme {
                androidx.compose.foundation.layout.Column {
                    GitHubStatus.entries.forEach { status ->
                        AppStateChip(status = status, label = labels[status.ordinal])
                    }
                }
            }
        }
        labels.forEach { label -> composeRule.onNodeWithText(label).assertIsDisplayed() }
    }
}
