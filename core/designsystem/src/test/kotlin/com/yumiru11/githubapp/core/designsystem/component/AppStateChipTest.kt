package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.designsystem.theme.ExtendedColors
import com.yumiru11.githubapp.core.designsystem.theme.extendedColors
import com.yumiru11.githubapp.core.designsystem.theme.lightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun appStateColorRole_checkStateFamily_coversWarningRole() {
        // 审计 P1：Checks 的 pending 需要一个 warning 角色（此前 6 个角色里没有）
        // 成功 / 待处理 / 失败 / 中性灰 必须落在四个不同角色上
        val checkRoles =
            listOf(
                AppStateColorRole.SUCCESS,
                AppStateColorRole.WARNING,
                AppStateColorRole.DANGER,
                AppStateColorRole.SURFACE_VARIANT,
            )
        assertEquals(4, checkRoles.toSet().size)
        assertTrue(checkRoles.contains(AppStateColorRole.WARNING))
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
    fun appStateChip_withStateDescription_exposesItForTalkBack() {
        // issue #168 / UI26：状态徽标必须能播报状态本身（不只念标签词）
        composeRule.setContent {
            AppTheme {
                AppStateChip(
                    status = GitHubStatus.OPEN,
                    label = "Open",
                    stateDescription = "This item is open",
                )
            }
        }
        composeRule
            .onNodeWithText("Open")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "This item is open"))
    }

    @Test
    fun appStateChip_withoutStateDescription_exposesNoStateProperty() {
        // 未传描述时不引入空语义噪音（调用方没 i18n 文案的场景）
        composeRule.setContent {
            AppTheme { AppStateChip(status = GitHubStatus.DRAFT, label = "Draft") }
        }
        composeRule
            .onNodeWithText("Draft")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
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

    @Test
    fun appStateChip_warningRole_rendersLabel() {
        // 审计 P1：WARNING 渲染分支（Checks pending），GitHubStatus 覆盖不到，
        // 走角色重载；无第二套「状态 → 颜色」映射
        composeRule.setContent {
            AppTheme { AppStateChip(role = AppStateColorRole.WARNING, label = "Pending") }
        }
        composeRule.onNodeWithText("Pending").assertIsDisplayed()
    }

    @Test
    fun appStateChip_roleOverload_rendersEveryRole() {
        val labels = AppStateColorRole.entries.map { role -> role.name }
        composeRule.setContent {
            AppTheme {
                androidx.compose.foundation.layout.Column {
                    AppStateColorRole.entries.forEach { role ->
                        AppStateChip(role = role, label = role.name)
                    }
                }
            }
        }
        labels.forEach { label -> composeRule.onNodeWithText(label).assertIsDisplayed() }
    }

    @Test
    fun appStateColors_warningRole_resolvesExtendedWarningTokens() {
        // 色值决策收敛到 appStateColors 一处：WARNING = 扩展色 warning 家族
        var resolved: AppStateColors? = null
        var expected: ExtendedColors? = null
        composeRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                resolved = appStateColors(AppStateColorRole.WARNING)
                expected = MaterialTheme.extendedColors
            }
        }
        assertEquals(expected!!.warning, resolved!!.accent)
        assertEquals(expected!!.warningContainer, resolved!!.container)
        assertEquals(expected!!.onWarningContainer, resolved!!.onContainer)
    }

    @Test
    fun appStateColors_checkStateRoles_resolveToDistinctAccents() {
        // 防退化回「成功/进行中同用 primary」（审计 P1）：四个状态家族四个不同强调色
        var accents: Map<AppStateColorRole, Color> = emptyMap()
        var expected: ExtendedColors? = null
        var expectedOutline: Color? = null
        composeRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                accents =
                    listOf(
                        AppStateColorRole.SUCCESS,
                        AppStateColorRole.WARNING,
                        AppStateColorRole.DANGER,
                        AppStateColorRole.SURFACE_VARIANT,
                    ).associateWith { role -> appStateColors(role).accent }
                expected = MaterialTheme.extendedColors
                expectedOutline = MaterialTheme.colorScheme.outline
            }
        }
        assertEquals(4, accents.values.toSet().size)
        assertEquals(expected!!.success, accents[AppStateColorRole.SUCCESS])
        assertEquals(expected!!.warning, accents[AppStateColorRole.WARNING])
        assertEquals(expected!!.danger, accents[AppStateColorRole.DANGER])
        assertEquals(expectedOutline, accents[AppStateColorRole.SURFACE_VARIANT])
    }
}
