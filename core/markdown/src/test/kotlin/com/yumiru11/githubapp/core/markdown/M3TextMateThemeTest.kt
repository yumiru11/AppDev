package com.yumiru11.githubapp.core.markdown

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [M3TextMateTheme] 纯函数层测试。
 *
 * 测试 scope→M3 映射、色值工具函数、模板 JSON 替换。
 * 不依赖 Android 环境/Compose/Robolectric（纯 JUnit4 + kotlin.test）。
 */
class M3TextMateThemeTest {
    private val lightScheme = lightColorScheme()

    // ── 切片 1：light 主题 foreground 映射 ──────────────────────────────────

    @Test
    fun m3Theme_lightTheme_foregroundMatchesM3Role() {
        // keyword → primary
        assertEquals(lightScheme.primary, resolveScopeColor("keyword.control", lightScheme))
        // storage.modifier → primary
        assertEquals(lightScheme.primary, resolveScopeColor("storage.modifier", lightScheme))
        // entity.name.function / support.function → tertiary
        assertEquals(lightScheme.tertiary, resolveScopeColor("entity.name.function", lightScheme))
        assertEquals(lightScheme.tertiary, resolveScopeColor("support.function", lightScheme))
        // storage.type / entity.name.type → secondary
        assertEquals(lightScheme.secondary, resolveScopeColor("storage.type", lightScheme))
        assertEquals(lightScheme.secondary, resolveScopeColor("entity.name.type", lightScheme))
        // variable → onSurfaceVariant
        assertEquals(lightScheme.onSurfaceVariant, resolveScopeColor("variable", lightScheme))
        // comment → outline
        assertEquals(lightScheme.outline, resolveScopeColor("comment", lightScheme))
        // invalid → error
        assertEquals(lightScheme.error, resolveScopeColor("invalid", lightScheme))
    }
}

class M3TextMateThemeTestDark {
    private val darkScheme = darkColorScheme()

    @Test
    fun m3Theme_darkTheme_foregroundMatchesM3Role() {
        // keyword → primary
        assertEquals(darkScheme.primary, resolveScopeColor("keyword.control", darkScheme))
        // entity.name.function → tertiary
        assertEquals(darkScheme.tertiary, resolveScopeColor("entity.name.function", darkScheme))
        // storage.type / entity.name.type → secondary
        assertEquals(darkScheme.secondary, resolveScopeColor("storage.type", darkScheme))
        // comment → outline
        assertEquals(darkScheme.outline, resolveScopeColor("comment", darkScheme))
        // variable → onSurfaceVariant
        assertEquals(darkScheme.onSurfaceVariant, resolveScopeColor("variable", darkScheme))
        // invalid → error
        assertEquals(darkScheme.error, resolveScopeColor("invalid", darkScheme))
    }

    @Test
    fun m3Theme_punctuation_readableInBothThemes() {
        // 回归原型符号透明 bug：标点/操作符必须有明确前景色（非透明、非纯黑/纯白极端）
        val punctScopes = listOf("punctuation.definition", "punctuation.separator", "keyword.operator")
        for (scope in punctScopes) {
            val lightColor = resolveScopeColor(scope, lightColorScheme())
            val darkColor = resolveScopeColor(scope, darkColorScheme())
            assertTrue("punctuation $scope 在 light 下不可读: $lightColor", lightColor != Color.Transparent)
            assertTrue("punctuation $scope 在 dark 下不可读: $darkColor", darkColor != Color.Transparent)
        }
        // 与背景对比：onSurfaceVariant/outlineVariant 都是语义可读色
        assertNotEquals(lightColorScheme().background, resolveScopeColor("punctuation.definition", lightColorScheme()))
    }
}

class M3TextMateThemeJsonTest {
    @Test
    fun m3Theme_italicFontStyle_preservedInJson() {
        val scheme = lightColorScheme()
        val json = buildThemeJson("test", scheme)
        // markup.italic → italic fontStyle
        assertTrue("italic fontStyle 未保留", json.contains("\"scope\": \"markup.italic\""))
        assertTrue("italic fontStyle 缺失", json.contains("\"fontStyle\": \"italic\""))
        // markup.bold → bold fontStyle
        assertTrue("bold fontStyle 缺失", json.contains("\"fontStyle\": \"bold\""))
        // 默认规则无 fontStyle（仅 markup.* 显式设置）
        val keywordRule = Regex("\"scope\": \"keyword\"[^}]*}").find(json)?.value.orEmpty()
        assertFalse("keyword 不应有 fontStyle", keywordRule.contains("fontStyle"))
        // scope 结构保留：映射表里所有规则全部输出
        assertEquals(SCOPE_RULES.size, Regex("\"scope\":").findAll(json).count())
    }
}
