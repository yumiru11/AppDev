package com.yumiru11.githubapp.core.editor

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.yumiru11.githubapp.core.designsystem.theme.DefaultExtendedColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M3 → 编辑器主题映射测试（plan.md §8.2 映射表逐条验证）。
 *
 * 纯函数层测试：不依赖 Compose 运行时（lightColorScheme/darkColorScheme 为纯数据构造）。
 */
class M3EditorThemeTest {
    private val light = lightColorScheme()
    private val dark = darkColorScheme()

    @Test
    fun m3EditorThemeTokens_lightScheme_mapsEachRolePerSpec() {
        val tokens = m3EditorThemeTokens(light, DefaultExtendedColors)

        assertEquals(light.surfaceContainerLow, tokens.background)
        assertEquals(light.onSurface, tokens.text)
        assertEquals(light.onSurfaceVariant, tokens.lineNumber)
        assertEquals(light.primaryContainer, tokens.selection)
        assertEquals(light.surfaceContainerHigh, tokens.currentLine)
        assertEquals(light.tertiary, tokens.keyword)
        assertEquals(DefaultExtendedColors.success, tokens.string)
        assertEquals(light.onSurfaceVariant, tokens.comment)
        assertEquals(light.primary, tokens.function)
        assertEquals(light.secondary, tokens.number)
    }

    @Test
    fun m3EditorThemeTokens_darkScheme_mapsEachRolePerSpec() {
        val tokens = m3EditorThemeTokens(dark, DefaultExtendedColors)

        assertEquals(dark.surfaceContainerLow, tokens.background)
        assertEquals(dark.onSurface, tokens.text)
        assertEquals(dark.onSurfaceVariant, tokens.lineNumber)
        assertEquals(dark.primaryContainer, tokens.selection)
        assertEquals(dark.surfaceContainerHigh, tokens.currentLine)
        assertEquals(dark.tertiary, tokens.keyword)
        assertEquals(dark.onSurfaceVariant, tokens.comment)
        assertEquals(dark.primary, tokens.function)
        assertEquals(dark.secondary, tokens.number)
    }

    @Test
    fun m3EditorThemeTokens_extendedColorsSuccess_drivesStringColor() {
        // string = success 来自 ExtendedColors（语义色），非 colorScheme 角色
        val tokens = m3EditorThemeTokens(light, DefaultExtendedColors)
        assertEquals(DefaultExtendedColors.success, tokens.string)
    }

    @Test
    fun buildEditorThemeJson_containsAllChromeColors() {
        val tokens = m3EditorThemeTokens(light, DefaultExtendedColors)
        val json = buildEditorThemeJson("M3 Editor", tokens)

        assertTrue("editor.background 存在", json.contains("\"editor.background\""))
        assertTrue("editor.foreground 存在", json.contains("\"editor.foreground\""))
        assertTrue("editorLineNumber.foreground 存在", json.contains("\"editorLineNumber.foreground\""))
        assertTrue("editor.selectionBackground 存在", json.contains("\"editor.selectionBackground\""))
        assertTrue("editor.lineHighlightBackground 存在", json.contains("\"editor.lineHighlightBackground\""))
    }

    @Test
    fun buildEditorThemeJson_containsAllTokenScopeRules() {
        val tokens = m3EditorThemeTokens(light, DefaultExtendedColors)
        val json = buildEditorThemeJson("M3 Editor", tokens)

        for (rule in TOKEN_SCOPE_RULES) {
            assertTrue("scope ${rule.scope} 存在", json.contains("\"scope\": \"${rule.scope}\""))
        }
    }

    @Test
    fun buildEditorThemeJson_hexColorsAreSixDigit() {
        val tokens = m3EditorThemeTokens(light, DefaultExtendedColors)
        val json = buildEditorThemeJson("M3 Editor", tokens)

        // #RRGGBB 六位十六进制（无 alpha；TextMate 主题色不携带透明度）
        val hex = Regex("#[0-9A-F]{6}")
        val colorValues = hex.findAll(json).toList()
        assertTrue("至少 5 个 chrome 色", colorValues.size >= 5)
    }

    @Test
    fun buildEditorThemeJson_isStructurallyBalancedJson() {
        // 纯 JVM 无 org.json 实现：以花括号/方括号配平 + 引号转义校验结构完整性
        val tokens = m3EditorThemeTokens(dark, DefaultExtendedColors)
        val json = buildEditorThemeJson("M3 Editor", tokens)

        assertTrue(json.trimStart().startsWith("{"))
        assertTrue(json.trimEnd().endsWith("}"))
        assertEquals(openCount(json, '{'), closeCount(json, '}'))
        assertEquals(openCount(json, '['), closeCount(json, ']'))
        assertEquals(1, "\"colors\"".toRegex().findAll(json).count())
        assertEquals(1, "\"tokenColors\"".toRegex().findAll(json).count())
    }

    private fun openCount(
        text: String,
        ch: Char,
    ): Int = text.count { it == ch }

    private fun closeCount(
        text: String,
        ch: Char,
    ): Int = text.count { it == ch }

    @Test
    fun buildEditorThemeJson_tokenColorsUseMappedHexValues() {
        val tokens = m3EditorThemeTokens(light, DefaultExtendedColors)
        val json = buildEditorThemeJson("M3 Editor", tokens)

        fun hex(color: androidx.compose.ui.graphics.Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)
        assertTrue("keyword 用 tertiary", json.contains(hex(light.tertiary)))
        assertTrue("string 用 success", json.contains(hex(DefaultExtendedColors.success)))
        assertTrue("function 用 primary", json.contains(hex(light.primary)))
        assertTrue("number 用 secondary", json.contains(hex(light.secondary)))
    }
}
