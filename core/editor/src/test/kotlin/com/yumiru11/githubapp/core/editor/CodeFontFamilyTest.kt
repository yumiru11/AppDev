package com.yumiru11.githubapp.core.editor

import com.yumiru11.githubapp.core.datastore.model.CodeFont
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `CodeFont` → 字形家族映射测试（纯 JVM，无 Robolectric / 无 Sora）。
 *
 * 为什么只测到「家族」这一层：`Typeface.MONOSPACE` / `Typeface.DEFAULT` 在 mockable
 * android.jar 下恒为 null，直接断言 Typeface 会写成「两边都是 null」的假绿测试。
 * 「家族 → Typeface」与「应用到 Sora 实例」由 [CodeFontTypefaceTest] 在 Robolectric 下断言。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class CodeFontFamilyTest {
    @Test
    fun codeFontFamily_mono_mapsToMonospace() {
        assertEquals(CodeFontFamily.MONOSPACE, codeFontFamily(CodeFont.MONO))
    }

    @Test
    fun codeFontFamily_system_mapsToPlatformDefault() {
        assertEquals(CodeFontFamily.PLATFORM_DEFAULT, codeFontFamily(CodeFont.SYSTEM))
    }

    @Test
    fun codeFontFamily_everyPreference_hasDistinctFamily() {
        // 两档偏好必须落到两档不同字形，否则「代码字体」开关会变成两个都一样的假开关
        val families = CodeFont.entries.map(::codeFontFamily)
        assertEquals(CodeFont.entries.size, families.toSet().size)
    }
}
