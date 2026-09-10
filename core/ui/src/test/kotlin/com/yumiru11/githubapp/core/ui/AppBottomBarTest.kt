package com.yumiru11.githubapp.core.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons
import com.yumiru11.githubapp.core.designsystem.icon.AppIcons
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 底栏语义与图标来源（issue #168 / UI23，ui-audit #12）。
 *
 * - 三个大分区各有本地化标签（en/zh 成对维护），点击回传对应路由
 * - 「仓库」Tab 的图标必须是 **Octicons repo**，不再是 Star（Star = 收藏语义，
 *   ui-design §1.2：GitHub 独有功能必须用 GitHub 图标）
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppBottomBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appBottomBar_rendersThreeSectionsWithLocalizedLabels() {
        composeRule.setContent {
            AppTheme { AppBottomBar(selectedTab = MainTab.HOME, onTabSelected = {}) }
        }

        composeRule.onNodeWithText("Home").assertExists()
        composeRule.onNodeWithText("Repos").assertExists()
        composeRule.onNodeWithText("Profile").assertExists()
    }

    @Test
    fun appBottomBar_tapReposTab_reportsReposRoute() {
        var selected: String? = null
        composeRule.setContent {
            AppTheme { AppBottomBar(selectedTab = MainTab.HOME, onTabSelected = { selected = it }) }
        }

        composeRule.onNodeWithText("Repos").performClick()
        assertEquals(MainTab.REPOS, selected)
    }

    @Test
    fun appBottomBar_reposTab_usesOcticonRepoNotStar() {
        // UI23 回归护栏：仓库 Tab 的矢量必须来自 AppDevOcticons.Repo
        assertEquals(AppDevOcticons.Repo, AppIcons.Repo.rounded)
        assertEquals(AppDevOcticons.Repo, AppIcons.Repo.outlined)
    }
}
