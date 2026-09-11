package com.yumiru11.githubapp.core.markdown.webview

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * WebView 路径的 **Material You 令牌注入契约**回归（plan.md §2.10）。
 *
 * ## 这一层能证明什么
 *
 * 注入 HTML 的 `<style id="theme-vars">` 里是否按 §2.10 声明了全部 Material You 变量、
 * 是否用 Kotlin **预计算**的混色值（而不是真机不支持的 CSS `color-mix()`）、以及自维护
 * 的 `markdown-you.css` 是否引用同一套变量名。这些都是**产物可查**的事实，
 * 不依赖 WebView 栅格化。
 *
 * ## 这一层不能证明什么
 *
 * 不能证明这些变量的**视觉结果**（真机 WebView 是否真的按变量上色）——那需要真实像素，
 * 由 CI 模拟器截图（`.github/scripts/screenshots.sh`）与真机走查兜底。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class WebViewMaterialYouTokenContractTest {
    private val scheme = lightColorScheme()
    private val darkScheme = darkColorScheme()

    @Test
    fun buildCss_planSection210ColorRoles_areAllInjected() {
        val css = MaterialYouFusionMapper.buildCss(scheme, isDark = false)

        // plan.md §2.10 逐条列出的颜色变量
        val required =
            listOf(
                "--md-sys-color-primary",
                "--md-sys-color-on-surface",
                "--md-sys-color-surface-container-low",
                "--md-sys-color-surface-container-high",
                "--md-sys-color-outline-variant",
            )

        val missing = required.filterNot { css.contains(it) }
        assertTrue("§2.10 列出的颜色变量必须全部注入，缺：$missing", missing.isEmpty())
    }

    @Test
    fun buildCss_materialRoleFamily_isInjectedBeyondPlanSection210() {
        // §2.10 只点名 5 个颜色变量；实现注入完整 M3 角色族（融合方案要求）。
        // 本断言锁住「完整族」这一事实，防止以「§2.10 只列了 5 个」为由被裁剪。
        val css = MaterialYouFusionMapper.buildCss(scheme, isDark = false)
        val family =
            listOf(
                "--md-sys-color-primary-container",
                "--md-sys-color-secondary",
                "--md-sys-color-tertiary",
                "--md-sys-color-error",
                "--md-sys-color-error-container",
                "--md-sys-color-background",
                "--md-sys-color-surface",
                "--md-sys-color-surface-container",
                "--md-sys-color-surface-container-lowest",
                "--md-sys-color-surface-container-highest",
                "--md-sys-color-inverse-surface",
                "--md-sys-color-outline",
                "--md-sys-color-scrim",
            )

        val missing = family.filterNot { css.contains(it) }
        assertTrue("M3 角色族必须完整注入，缺：$missing", missing.isEmpty())
    }

    @Test
    fun buildCss_cornerTokens_followPlanSection210() {
        val css = MaterialYouFusionMapper.buildCss(scheme, isDark = false)

        assertTrue("§2.10 要求 --md-sys-shape-corner-medium", css.contains("--md-sys-shape-corner-medium: 12px"))
        assertTrue("圆角族应同时提供 small/large（组件复用）", css.contains("--md-sys-shape-corner-small"))
        assertTrue(css.contains("--md-sys-shape-corner-large"))
    }

    @Test
    fun buildCss_fontVariables_deviateFromPlanSection210NamesAsDocumented() {
        // 已知偏差（2026-09-11 记录，见 docs/agents/markdown-consistency-2026-09-11.md）：
        // plan.md §2.10 写的是 --md-sys-font-sans / --md-sys-font-mono，实现用的是 GitHub
        // 命名的 --fontStack-sansSerif / --fontStack-monospace，且 markdown-you.css 消费的正是后者。
        // 因此这是**命名漂移**（文档失真），不是功能缺口——字体确实注入了。
        //
        // 本断言双向锁定：若某天补齐 §2.10 名称（或改名），此测试会红，强制同步报告与 plan.md。
        val css = MaterialYouFusionMapper.buildCss(scheme, isDark = false)

        assertTrue("实现注入的是 --fontStack-sansSerif", css.contains("--fontStack-sansSerif"))
        assertTrue("实现注入的是 --fontStack-monospace", css.contains("--fontStack-monospace"))
        assertFalse("§2.10 文档中的 --md-sys-font-sans 目前未注入（命名漂移，非功能缺口）", css.contains("--md-sys-font-sans"))
        assertFalse("§2.10 文档中的 --md-sys-font-mono 目前未注入（命名漂移，非功能缺口）", css.contains("--md-sys-font-mono"))
    }

    @Test
    fun buildCss_themeVars_containNoCssColorMix() {
        // AGENTS.md 铁律：真机 WebView（vivo 系统 WebView）实测 CSS.supports('color-mix') == false，
        // 混色必须由 Kotlin 预计算。注入块里出现 color-mix( 即为回归。
        listOf(false, true).forEach { isDark ->
            val css = MaterialYouFusionMapper.buildCss(if (isDark) darkScheme else scheme, isDark = isDark)
            assertFalse("注入的主题变量不得含 color-mix()（isDark=$isDark）", css.contains("color-mix("))
        }
    }

    @Test
    fun bundledWebviewCss_containNoCssColorMix() {
        val cssFiles = listOf("github-markdown.css", "markdown-you.css", "highlight-theme.css")

        cssFiles.forEach { name ->
            val file = File("src/main/assets/webview/$name")
            assertTrue("CSS 资源必须存在（路径漂移会导致本测试静默空转）: ${file.path}", file.isFile)
            assertFalse("$name 不得含 color-mix()（真机 WebView 不支持，会静默失效）", file.readText().contains("color-mix("))
        }
    }

    @Test
    fun buildCss_preMixedBackgrounds_areConcreteColorLiterals() {
        val css = MaterialYouFusionMapper.buildCss(scheme, isDark = false)

        // 预混色变量必须落到具体颜色字面量，而不是 var()/color-mix() 转交浏览器
        val preMixed =
            listOf(
                "--alert-note-bg",
                "--alert-tip-bg",
                "--alert-important-bg",
                "--alert-warning-bg",
                "--alert-caution-bg",
                "--code-bg",
                "--inline-code-bg",
            )

        preMixed.forEach { name ->
            val line = css.lineSequence().firstOrNull { it.trimStart().startsWith("$name:") }
            assertTrue("预混色变量 $name 必须被注入", line != null)
            assertTrue(
                "预混色变量 $name 必须是具体颜色字面量，实际：$line",
                line!!.contains(Regex("""#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})|rgba?\(""")),
            )
        }
    }

    @Test
    fun buildCss_selectorList_includesRootMarkdownBodyAndDataTheme() {
        val css = MaterialYouFusionMapper.buildCss(scheme, isDark = true)

        assertTrue(css.contains(":root"))
        assertTrue("变量必须同时声明在 .markdown-body（github-markdown-css 的作用域）", css.contains(".markdown-body"))
        assertTrue("变量必须声明在 [data-theme=\"dark\"]（深色翻转）", css.contains("[data-theme=\"dark\"]"))
    }

    @Test
    fun buildCss_lightAndDark_injectDifferentConcreteValues() {
        val light = MaterialYouFusionMapper.buildCss(scheme, isDark = false)
        val dark = MaterialYouFusionMapper.buildCss(darkScheme, isDark = true)

        assertTrue("浅色注入块必须带 [data-theme=\"light\"]", light.contains("[data-theme=\"light\"]"))
        assertTrue("深浅两套注入块不能逐字节相同（主题切换必须真的改值）", light != dark)
    }

    @Test
    fun buildStartScript_injectsSameColorVarsAsStyleBlock_andNoMarkup() {
        val script = MaterialYouFusionMapper.buildStartScript(scheme, isDark = false)

        assertTrue("首帧注入脚本必须设置 data-theme", script.contains("setAttribute('data-theme','light')"))
        assertTrue("首帧注入脚本必须内联 --md-sys-color-primary", script.contains("--md-sys-color-primary"))
        assertFalse("注入脚本不得含 color-mix()", script.contains("color-mix("))
        assertFalse("注入脚本不得携带任何 HTML 标签（只允许 setProperty）", script.contains("<"))
    }

    @Test
    fun buildCss_containsNoColorMixEvenForAllBundledThemeSchemes() {
        // 覆盖 OLED / 动态色的高频入口：任意 ColorScheme 实例都必须走 Kotlin 预计算路径
        listOf(lightColorScheme(), darkColorScheme()).forEach { candidate ->
            listOf(false, true).forEach { isDark ->
                val css = MaterialYouFusionMapper.buildCss(candidate, isDark = isDark)
                assertFalse(css.contains("color-mix("))
            }
        }
    }
}
