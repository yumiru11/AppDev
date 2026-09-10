package com.yumiru11.githubapp.core.designsystem.token

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 毛玻璃逐项开关裁决（#167 / UI03，ui-design §6.3）的纯函数断言。
 *
 * 契约：生效 = 总开关 ∧ 该点位开关；OLED 或高对比任一开启 → 全部点位强制关闭。
 */
class GlassSettingsTest {
    @Test
    fun enabledFor_allDefaults_returnsTrueForEveryScope() {
        val settings = GlassSettings()

        GlassScope.entries.forEach { scope ->
            assertTrue("默认应全开：$scope", settings.enabledFor(scope))
        }
    }

    @Test
    fun enabledFor_masterOff_returnsFalseForEveryScope() {
        val settings = GlassSettings(masterEnabled = false)

        GlassScope.entries.forEach { scope ->
            assertFalse("总开关关闭应全关：$scope", settings.enabledFor(scope))
        }
    }

    @Test
    fun enabledFor_singleScopeOff_affectsOnlyThatScope() {
        val settings = GlassSettings(bottomSheetEnabled = false)

        assertFalse(settings.enabledFor(GlassScope.BOTTOM_SHEET))
        assertTrue(settings.enabledFor(GlassScope.TOP_BAR))
        assertTrue(settings.enabledFor(GlassScope.BOTTOM_BAR))
        assertTrue(settings.enabledFor(GlassScope.PANEL))
    }

    @Test
    fun enabledFor_eachScopeFlagIsIndependent() {
        val settings =
            GlassSettings(
                topBarEnabled = false,
                bottomBarEnabled = true,
                panelEnabled = false,
                bottomSheetEnabled = true,
            )

        assertFalse(settings.enabledFor(GlassScope.TOP_BAR))
        assertTrue(settings.enabledFor(GlassScope.BOTTOM_BAR))
        assertFalse(settings.enabledFor(GlassScope.PANEL))
        assertTrue(settings.enabledFor(GlassScope.BOTTOM_SHEET))
    }

    @Test
    fun withAccessibilityOverrides_oledEnabled_disablesEverything() {
        val settings = GlassSettings().withAccessibilityOverrides(oledEnabled = true, highContrastEnabled = false)

        assertFalse(settings.masterEnabled)
        GlassScope.entries.forEach { scope -> assertFalse(settings.enabledFor(scope)) }
    }

    @Test
    fun withAccessibilityOverrides_highContrastEnabled_disablesEverything() {
        val settings = GlassSettings().withAccessibilityOverrides(oledEnabled = false, highContrastEnabled = true)

        GlassScope.entries.forEach { scope -> assertFalse(settings.enabledFor(scope)) }
    }

    @Test
    fun withAccessibilityOverrides_neitherEnabled_keepsPerScopeFlags() {
        val base = GlassSettings(panelEnabled = false)

        val settings = base.withAccessibilityOverrides(oledEnabled = false, highContrastEnabled = false)

        assertEquals(base, settings)
        assertFalse(settings.enabledFor(GlassScope.PANEL))
        assertTrue(settings.enabledFor(GlassScope.TOP_BAR))
    }

    @Test
    fun glassScope_containsExactlyTheFourAllowlistedSurfaces() {
        // ui-design §6.1 允许清单（8 项里只有 4 项开玻璃）——枚举不得悄悄扩张
        assertEquals(
            listOf(GlassScope.TOP_BAR, GlassScope.BOTTOM_BAR, GlassScope.PANEL, GlassScope.BOTTOM_SHEET),
            GlassScope.entries.toList(),
        )
    }
}
