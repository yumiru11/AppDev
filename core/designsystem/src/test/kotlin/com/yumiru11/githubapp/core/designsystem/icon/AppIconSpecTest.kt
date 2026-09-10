package com.yumiru11.githubapp.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.datastore.model.IconStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * [AppIconSpec] 风格解析矩阵单测（issue #168 / UI12）。
 *
 * 锁住「设置页切图标风格 → 底栏/顶栏换 Material Symbols 变体」的核心纯函数：
 * 3 档风格 × 2 种选中态 = 6 个分支一一断言（ui-design §5.1：选中实心、未选空心）。
 */
class AppIconSpecTest {
    private val outlined = fakeVector("outlined")
    private val outlinedFilled = fakeVector("outlined-filled")
    private val rounded = fakeVector("rounded")
    private val roundedFilled = fakeVector("rounded-filled")

    private val spec =
        AppIconSpec(
            outlined = outlined,
            outlinedFilled = outlinedFilled,
            rounded = rounded,
            roundedFilled = roundedFilled,
        )

    @Test
    fun vectorFor_outlinedStyleUnselected_returnsOutlinedVariant() {
        assertSame(outlined, spec.vectorFor(IconStyle.OUTLINED, selected = false))
    }

    @Test
    fun vectorFor_outlinedStyleSelected_returnsOutlinedFilledVariant() {
        assertSame(outlinedFilled, spec.vectorFor(IconStyle.OUTLINED, selected = true))
    }

    @Test
    fun vectorFor_roundedStyleUnselected_returnsRoundedVariant() {
        assertSame(rounded, spec.vectorFor(IconStyle.ROUNDED, selected = false))
    }

    @Test
    fun vectorFor_roundedStyleSelected_returnsRoundedFilledVariant() {
        assertSame(roundedFilled, spec.vectorFor(IconStyle.ROUNDED, selected = true))
    }

    @Test
    fun vectorFor_filledStyle_returnsRoundedFilledForBothStates() {
        // FILLED = 恒填充：Material Symbols 没有独立 Filled 家族，填充即圆润家族的 FILL=1
        assertSame(roundedFilled, spec.vectorFor(IconStyle.FILLED, selected = false))
        assertSame(roundedFilled, spec.vectorFor(IconStyle.FILLED, selected = true))
    }

    @Test
    fun single_octiconsLikeSpec_returnsSameVectorForEveryStyleAndState() {
        // GitHub 专属图标（Octicons）无风格家族：选中态由 M3 胶囊/着色表达（ADR-0006）
        val only = fakeVector("repo")
        val single = AppIconSpec.single(only)

        IconStyle.entries.forEach { style ->
            assertSame(only, single.vectorFor(style, selected = false))
            assertSame(only, single.vectorFor(style, selected = true))
        }
    }

    @Test
    fun appIconsCatalog_home_hasFourDistinctMaterialSymbolsVariants() {
        // 目录条目必须真的来自四个不同家族（防止某档误用同一矢量导致「切了没变化」）
        val home = AppIcons.Home
        assertEquals(
            4,
            setOf(home.outlined, home.outlinedFilled, home.rounded, home.roundedFilled).size,
        )
    }

    @Test
    fun appIconsCatalog_repo_isOcticonSingleVector() {
        val repo = AppIcons.Repo
        assertEquals(repo.outlined, AppDevOcticons.Repo)
        assertEquals(repo.rounded, repo.roundedFilled)
    }

    private fun fakeVector(name: String): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).addPath(
                pathData = emptyList(),
                fill = SolidColor(Color.Black),
            ).build()
}
